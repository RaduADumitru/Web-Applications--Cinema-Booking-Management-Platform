export interface ProfileResponse {
  id: number;
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  loyaltyPoints: number;
  role: "OWNER" | "STAFF" | "USER";
}

export interface UpdateProfileRequest {
  firstName?: string;
  lastName?: string;
  email?: string;
  phoneNumber?: string;
}

export interface PromoteRequest {
  id: number;
  role: "STAFF" | "OWNER" | "USER";
}

export interface MessageResponse {
  message: string;
}