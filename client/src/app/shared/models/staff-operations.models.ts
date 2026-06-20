import { TicketType } from './ticket.models';

export type RoomType = 'NORMAL' | 'IMAX' | 'THREE_D' | 'VIP' | string;
export type SeatZoneOption = 'VIP' | 'A' | 'B' | 'C' | 'D';

export interface RoomResponse {
  id: number;
  type: RoomType;
  name: string;
  floor: number;
}

export interface SaveRoomRequest {
  type: RoomType;
  name: string;
  floor: number;
}

export interface ScreenSessionResponse {
  id: number;
  date: string;
  startTime: string;
  endTime: string;
  movieId: number;
  movieTitle: string;
  sessionInfoId: number | null;
  format: string | null;
  points: number | null;
  roomIds: number[];
}

export interface SaveScreenSessionRequest {
  movieId: number;
  date: string;
  startTime: string;
  endTime: string;
  sessionInfoId: number | null;
  roomId: number;
}

export type SessionFormatOption = 'TWO_D' | 'THREE_D';

export interface SessionInfoResponse {
  id: number;
  format: SessionFormatOption;
  points: number;
}

export interface SaveSessionInfoRequest {
  format: SessionFormatOption;
  points: number;
}

export interface SaveSeatRequest {
  row: number;
  number: number;
  zone: SeatZoneOption;
  categoryId: number | null;
  roomId: number;
}

export interface GenerateSeatsRequest {
  roomId: number;
  rows: number;
  seatsPerRow: number;
  startRow: number;
  startSeatNumber: number;
  zone: SeatZoneOption;
  categoryId: number | null;
}

export interface SaveTicketRequest {
  seatId: number;
  roomId: number;
  screenSessionId: number;
}

export interface TicketInfoResponse {
  id: number;
  type: TicketType;
  price: number;
}

export interface SaveTicketInfoRequest {
  type: TicketType;
  price: number;
}
