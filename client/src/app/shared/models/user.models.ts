export interface ProfileResponse {
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
  userId: number;
  newRole: "STAFF" | "OWNER" | "USER";
}

export interface MessageResponse {
  message: string;
}