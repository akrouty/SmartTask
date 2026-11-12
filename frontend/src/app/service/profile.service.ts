import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

// Ajuste si tu as déjà un API_BASE ailleurs

const API_BASE = `${environment.apiUrl}`;

export type UserRole = 'admin' | 'user' | string;

export interface MeDto {
  id: string;
  fullName: string;
  email: string;
  role: UserRole;
}

@Injectable({ providedIn: 'root' })
export class ProfileService {
  constructor(private http: HttpClient) {}

  me(): Observable<MeDto> {
    return this.http.get<MeDto>(`${API_BASE}/users/me`);
  }

  updateMe(fullName: string): Observable<MeDto> {
    return this.http.put<MeDto>(`${API_BASE}/users/me`, { fullName });
  }

  changePassword(
    currentPassword: string,
    newPassword: string
  ): Observable<void> {
    return this.http.put<void>(`${API_BASE}/users/me/password`, {
      currentPassword,
      newPassword,
    });
  }
}
