import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import Swal from 'sweetalert2';
import { StaffOperationsService } from '@app/core/services/staff-operations.service';
import { TicketInfoResponse, SaveTicketInfoRequest, SeatCategoryResponse } from '@app/shared/models/staff-operations.models';
import { TicketType } from '@app/shared/models/ticket.models';

@Component({
  selector: 'app-ticket-pricing',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ticket-pricing.html',
  styleUrl: '../staff-panel.css',
})
export class TicketPricingComponent implements OnInit {
  readonly ticketTypes: TicketType[] = ['ADULT', 'STUDENT', 'CHILD'];

  ticketInfos = signal<TicketInfoResponse[]>([]);
  seatCategories = signal<SeatCategoryResponse[]>([]);
  loading = signal(false);
  saving = signal(false);
  savingCategory = signal(false);
  error = signal<string | null>(null);

  // Ticket type form
  selectedType: TicketType = 'ADULT';
  price: number | null = null;

  // Seat category form
  selectedCategoryId: number | null = null;
  editExtraFee: number | null = null;
  editExtraPoints: number | null = null;

  constructor(private staffOperations: StaffOperationsService) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading.set(true);
    this.error.set(null);

    this.staffOperations.getTicketInfos().subscribe({
      next: (response) => this.ticketInfos.set(response.content),
      error: () => this.error.set('Unable to load ticket prices.'),
    }).add(() => this.loading.set(false));

    this.staffOperations.getSeatCategories().subscribe({
      next: (response) => {
        this.seatCategories.set(response);
        if (response.length > 0 && this.selectedCategoryId == null) {
          this.selectedCategoryId = response[0].id;
          this.editExtraFee = response[0].extraFee;
          this.editExtraPoints = response[0].extraPoints;
        }
      },
      error: (error) => console.error('Unable to load seat categories:', error),
    });
  }

  getCategoryPrice(type: TicketType): string {
    const info = this.ticketInfos().find((t) => t.type === type);
    return info ? info.price.toFixed(2) : 'Not Set';
  }

  hasCategory(type: TicketType): boolean {
    return this.ticketInfos().some((t) => t.type === type);
  }

  selectTicketType(type: TicketType): void {
    this.selectedType = type;
    const existing = this.ticketInfos().find((t) => t.type === type);
    this.price = existing ? existing.price : null;
  }

  onSeatCategoryChange(): void {
    const cat = this.seatCategories().find((c) => c.id === this.selectedCategoryId);
    if (cat) {
      this.editExtraFee = cat.extraFee;
      this.editExtraPoints = cat.extraPoints;
    }
  }

  savePrice(): void {
    if (this.price == null || this.price < 0) {
      Swal.fire('Invalid Price', 'Please enter a valid non-negative price.', 'error');
      return;
    }

    this.saving.set(true);
    const existing = this.ticketInfos().find((t) => t.type === this.selectedType);
    const payload: SaveTicketInfoRequest = { type: this.selectedType, price: this.price };

    const request$ = existing
      ? this.staffOperations.updateTicketInfo(existing.id, payload)
      : this.staffOperations.createTicketInfo(payload);

    request$.subscribe({
      next: () => {
        Swal.fire('Saved', `Price for ${this.selectedType} updated.`, 'success');
        this.loadData();
        this.price = null;
      },
      error: () => Swal.fire('Failed', 'The ticket price could not be saved.', 'error'),
    }).add(() => this.saving.set(false));
  }

  saveCategoryFee(): void {
    if (this.selectedCategoryId == null || this.editExtraFee == null || this.editExtraPoints == null) return;

    this.savingCategory.set(true);
    this.staffOperations.updateSeatCategory(this.selectedCategoryId, {
      extraFee: this.editExtraFee,
      extraPoints: this.editExtraPoints,
    }).subscribe({
      next: () => {
        Swal.fire('Updated', 'Seat category fee updated.', 'success');
        this.staffOperations.getSeatCategories().subscribe(r => this.seatCategories.set(r));
      },
      error: () => Swal.fire('Failed', 'The seat category could not be updated.', 'error'),
    }).add(() => this.savingCategory.set(false));
  }

  trackByType(_: number, type: TicketType): string {
    return type;
  }

  trackByCat(_: number, cat: SeatCategoryResponse): number {
    return cat.id;
  }
}
