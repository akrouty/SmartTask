import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { interval, Subscription } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { ProjectService } from '../../service/project.service';
import { TaskService } from '../../service/task.service';

import { Project, ProjectMember } from '../../model/project.model';
import { Task } from '../../model/task.model';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AuthRoutingModule } from '../../auth/auth-routing.module';
import { AuthService } from '../../core/auth.service';
import { InvitesService, ProjectRole } from '../../core/invites.service';
import {
  ProjectMemberDto,
  ProjectMembersService,
} from '../../service/project-members.service';
import { RouterModule } from '@angular/router';
import { UiButtonComponent } from '../../shared/ui/button/ui-button/ui-button.component';
import { UiCardComponent } from '../../shared/ui/card/ui-card/ui-card.component';

interface MemberAvatar {
  display: string;
  initials: string;
}

interface ProjectCard {
  project: Project;
  totalTasks: number;
  doneTasks: number;
  progressPercent: number;
  membersCount: number;
  currentUserRole: ProjectRole | null;
  memberAvatars: MemberAvatar[];
}
@Component({
  standalone: true,
  selector: 'app-projects-list',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    AuthRoutingModule,
    RouterModule,
    UiButtonComponent,
    UiCardComponent,
    ReactiveFormsModule,
  ],
  templateUrl: './projects.component.html',
})
export class ProjectsListComponent implements OnInit {
  loading = false;
  error: string | null = null;

  projects: Project[] = [];
  projectCards: ProjectCard[] = [];
  createForm: FormGroup;
  showCreateModal = false;
  editing = false;
  editingProjectId: string | null = null;
  selectedProjectMembers: ProjectMemberDto[] = [];

  inviteForm: FormGroup | null = null;
  projectForInvite: Project | null = null;
  inviteLoading = false;
  inviteError: string | null = null;
  inviteSuccess: string | null = null;
  showInviteModal = false;
  currentInviteProjectId: string | null = null;

  private refreshSub?: Subscription;

  constructor(
    private projectService: ProjectService,
    private taskService: TaskService,
    private fb: FormBuilder,
    private authService: AuthService,
    private projectMembersService: InvitesService
  ) {
    this.createForm = this.fb.group({
      name: ['', Validators.required],
      description: [''],
    });

    this.inviteForm = this?.fb.group({
      email: ['', [Validators.required, Validators.email]],
      role: [1 as ProjectRole, [Validators.required]],
    });
  }

  ngOnInit(): void {
    this.loadData();
    // Auto-refresh every 60 seconds
    this.refreshSub = interval(600000)
      .pipe(switchMap(() => this.projectService.getProjects()))
      .subscribe({
        next: (projects) => {
          this.projects = projects;
          this.loadData();
        },
        error: (err) => {
          console.error('Auto-refresh projects error', err);
        },
      });
  }

  loadData() {
    this.loading = true;
    this.error = null;

    const currentUserId = this.authService.getCurrentUserId();
    const currentUser = this.authService.getCurrentUser();

    forkJoin({
      projects: this.projectService.getProjects(), // Observable<Project[]>
      tasksRes: this.taskService.getTasks(), // { items: Task[], total: number }
    }).subscribe({
      next: ({ projects, tasksRes }) => {
        const tasks = tasksRes.items;

        this.projects = projects;

        this.projectCards = projects.map((project) => {
          const projectTasks = tasks.filter(
            (t: { projectId: any }) => t.projectId === project.id
          );
          const totalTasks = projectTasks.length;
          const doneTasks = projectTasks.filter(
            (t: { status: string }) => t.status === 'done'
          ).length;

          const progressPercent =
            totalTasks === 0 ? 0 : Math.round((doneTasks / totalTasks) * 100);

          const members = (project as any).members ?? [];
          const membersCount = members.length;

          let currentUserRole: ProjectRole | null = null;
          if (currentUserId !== null) {
            const membership = members.find(
              (m: any) => m.userId === currentUserId
            );
            currentUserRole = membership
              ? (membership.role as ProjectRole)
              : null;
          }

          const memberAvatars = members.slice(0, 4).map((m: any) => {
            const fullName = m.fullName ?? m.user?.fullName ?? null;
            const email = m.email ?? m.user?.email ?? null;

            const display = fullName || email || m.userId;
            const initials = this.getInitialsFromDisplay(display);

            return { display, initials };
          });

          return {
            project,
            totalTasks,
            doneTasks,
            progressPercent,
            membersCount,
            currentUserRole,
            memberAvatars,
          };
        });

        this.loading = false;
      },
      error: (err) => {
        console.error('Projects load error', err);
        this.error =
          err.error?.error || 'Erreur lors du chargement des projets.';
        this.loading = false;
      },
    });
  }

