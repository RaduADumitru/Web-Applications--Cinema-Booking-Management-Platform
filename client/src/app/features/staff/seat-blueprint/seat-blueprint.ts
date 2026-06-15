import { CommonModule } from '@angular/common';
import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import Swal from 'sweetalert2';
import { StaffOperationsService } from '@app/core/services/staff-operations.service';
import { SeatZoneOption } from '@app/shared/models/staff-operations.models';

@Component({
  selector: 'app-seat-blueprint',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './seat-blueprint.html',
  styleUrl: '../staff-panel.css',
})
export class SeatBlueprintComponent {
  readonly zones: SeatZoneOption[] = ['VIP', 'A', 'B', 'C', 'D'];

  roomId: number | null = null;
  rows = 6;
  seatsPerRow = 10;
  startRow = 1;
  startSeatNumber = 1;
  zone: SeatZoneOption = 'A';
  categoryId: number | null = null;
  creating = signal(false);

  constructor(private staffOperations: StaffOperationsService) {}

  rowPreview(): number[] {
    return Array.from({ length: Math.max(this.rows, 0) }, (_, index) => this.startRow + index);
  }

  seatPreview(): number[] {
    return Array.from({ length: Math.max(this.seatsPerRow, 0) }, (_, index) => this.startSeatNumber + index);
  }

  createBlueprint(): void {
    if (this.roomId == null || this.rows < 1 || this.seatsPerRow < 1) {
      return;
    }

    const payload = {
      roomId: this.roomId as number,
      rows: this.rows,
      seatsPerRow: this.seatsPerRow,
      startRow: this.startRow,
      startSeatNumber: this.startSeatNumber,
      zone: this.zone,
      categoryId: this.categoryId,
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
