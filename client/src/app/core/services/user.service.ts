import { 
  ProfileResponse, 
  UpdateProfileRequest, 
  PromoteRequest, 
  MessageResponse 
} from '@models/user.models';
import { Injectable, signal, inject } from '@angular/core';
import { catchError, map, Observable, of, tap } from 'rxjs';
import { ApiResponse, PagedResponse } from '@models/api.models';
import { ApiService } from './api.service';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private readonly basePath = '/user';

  currentUser = signal<ProfileResponse | null>(null);

  constructor(private apiService: ApiService) {}

  getProfile(): Observable<ProfileResponse> {
    return this.apiService.get<ProfileResponse>(this.basePath);
  }

  updateProfile(updateData: UpdateProfileRequest): Observable<ProfileResponse> {
    return this.apiService.patch<ProfileResponse>(this.basePath, updateData);
  }

  deleteOwnAccount(): Observable<MessageResponse> {
    return this.apiService.delete<MessageResponse>(this.basePath);
  }

  deleteUserById(id: number): Observable<MessageResponse> {
    return this.apiService.delete<MessageResponse>(`${this.basePath}/${id}`);
  }

  promoteUser(promoteData: PromoteRequest): Observable<ProfileResponse> {
    return this.apiService.patch<ProfileResponse>(`${this.basePath}/promote`, promoteData);
  }

  getUsersPage(page: number = 1, size: number = 10): Observable<PagedResponse<ProfileResponse>> {
    return this.apiService.getPaged<ProfileResponse>(`${this.basePath}/all`, page, size).pipe(
      map((response: any) => {
        return {
          content: response.content || [],
          page: response.page ? {
            number: response.page.number,
            size: response.page.size,
            totalElements: response.page.totalElements,
            totalPages: response.page.totalPages
          } : {
            number: response.number || 0,
            size: response.size || 10,
            totalElements: response.totalElements || 0,
            totalPages: response.totalPages || 0
          }
        } as PagedResponse<ProfileResponse>;
      })
    );
  }

loadUserProfile(): Observable<ProfileResponse | null> {
  return this.apiService.get<ProfileResponse>(`${this.basePath}`).pipe(
    map((response: any) => {
      if (response && typeof response === 'object' && 'data' in response) {
        return (response as ApiResponse<ProfileResponse>).data ?? null;
      }

      return response as ProfileResponse | null;
    }),
    tap(profile => this.currentUser.set(profile)),
    catchError(() => {
      this.currentUser.set(null);
      return of(null);
    })
  );
}

  hasRole(requiredRole: string): boolean {
    const user = this.currentUser();
    return user ? user.role === requiredRole : false;
  }

  getRole(): string {
    const user = this.currentUser();
    return user ? user.role : "USER";
  }

  isAuthenticated(): boolean {
    return this.currentUser() !== null;
  }
}