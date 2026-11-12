import { Component, EventEmitter, Output } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  Validators,
  AbstractControl,
  ValidationErrors,
  ReactiveFormsModule,
} from '@angular/forms';
import { RouterModule, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  standalone: true,
  imports: [ReactiveFormsModule, CommonModule, RouterModule],
})
export class RegisterComponent {
  @Output() switchToLogin = new EventEmitter<void>();
  form: FormGroup;
  loading = false;
  error: string | null = null;

  constructor(
    private fb: FormBuilder,
    private auth: AuthService,
    private router: Router
  ) {
    this.form = this.fb.group(
      {
        fullName: ['', [Validators.required, Validators.minLength(3)]],
        email: ['', [Validators.required, Validators.email]],
        password: ['', [Validators.required, Validators.minLength(6)]],
        confirmPassword: ['', [Validators.required]],
        acceptTerms: [false, [Validators.requiredTrue]],
      },
      {
        validators: [this.passwordsMatchValidator],
      }
    );
  }

  get fullName() {
    return this.form.get('fullName');
  }
  get email() {
    return this.form.get('email');
  }
  get password() {
    return this.form.get('password');
  }
  get confirmPassword() {
    return this.form.get('confirmPassword');
  }
  get acceptTerms() {
    return this.form.get('acceptTerms');
  }

  private passwordsMatchValidator(
    control: AbstractControl
  ): ValidationErrors | null {
    const pwd = control.get('password')?.value;
    const confirm = control.get('confirmPassword')?.value;
    if (!pwd || !confirm) return null;
    return pwd !== confirm ? { passwordsMismatch: true } : null;
  }

  submit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.error = null;

    const { fullName, email, password } = this.form.value;

    this.auth.register({ fullName, email, password }).subscribe({
      next: () => {
        this.loading = false;
        // soit tu vas direct au dashboard :
        this.router.navigateByUrl('/dashboard');
        // soit tu pourrais faire : this.router.navigateByUrl('/auth/login');
      },
      error: (err) => {
        this.loading = false;
        this.error = err.error?.error || "Échec de l'inscription.";
        console.error('Register error', err);
      },
    });
    setTimeout(() => {
      this.loading = false;
      this.switchToLogin.emit(); // optionnel: revenir au login après création
    }, 400);
  }
}
