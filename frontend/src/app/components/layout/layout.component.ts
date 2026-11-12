import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import {
  RouterModule,
  Router,
  RouterOutlet,
  RouterLink,
  RouterLinkActive,
} from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ThemeService } from '../../core/theme.service';
import {
  LucideAngularModule,
  LayoutDashboard,
  Folder,
  CheckSquare,
  MessageSquare,
  PieChart,
  User,
  Bell,
  Sun,
  Moon,
  PlusIcon,
} from 'lucide-angular';
import { UiButtonComponent } from '../../shared/ui/button/ui-button/ui-button.component';
import { NotificationsStoreService } from '../../service/notifications-store.service';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    LucideAngularModule,
  ],
  templateUrl: './layout.component.html',
})
export class MainLayoutComponent {
  icons = {
    dashboard: LayoutDashboard,
    projects: Folder,
    tasks: CheckSquare,
    messages: MessageSquare,
    stats: PieChart,
    profile: User,
    notifications: Bell,
    plus: PlusIcon,
  };

  sun = Sun;
  moon = Moon;
  open = false;
  unreadCount$!: Observable<number>;
  latest$!: Observable<any[]>;
  private closeTimer: any = null;

  constructor(
    public auth: AuthService,
    private router: Router,
    public theme: ThemeService,
    private notifStore: NotificationsStoreService,
    private store: NotificationsStoreService
  ) {
    this.unreadCount$ = this.notifStore.unreadCount$;
    this.latest$ = this.store.latest$;
  }

  logout() {
    this.auth.logout();
    this.router.navigate(['/auth/login']);
  }

  ngOnInit() {
    this.theme.init();
    this.notifStore.refresh();
    this.store.refresh();
  }
  goToNewProject() {
    this.router.navigate(['/projects']);
  }
  openDropdown() {
    if (this.closeTimer) clearTimeout(this.closeTimer);
    this.open = true;
  }

  scheduleClose() {
    if (this.closeTimer) clearTimeout(this.closeTimer);
    this.closeTimer = setTimeout(() => {
      this.open = false;
    }, 220); // ajuste 150–300ms selon ton feeling
  }

  toggleDropdown() {
    if (this.closeTimer) clearTimeout(this.closeTimer);
    this.open = !this.open;
  }

  closeNow() {
    if (this.closeTimer) clearTimeout(this.closeTimer);
    this.open = false;
  }
}
