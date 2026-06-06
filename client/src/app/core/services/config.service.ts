import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class ConfigService {
  private config: { apiUrl: string } = { apiUrl: 'http://localhost:8080/api/v1' };

  constructor(private http: HttpClient) {}

  load(): Promise<void> {
    return firstValueFrom(
      this.http.get<{ apiUrl: string }>('/runtime-config.json')
    ).then(cfg => {
      this.config = cfg;
    }).catch(() => {});
  }

  get apiUrl(): string {
    return this.config.apiUrl;
  }
}
