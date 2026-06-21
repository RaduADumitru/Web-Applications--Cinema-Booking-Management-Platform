import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import Swal from 'sweetalert2';
import { StaffOperationsService } from '@app/core/services/staff-operations.service';
import { SeatZoneOption, RoomResponse, SeatCategoryResponse } from '@app/shared/models/staff-operations.models';

@Component({
  selector: 'app-seat-blueprint',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './seat-blueprint.html',
  styleUrl: '../staff-panel.css',
})
export class SeatBlueprintComponent implements OnInit {
  readonly zones: SeatZoneOption[] = ['VIP', 'A', 'B', 'C', 'D'];

  rooms = signal<RoomResponse[]>([]);
  seatCategories = signal<SeatCategoryResponse[]>([]);

  roomId: number | null = null;
  rows = 6;
  seatsPerRow = 10;
  startRow = 1;
  startSeatNumber = 1;
  zone: SeatZoneOption = 'A';
  creating = signal(false);

  constructor(private staffOperations: StaffOperationsService) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.staffOperations.getRooms().subscribe({
      next: (response) => this.rooms.set(response.content),
      error: (error) => console.error('Unable to load rooms:', error),
    });

    this.staffOperations.getSeatCategories().subscribe({
      next: (response) => this.seatCategories.set(response),
      error: (error) => console.error('Unable to load seat categories:', error),
    });
  }

  trackByRoom(_: number, room: RoomResponse): number {
    return room.id;
  }

  rowPreview(): number[] {
    return Array.from({ length: Math.max(this.rows, 0) }, (_, i) => this.startRow + i);
  }

  seatPreview(): number[] {
    return Array.from({ length: Math.max(this.seatsPerRow, 0) }, (_, i) => this.startSeatNumber + i);
  }

  private resolveCategoryId(): number | null {
    const targetType = this.zone === 'VIP' ? 'VIP' : 'STANDARD';
    return this.seatCategories().find(c => c.type === targetType)?.id ?? null;
  }

  createBlueprint(): void {
    if (this.roomId == null || this.rows < 1 || this.seatsPerRow < 1) return;

    const payload = {
      roomId: this.roomId,
      rows: this.rows,
      seatsPerRow: this.seatsPerRow,
      startRow: this.startRow,
      startSeatNumber: this.startSeatNumber,
      zone: this.zone,
      categoryId: this.resolveCategoryId(),
    };

    this.creating.set(true);
    this.staffOperations.generateSeats(payload).subscribe({
      next: (seats) => Swal.fire('Seats created', `${seats.length} seats were added to the room.`, 'success'),
      error: (error) => {
        console.error('Unable to create seat blueprint:', error);
        Swal.fire('Blueprint failed', 'The seats could not be created.', 'error');
      },
    }).add(() => this.creating.set(false));
  }
}
