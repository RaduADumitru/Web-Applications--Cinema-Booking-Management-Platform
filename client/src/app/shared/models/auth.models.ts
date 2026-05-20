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