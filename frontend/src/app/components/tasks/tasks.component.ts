import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { forkJoin, interval, Subscription } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import {
  FormBuilder,
  FormGroup,
  Validators,
  ReactiveFormsModule,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import {
  DragDropModule,
  CdkDragDrop,
  moveItemInArray,
  transferArrayItem,
} from '@angular/cdk/drag-drop';

import { TaskService } from '../../service/task.service';
import { ProjectService } from '../../service/project.service';
import { ProjectMembersService } from '../../service/project-members.service';
import { AuthService } from '../../core/auth.service';

import { Task } from '../../model/task.model';
import { Project } from '../../model/project.model';

import { UiCardComponent } from '../../shared/ui/card/ui-card/ui-card.component';
import { UiButtonComponent } from '../../shared/ui/button/ui-button/ui-button.component';
import { LucideAngularModule } from 'lucide-angular';

interface TaskColumn {
  key: 'open' | 'in-progression' | 'done';
  title: string;
  icon: string;
  tasks: Task[];
}

type ProjectRole = 0 | 1 | 2;
type SortKey = 'dueDate' | 'priority' | 'title' | 'project';
type SortDir = 'asc' | 'desc';
type SortMode = 'priority' | 'dueDate' | null;
type NoticeType = 'info' | 'warning' | 'error' | 'success';

@Component({
  standalone: true,
  selector: 'app-tasks-list',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    DragDropModule,
    LucideAngularModule,
    UiButtonComponent,
    UiCardComponent,
  ],
  templateUrl: './tasks.component.html',
})
export class TasksListComponent implements OnInit, OnDestroy {
  // ===== Data sources
  private allTasks: Task[] = [];
  projects: Project[] = [];
  projectNameById: Record<string, string> = {};

  // ===== Context (source de vérité)
  activeProjectId: string | null = null;

  // ===== UI State
  loading = false;
  error: string | null = null;

  showFiltersPanel = false;
  showCreateModal = false;

  // ===== Filters
  searchTerm = '';
  filterStatus: string | null = null;
  filterPriority: number | null = null;

  // ===== List View (global)
  sortKey: SortKey = 'priority';
  sortMode: SortMode = null;
  sortDir: SortDir = 'desc';
  pageSize = 20;
  pageIndex = 1;

  // ===== Derived
  tasksFiltered: Task[] = []; // après filtre/tri (global OU projet)
  columns: TaskColumn[] = []; // uniquement en mode projet

  // ===== Permissions (uniquement cohérentes quand activeProjectId set)
  currentProjectRole: ProjectRole | null = null;
  canCreateTasks = false;
  canEditTasks = false;
  canDeleteTasks = false;

  // ===== Modal
  editing = false;
  editingTaskId: string | null = null;
  createForm: FormGroup;

  private refreshSubscription: Subscription | null = null;

  constructor(
    private taskService: TaskService,
    private projectService: ProjectService,
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private projectMembersService: ProjectMembersService,
    private auth: AuthService
  ) {
    this.createForm = this.fb.group({
      title: ['', [Validators.required, Validators.minLength(3)]],
      description: [''],
      projectId: ['', Validators.required],
      priority: [1, Validators.required], // 0/1/2 selon ton mapping actuel
      status: ['open', Validators.required],
      dueDate: [''],
    });
  }

  notice: { type: NoticeType; message: string } | null = null;
  private noticeTimer: any = null;

  private showNotice(type: NoticeType, message: string, ms = 4500) {
    this.notice = { type, message };
    if (this.noticeTimer) clearTimeout(this.noticeTimer);
    this.noticeTimer = setTimeout(() => (this.notice = null), ms);
  }

