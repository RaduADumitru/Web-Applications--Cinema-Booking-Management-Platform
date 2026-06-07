import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpEvent, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse, PagedResponse } from '@models/api.models';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private readonly apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  get<T>(endpoint: string, options?: any): Observable<T> {
    return this.http.get<T>(
      `${this.apiUrl}${endpoint}`,
      { ...options, withCredentials: true }
    ) as Observable<T>;
  }

  post<T>(endpoint: string, body: any, options?: any): Observable<T> {
      return this.http.post<T>(
        `${this.apiUrl}${endpoint}`,
        body,
        { ...options, withCredentials: true }
      ) as Observable<T>;
    }
  
  put<T>(endpoint: string, body: any, options?: any): Observable<T> {
    return this.http.put<T>(
      `${this.apiUrl}${endpoint}`,
      body,
      { ...options, withCredentials: true }
    ) as Observable<T>;
  }

  delete<T>(endpoint: string, options?: any): Observable<T> {
    return this.http.delete<T>(
      `${this.apiUrl}${endpoint}`,
      { ...options, withCredentials: true }
    ) as Observable<T>;
  }

  patch<T>(endpoint: string, body: any, options?: any): Observable<T> {
      return this.http.patch<T>(
        `${this.apiUrl}${endpoint}`,
        body,
        { ...options, withCredentials: true }
      ) as Observable<T>;
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
