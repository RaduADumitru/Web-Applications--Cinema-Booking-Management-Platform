export type TicketType = 'ADULT' | 'STUDENT' | 'CHILD';

export interface TicketResponse {
  id: number;
  isAvailable: boolean;
  seatId: number;
  roomId: number;
  screenSessionId: number;
  type: TicketType | null;
  price: number | null;
}
