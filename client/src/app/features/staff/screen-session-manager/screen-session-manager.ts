import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable, switchMap } from 'rxjs';
import Swal from 'sweetalert2';
import { StaffOperationsService } from '@app/core/services/staff-operations.service';
import { MovieResponse } from '@app/shared/models/movie.models';
import {
  SaveScreenSessionRequest,
  SaveSessionInfoRequest,
  ScreenSessionResponse,
  SessionFormatOption,
  RoomResponse,
  SessionInfoResponse,
} from '@app/shared/models/staff-operations.models';

type SessionInfoMode = 'existing' | 'new';

@Component({
  selector: 'app-screen-session-manager',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './screen-session-manager.html',
  styleUrl: '../staff-panel.css',
})
export class ScreenSessionManagerComponent implements OnInit {
  sessions = signal<ScreenSessionResponse[]>([]);
  movies = signal<MovieResponse[]>([]);
  rooms = signal<RoomResponse[]>([]);
  sessionInfos = signal<SessionInfoResponse[]>([]);
  loading = signal(false);
  saving = signal(false);
  editingId: number | null = null;
  readonly formatOptions: SessionFormatOption[] = ['TWO_D', 'THREE_D'];

  form: SaveScreenSessionRequest = this.emptyForm();
  sessionInfoMode: SessionInfoMode = 'existing';
  sessionInfoForm: SaveSessionInfoRequest = this.emptySessionInfoForm();

  constructor(private staffOperations: StaffOperationsService) {}

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading.set(true);
    this.staffOperations.getMovies().subscribe({
      next: (response) => this.movies.set(response.content),
      error: (error) => console.error('Unable to load movies:', error),
    });

    this.staffOperations.getRooms().subscribe({
      next: (response) => this.rooms.set(response.content),
      error: (error) => console.error('Unable to load rooms:', error),
    });

    this.staffOperations.getSessionInfos().subscribe({
      next: (response) => this.sessionInfos.set(response.content),
      error: (error) => console.error('Unable to load session infos:', error),
    });

    this.staffOperations.getScreenSessions().subscribe({
      next: (response) => this.sessions.set(response.content),
      error: (error) => console.error('Unable to load screen sessions:', error),
    }).add(() => this.loading.set(false));
  }

  saveSession(): void {
    if (this.form.movieId == null || this.form.roomId == null) {
      return;
    }

    if (this.sessionInfoMode === 'new' && (this.sessionInfoForm.points == null || Number(this.sessionInfoForm.points) < 0)) {
      return;
    }

    const payload: SaveScreenSessionRequest = {
      ...this.form,
      sessionInfoId: this.sessionInfoMode === 'existing'
        ? this.normalizeOptionalNumber(this.form.sessionInfoId)
        : null,
    };
    const request = this.buildSaveRequest(payload);

    this.saving.set(true);
    request.subscribe({
      next: () => {
        Swal.fire('Showtime saved', 'The screening schedule was updated.', 'success');
        this.resetForm();
        this.loadData();
      },
      error: (error) => {
        console.error('Unable to save showtime:', error);
        Swal.fire('Showtime failed', 'The screen session could not be saved.', 'error');
      },
    }).add(() => this.saving.set(false));
  }

  editSession(session: ScreenSessionResponse): void {
    this.editingId = session.id;
    this.sessionInfoMode = 'existing';
    this.form = {
      movieId: session.movieId,
      date: session.date,
      startTime: session.startTime,
      endTime: session.endTime,
      sessionInfoId: session.sessionInfoId,
      roomId: 0,
    };
    this.sessionInfoForm = {
      format: (session.format as SessionFormatOption | null) ?? 'TWO_D',
      points: session.points ?? 0,
    };
  }

  deleteSession(id: number): void {
    Swal.fire({
      title: 'Delete showtime?',
      text: 'This removes the screen session from the schedule.',
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Delete',
    }).then((result) => {
      if (!result.isConfirmed) {
        return;
      }

      this.staffOperations.deleteScreenSession(id).subscribe({
        next: () => {
          this.sessions.set(this.sessions().filter((session) => session.id !== id));
          Swal.fire('Deleted', 'The showtime was removed.', 'success');
        },
        error: (error) => {
          console.error('Unable to delete showtime:', error);
          Swal.fire('Delete failed', 'The showtime could not be removed.', 'error');
        },
      });
    });
  }

  resetForm(): void {
    this.editingId = null;
    this.form = this.emptyForm();
    this.sessionInfoMode = 'existing';
    this.sessionInfoForm = this.emptySessionInfoForm();
  }

  trackBySession(_: number, session: ScreenSessionResponse): number {
    return session.id;
  }

  trackByMovie(_: number, movie: MovieResponse): number {
    return movie.id;
  }

  trackByRoom(_: number, room: RoomResponse): number {
    return room.id;
  }

  trackBySessionInfo(_: number, info: SessionInfoResponse): number {
    return info.id;
  }

  private emptyForm(): SaveScreenSessionRequest {
    return {
      movieId: 0,
      date: '',
      startTime: '',
      endTime: '',
      sessionInfoId: null,
      roomId: 0,
    };
  }

  private emptySessionInfoForm(): SaveSessionInfoRequest {
    return {
      format: 'TWO_D',
      points: 0,
    };
  }

  private buildSaveRequest(payload: SaveScreenSessionRequest): Observable<ScreenSessionResponse> {
    if (this.sessionInfoMode === 'new') {
      return this.staffOperations.createSessionInfo({
        ...this.sessionInfoForm,
        points: Number(this.sessionInfoForm.points),
      }).pipe(
        switchMap((sessionInfo) => this.persistSession({
          ...payload,
          sessionInfoId: sessionInfo.id,
        }))
      );
    }

    return this.persistSession(payload);
  }

  private persistSession(payload: SaveScreenSessionRequest): Observable<ScreenSessionResponse> {
    return this.editingId == null
      ? this.staffOperations.createScreenSession(payload)
      : this.staffOperations.updateScreenSession(this.editingId, payload);
  }

  private normalizeOptionalNumber(value: number | null): number | null {
    if (value == null || Number(value) < 1) {
      return null;
    }
    return Number(value);
  }
}
