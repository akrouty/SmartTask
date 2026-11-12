import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LucideAngularModule, Moon } from 'lucide-angular';
import { RegisterComponent } from '../register/register.component';
import { LoginComponent } from '../login/login.component';
import { CommonModule } from '@angular/common';
import { ThemeService } from '../../core/theme.service';

type AuthMode = 'login' | 'register';

@Component({
  selector: 'app-auth-shell',
  imports: [
    LucideAngularModule,
    RegisterComponent,
    LoginComponent,
    CommonModule,
  ],
  templateUrl: './auth-shell.component.html',
  styleUrls: ['./auth-shell.component.scss'],
})
export class AuthShellComponent implements OnInit {
  mode: AuthMode = 'login';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    public theme: ThemeService
  ) {}

  ngOnInit(): void {
    // Permet d’ouvrir directement /auth?mode=register
    const m = (this.route.snapshot.queryParamMap.get('mode') ||
      'login') as AuthMode;
    this.mode = m === 'register' ? 'register' : 'login';
  }

  setMode(mode: AuthMode) {
    this.mode = mode;
    // garde l’URL sync sans recharger
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { mode },
      queryParamsHandling: 'merge',
    });
  }
  toggleTheme() {
    this.theme.toggle(); // si ta méthode s'appelle différemment, dis-moi son nom
  }
}
