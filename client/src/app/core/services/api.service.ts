import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpEvent, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse, PagedResponse } from '@models/api.models';
import { ConfigService } from './config.service';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  constructor(private http: HttpClient, private config: ConfigService) {}

  private get apiUrl(): string {
    return this.config.apiUrl;
  }

  get<T>(endpoint: string, options?: { observe?: 'body' } & any): Observable<ApiResponse<T>>;
  get<T>(endpoint: string, options: { observe: 'response' } & any): Observable<HttpResponse<ApiResponse<T>>>;
  get<T>(endpoint: string, options: { observe: 'events' } & any): Observable<HttpEvent<ApiResponse<T>>>;
  get<T>(endpoint: string, options?: any): Observable<any> {
    return this.http.get<ApiResponse<T>>(
      `${this.apiUrl}${endpoint}`,
      { ...options, withCredentials: true } );
  }

  post<T>(endpoint: string, body: any, options?: { observe?: 'body' } & any): Observable<ApiResponse<T>>;
  post<T>(endpoint: string, body: any, options?: { observe: 'response' } & any): Observable<HttpResponse<ApiResponse<T>>>;
  post<T>(endpoint: string, body: any, options?: { observe: 'events' } & any): Observable<HttpEvent<ApiResponse<T>>>;
  post<T>(endpoint: string, body: any, options?: any): Observable<any> {
    return this.http.post<ApiResponse<T>>(
      `${this.apiUrl}${endpoint}`,
      body,
      { ...options, withCredentials: true }
    );
  }

  put<T>(endpoint: string, body: any, options?: { observe?: 'body' } & any): Observable<ApiResponse<T>>;
  put<T>(endpoint: string, body: any, options?: { observe: 'response' } & any): Observable<HttpResponse<ApiResponse<T>>>;
  put<T>(endpoint: string, body: any, options?: { observe: 'events' } & any): Observable<HttpEvent<ApiResponse<T>>>;
  put<T>(endpoint: string, body: any, options?: any): Observable<any> {
    return this.http.put<ApiResponse<T>>(
      `${this.apiUrl}${endpoint}`,
      body,
      { ...options, withCredentials: true }
    );
  }

  delete<T>(endpoint: string, options?: { observe?: 'body' } & any): Observable<ApiResponse<T>>;
  delete<T>(endpoint: string, options?: { observe: 'response' } & any): Observable<HttpResponse<ApiResponse<T>>>;
  delete<T>(endpoint: string, options?: { observe: 'events' } & any): Observable<HttpEvent<ApiResponse<T>>>;
  delete<T>(endpoint: string, options?: any): Observable<any> {
    return this.http.delete<ApiResponse<T>>(
      `${this.apiUrl}${endpoint}`,
      { ...options, withCredentials: true }
    );
  }

  getWithParams<T>(endpoint: string, params: any = {}): Observable<ApiResponse<T>> {
    let httpParams = new HttpParams();
    Object.keys(params).forEach(key => {
      if (params[key] != null) {
        httpParams = httpParams.set(key, params[key]);
      }
    });

    return this.http.get<ApiResponse<T>>(
      `${this.apiUrl}${endpoint}`,
      { params: httpParams, withCredentials: true }
    );
  }

  getPaged<T>(endpoint: string, page: number = 0, size: number = 10, params?: any): Observable<PagedResponse<T>> {
    let httpParams = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (params) {
      Object.keys(params).forEach(key => {
        if (params[key] != null) {
          httpParams = httpParams.set(key, params[key]);
        }
      });
    }

    return this.http.get<PagedResponse<T>>(
      `${this.apiUrl}${endpoint}`,
      { params: httpParams, withCredentials: true }
    );
  }
}