  ngOnInit(): void {
    // Lire le contexte depuis l'URL
    this.route.queryParamMap.subscribe((params) => {
      const projectIdParam = params.get('projectId');
      const roleParam = params.get('role');

      this.activeProjectId =
        projectIdParam && projectIdParam !== 'all' ? projectIdParam : null;

      // Permissions : uniquement en contexte projet
      if (this.activeProjectId) {
        if (roleParam !== null && roleParam !== 'null' && roleParam !== '') {
          this.applyRolePermissions(Number(roleParam) as ProjectRole);
        } else {
          this.loadPermissionsForProject(this.activeProjectId);
        }
      } else {
        this.clearPermissions(); // global = lecture / création via sélection projet dans modale
      }

      // Charger data
      this.fetchAll();

      // Auto-refresh uniquement si mode projet
      this.setupAutoRefresh();
    });
  }

  ngOnDestroy(): void {
    this.refreshSubscription?.unsubscribe();
  }

  // ===== View helpers
  get isProjectMode(): boolean {
    return !!this.activeProjectId;
  }

  get pagedTasks(): Task[] {
    const start = (this.pageIndex - 1) * this.pageSize;
    return this.tasksFiltered.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.tasksFiltered.length / this.pageSize));
  }

  // ===== Fetch
  fetchAll(): void {
    this.loading = true;
    this.error = null;

    forkJoin({
      tasksRes: this.taskService.getTasks(),
      projects: this.projectService.getProjects(),
    }).subscribe({
      next: ({ tasksRes, projects }) => {
        this.projects = projects;
        this.projectNameById = {};
        for (const p of projects) this.projectNameById[p.id] = p.name;

        // Normalisation
        this.allTasks = (tasksRes.items ?? []).map((t: any) => ({
          ...t,
          priority: t.priority != null ? t.priority : 1,
          dueDate: t.dueDate && t.dueDate !== 'string' ? t.dueDate : null,
          status: t.status ?? 'open',
        }));

        this.applyQuery();
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error =
          err.error?.error || 'Erreur lors du chargement des tâches.';
        console.error('Tasks fetch error', err);
      },
    });
  }

  reload(): void {
    this.fetchAll();
  }

  // ===== Auto refresh (project only)
  private setupAutoRefresh(): void {
    this.refreshSubscription?.unsubscribe();
    this.refreshSubscription = null;

    if (!this.activeProjectId) return;

    this.refreshSubscription = interval(15000)
      .pipe(switchMap(() => this.taskService.getTasks()))
      .subscribe({
        next: (tasksRes) => {
          this.allTasks = (tasksRes.items ?? []).map((t: any) => ({
            ...t,
            priority: t.priority != null ? t.priority : 1,
            dueDate: t.dueDate && t.dueDate !== 'string' ? t.dueDate : null,
            status: t.status ?? 'open',
          }));
          this.applyQuery();
        },
        error: (err) => console.error('Auto-refresh tasks error', err),
      });
  }

  // ===== Context switch (dropdown)
  onContextProjectChange(value: string): void {
    const next = value === '' ? null : value;

    // Mettre à jour l’URL (source de vérité)
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { projectId: next ?? null, role: null },
      queryParamsHandling: 'merge',
    });
  }

  // ===== Filter / sort
  applyQuery(): void {
    let filtered = [...this.allTasks];

    // Contexte projet
    if (this.activeProjectId) {
      filtered = filtered.filter((t) => t.projectId === this.activeProjectId);
    }

    // Search
    const term = this.searchTerm.trim().toLowerCase();
    if (term) {
      filtered = filtered.filter((t: any) => {
        const projName = this.projectNameById[t.projectId] ?? '';
        return (
          (t.title ?? '').toLowerCase().includes(term) ||
          (t.description ?? '').toLowerCase().includes(term) ||
          projName.toLowerCase().includes(term)
        );
      });
    }

    // Filters
    if (this.filterStatus)
      filtered = filtered.filter((t) => t.status === this.filterStatus);
    if (this.filterPriority !== null)
      filtered = filtered.filter(
        (t) => (t.priority ?? 1) === this.filterPriority
      );

    // Sort (global list surtout)
    filtered = this.sortTasks(filtered);

    this.tasksFiltered = filtered;
    const toTime = (d?: string | null) => {
      if (!d) return Number.POSITIVE_INFINITY; // sans dueDate -> à la fin
      const t = new Date(d).getTime();
      return Number.isNaN(t) ? Number.POSITIVE_INFINITY : t;
    };

    if (this.sortMode === 'priority') {
      filtered.sort((a, b) => {
        const pa = a.priority ?? 1;
        const pb = b.priority ?? 1;

        if (pa !== pb) return this.sortDir === 'asc' ? pa - pb : pb - pa;

        // tie-breaker: dueDate la plus proche d'abord
        return toTime(a.dueDate) - toTime(b.dueDate);
      });
    }

    if (this.sortMode === 'dueDate') {
      filtered.sort((a, b) => {
        const da = toTime(a.dueDate);
        const db = toTime(b.dueDate);

        if (da !== db) return this.sortDir === 'asc' ? da - db : db - da;

        // tie-breaker: priorité haute d'abord
        const pa = a.priority ?? 1;
        const pb = b.priority ?? 1;
        return pb - pa;
      });
    }

    // Reset pagination if needed
    if (!this.activeProjectId) {
      this.pageIndex = Math.min(this.pageIndex, this.totalPages);
      if (this.pageIndex < 1) this.pageIndex = 1;
    }

    // Build board uniquement si projet
    if (this.activeProjectId) this.buildColumns();
    else this.columns = [];
  }

  private sortTasks(arr: Task[]): Task[] {
    const dir = this.sortDir === 'asc' ? 1 : -1;

    return arr.sort((a: any, b: any) => {
      const pa = a.priority ?? 1;
      const pb = b.priority ?? 1;

      const da = a.dueDate ?? '9999-12-31';
      const db = b.dueDate ?? '9999-12-31';

      const ta = (a.title ?? '').toLowerCase();
      const tb = (b.title ?? '').toLowerCase();

      const pra = this.projectNameById[a.projectId] ?? '';
      const prb = this.projectNameById[b.projectId] ?? '';

      switch (this.sortKey) {
        case 'priority':
          return (pb - pa) * dir;
        case 'dueDate':
          return da.localeCompare(db) * dir;
        case 'title':
          return ta.localeCompare(tb) * dir;
        case 'project':
          return pra.localeCompare(prb) * dir;
        default:
          return 0;
      }
    });
  }

  onSearchTermChange(value: string): void {
    this.searchTerm = value;
    this.pageIndex = 1;
    this.applyQuery();
  }

  toggleFiltersPanel(): void {
    this.showFiltersPanel = !this.showFiltersPanel;
  }

  onStatusFilterChange(value: string): void {
    this.filterStatus = value || null;
    this.pageIndex = 1;
    this.applyQuery();
  }

  onPriorityFilterChange(value: string): void {
    this.filterPriority = value === '' ? null : Number(value);
    this.pageIndex = 1;
    this.applyQuery();
  }

  resetFilters(): void {
    this.filterStatus = null;
    this.filterPriority = null;
    this.searchTerm = '';
    this.pageIndex = 1;
    this.applyQuery();
  }

  setSort(mode: Exclude<SortMode, null>) {
    // comportement "checkbox mais radio"
    if (this.sortMode === mode) {
      // reclic -> inverse l'ordre
      this.sortDir = this.sortDir === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortMode = mode;
      // valeurs par défaut selon mode
      this.sortDir = mode === 'dueDate' ? 'asc' : 'desc';
    }
    this.applyQuery();
  }
  clearSort() {
    this.sortMode = null;
    this.sortDir = 'desc';
    this.applyQuery();
  }

  setPageSize(v: string): void {
    this.pageSize = Number(v);
    this.pageIndex = 1;
  }

  prevPage(): void {
    this.pageIndex = Math.max(1, this.pageIndex - 1);
  }

  nextPage(): void {
    this.pageIndex = Math.min(this.totalPages, this.pageIndex + 1);
  }

  // ===== Board build
  private buildColumns(): void {
    const byStatus: Record<'open' | 'in-progression' | 'done', Task[]> = {
      open: [],
      'in-progression': [],
      done: [],
    };

    for (const t of this.tasksFiltered) {
      const s = (t.status ?? 'open') as keyof typeof byStatus;
      (byStatus[s] ?? byStatus.open).push(t);
    }

    const sortBoard = (arr: Task[]) =>
      arr.sort((a, b) => {
        const pa = a.priority ?? 1;
        const pb = b.priority ?? 1;
        if (pa !== pb) return pb - pa;

        const da = a.dueDate ?? '9999-12-31';
        const db = b.dueDate ?? '9999-12-31';
        return da.localeCompare(db);
      });

    sortBoard(byStatus.open);
    sortBoard(byStatus['in-progression']);
    sortBoard(byStatus.done);

    this.columns = [
      { key: 'open', title: 'À faire', icon: 'circle', tasks: byStatus.open },
      {
        key: 'in-progression',
        title: 'En cours',
        icon: 'loader',
        tasks: byStatus['in-progression'],
      },
      {
        key: 'done',
        title: 'Terminé',
        icon: 'check-circle',
        tasks: byStatus.done,
      },
    ];
  }
  private buildUpdatePayload(
    task: Task,
    overrides: Partial<Task> = {}
  ): Partial<Task> {
    const t: any = task;

    return {
      title: task.title,
      // si tu stockes description dans (task as any).description
      description: t.description ?? '',
      projectId: task.projectId,
      priority: task.priority ?? 1,
      status: task.status ?? 'open',
      dueDate: task.dueDate ? String(task.dueDate).substring(0, 10) : undefined,
      ...overrides,
    };
  }

  // ===== Drag & Drop (project mode)
  onDrop(
    event: CdkDragDrop<Task[]>,
    targetStatus: 'open' | 'in-progression' | 'done'
  ) {
    if (!this.canEditTasks) {
      this.showNotice(
        'warning',
        "Mode lecture seule : vous n'êtes pas autorisé à déplacer les tâches (Observer).",
        4500
      );
      return;
    }

    // même colonne -> reorder only
    if (event.previousContainer === event.container) {
      moveItemInArray(
        event.container.data,
        event.previousIndex,
        event.currentIndex
      );
      return;
    }

    // colonne -> colonne
    transferArrayItem(
      event.previousContainer.data,
      event.container.data,
      event.previousIndex,
      event.currentIndex
    );

    const movedTask = event.container.data[event.currentIndex];
    if (!movedTask) return;

    const oldStatus = movedTask.status as any;

    // Optimistic UI
    movedTask.status = targetStatus;

    // IMPORTANT: envoyer un payload COMPLET (pas juste {status})
    const payload = this.buildUpdatePayload(movedTask, {
      status: targetStatus,
    });

    this.taskService.updateTask(movedTask.id, payload).subscribe({
      next: () => {
        // Le plus sûr: re-sync avec backend (surtout avec ton auto-refresh)
        this.fetchAll();
      },
      error: (err) => {
        console.error('DragDrop update status error', err);

        // Rollback UI
        movedTask.status = oldStatus;
        this.applyQuery();

        // Optionnel: montrer un message
        this.error =
          'Impossible de changer le statut (droits ou payload invalide).';
      },
    });
  }

  // ===== List interactions (global)
  openTaskFromGlobal(task: Task): void {
    // Global = pas d’édition directe : on va vers le contexte projet
    this.router.navigate(['/tasks'], {
      queryParams: { projectId: task.projectId },
    });
  }

  // ===== Create / Edit modal
  openCreateModal(): void {
    this.editing = false;
    this.editingTaskId = null;

    this.createForm.reset({
      title: '',
      description: '',
      projectId: this.activeProjectId ?? '',
      priority: 1,
      status: 'open',
      dueDate: '',
    });

    // Si contexte projet : on impose permissions du projet
    if (this.activeProjectId && !this.canCreateTasks) {
      this.showNotice(
        'warning',
        "Permission refusée : vous n'avez pas le droit d'effectuer cette action."
      );
      return;
    }

    this.showCreateModal = true;

    // Si on a déjà un projet sélectionné dans la modale, charger permissions
    const pid = this.createForm.value.projectId;
    if (pid) this.loadPermissionsForProject(pid);
  }

  openEditModal(task: Task): void {
    // Autorisé uniquement en mode projet (propre)
    if (!this.activeProjectId) {
      this.openTaskFromGlobal(task);
      return;
    }

    if (!this.canEditTasks) {
      this.showNotice(
        'warning',
        "Permission refusée : vous n'avez pas le droit d'effectuer cette action."
      );

      return;
    }

    this.editing = true;
    this.editingTaskId = task.id;

    this.createForm.reset({
      title: task.title,
      description: (task as any).description || '',
      projectId: task.projectId,
      priority: task.priority ?? 1,
      status: task.status ?? 'open',
      dueDate: task.dueDate ? String(task.dueDate).substring(0, 10) : '',
    });

    this.showCreateModal = true;
    this.loadPermissionsForProject(task.projectId);
  }

  closeCreateModal(): void {
    this.showCreateModal = false;
  }

  onModalProjectChange(value: string): void {
    this.createForm.patchValue({ projectId: value });
    if (value) this.loadPermissionsForProject(value);
    else this.clearPermissions();
  }

  submitCreate(): void {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }

    const raw = this.createForm.value;

    // Permissions check
    if (this.editing) {
      if (!this.canEditTasks) {
        alert('Permission refusée: modification non autorisée.');
        return;
      }
    } else {
      if (!this.canCreateTasks) {
        this.showNotice(
          'warning',
          "Permission refusée : vous n'avez pas le droit d'effectuer cette action."
        );

        return;
      }
    }

    const payload: Partial<Task> = {
      title: raw.title,
      // @ts-ignore
      description: raw.description,
      projectId: raw.projectId,
      status: raw.status,
      priority: raw.priority,
      dueDate: raw.dueDate ? raw.dueDate : undefined,
    };

    this.loading = true;

    if (this.editing && this.editingTaskId) {
      this.taskService.updateTask(this.editingTaskId, payload).subscribe({
        next: () => {
          this.loading = false;
          this.showCreateModal = false;
          this.editingTaskId = null;
          this.fetchAll();
        },
        error: (err) => {
          this.loading = false;
          console.error('Update task error', err);
        },
      });
    } else {
      this.taskService.createTask(payload).subscribe({
        next: () => {
          this.loading = false;
          this.showCreateModal = false;
          // Après création, si on est global mais on a choisi un projet, on peut basculer dessus (optionnel)
          if (!this.activeProjectId && raw.projectId) {
            this.router.navigate(['/tasks'], {
              queryParams: { projectId: raw.projectId },
            });
            return;
          }
          this.fetchAll();
        },
        error: (err) => {
          this.loading = false;
          console.error('Create task error', err);
        },
      });
    }
  }

  // ===== Delete
  onDeleteTask(task: Task): void {
    if (!this.activeProjectId) {
      this.openTaskFromGlobal(task);
      return;
    }

    if (!this.canDeleteTasks) {
      alert("Vous n'avez pas la permission de supprimer cette tâche.");
      return;
    }

    const ok = confirm(`Supprimer la tâche "${task.title}" ?`);
    if (!ok) return;

    this.loading = true;
    this.taskService.deleteTask(task.id).subscribe({
      next: () => {
        this.loading = false;
        this.fetchAll();
      },
      error: (err) => {
        this.loading = false;
        console.error('Delete task error', err);
        this.error =
          err.error?.error || 'Erreur lors de la suppression de la tâche.';
      },
    });
  }

  // ===== Permissions
  private clearPermissions(): void {
    this.currentProjectRole = null;
    this.canCreateTasks = false;
    this.canEditTasks = false;
    this.canDeleteTasks = false;
  }

  private loadPermissionsForProject(projectId: string): void {
    const userId = this.auth.getCurrentUserId?.() ?? null;

    if (!userId) {
      this.clearPermissions();
      return;
    }

    this.projectMembersService.getMembers(projectId).subscribe({
      next: (res: any) => {
        const membership = res.members.find(
          (m: any) => String(m.userId) === String(userId)
        );

        if (!membership) {
          this.clearPermissions();
          return;
        }

        const role = membership.role as ProjectRole;
        this.applyRolePermissions(role);
      },
      error: (err: any) => {
        console.error('Load project members error', err);
        this.clearPermissions();
      },
    });
  }

  private applyRolePermissions(role: ProjectRole): void {
    this.currentProjectRole = role;

    const canEdit = role === 0 || role === 1; // Owner + Member
    this.canCreateTasks = canEdit; // (selon ton choix)
    this.canEditTasks = canEdit;
    this.canDeleteTasks = role === 0; // Owner only
    if (this.activeProjectId && role === 2) {
      this.showNotice(
        'info',
        'Vous êtes en mode lecture seule sur ce projet (Observer).'
      );
    }
  }

  // ===== UI labels
  getStatusLabel(status: string): string {
    switch (status) {
      case 'in-progression':
        return 'En cours';
      case 'open':
        return 'À faire';
      case 'done':
        return 'Terminée';
      default:
        return status;
    }
  }

  getPriorityLabel(priority?: number): string {
    switch (priority) {
      case 0:
        return 'Basse';
      case 1:
        return 'Moyenne';
      case 2:
        return 'Haute';
      default:
        return 'Moyenne';
    }
  }

  getPriorityClasses(priority?: number): string {
    switch (priority) {
      case 0:
        return 'bg-slate-100 text-slate-600 border border-slate-200 dark:bg-slate-800/60 dark:text-slate-200 dark:border-slate-700';
      case 1:
        return 'bg-orange-50 text-orange-600 border border-orange-200 dark:bg-orange-500/10 dark:text-orange-200 dark:border-orange-500/20';
      case 2:
        return 'bg-red-50 text-red-600 border border-red-200 dark:bg-red-500/10 dark:text-red-200 dark:border-red-500/20';
      default:
        return 'bg-slate-100 text-slate-600 border border-slate-200';
    }
  }

  clearNotice() {
    this.notice = null;
    if (this.noticeTimer) clearTimeout(this.noticeTimer);
    this.noticeTimer = null;
  }

  get noticeCardClass(): string {
    switch (this.notice?.type) {
      case 'success':
        return 'border-emerald-100 bg-emerald-50 dark:border-emerald-900/40 dark:bg-emerald-950/30';
      case 'warning':
        return 'border-amber-100 bg-amber-50 dark:border-amber-900/40 dark:bg-amber-950/30';
      case 'error':
        return 'border-red-100 bg-red-50 dark:border-red-900/40 dark:bg-red-950/30';
      default:
        return 'border-slate-100 bg-white/60 dark:border-slate-800 dark:bg-slate-950/40';
    }
  }

  get noticeTextClass(): string {
    switch (this.notice?.type) {
      case 'success':
        return 'text-emerald-800 dark:text-emerald-200';
      case 'warning':
        return 'text-amber-800 dark:text-amber-200';
      case 'error':
        return 'text-red-800 dark:text-red-200';
      default:
        return 'text-slate-700 dark:text-slate-200';
    }
  }
}
