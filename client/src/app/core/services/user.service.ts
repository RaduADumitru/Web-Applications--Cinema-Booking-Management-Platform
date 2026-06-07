import { 
  ProfileResponse, 
  UpdateProfileRequest, 
  PromoteRequest, 
  MessageResponse 
} from '@models/user.models';
import { Injectable, signal, inject } from '@angular/core';
import { catchError, map, Observable, of, tap } from 'rxjs';
import { ApiResponse } from '@models/api.models';
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

  isAuthenticated(): boolean {
    console.log('Checking authentication status:', this.currentUser());
    return this.currentUser() !== null;
  }
}