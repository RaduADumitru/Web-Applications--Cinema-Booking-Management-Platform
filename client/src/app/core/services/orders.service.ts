import { Injectable, inject, signal, computed } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { map } from 'rxjs/operators';
import { ApiService } from './api.service';
import { OrderResponse, OrderStatus } from '@app/shared/models/order.models';
import { TicketResponse } from '@app/shared/models/ticket.models';
import { ScreenSessionResponse, RoomResponse } from '@app/shared/models/staff-operations.models';
import { SeatResponse } from '@app/shared/models/seat.models';

@Injectable({
  providedIn: 'root'
})
export class OrdersService {
  private readonly api = inject(ApiService);

  // Shared signal for holding user's orders
  orders = signal<OrderResponse[]>([]);

  // Computed signal for pending/processing orders
  pendingOrders = computed(() => 
    this.orders().filter(o => o.status === OrderStatus.PENDING)
  );

  // Computed count of pending orders
  pendingCount = computed(() => this.pendingOrders().length);

  loadMyOrders(): Observable<OrderResponse[]> {
    return this.api.getPaged<OrderResponse>('/orders/my', 0, 100).pipe(
      map(res => res.content),
      tap(orders => this.orders.set(orders))
    );
  }

  payOrder(id: number): Observable<OrderResponse> {
    return this.api.patch<OrderResponse>(`/orders/${id}/pay`, {}).pipe(
      tap(() => this.refreshOrders())
    );
  }

  cancelOrder(id: number): Observable<OrderResponse> {
    return this.api.patch<OrderResponse>(`/orders/${id}/cancel`, {}).pipe(
      tap(() => this.refreshOrders())
    );
  }

  refreshOrders(): void {
    this.loadMyOrders().subscribe({
      error: (err) => console.error('Failed to refresh orders in service:', err)
    });
  }

  getTicket(id: number): Observable<TicketResponse> {
    return this.api.get<TicketResponse>(`/tickets/${id}`);
  }

  getScreenSession(id: number): Observable<ScreenSessionResponse> {
    return this.api.get<ScreenSessionResponse>(`/screen-sessions/${id}`);
  }

  getSeat(id: number): Observable<SeatResponse> {
    return this.api.get<SeatResponse>(`/seats/${id}`);
  }

  getRoom(id: number): Observable<RoomResponse> {
    return this.api.get<RoomResponse>(`/rooms/${id}`);
  }
}