  private getInitialsFromDisplay(display: string): string {
    if (!display) return '?';

    display = display.trim();

    // 1) Cas e-mail : on prend la partie avant le @
    const atIndex = display.indexOf('@');
    if (atIndex > 0) {
      const localPart = display.substring(0, atIndex); // avant le @
      // On découpe sur ., _, - pour simuler “prénom nom”
      const parts = localPart.split(/[.\-_]+/).filter((p) => p.length > 0);

      if (parts.length === 0) {
        return localPart.substring(0, 2).toUpperCase();
      }

      if (parts.length === 1) {
        // On enlève les chiffres au début
        const lettersOnly = parts[0].replace(/^[^a-zA-Z]+/, '');
        if (!lettersOnly) return parts[0].substring(0, 2).toUpperCase();
        return lettersOnly.substring(0, 2).toUpperCase();
      }

      // deux segments ou plus
      const first = parts[0].replace(/^[^a-zA-Z]+/, '');
      const second = parts[1].replace(/^[^a-zA-Z]+/, '');
      const i1 = first ? first[0] : parts[0][0];
      const i2 = second ? second[0] : parts[1][0];

      return (i1 + i2).toUpperCase();
    }

    // 2) Cas “Prénom Nom”
    const words = display.split(/\s+/).filter((w) => w.length > 0);
    if (words.length === 1) {
      return words[0].substring(0, 2).toUpperCase();
    }

    const i1 = words[0][0];
    const i2 = words[1][0];
    return (i1 + i2).toUpperCase();
  }

  openCreateModal() {
    this.editing = false;
    this.editingProjectId = null;

    this.createForm.reset({
      name: '',
      description: '',
    });
    this.showCreateModal = true;
  }
  closeCreateModal() {
    this.showCreateModal = false;
  }

  submitCreate() {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }

    const raw = this.createForm.value;
    const payload = {
      name: raw.name as string,
      description: raw.description ?? null,
    };

    this.loading = true;

    if (this.editing && this.editingProjectId) {
      // 🔹 UPDATE
      this.projectService
        .updateProject(this.editingProjectId, payload)
        .subscribe({
          next: () => {
            this.loading = false;
            this.showCreateModal = false;
            this.editingProjectId = null;
            this.loadData();
          },
          error: (err) => {
            this.loading = false;
            console.error('Update project error', err);
            this.error =
              err.error?.error || 'Erreur lors de la mise à jour du projet.';
          },
        });
    } else {
      // 🔹 CREATE
      this.projectService.createProject(payload).subscribe({
        next: () => {
          this.loading = false;
          this.showCreateModal = false;
          this.loadData();
        },
        error: (err) => {
          this.loading = false;
          console.error('Create project error', err);
          this.error =
            err.error?.error || 'Erreur lors de la création du projet.';
        },
      });
    }
  }

  get name() {
    return this.createForm.get('name');
  }

  openEditModal(card: ProjectCard) {
    this.editing = true;
    this.editingProjectId = card.project.id;

    this.createForm.reset({
      name: card.project.name,
      description: card.project.description ?? '',
    });

    this.projectMembersService.getMembers(card.project.id).subscribe({
      next: (res) => {
        this.selectedProjectMembers = res.members;
      },
      error: (err) => {
        console.error('Error loading project members', err);
        this.selectedProjectMembers = [];
      },
    });

    this.showCreateModal = true;
  }

  onDeleteProject(card: ProjectCard) {
    const ok = confirm(
      `Supprimer le projet "${card.project.name}" ? Toutes les tâches associées seront supprimées.`
    );
    if (!ok) return;

    this.loading = true;

    this.projectService.deleteProject(card.project.id).subscribe({
      next: () => {
        this.loading = false;
        this.loadData();
      },
      error: (err) => {
        this.loading = false;
        console.error('Delete project error', err);
        this.error =
          err.error?.error || 'Erreur lors de la suppression du projet.';
      },
    });
  }

  openInviteModal(project: Project) {
    this.currentInviteProjectId = project.id;
    this.inviteForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      role: [1, Validators.required], // 1 = Member par défaut
    });
    this.showInviteModal = true;
  }

  closeInviteModal() {
    this.showInviteModal = false;
    this.inviteForm = null;
  }

  getInviteEmail() {
    return this.inviteForm?.get('email');
  }

  submitInvite() {
    if (!this.currentInviteProjectId) {
      this.inviteError = 'Projet cible introuvable pour cette invitation.';
      return;
    }

    if (!this.selectedProjectMembers) return;
    if (!this.inviteForm || this.inviteForm.invalid) {
      this.inviteForm?.markAllAsTouched();
      return;
    }

    const body = this.inviteForm.value;
    const payload = {
      email: body.email,
      role: Number(body.role) as ProjectRole,
    };
    this.inviteLoading = true;
    this.inviteError = null;

    this.projectMembersService
      .createInvite(this.currentInviteProjectId, payload)
      .subscribe({
        next: (invite) => {
          this.inviteLoading = false;
          const link = `${window.location.origin}/accept-invite/${invite.token}`;

          this.inviteSuccess = `Invitation créée pour ${invite.email}. Lien d'invitation : ${link}`;
          this.inviteForm?.reset({
            email: '',
            role: 1,
          });
          this.loadData();

          if (invite.reused) {
            this.inviteSuccess +=
              ' (Un lien d’invitation existant a été réutilisé.)';
          } else {
            this.inviteSuccess += ' (Un email a été envoyé.)';
          }
        },
        error: (err) => {
          this.inviteLoading = false;
          console.error('Create invite error', err);
          const errors = err.error?.errors;
          if (errors) {
            const firstKey = Object.keys(errors)[0];
            if (
              firstKey &&
              Array.isArray(errors[firstKey]) &&
              errors[firstKey].length
            ) {
              this.inviteError = errors[firstKey][0];
            } else {
              this.inviteError = 'Données invalides pour l’invitation.';
            }
          } else if (err.error?.error) {
            this.inviteError = err.error.error;
          } else {
            this.inviteError = 'Erreur lors de la création de l’invitation.';
          }
        },
      });
  }
  reload() {
    this.loadData();
  }
}
