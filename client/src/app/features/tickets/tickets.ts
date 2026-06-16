import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin, of, Observable } from 'rxjs';
import { switchMap, map, catchError, tap } from 'rxjs/operators';
import Swal from 'sweetalert2';
import { OrdersService } from '@app/core/services/orders.service';
import { OrderResponse } from '@app/shared/models/order.models';
import { TicketResponse } from '@app/shared/models/ticket.models';
import { ScreenSessionResponse, RoomResponse } from '@app/shared/models/staff-operations.models';
import { SeatResponse } from '@app/shared/models/seat.models';

export interface ResolvedTicket {
  id: number;
  orderId: string;
  status: string;
  price: number;
  type: string | null;
  seatRow: number;
  seatNumber: number;
  seatZone: string;
  roomId: number;
  roomName: string;
  screenSessionId: number;
  movieId: number;
  movieTitle: string;
  date: string;
  startTime: string;
  endTime: string;
  format: string | null;
}

@Component({
  selector: 'app-tickets',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './tickets.html',
  styleUrl: './tickets.css'
})
export class TicketsComponent implements OnInit {
  private readonly ordersService = inject(OrdersService);

  ticketsList = signal<ResolvedTicket[]>([]);
  activeTab = signal<'active' | 'pending' | 'past'>('active');
  isLoading = signal<boolean>(false);

  // In-memory caches to prevent redundant HTTP requests for seats, sessions, and rooms
  private sessionCache = new Map<number, ScreenSessionResponse>();
  private seatCache = new Map<number, SeatResponse>();
  private roomCache = new Map<number, RoomResponse>();

  ngOnInit() {
    this.loadTickets();
  }

  loadTickets() {
    this.isLoading.set(true);
    this.ordersService.loadMyOrders().subscribe({
      next: (orders) => {
        // Filter out soft deleted orders
        const validOrders = orders.filter(o => !o.deletedAt);
        const ticketTasks: Observable<ResolvedTicket | null>[] = [];

        for (const order of validOrders) {
          if (order.status === 'CANCELLED') {
            continue; // Skip cancelled order tickets as they are no longer reserved
          }
          if (order.ticketIds && order.ticketIds.length > 0) {
            for (const ticketIdStr of order.ticketIds) {
              const ticketId = Number(ticketIdStr);
              ticketTasks.push(this.resolveTicket(ticketId, order));
            }
          }
        }

        if (ticketTasks.length === 0) {
          this.ticketsList.set([]);
          this.isLoading.set(false);
          return;
        }

        forkJoin(ticketTasks).subscribe({
          next: (resolved) => {
            const list = resolved.filter((t): t is ResolvedTicket => t !== null);
            this.ticketsList.set(list);
            this.isLoading.set(false);
          },
          error: (err) => {
            console.error('Failed to forkJoin tickets resolution:', err);
            this.isLoading.set(false);
          }
        });
      },
      error: (err) => {
        console.error('Failed to load user orders:', err);
        this.isLoading.set(false);
      }
    });
  }

  resolveTicket(ticketId: number, order: OrderResponse): Observable<ResolvedTicket | null> {
    return this.ordersService.getTicket(ticketId).pipe(
      switchMap((ticket) => {
        // 1. Fetch Screen Session (or load from cache)
        const session$ = this.sessionCache.has(ticket.screenSessionId)
          ? of(this.sessionCache.get(ticket.screenSessionId)!)
          : this.ordersService.getScreenSession(ticket.screenSessionId).pipe(
              tap(s => this.sessionCache.set(ticket.screenSessionId, s))
            );

        // 2. Fetch Seat (or load from cache)
        const seat$ = this.seatCache.has(ticket.seatId)
          ? of(this.seatCache.get(ticket.seatId)!)
          : this.ordersService.getSeat(ticket.seatId).pipe(
              tap(s => this.seatCache.set(ticket.seatId, s))
            );

        // 3. Fetch Room (or load from cache)
        const room$ = this.roomCache.has(ticket.roomId)
          ? of(this.roomCache.get(ticket.roomId)!)
          : this.ordersService.getRoom(ticket.roomId).pipe(
              tap(r => this.roomCache.set(ticket.roomId, r))
            );

        return forkJoin({ session: session$, seat: seat$, room: room$ }).pipe(
          map(({ session, seat, room }) => {
            return {
              id: ticket.id,
              orderId: order.id,
              status: order.status,
              price: ticket.price ?? (order.price / order.ticketIds.length), // Estimate ticket portion of total price
              type: ticket.type,
              seatRow: seat.row,
              seatNumber: seat.number,
              seatZone: seat.zone,
              roomId: room.id,
              roomName: room.name,
              screenSessionId: session.id,
              movieId: session.movieId,
              movieTitle: session.movieTitle,
              date: session.date,
              startTime: session.startTime,
              endTime: session.endTime,
              format: session.format
            } as ResolvedTicket;
          }),
          catchError((err) => {
            console.error(`Partial resolve failure for ticket ${ticketId}:`, err);
            // Fallback object to show some info even if a sub-request fails
            return of({
              id: ticket.id,
              orderId: order.id,
              status: order.status,
              price: ticket.price ?? 0,
              type: ticket.type,
              seatRow: 0,
              seatNumber: 0,
              seatZone: 'UNKNOWN',
              roomId: ticket.roomId,
              roomName: `Room #${ticket.roomId}`,
              screenSessionId: ticket.screenSessionId,
              movieId: 0,
              movieTitle: 'Movie Details Unavailable',
              date: '',
              startTime: '',
              endTime: '',
              format: null
            } as ResolvedTicket);
          })
        );
      }),
      catchError((err) => {
        console.error(`Base ticket fetch failure for ID ${ticketId}:`, err);
        return of(null); // Filtered out by the caller
      })
    );
  }

