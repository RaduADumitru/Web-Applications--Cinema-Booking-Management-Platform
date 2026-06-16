export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  message?: string;
  error?: string;
  statusCode?: number;
}

export interface PagedResponse<T> {
  content: T[];
  page: PagedInfo;
}

export interface PagedInfo {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface ErrorResponse {
  success: boolean;
  message: string;
  error: string;
  statusCode: number;
  timestamp?: string;
}


export interface PaginatedResponse<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}