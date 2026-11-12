import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { forkJoin } from 'rxjs';
import { Router } from '@angular/router';

import { ProjectService } from '../../service/project.service';
import { TaskService } from '../../service/task.service';
import { Project } from '../../model/project.model';
import { Task } from '../../model/task.model';
import { UiButtonComponent } from '../../shared/ui/button/ui-button/ui-button.component';
import { UiCardComponent } from '../../shared/ui/card/ui-card/ui-card.component';
import { CountUpDirective } from '../../shared/ui/count/count-up/count-up.component';

interface StatCard {
  id: 'projects' | 'inProgress' | 'late' | 'completion';
  label: string;
  value: number;
  subLabel?: string;
  suffix?: string; // ex: '%'
  prefix?: string; // ex: '+'
  decimals?: number; //
}

interface ProjectCard {
  id: string;
  name: string;
  description: string;
  tasksDone: number;
  tasksTotal: number;
  progress: number; // %
  dueDate: string | null; // ISO string
  badgeColor: 'blue' | 'purple' | 'green';
}

interface UrgentTask {
  id: string;
  title: string;
  project: string;
  priority: 'Haute' | 'Moyenne' | 'Basse';
  dueDate: string | null;
}

@Component({
  standalone: true,
  selector: 'app-dashboard',
  imports: [CommonModule, UiButtonComponent, UiCardComponent, CountUpDirective],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss'],
})
export class DashboardComponent implements OnInit {
  // --- état général ---
  loading = false;
  error: string | null = null;

  // --- données pour le template ---
  stats: StatCard[] = [
    { id: 'projects', label: 'Projets actifs', value: 0, subLabel: 'Total' },
    {
      id: 'inProgress',
      label: 'Tâches en cours',
      value: 0,
      subLabel: 'À traiter',
    },
    { id: 'late', label: 'En retard', value: 0, subLabel: 'À traiter' },
    {
      id: 'completion',
      label: 'Taux de complétion',
      value: 0,
      subLabel: 'Du total',
      suffix: '%',
      decimals: 0,
    },
  ];

  recentProjects: ProjectCard[] = [];
  urgentTasks: UrgentTask[] = [];

  // cache pour chercher les noms de projets
  private projectNameById: Record<string, string> = {};

  constructor(
    private projectService: ProjectService,
    private taskService: TaskService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadDashboard();
  }

  private loadDashboard() {
    this.loading = true;
    this.error = null;

    forkJoin({
      projects: this.projectService.getProjects(), // Observable<Project[]>
      tasksRes: this.taskService.getTasks(), // Observable<TaskListResponse>
    }).subscribe({
      next: ({ projects, tasksRes }) => {
        const tasks: Task[] = tasksRes.items;

        // index des noms de projets
        this.projectNameById = {};
        for (const p of projects) {
          this.projectNameById[p.id] = p.name;
        }

        this.buildStats(projects, tasks);
        this.buildRecentProjects(projects, tasks);
        this.buildUrgentTasks(tasks);

        this.loading = false;
      },
      error: (err) => {
        console.error('Dashboard load error', err);
        this.error =
          err.error?.error || 'Erreur lors du chargement du tableau de bord.';
        this.loading = false;
      },
    });
  }

  // ----------------- STATISTIQUES DU HAUT -----------------
  private buildStats(projects: Project[], tasks: Task[]) {
    const totalProjects = projects.length;

    const inProgressTasks = tasks.filter(
      (t) => t.status === 'in-progression'
    ).length;
    const doneTasks = tasks.filter((t) => t.status === 'done').length;
    const totalTasks = tasks.length;

    // tâches en retard = dueDate < aujourd'hui et pas done
    const now = new Date();
    now.setHours(0, 0, 0, 0);

    const lateTasks = tasks.filter((t) => {
      if (!t.dueDate) return false;
      if (t.status === 'done') return false;

      const d = new Date(t.dueDate);
      d.setHours(0, 0, 0, 0);

      return d.getTime() < now.getTime();
    }).length;

    const completionRate =
      totalTasks > 0 ? Math.round((doneTasks * 100) / totalTasks) : 0;

    this.stats = [
      {
        id: 'projects',
        label: 'Projets actifs',
        value: totalProjects,
        subLabel: 'Total',
      },
      {
        id: 'inProgress',
        label: 'Tâches en cours',
        value: inProgressTasks,
        subLabel: 'À traiter',
      },
      {
        id: 'late',
        label: 'En retard',
        value: lateTasks,
        subLabel: 'À traiter',
      },
      {
        id: 'completion',
        label: 'Taux de complétion',
        value: completionRate,
        subLabel: 'Du total',
        suffix: '%',
        decimals: 0,
      },
    ];
  }

