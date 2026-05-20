import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { ApiResponse } from '@models/api.models';
import { ApiService } from './api.service';

export type UserRole = 'OWNER' | 'STAFF' | 'USER';

export interface AuthUser {
	username: string;
	email: string;
	firstName: string;
	lastName: string;
	role: UserRole;
}

export interface LoginRequest {
	username: string;
	password: string;
}

export interface RegisterRequest {
	username: string;
	password: string;
	confirmPassword?: string;
	email: string;
	firstName: string;
	lastName: string;
	phoneNumber?: string;
}

export interface RegisterResponse {
	message: string;
	username: string;
}

@Injectable({
	providedIn: 'root'
})
export class AuthService {
	private readonly storageKey = 'cinema_auth_user';
	private readonly currentUserSubject = new BehaviorSubject<AuthUser | null>(this.loadCurrentUser());

	readonly currentUser$ = this.currentUserSubject.asObservable();

	constructor(private api: ApiService) {}

	login(credentials: LoginRequest): Observable<ApiResponse<AuthUser>> {
		return this.api.post<AuthUser>('/auth/login', credentials, { withCredentials: true }).pipe(
			tap((response) => {
				if (response.data) {
					this.setCurrentUser(response.data);
				}
			})
		);
	}

	register(payload: RegisterRequest): Observable<ApiResponse<RegisterResponse>> {
		return this.api.post<RegisterResponse>('/auth/register', payload, { withCredentials: true });
	}

	logout(): void {
		this.clearCurrentUser();
	}

	getCurrentUser(): AuthUser | null {
		return this.currentUserSubject.value;
	}

	getCurrentUsername(): string | null {
		return this.currentUserSubject.value?.username ?? null;
	}

	isAuthenticated(): boolean {
		return this.currentUserSubject.value !== null;
	}

	setCurrentUser(user: AuthUser): void {
		if (typeof localStorage !== 'undefined') {
			localStorage.setItem(this.storageKey, JSON.stringify(user));
		}
		this.currentUserSubject.next(user);
	}

	clearCurrentUser(): void {
		if (typeof localStorage !== 'undefined') {
			localStorage.removeItem(this.storageKey);
		}
		this.currentUserSubject.next(null);
	}

	private loadCurrentUser(): AuthUser | null {
		if (typeof localStorage === 'undefined') {
			return null;
		}

		const storedUser = localStorage.getItem(this.storageKey);
		if (!storedUser) {
			return null;
		}

		try {
			return JSON.parse(storedUser) as AuthUser;
		} catch {
			localStorage.removeItem(this.storageKey);
			return null;
		}
	}
}
