import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpEvent, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ApiResponse, PagedResponse } from '@models/api.models';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private readonly apiUrl = 'http://localhost:8080/api/v1';

  constructor(private http: HttpClient) {}

  get<T>(endpoint: string, options?: { observe?: 'body' } & any): Observable<ApiResponse<T>>;
  get<T>(endpoint: string, options: { observe: 'response' } & any): Observable<HttpResponse<ApiResponse<T>>>;
  get<T>(endpoint: string, options: { observe: 'events' } & any): Observable<HttpEvent<ApiResponse<T>>>;
  get<T>(endpoint: string, options?: any): Observable<any> {
    return this.http.get<ApiResponse<T>>(
      `${this.apiUrl}${endpoint}`,
      options
    );
  }

  post<T>(endpoint: string, body: any, options?: { observe?: 'body' } & any): Observable<ApiResponse<T>>;
  post<T>(endpoint: string, body: any, options?: { observe: 'response' } & any): Observable<HttpResponse<ApiResponse<T>>>;
  post<T>(endpoint: string, body: any, options?: { observe: 'events' } & any): Observable<HttpEvent<ApiResponse<T>>>;
  post<T>(endpoint: string, body: any, options?: any): Observable<any> {
    return this.http.post<ApiResponse<T>>(
      `${this.apiUrl}${endpoint}`,
      body,
      options
    );
  }

  put<T>(endpoint: string, body: any, options?: { observe?: 'body' } & any): Observable<ApiResponse<T>>;
  put<T>(endpoint: string, body: any, options?: { observe: 'response' } & any): Observable<HttpResponse<ApiResponse<T>>>;
  put<T>(endpoint: string, body: any, options?: { observe: 'events' } & any): Observable<HttpEvent<ApiResponse<T>>>;
  put<T>(endpoint: string, body: any, options?: any): Observable<any> {
    return this.http.put<ApiResponse<T>>(
      `${this.apiUrl}${endpoint}`,
      body,
      options
    );
  }

  delete<T>(endpoint: string, options?: { observe?: 'body' } & any): Observable<ApiResponse<T>>;
  delete<T>(endpoint: string, options?: { observe: 'response' } & any): Observable<HttpResponse<ApiResponse<T>>>;
  delete<T>(endpoint: string, options?: { observe: 'events' } & any): Observable<HttpEvent<ApiResponse<T>>>;
  delete<T>(endpoint: string, options?: any): Observable<any> {
    return this.http.delete<ApiResponse<T>>(
      `${this.apiUrl}${endpoint}`,
      options
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
      { params: httpParams }
    );
  }

  getPaged<T>(endpoint: string, page: number = 0, size: number = 10, params?: any): Observable<ApiResponse<PagedResponse<T>>> {
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

    return this.http.get<ApiResponse<PagedResponse<T>>>(
      `${this.apiUrl}${endpoint}`,
      { params: httpParams }
    );
  }
}
