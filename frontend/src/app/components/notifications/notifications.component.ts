import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { LucideAngularModule } from 'lucide-angular';

import { UiCardComponent } from '../../shared/ui/card/ui-card/ui-card.component';
import { UiButtonComponent } from '../../shared/ui/button/ui-button/ui-button.component';

import { NotificationDto } from '../../model/notification.model';
import { NotificationsService } from '../../service/notification.service';

type FilterMode = 'all' | 'unread';

@Component({
  standalone: true,
  selector: 'app-notifications',
  imports: [
    CommonModule,
    RouterModule,
    LucideAngularModule,
    UiButtonComponent,
    UiCardComponent,
  ],
  templateUrl: './notifications.component.html',
})
export class NotificationsComponent implements OnInit, OnDestroy {
  loading = false;
  error: string | null = null;

  items: NotificationDto[] = [];
  filtered: NotificationDto[] = [];

  searchTerm = '';
  mode: FilterMode = 'unread';
  take = 50;

  markingAll = false;

  private sub = new Subscription();

  constructor(private api: NotificationsService, private router: Router) {}

  ngOnInit(): void {
    this.fetch();

    // Auto-refresh léger (comme Messenger): ajuste ou supprime si tu veux
    const s = interval(15000)
      .pipe(
        switchMap(() =>
          this.api.getNotifications(this.take, this.mode === 'unread')
        )
      )
      .subscribe({
        next: (items) => {
          this.items = items ?? [];
          this.applyFilter();
        },
        error: () => {
          // on ne spam pas l'UI en auto-refresh; on ignore silencieusement
        },
      });

    this.sub.add(s);
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  // ===== Data
  fetch(): void {
    this.loading = true;
    this.error = null;

    const s = this.api
      .getNotifications(this.take, this.mode === 'unread')
      .subscribe({
        next: (items) => {
          this.items = items ?? [];
          this.applyFilter();
          this.loading = false;
        },
        error: (err) => {
          console.error(err);
          this.loading = false;
          this.error = 'Erreur lors du chargement des notifications.';
        },
      });

    this.sub.add(s);
  }

  // ===== UI actions
  reload(): void {
    this.fetch();
  }

  setMode(next: FilterMode): void {
    this.mode = next;
    this.fetch();
  }

  onSearch(value: string): void {
    this.searchTerm = value ?? '';
    this.applyFilter();
  }

  private applyFilter(): void {
    const term = this.searchTerm.trim().toLowerCase();
    if (!term) {
      this.filtered = [...this.items].sort((a, b) =>
        b.createdAt.localeCompare(a.createdAt)
      );
      return;
    }

    this.filtered = this.items
      .filter((n) => {
        const t = (n.title ?? '').toLowerCase();
        const b = (n.body ?? '').toLowerCase();
        return t.includes(term) || b.includes(term);
      })
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  get unreadCount(): number {
    return this.items.filter((x) => !x.readAt).length;
  }

  // ===== Mark read
  markRead(n: NotificationDto, ev?: Event): void {
    ev?.stopPropagation();
    if (n.readAt) return;

    // Optimistic UI
    const prev = n.readAt;
    n.readAt = new Date().toISOString();

    const s = this.api.markAsRead(n.id).subscribe({
      next: () => {
        this.applyFilter();
      },
      error: (err) => {
        console.error(err);
        n.readAt = prev ?? null;
        this.error = 'Impossible de marquer comme lu.';
      },
    });

    this.sub.add(s);
  }

  markAllRead(): void {
    if (!this.unreadCount) return;

    this.markingAll = true;
    const snapshot = this.items.map((x) => ({ id: x.id, readAt: x.readAt }));

    // Optimistic UI
    const now = new Date().toISOString();
    this.items = this.items.map((x) => (x.readAt ? x : { ...x, readAt: now }));
    this.applyFilter();

    const s = this.api.markAllAsRead().subscribe({
      next: () => {
        this.markingAll = false;
      },
      error: (err) => {
        console.error(err);
        // rollback
        const map = new Map(snapshot.map((x) => [x.id, x.readAt]));
        this.items = this.items.map((x) => ({
          ...x,
          readAt: map.get(x.id) ?? null,
        }));
        this.applyFilter();
        this.markingAll = false;
        this.error = 'Impossible de tout marquer comme lu.';
      },
    });

    this.sub.add(s);
  }

  // ===== Navigation
  openNotification(n: NotificationDto): void {
    // Mark read on open
    if (!n.readAt) this.markRead(n);

    // Route logique: si taskId => page tasks du projet; sinon => projets
    if (n.projectId && n.taskId) {
      this.router.navigate(['/tasks'], {
        queryParams: { projectId: n.projectId },
      });
      return;
    }

    if (n.projectId) {
      this.router.navigate(['/projects'], {
        queryParams: { focus: n.projectId },
      });
      return;
    }
  }

  // ===== Styling helpers
  pillClass(n: NotificationDto): string {
    const lvl = String(n.level ?? '').toLowerCase();

    if (lvl.includes('success'))
      return 'bg-emerald-500/15 text-emerald-200 border border-emerald-500/20';
    if (lvl.includes('warning'))
      return 'bg-amber-500/15 text-amber-200 border border-amber-500/20';
    if (lvl.includes('error'))
      return 'bg-red-500/15 text-red-200 border border-red-500/20';
    return 'bg-purple-500/15 text-purple-200 border border-purple-500/20';
  }

  iconName(n: NotificationDto): string {
    const lvl = String(n.level ?? '').toLowerCase();
    if (lvl.includes('success')) return 'Check';
    if (lvl.includes('warning')) return 'AlertTriangle';
    if (lvl.includes('error')) return 'ShieldAlert';
    return 'Info';
  }

  rowClass(n: NotificationDto): string {
    const base =
      'st-enter w-full text-left rounded-2xl border p-4 transition-all hover:shadow-sm hover:-translate-y-[1px]';
    const read = n.readAt
      ? 'border-slate-200 bg-white/70 dark:border-slate-800 dark:bg-slate-950/40'
      : 'border-purple-200 bg-purple-50/60 ring-1 ring-purple-100 dark:border-purple-500/25 dark:bg-purple-500/10 dark:ring-purple-500/10';
    return `${base} ${read}`;
  }

  clearError(): void {
    this.error = null;
  }
}
