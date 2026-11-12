import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { NotificationDto } from '../model/notification.model';

@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private baseUrl = `${environment.apiUrl}`;

  constructor(private http: HttpClient) {}

  getNotifications(
    take = 50,
    unreadOnly = false
  ): Observable<NotificationDto[]> {
    let params = new HttpParams().set('take', String(take));
    if (unreadOnly) params = params.set('unreadOnly', 'true');

    return this.http.get<NotificationDto[]>(`${this.baseUrl}/notifications`, {
      params,
    });
  }

  markAsRead(id: string): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/notifications/${id}/read`, {});
  }

  markAllAsRead(): Observable<void> {
    return this.http.put<void>(
      `${this.baseUrl}/notifications/mark-all-read`,
      {}
    );
  }
}
