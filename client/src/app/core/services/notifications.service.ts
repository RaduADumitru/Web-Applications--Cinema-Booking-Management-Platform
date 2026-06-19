import { Injectable, signal, inject } from '@angular/core';
import { catchError, map, Observable, of, tap } from 'rxjs';
import { ApiResponse, PaginatedResponse } from '@models/api.models';
import { ApiService } from './api.service';
import { NotificationResponse } from '@models/notifications.models';
@Injectable({
  providedIn: 'root'
})

export class NotificationsService {
    private readonly basePath = '/notifications';

    private readonly apiService = inject(ApiService);

    getNotifications(): Observable<NotificationResponse[]> {
    return this.apiService
        .get<PaginatedResponse<NotificationResponse>>(this.basePath + '/my')
        .pipe(
        map(response => response.content)
        );
    }

    getNotification(id: number): Observable<NotificationResponse | null> {
        return this.apiService.get<NotificationResponse>(`${this.basePath}/${id}`);
    }


    markAsRead(id: number): Observable<NotificationResponse> {
        return this.apiService.patch<NotificationResponse>(`${this.basePath}/${id}/send`, {});
    }
}