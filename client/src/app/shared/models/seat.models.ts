export type SeatZone = 'VIP' | 'A' | 'B' | 'C' | 'D' | string;

export interface SeatResponse {
  id: number;
  row: number;
  number: number;
  zone: SeatZone;
  categoryId: number | null;
}