  // ----------------- PROJETS RÉCENTS -----------------
  private buildRecentProjects(projects: Project[], tasks: Task[]) {
    // on trie les projets par date de création décroissante
    const sorted = [...projects].sort(
      (a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    );

    const colors: ProjectCard['badgeColor'][] = ['blue', 'purple', 'green'];

    this.recentProjects = sorted.slice(0, 3).map((p, index) => {
      const tasksOfProject = tasks.filter((t) => t.projectId === p.id);
      const total = tasksOfProject.length;
      const done = tasksOfProject.filter((t) => t.status === 'done').length;
      const progress = total > 0 ? Math.round((done * 100) / total) : 0;

      // on prend la date d’échéance la plus proche (ou null si aucune)
      const openTasks = tasksOfProject.filter((t) => t.status !== 'done');

      const dueDates = openTasks
        .map((t) => t.dueDate)
        .filter((d): d is string => !!d)
        .sort();

      const nearestDueDate = dueDates[0] ?? null;

      return {
        id: p.id,
        name: p.name,
        description: p.description ?? '',
        tasksDone: done,
        tasksTotal: total,
        progress,
        dueDate: nearestDueDate,
        badgeColor: colors[index] ?? 'blue',
      };
    });
  }

  // ----------------- TÂCHES URGENTES -----------------
  private buildUrgentTasks(tasks: Task[]) {
    const today = new Date();
    const limit = new Date();
    limit.setDate(today.getDate() + 7); // prochaines 7 jours

    const toIso = (d: Date) => d.toISOString().substring(0, 10);

    const urgent = tasks.filter((t) => {
      const isHigh = t.priority === 2; // 2 = High dans ton enum
      const hasDue = !!t.dueDate;
      const isSoon =
        hasDue && t.dueDate! >= toIso(today) && t.dueDate! <= toIso(limit);

      return t.status !== 'done' && (isHigh || isSoon);
    });

    // on trie : priorité haute d’abord, puis dueDate la plus proche
    urgent.sort((a, b) => {
      const pa = a.priority ?? 1;
      const pb = b.priority ?? 1;
      if (pa !== pb) return pb - pa; // haute avant basse

      const da = a.dueDate ?? '9999-12-31';
      const db = b.dueDate ?? '9999-12-31';
      return da.localeCompare(db);
    });

    this.urgentTasks = urgent.slice(0, 3).map((t) => ({
      id: t.id,
      title: t.title,
      project: this.projectNameById[t.projectId] ?? t.projectId,
      priority: this.mapPriorityLabel(t.priority),
      dueDate: t.dueDate ?? null,
    }));
  }

  private mapPriorityLabel(p?: number): UrgentTask['priority'] {
    switch (p) {
      case 2:
        return 'Haute';
      case 0:
        return 'Basse';
      default:
        return 'Moyenne';
    }
  }

  // ----------------- CSS HELPERS -----------------
  getBadgeClasses(color: ProjectCard['badgeColor']): string {
    switch (color) {
      case 'blue':
        return 'bg-blue-100 text-blue-700';
      case 'purple':
        return 'bg-purple-100 text-purple-700';
      case 'green':
        return 'bg-green-100 text-green-700';
      default:
        return 'bg-slate-100 text-slate-700';
    }
  }

  getPriorityClasses(priority: UrgentTask['priority']): string {
    switch (priority) {
      case 'Haute':
        return 'bg-red-50 text-red-600';
      case 'Moyenne':
        return 'bg-orange-50 text-orange-600';
      case 'Basse':
        return 'bg-emerald-50 text-emerald-600';
    }
  }
  openProject(projectId: string) {
    this.router.navigate(['/projects']);
  }

  openTask(taskId: string) {
    this.router.navigate(['/tasks']);
  }

  reload() {
    this.loadDashboard();
  }
  formatDueDate(dueDate: string | null): string {
    if (!dueDate) return 'Aucune';
    const d = new Date(dueDate);
    if (isNaN(d.getTime())) return dueDate;
    return d.toLocaleDateString('fr-FR', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });
  }

  getDueStatus(
    dueDate: string | null
  ): 'En retard' | 'Aujourd’hui' | 'Bientôt' | 'À venir' | '—' {
    if (!dueDate) return '—';

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const d = new Date(dueDate);
    d.setHours(0, 0, 0, 0);

    const diffDays = Math.floor(
      (d.getTime() - today.getTime()) / (1000 * 60 * 60 * 24)
    );

    if (diffDays < 0) return 'En retard';
    if (diffDays === 0) return 'Aujourd’hui';
    if (diffDays <= 7) return 'Bientôt';
    return 'À venir'; // <-- au lieu de '—'
  }

  getDueStatusClasses(
    status: ReturnType<DashboardComponent['getDueStatus']>
  ): string {
    switch (status) {
      case 'En retard':
        return 'bg-red-50 text-red-600';
      case 'Aujourd’hui':
        return 'bg-orange-50 text-orange-600';
      case 'Bientôt':
        return 'bg-amber-50 text-amber-700';
      case 'À venir':
        return 'bg-slate-50 text-slate-600';
      default:
        return 'bg-slate-50 text-slate-500';
    }
  }
  openStat(statId: StatCard['id']) {
    switch (statId) {
      case 'projects':
        this.router.navigate(['/projects']);
        break;

      case 'inProgress':
        this.router.navigate(['/tasks']); // filtre plus tard
        break;

      case 'late':
        this.router.navigate(['/tasks']); // filtre plus tard
        break;

      case 'completion':
        this.router.navigate(['/tasks']); // filtre plus tard
        break;
    }
  }

  goToProjects() {
    this.router.navigate(['/projects']);
  }

  goToTasks() {
    this.router.navigate(['/tasks']);
  }
  goToNewTask() {
    this.router.navigate(['/tasks']);
  }

  goToNewProject() {
    this.router.navigate(['/projects']);
  }
}
