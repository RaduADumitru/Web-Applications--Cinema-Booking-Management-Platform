import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PagedResponse } from '@app/shared/models/api.models';
import { CreateOrderRequest, OrderResponse } from '@app/shared/models/order.models';
import { SeatResponse } from '@app/shared/models/seat.models';
import { TicketResponse, TicketType } from '@app/shared/models/ticket.models';
import { ApiService } from './api.service';

@Injectable({
  providedIn: 'root',
})
export class SeatSelectionService {
  private readonly pageSize = 1000;

  constructor(private api: ApiService) {}

  getSeats(params: {
    screenSessionId?: number;
    movieId?: number;
    roomType?: string;
  } = {}): Observable<PagedResponse<SeatResponse>> {
    return this.api.getPaged<SeatResponse>('/seats', 0, this.pageSize, params);
  }

  getTickets(params: {
    sessionId?: number;
    roomId?: number;
    isAvailable?: boolean;
  } = {}): Observable<PagedResponse<TicketResponse>> {
    return this.api.getPaged<TicketResponse>('/tickets', 0, this.pageSize, params);
  }

  createOrder(ticketIds: number[], type: TicketType, useDiscount: boolean): Observable<OrderResponse> {
    const payload: CreateOrderRequest = {
      items: ticketIds.map((ticketId) => ({ ticketId, type })),
      useDiscount,
    };

    return this.api.post<OrderResponse>('/orders', payload);
  }
}
