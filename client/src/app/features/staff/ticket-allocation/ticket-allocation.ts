import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import Swal from 'sweetalert2';
import { StaffOperationsService } from '@app/core/services/staff-operations.service';
import { SeatResponse } from '@app/shared/models/seat.models';
import { TicketResponse } from '@app/shared/models/ticket.models';
import { RoomResponse, ScreenSessionResponse } from '@app/shared/models/staff-operations.models';

@Component({
  selector: 'app-ticket-allocation',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ticket-allocation.html',
  styleUrl: '../staff-panel.css',
})
export class TicketAllocationComponent implements OnInit {
  rooms = signal<RoomResponse[]>([]);
  screenSessions = signal<ScreenSessionResponse[]>([]);

  roomId: number | null = null;
  screenSessionId: number | null = null;
  seats = signal<SeatResponse[]>([]);
  tickets = signal<TicketResponse[]>([]);
  loading = signal(false);
  generating = signal(false);

  constructor(private staffOperations: StaffOperationsService) {}

  ngOnInit(): void {
    this.loadMetadata();
  }

  loadMetadata(): void {
    this.staffOperations.getRooms().subscribe({
      next: (response) => this.rooms.set(response.content),
      error: (error) => console.error('Unable to load rooms:', error),
    });

    this.staffOperations.getScreenSessions().subscribe({
      next: (response) => this.screenSessions.set(response.content),
      error: (error) => console.error('Unable to load screen sessions:', error),
    });
  }

  trackByRoom(_: number, room: RoomResponse): number {
    return room.id;
  }

  trackBySession(_: number, session: ScreenSessionResponse): number {
    return session.id;
  }

  missingSeats(): SeatResponse[] {
    const ticketSeatIds = new Set(this.tickets().map((ticket) => ticket.seatId));
    return this.seats().filter((seat) => !ticketSeatIds.has(seat.id));
  }

  loadInventory(): void {
    if (this.roomId == null || this.screenSessionId == null) {
      return;
    }

    this.loading.set(true);
    forkJoin({
      seats: this.staffOperations.getSeatsForSession(this.screenSessionId),
      tickets: this.staffOperations.getTickets(this.screenSessionId, this.roomId),
    }).subscribe({
      next: ({ seats, tickets }) => {
        this.seats.set(seats.content);
        this.tickets.set(tickets.content);
      },
      error: (error) => {
        console.error('Unable to load ticket inventory:', error);
        Swal.fire('Inventory failed', 'Seats or tickets could not be loaded.', 'error');
      },
    }).add(() => this.loading.set(false));
  }

  generateMissingTickets(): void {
    if (this.roomId == null || this.screenSessionId == null) {
      return;
    }

    const missing = this.missingSeats();
    if (missing.length === 0) {
      Swal.fire('Already allocated', 'Every seat already has a ticket for this room and session.', 'info');
      return;
    }

    this.generating.set(true);
    forkJoin(missing.map((seat) => this.staffOperations.createTicket({
      seatId: seat.id,
      roomId: this.roomId as number,
      screenSessionId: this.screenSessionId as number,
    }))).subscribe({
      next: () => {
        Swal.fire('Tickets generated', `${missing.length} tickets are now available for booking.`, 'success');
        this.loadInventory();
      },
      error: (error) => {
        console.error('Unable to generate tickets:', error);
        Swal.fire('Generation failed', 'One or more tickets could not be generated.', 'error');
      },
    }).add(() => this.generating.set(false));
  }
}
