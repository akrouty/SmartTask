import { Routes } from '@angular/router';
import { AuthGuard } from './core/auth.guard';
import { MainLayoutComponent } from './components/layout/layout.component';
import { AcceptInviteComponent } from './components/accept-invite/accept-invite.component';

export const routes: Routes = [
  // Auth (login / register)
  {
    path: 'auth',
    loadChildren: () => import('./auth/auth.module').then((m) => m.AuthModule),
  },

  // ✅ PUBLIC: Accept invite must be outside AuthGuard
  { path: 'accept-invite/:token', component: AcceptInviteComponent },

  // Layout protégé par AuthGuard
  {
    path: '',
    canActivate: [AuthGuard],
    component: MainLayoutComponent,
    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./components/dashboard/dashboard.component').then(
            (m) => m.DashboardComponent
          ),
      },
      {
        path: 'projects',
        loadComponent: () =>
          import('./components/projects/projects.component').then(
            (m) => m.ProjectsListComponent
          ),
      },
      {
        path: 'tasks',
        loadComponent: () =>
          import('./components/tasks/tasks.component').then(
            (m) => m.TasksListComponent
          ),
      },
      {
        path: 'stats',
        loadComponent: () =>
          import('./components/stats/stats.component').then(
            (m) => m.StatsComponent
          ),
      },
      {
        path: 'messages',
        loadComponent: () =>
          import('./components/messages/messages.component').then(
            (m) => m.MessagesComponent
          ),
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./components/notifications/notifications.component').then(
            (m) => m.NotificationsComponent
          ),
      },
      {
        path: 'profile',
        loadComponent: () =>
          import('./components/profile/profile.component').then(
            (m) => m.ProfileComponent
          ),
      },

      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },

  // fallback
  { path: '**', redirectTo: 'auth/login' },
];
