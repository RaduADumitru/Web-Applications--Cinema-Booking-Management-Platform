import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import Swal from 'sweetalert2';
import { StaffOperationsService } from '@app/core/services/staff-operations.service';
import { RoomResponse, RoomType, SaveRoomRequest, ScreenSessionResponse } from '@app/shared/models/staff-operations.models';

@Component({
  selector: 'app-room-layout-manager',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './room-layout-manager.html',
  styleUrl: '../staff-panel.css',
})
export class RoomLayoutManagerComponent implements OnInit {
  readonly roomTypes: RoomType[] = ['NORMAL', 'IMAX'];

  rooms = signal<RoomResponse[]>([]);
  sessions = signal<ScreenSessionResponse[]>([]);
  loading = signal(false);
  saving = signal(false);
  error = signal<string | null>(null);

  form: SaveRoomRequest = {
    name: '',
    type: 'NORMAL',
    floor: 1,
  };

  selectedRoomId: number | null = null;
  existingSeatId: number | null = null;
  existingSessionId: number | null = null;

  constructor(private staffOperations: StaffOperationsService) {}

  ngOnInit(): void {
    this.loadRooms();
    this.loadSessions();
  }

  loadRooms(): void {
    this.loading.set(true);
    this.error.set(null);

    this.staffOperations.getRooms().subscribe({
      next: (response) => this.rooms.set(response.content),
      error: (error) => {
        console.error('Unable to load rooms:', error);
        this.error.set('Unable to load rooms.');
      },
    }).add(() => this.loading.set(false));
  }

  loadSessions(): void {
    this.staffOperations.getScreenSessions().subscribe({
      next: (response) => this.sessions.set(response.content),
      error: (error) => console.error('Unable to load screen sessions:', error),
    });
  }

  trackBySession(_: number, session: ScreenSessionResponse): number {
    return session.id;
  }

  saveRoom(): void {
    if (this.form.name.trim() === '') {
      return;
    }

    this.saving.set(true);
    this.staffOperations.createRoom(this.form).subscribe({
      next: () => {
        this.form = { name: '', type: 'NORMAL', floor: 1 };
        this.loadRooms();
        Swal.fire('Room created', 'The room is ready for seats and showtimes.', 'success');
      },
      error: (error) => {
        console.error('Unable to create room:', error);
        Swal.fire('Room failed', 'The room could not be created.', 'error');
      },
    }).add(() => this.saving.set(false));
  }

  attachSeat(): void {
    if (this.selectedRoomId == null || this.existingSeatId == null) {
      return;
    }

    this.staffOperations.addSeatToRoom(this.selectedRoomId, this.existingSeatId).subscribe({
      next: () => Swal.fire('Seat attached', 'The existing seat was linked to this room.', 'success'),
      error: (error) => {
        console.error('Unable to attach seat:', error);
        Swal.fire('Attach failed', 'The seat could not be attached.', 'error');
      },
    });
  }

  attachSession(): void {
    if (this.selectedRoomId == null || this.existingSessionId == null) {
      return;
    }

    this.staffOperations.addSessionToRoom(this.selectedRoomId, this.existingSessionId).subscribe({
      next: () => Swal.fire('Showtime attached', 'The existing screen session was linked to this room.', 'success'),
      error: (error) => {
        console.error('Unable to attach showtime:', error);
        Swal.fire('Attach failed', 'The screen session could not be attached.', 'error');
      },
    });
  }

  trackByRoom(_: number, room: RoomResponse): number {
    return room.id;
  }
}
