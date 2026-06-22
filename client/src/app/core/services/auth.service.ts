import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { ApiResponse } from '@models/api.models';
import { ApiService } from './api.service';
import { AuthUser, LoginRequest, RegisterRequest, RegisterResponse } from '@models/auth.models';

@Injectable({
	providedIn: 'root'
})
export class AuthService {

	constructor(private api: ApiService) { }

	login(credentials: LoginRequest): Observable<AuthUser> {
		return this.api.post<AuthUser>('/auth/login', credentials, { withCredentials: true });
	}

	register(payload: RegisterRequest): Observable<RegisterResponse> {
		return this.api.post<RegisterResponse>('/auth/register', payload, { withCredentials: true });
	}

	refresh(): Observable<{ message: string }> {
		return this.api.post<{ message: string }>('/auth/refresh', {}, { withCredentials: true });
	}

	logout(): Observable<{ message: string }> {
		return this.api.post<{ message: string }>('/auth/logout', {}, { withCredentials: true });
	}
}
