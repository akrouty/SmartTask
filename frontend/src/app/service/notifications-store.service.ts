import { Injectable } from '@angular/core';
import { BehaviorSubject, map } from 'rxjs';
import { NotificationsService } from './notification.service';
import { NotificationDto } from '../model/notification.model';

@Injectable({ providedIn: 'root' })
export class NotificationsStoreService {
  private _unreadCount = new BehaviorSubject<number>(0);
  unreadCount$ = this._unreadCount.asObservable();

  private _latest = new BehaviorSubject<NotificationDto[]>([]);
  latest$ = this._latest.asObservable();

  constructor(private api: NotificationsService) {}

  refresh(): void {
    // on récupère 20 dernières et on calcule unread
    this.api.getNotifications(50).subscribe({
      next: (items) => {
        const list = items ?? [];
        this._latest.next(list);
        const unread = list.filter((n) => !n.readAt).length;
        this._unreadCount.next(unread);
      },
      error: () => {
        // ne casse pas l’UI
        this._unreadCount.next(0);
        this._latest.next([]);
      },
    });
  }

  setUnreadCount(count: number) {
    this._unreadCount.next(Math.max(0, count));
  }
}