  // Active / Paid Upcoming Tickets
  activeTickets = computed(() => {
    const todayStr = new Date().toISOString().split('T')[0];
    return this.ticketsList().filter(t => {
      if (t.status !== 'PAID') return false;
      if (!t.date) return true;
      return t.date >= todayStr;
    });
  });

  // Pending payment tickets
  pendingTickets = computed(() => {
    return this.ticketsList().filter(t => t.status === 'PENDING');
  });

  // Past / History Tickets
  pastTickets = computed(() => {
    const todayStr = new Date().toISOString().split('T')[0];
    return this.ticketsList().filter(t => {
      if (t.status !== 'PAID') return false;
      if (!t.date) return false;
      return t.date < todayStr;
    });
  });

  getCurrentTabTickets(): ResolvedTicket[] {
    switch (this.activeTab()) {
      case 'pending':
        return this.pendingTickets();
      case 'past':
        return this.pastTickets();
      case 'active':
      default:
        return this.activeTickets();
    }
  }

  setActiveTab(tab: 'active' | 'pending' | 'past') {
    this.activeTab.set(tab);
  }

  formatDate(dateStr: string): string {
    if (!dateStr) return 'TBA';
    try {
      const date = new Date(dateStr);
      return date.toLocaleDateString('en-US', {
        weekday: 'short',
        month: 'short',
        day: 'numeric'
      });
    } catch {
      return dateStr;
    }
  }

  formatTime(timeStr: string): string {
    if (!timeStr) return 'TBA';
    return timeStr.substring(0, 5);
  }

  trackByTicketId(_: number, ticket: ResolvedTicket): number {
    return ticket.id;
  }

  showQRCode(ticket: ResolvedTicket) {
    const ticketInfoString = `Ticket ID: ${ticket.id}\nOrder ID: ${ticket.orderId}\nMovie: ${ticket.movieTitle} (${ticket.format || '2D'})\nDate: ${ticket.date}\nTime: ${ticket.startTime}\nRoom: ${ticket.roomName}\nSeat: Row ${ticket.seatRow}, Seat ${ticket.seatNumber} (${ticket.seatZone})\nType: ${ticket.type || 'ADULT'}\nPrice: EUR ${ticket.price.toFixed(2)}`;

    const qrUrl = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(ticketInfoString)}`;

    Swal.fire({
      title: 'Your Entry Pass',
      html: `
        <div class="qr-modal-container">
          <div class="qr-image-wrapper">
            <img src="${qrUrl}" alt="Ticket QR Code" style="width: 200px; height: 200px;" />
          </div>
          <div class="qr-details-text">
            <p class="font-bold text-lg" style="color: var(--foreground);">${ticket.movieTitle}</p>
            <p class="text-sm mt-1" style="color: var(--foreground-muted); font-family: monospace;">Row ${ticket.seatRow} • Seat ${ticket.seatNumber}</p>
            <p class="text-xs mt-2" style="color: var(--primary); font-weight: bold; text-transform: uppercase;">Show this QR code at the screen entrance</p>
          </div>
        </div>
      `,
      confirmButtonText: 'Close Pass',
      confirmButtonColor: 'var(--primary)',
      customClass: {
        popup: 'swal-bg'
      }
    });
  }
}
