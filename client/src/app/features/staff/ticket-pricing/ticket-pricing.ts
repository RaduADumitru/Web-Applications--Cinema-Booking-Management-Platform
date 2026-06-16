import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import Swal from 'sweetalert2';
import { StaffOperationsService } from '@app/core/services/staff-operations.service';
import { TicketInfoResponse, SaveTicketInfoRequest } from '@app/shared/models/staff-operations.models';
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
  loading = signal(false);
  saving = signal(false);
  error = signal<string | null>(null);

  selectedType: TicketType = 'ADULT';
  price: number | null = null;

  constructor(private staffOperations: StaffOperationsService) { }

  ngOnInit(): void {
    this.loadPrices();
  }

  loadPrices(): void {
    this.loading.set(true);
    this.error.set(null);

    this.staffOperations.getTicketInfos().subscribe({
      next: (response) => {
        this.ticketInfos.set(response.content);
      },
      error: (error) => {
        console.error('Unable to load ticket prices:', error);
        this.error.set('Unable to load ticket prices.');
      },
    }).add(() => this.loading.set(false));
  }

  getCategoryPrice(type: TicketType): string {
    const info = this.ticketInfos().find((t) => t.type === type);
    return info ? `${info.price.toFixed(2)}` : 'Not Set';
  }

  hasCategory(type: TicketType): boolean {
    return this.ticketInfos().some((t) => t.type === type);
  }

  selectCategory(type: TicketType): void {
    this.selectedType = type;
    const existing = this.ticketInfos().find((t) => t.type === type);
    this.price = existing ? existing.price : null;
  }

  savePrice(): void {
    if (this.price == null || this.price < 0) {
      Swal.fire('Invalid Price', 'Please enter a valid non-negative price.', 'error');
      return;
    }

    this.saving.set(true);
    const existing = this.ticketInfos().find((t) => t.type === this.selectedType);
    const payload: SaveTicketInfoRequest = {
      type: this.selectedType,
      price: this.price,
    };

    if (existing) {
      this.staffOperations.updateTicketInfo(existing.id, payload).subscribe({
        next: () => {
          Swal.fire('Price updated', `Price for ${this.selectedType} category is now ${this.price}.`, 'success');
          this.loadPrices();
          this.price = null;
        },
        error: (error) => {
          console.error('Unable to update ticket price:', error);
          Swal.fire('Update failed', 'The ticket price could not be updated.', 'error');
        },
      }).add(() => this.saving.set(false));
    } else {
      this.staffOperations.createTicketInfo(payload).subscribe({
        next: () => {
          Swal.fire('Price configured', `Price for ${this.selectedType} category has been set to ${this.price}.`, 'success');
          this.loadPrices();
          this.price = null;
        },
        error: (error) => {
          console.error('Unable to create ticket price:', error);
          Swal.fire('Configuration failed', 'The ticket price could not be set.', 'error');
        },
      }).add(() => this.saving.set(false));
    }
  }

  trackByType(_: number, type: TicketType): string {
    return type;
  }
}
