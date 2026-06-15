import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, OnInit, SimpleChanges, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import Swal from 'sweetalert2';
import { SeatSelectionService } from '@app/core/services/seat-selection.service';
import { SeatResponse } from '@app/shared/models/seat.models';
import { TicketResponse, TicketType } from '@app/shared/models/ticket.models';

interface SeatView {
  seat: SeatResponse;
  ticket: TicketResponse | null;
  gridRow: number;
  gridColumn: number;
  isSelected: boolean;
  status: 'available' | 'booked' | 'unavailable';
}

@Component({
  selector: 'app-seat-selection',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './seat-selection.html',
  styleUrl: './seat-selection.css',
})
export class SeatSelectionComponent implements OnInit, OnChanges {
  @Input() sessionId: number | null = null;
  @Input() roomId: number | null = null;
  @Input() movieId: number | null = null;
  @Input() roomType: string | null = null;

  readonly ticketTypes: TicketType[] = ['ADULT', 'STUDENT', 'CHILD'];
  selectedTicketType: TicketType = 'ADULT';
  useDiscount = false;

  seats = signal<SeatResponse[]>([]);
  tickets = signal<TicketResponse[]>([]);
  selectedSeatIds = signal<Set<number>>(new Set<number>());
  isLoading = signal(false);
  isBooking = signal(false);
  error = signal<string | null>(null);

  private initialized = false;

  ticketBySeatId = computed(() => {
    const lookup = new Map<number, TicketResponse>();
    this.tickets().forEach((ticket) => lookup.set(ticket.seatId, ticket));
    return lookup;
  });

  visibleSeats = computed(() => {
    const tickets = this.tickets();
    if (tickets.length === 0) {
      return this.seats();
    }

    const scopedSeatIds = new Set(tickets.map((ticket) => ticket.seatId));
    return this.seats().filter((seat) => scopedSeatIds.has(seat.id));
  });

  rowNumbers = computed(() => {
    return [...new Set(this.visibleSeats().map((seat) => seat.row))].sort((a, b) => a - b);
  });

  maxColumn = computed(() => {
    return this.visibleSeats().reduce((max, seat) => Math.max(max, seat.number), 0);
  });

  gridTemplateColumns = computed(() => {
    const columns = Math.max(this.maxColumn(), 1);
    return `3rem repeat(${columns}, minmax(2.25rem, 1fr))`;
  });

  seatViews = computed<SeatView[]>(() => {
    const rowIndex = new Map<number, number>();
    this.rowNumbers().forEach((row, index) => rowIndex.set(row, index + 1));

    return this.visibleSeats()
      .map((seat) => {
        const ticket = this.ticketBySeatId().get(seat.id) ?? null;
        const isSelected = this.selectedSeatIds().has(seat.id);
        const status: SeatView['status'] = !ticket
          ? 'unavailable'
          : ticket.isAvailable
            ? 'available'
            : 'booked';

        return {
          seat,
          ticket,
          gridRow: rowIndex.get(seat.row) ?? 1,
          gridColumn: seat.number + 1,
          isSelected,
          status,
        };
      })
      .sort((a, b) => a.seat.row - b.seat.row || a.seat.number - b.seat.number);
  });

  selectedViews = computed(() => {
    const selectedIds = this.selectedSeatIds();
    return this.seatViews().filter((view) => selectedIds.has(view.seat.id));
  });

  selectedTotal = computed(() => {
    return this.selectedViews().reduce((total, view) => total + (view.ticket?.price ?? 0), 0);
  });

  availableCount = computed(() => {
    return this.seatViews().filter((view) => view.status === 'available').length;
  });

  bookedCount = computed(() => {
    return this.seatViews().filter((view) => view.status === 'booked').length;
  });

  constructor(
    private route: ActivatedRoute,
    private seatSelectionService: SeatSelectionService,
  ) {}

  ngOnInit(): void {
    this.applyRouteFallbacks();
    this.initialized = true;
    this.loadLayout();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!this.initialized) {
      return;
    }

    if (changes['sessionId'] || changes['roomId'] || changes['movieId'] || changes['roomType']) {
      this.loadLayout();
    }
  }

  loadLayout(): void {
    this.error.set(null);
    this.selectedSeatIds.set(new Set<number>());

    if (this.sessionId == null || this.roomId == null) {
      this.seats.set([]);
      this.tickets.set([]);
      this.error.set('Choose a screening session and room before selecting seats.');
      return;
    }

    this.isLoading.set(true);

    forkJoin({
      seats: this.seatSelectionService.getSeats({
        screenSessionId: this.sessionId,
        movieId: this.movieId ?? undefined,
        roomType: this.roomType ?? undefined,
      }),
      tickets: this.seatSelectionService.getTickets({
        sessionId: this.sessionId,
        roomId: this.roomId,
      }),
    })
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: ({ seats, tickets }) => {
          this.seats.set(seats.content);
          this.tickets.set(tickets.content);
        },
        error: (error) => {
          console.error('Unable to load seat layout:', error);
          this.error.set('Unable to load the room layout. Please try again.');
        },
      });
  }

  toggleSeat(view: SeatView): void {
    if (view.status !== 'available' || this.isBooking()) {
      return;
    }

    const next = new Set(this.selectedSeatIds());
    if (next.has(view.seat.id)) {
      next.delete(view.seat.id);
    } else {
      next.add(view.seat.id);
    }

    this.selectedSeatIds.set(next);
  }

  reserveSelection(): void {
    const ticketIds = this.selectedViews()
      .map((view) => view.ticket?.id)
      .filter((id): id is number => id != null);

    if (ticketIds.length === 0 || this.isBooking()) {
      return;
    }

    this.isBooking.set(true);
    this.seatSelectionService.createOrder(ticketIds, this.selectedTicketType, this.useDiscount)
      .pipe(finalize(() => this.isBooking.set(false)))
      .subscribe({
        next: (order) => {
          Swal.fire('Reserved!', `Order #${order.id} is pending payment.`, 'success');
          this.loadLayout();
        },
        error: (error) => {
          console.error('Unable to reserve selected seats:', error);
          Swal.fire('Reservation failed', 'One or more selected seats could not be booked.', 'error');
          this.loadLayout();
        },
      });
  }

  trackBySeat(_: number, view: SeatView): number {
    return view.seat.id;
  }

  trackByRow(_: number, row: number): number {
    return row;
  }

  formatZone(zone: string): string {
    return zone.replace(/_/g, ' ');
  }

  private applyRouteFallbacks(): void {
    const queryParams = this.route.snapshot.queryParamMap;
    this.sessionId ??= this.readNumberParam(queryParams.get('sessionId'));
    this.roomId ??= this.readNumberParam(queryParams.get('roomId'));
    this.movieId ??= this.readNumberParam(queryParams.get('movieId'));
    this.roomType ??= queryParams.get('roomType');
  }

  private readNumberParam(value: string | null): number | null {
    if (value == null || value.trim() === '') {
      return null;
    }

    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
}
