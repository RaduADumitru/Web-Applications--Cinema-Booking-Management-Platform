import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PagedResponse } from '@app/shared/models/api.models';
import { SeatResponse } from '@app/shared/models/seat.models';
import { TicketResponse } from '@app/shared/models/ticket.models';
import {
  RoomResponse,
  GenerateSeatsRequest,
  SaveRoomRequest,
  SaveScreenSessionRequest,
  SaveSessionInfoRequest,
  SaveSeatRequest,
  SaveTicketRequest,
  ScreenSessionResponse,
  SessionInfoResponse,
} from '@app/shared/models/staff-operations.models';
import { MovieResponse } from '@app/shared/models/movie.models';
import { ApiService } from './api.service';

@Injectable({
  providedIn: 'root',
})
export class StaffOperationsService {
  private readonly pageSize = 1000;

  constructor(private api: ApiService) {}

  getRooms(): Observable<PagedResponse<RoomResponse>> {
    return this.api.getPaged<RoomResponse>('/rooms', 0, this.pageSize);
  }

  createRoom(payload: SaveRoomRequest): Observable<RoomResponse> {
    return this.api.post<RoomResponse>('/rooms', payload);
  }

  updateRoom(id: number, payload: SaveRoomRequest): Observable<RoomResponse> {
    return this.api.put<RoomResponse>(`/rooms/${id}`, payload);
  }

  deleteRoom(id: number): Observable<void> {
    return this.api.delete<void>(`/rooms/${id}`);
  }

  addSeatToRoom(roomId: number, seatId: number): Observable<RoomResponse> {
    return this.api.post<RoomResponse>(`/rooms/${roomId}/seats/${seatId}`, {});
  }

  addSessionToRoom(roomId: number, sessionId: number): Observable<RoomResponse> {
    return this.api.post<RoomResponse>(`/rooms/${roomId}/screen-sessions/${sessionId}`, {});
  }

  getScreenSessions(movieId?: number): Observable<PagedResponse<ScreenSessionResponse>> {
    const params = movieId != null ? { movieId } : undefined;
    return this.api.getPaged<ScreenSessionResponse>('/screen-sessions', 0, this.pageSize, params);
  }

  createScreenSession(payload: SaveScreenSessionRequest): Observable<ScreenSessionResponse> {
    return this.api.post<ScreenSessionResponse>('/screen-sessions', payload);
  }

  updateScreenSession(id: number, payload: SaveScreenSessionRequest): Observable<ScreenSessionResponse> {
    return this.api.put<ScreenSessionResponse>(`/screen-sessions/${id}`, payload);
  }

  deleteScreenSession(id: number): Observable<void> {
    return this.api.delete<void>(`/screen-sessions/${id}`);
  }

  getSessionInfos(): Observable<PagedResponse<SessionInfoResponse>> {
    return this.api.getPaged<SessionInfoResponse>('/session-infos', 0, this.pageSize);
  }

  createSessionInfo(payload: SaveSessionInfoRequest): Observable<SessionInfoResponse> {
    return this.api.post<SessionInfoResponse>('/session-infos', payload);
  }

  updateSessionInfo(id: number, payload: SaveSessionInfoRequest): Observable<SessionInfoResponse> {
    return this.api.put<SessionInfoResponse>(`/session-infos/${id}`, payload);
  }

  getSeatsForSession(screenSessionId: number): Observable<PagedResponse<SeatResponse>> {
    return this.api.getPaged<SeatResponse>('/seats', 0, this.pageSize, { screenSessionId });
  }

  createSeat(payload: SaveSeatRequest): Observable<SeatResponse> {
    return this.api.post<SeatResponse>('/seats', payload);
  }

  generateSeats(payload: GenerateSeatsRequest): Observable<SeatResponse[]> {
    return this.api.post<SeatResponse[]>('/seats/generate', payload);
  }

  getTickets(sessionId: number, roomId: number): Observable<PagedResponse<TicketResponse>> {
    return this.api.getPaged<TicketResponse>('/tickets', 0, this.pageSize, { sessionId, roomId });
  }

  createTicket(payload: SaveTicketRequest): Observable<TicketResponse> {
    return this.api.post<TicketResponse>('/tickets', payload);
  }

  getMovies(): Observable<PagedResponse<MovieResponse>> {
    return this.api.getPaged<MovieResponse>('/movies', 1, this.pageSize);
  }
}
