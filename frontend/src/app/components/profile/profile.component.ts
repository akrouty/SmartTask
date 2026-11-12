import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Subscription } from 'rxjs';
import { LucideAngularModule } from 'lucide-angular';

import { UiCardComponent } from '../../shared/ui/card/ui-card/ui-card.component';
import { UiButtonComponent } from '../../shared/ui/button/ui-button/ui-button.component';

import { ProfileService, MeDto } from '../../service/profile.service';

function matchPasswords(group: AbstractControl) {
  const newPwd = group.get('newPassword')?.value ?? '';
  const confirm = group.get('confirmPassword')?.value ?? '';
  if (!newPwd || !confirm) return null;
  return newPwd === confirm ? null : { passwordMismatch: true };
}

@Component({
  standalone: true,
  selector: 'app-profile',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    LucideAngularModule,
    UiCardComponent,
    UiButtonComponent,
  ],
  templateUrl: './profile.component.html',
  styles: [
    `
      .fade-in {
        animation: fadeIn 220ms ease-out;
      }
      .slide-up {
        animation: slideUp 260ms ease-out;
      }
      @keyframes fadeIn {
        from {
          opacity: 0;
          transform: translateY(6px);
        }
        to {
          opacity: 1;
          transform: translateY(0);
        }
      }
      @keyframes slideUp {
        from {
          opacity: 0;
          transform: translateY(10px);
        }
        to {
          opacity: 1;
          transform: translateY(0);
        }
      }
    `,
  ],
})
export class ProfileComponent implements OnInit, OnDestroy {
  me: MeDto | null = null;

  loading = false;
  savingProfile = false;
  savingPassword = false;

  error: string | null = null;
  success: string | null = null;

  profileForm: FormGroup;
  passwordForm: FormGroup;

  private sub = new Subscription();

  constructor(private fb: FormBuilder, private api: ProfileService) {
    this.profileForm = this.fb.group({
      fullName: ['', [Validators.required, Validators.minLength(2)]],
      email: [{ value: '', disabled: true }],
      role: [{ value: '', disabled: true }],
    });

    this.passwordForm = this.fb.group(
      {
        currentPassword: ['', [Validators.required, Validators.minLength(3)]],
        newPassword: ['', [Validators.required, Validators.minLength(6)]],
        confirmPassword: ['', [Validators.required]],
      },
      { validators: matchPasswords }
    );
  }

  ngOnInit(): void {
    this.refresh();
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  // ===== UI helpers
  initials(nameOrEmail: string | null | undefined): string {
    const v = (nameOrEmail ?? '').trim();
    if (!v) return 'U';
    const parts = v.includes('@') ? v.split('@')[0].split('.') : v.split(' ');
    const a = (parts[0] ?? 'U')[0] ?? 'U';
    const b = (parts[1] ?? '')[0] ?? '';
    return (a + b).toUpperCase();
  }

  roleLabel(role: string): string {
    const r = (role ?? '').toLowerCase();
    if (r === 'admin') return 'Admin';
    return 'User';
  }

  rolePillClass(role: string): string {
    const r = (role ?? '').toLowerCase();
    if (r === 'admin') {
      return 'bg-purple-600/10 text-purple-700 border border-purple-200 dark:bg-purple-500/15 dark:text-purple-100 dark:border-purple-500/25';
    }
    return 'bg-slate-600/10 text-slate-700 border border-slate-200 dark:bg-slate-700/25 dark:text-slate-100 dark:border-slate-600/30';
  }

  get pf() {
    return this.profileForm.controls;
  }
  get pwf() {
    return this.passwordForm.controls;
  }

  clearBanners(): void {
    this.error = null;
    this.success = null;
  }

  // ===== Data
  refresh(): void {
    this.loading = true;
    this.clearBanners();

    const s = this.api.me().subscribe({
      next: (me) => {
        this.me = me;
        this.profileForm.patchValue({
          fullName: me.fullName ?? '',
          email: me.email ?? '',
          role: me.role ?? '',
        });
        this.loading = false;
      },
      error: (err) => {
        console.error(err);
        this.loading = false;
        this.error = 'Impossible de charger votre profil.';
      },
    });

    this.sub.add(s);
  }

  saveProfile(): void {
    this.clearBanners();

    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }

    const fullName = String(
      this.profileForm.get('fullName')?.value ?? ''
    ).trim();
    if (!fullName) return;

    this.savingProfile = true;

    const s = this.api.updateMe(fullName).subscribe({
      next: (me) => {
        this.me = me;
        this.profileForm.patchValue({
          fullName: me.fullName ?? '',
          email: me.email ?? '',
          role: me.role ?? '',
        });
        this.savingProfile = false;
        this.success = 'Profil mis à jour.';
      },
      error: (err) => {
        console.error(err);
        this.savingProfile = false;
        this.error = 'Erreur lors de la mise à jour du profil.';
      },
    });

    this.sub.add(s);
  }

  changePassword(): void {
    this.clearBanners();

    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    if (this.passwordForm.hasError('passwordMismatch')) {
      this.error = 'Les mots de passe ne correspondent pas.';
      return;
    }

    const currentPassword = String(this.pwf['currentPassword'].value ?? '');
    const newPassword = String(this.pwf['newPassword'].value ?? '');

    this.savingPassword = true;

    const s = this.api.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.savingPassword = false;
        this.passwordForm.reset();
        this.success = 'Mot de passe modifié.';
      },
      error: (err) => {
        console.error(err);
        this.savingPassword = false;
        this.error = 'Mot de passe actuel incorrect ou erreur serveur.';
      },
    });

    this.sub.add(s);
  }
}
