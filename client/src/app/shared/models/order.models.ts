export enum OrderStatus {
  PENDING = 'PENDING',
  PAID = 'PAID',
  CANCELLED = 'CANCELLED'
}

export interface OrderResponse {
  id: string; 
  createdAt: Date;
  status: OrderStatus;
  paymentAt: Date | null; 
  deletedAt: Date | null; 
  price: number;
  loyaltyPoints: number;
  pointsUsed: number | null;
  discount: number | null;
  userId: string;
  ticketIds: string[]; 
  offerPercent: number | null;
  offerMessage: string | null;
}