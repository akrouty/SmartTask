import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { InvitesService, InviteDto } from '../../core/invites.service';
import { AuthService } from '../../core/auth.service';

@Component({
  standalone: true,
  selector: 'app-accept-invite',
  imports: [CommonModule],
  templateUrl: './accept-invite.component.html',
})
export class AcceptInviteComponent implements OnInit {
  loading = true;
  error: string | null = null;
  invite: InviteDto | null = null;
  accepting = false;

  // 🔹 on garde le token ici
  private token: string | null = null;

  constructor(
    private route: ActivatedRoute,
    public router: Router,
    private invites: InvitesService,
    private auth: AuthService
  ) {}

  ngOnInit(): void {
    // 1) On essaie d’abord en param d’URL : /accept-invite/:token
    const fromParam = this.route.snapshot.paramMap.get('token');

    // 2) Puis en query param : /accept-invite?token=...
    const fromQuery = this.route.snapshot.queryParamMap.get('token');

    this.token = fromParam || fromQuery;

    if (!this.token) {
      this.loading = false;
      this.error = 'Lien invalide : token manquant.';
      return;
    }

    // Charger les infos de l’invitation
    this.invites.getInvite(this.token).subscribe({
      next: (inv) => {
        this.invite = inv;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        console.error('Get invite error', err);
        this.error = err.error?.error || 'Invitation introuvable ou expirée.';
      },
    });
  }

  get isAuthenticated(): boolean {
    return this.auth.isAuthenticated();
  }

  accept(): void {
    if (!this.token) {
      // sécurité
      this.error = 'Lien d’invitation invalide.';
      return;
    }

    this.accepting = true;
    this.error = null;

    this.invites.acceptInvite(this.token).subscribe({
      next: () => {
        this.accepting = false;
        // après acceptation, redirection vers /projects
        this.router.navigateByUrl('/projects');
      },
      error: (err) => {
        this.accepting = false;
        console.error('Accept invite error', err);

        if (err.status === 401) {
          this.error =
            'Vous devez être connecté pour accepter cette invitation.';
        } else if (err.status === 403) {
          this.error =
            'Cette invitation ne correspond pas à votre adresse email.';
        } else {
          this.error =
            err.error?.error || 'Erreur lors de l’acceptation de l’invitation.';
        }
      },
    });
  }
}
