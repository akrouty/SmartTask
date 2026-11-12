import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { forkJoin } from 'rxjs';
import { TaskService } from '../../service/task.service';
import { ProjectService } from '../../service/project.service';
import { Task } from '../../model/task.model';
import { Project } from '../../model/project.model';
import { UiCardComponent } from '../../shared/ui/card/ui-card/ui-card.component';
import { LucideAngularModule } from 'lucide-angular';
import { CountUpDirective } from '../../shared/ui/count/count-up/count-up.component';

interface WeeklyStat {
  label: string;
  created: number;
  completed: number;
}

interface ProjectShare {
  name: string;
  percent: number;
  color: string;
}

interface MonthlyPoint {
  label: string;
  value: number;
}

@Component({
  standalone: true,
  selector: 'app-stats',
  imports: [
    CommonModule,
    UiCardComponent,
    LucideAngularModule,
    CountUpDirective,
  ],
  templateUrl: './stats.component.html',
})
export class StatsComponent implements OnInit {
  loading = false;
  error: string | null = null;

  // cartes de synthèse
  productivityVsLastWeek = 0; // en %
  tasksCompletedThisWeek = 0; // en réalité : nb de tâches terminées
  avgDurationPerTaskHours = 0.9; // valeur « estimée »

  // bar chart semaine
  weeklyTasks: WeeklyStat[] = [];
  weeklyMax = 1;

  // pie chart
  projectShares: ProjectShare[] = [];
  pieBackground = '';

  // progression mensuelle
  monthlyProgress: MonthlyPoint[] = [];
  animateCharts = false;

  constructor(
    private taskService: TaskService,
    private projectService: ProjectService
  ) {}

  ngOnInit(): void {
    this.loading = true;
    this.error = null;

    forkJoin({
      tasksRes: this.taskService.getTasks(),
      projects: this.projectService.getProjects(),
    }).subscribe({
      next: ({ tasksRes, projects }) => {
        const tasks = tasksRes.items || [];
        this.computeStats(tasks, projects);
        this.loading = false;
      },
      error: (err) => {
        console.error('Stats load error', err);
        this.error =
          err.error?.error || 'Erreur lors du chargement des statistiques.';
        this.loading = false;
      },
    });
    setTimeout(() => {
      this.animateCharts = true;
    }, 50);
  }

  // ---------------- LOGIQUE DES STATS ----------------

  private computeStats(tasks: Task[], projects: Project[]) {
    const totalTasks = tasks.length;
    const doneTasks = tasks.filter((t) => t.status === 'done').length;

    // 1) Cartes de synthèse
    // Productivité = % de tâches terminées (on n’a pas de vrai historique semaine dernière)
    this.productivityVsLastWeek =
      totalTasks > 0 ? Math.round((doneTasks / totalTasks) * 100) : 0;

    // « Tâches complétées cette semaine » = toutes les tâches done (faute de dates)
    this.tasksCompletedThisWeek = doneTasks;

    // Durée moyenne par tâche : petite valeur estimée en fonction de la priorité
    const avgPriority =
      totalTasks > 0
        ? tasks.reduce((sum, t) => sum + (t.priority ?? 1), 0) / totalTasks
        : 1;
    this.avgDurationPerTaskHours = Number((0.5 + avgPriority * 0.2).toFixed(1));

    // 2) Tâches cette semaine (Lun…Dim)
    this.buildWeeklyTasks(tasks);

    // 3) Répartition par projet (camembert)
    this.buildProjectShares(tasks, projects);

    // 4) Progression mensuelle (simple courbe basée sur les tâches done)
    this.buildMonthlyProgress(doneTasks);
  }

  private buildWeeklyTasks(tasks: Task[]) {
    const labels = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];
    const weekly: WeeklyStat[] = labels.map((label) => ({
      label,
      created: 0,
      completed: 0,
    }));

    if (tasks.length === 0) {
      this.weeklyTasks = weekly;
      this.weeklyMax = 1;
      return;
    }

    const now = new Date();
    const currentWeekStart = this.getMonday(now);
    const currentWeekEnd = new Date(currentWeekStart);
    currentWeekEnd.setDate(currentWeekEnd.getDate() + 7);

    tasks.forEach((t, index) => {
      let dayIndex = index % 7; // fallback si pas de date

      if (t.dueDate) {
        const d = new Date(t.dueDate);
        if (!isNaN(d.getTime())) {
          if (d >= currentWeekStart && d < currentWeekEnd) {
            const jsDay = d.getDay(); // 0=Dim, 1=Lun, ...
            dayIndex = jsDay === 0 ? 6 : jsDay - 1; // Lundi=0, … Dimanche=6
          }
        }
      }

      weekly[dayIndex].created++;
      if (t.status === 'done') {
        weekly[dayIndex].completed++;
      }
    });

    this.weeklyTasks = weekly;
    const maxVal = Math.max(
      ...weekly.map((d) => Math.max(d.created, d.completed))
    );
    this.weeklyMax = maxVal > 0 ? maxVal : 1;
  }

  private getMonday(d: Date): Date {
    const date = new Date(d);
    const day = date.getDay(); // 0–6, 0 = Dimanche
    const diff = day === 0 ? -6 : 1 - day; // ramener au lundi
    date.setDate(date.getDate() + diff);
    date.setHours(0, 0, 0, 0);
    return date;
  }

  private buildProjectShares(tasks: Task[], projects: Project[]) {
    if (tasks.length === 0) {
      this.projectShares = [];
      this.pieBackground = 'conic-gradient(#e5e7eb 0 100%)';
      return;
    }

    const counts = new Map<string, number>();
    for (const t of tasks) {
      counts.set(t.projectId, (counts.get(t.projectId) ?? 0) + 1);
    }

    const total = tasks.length;
    const baseColors = ['#6366f1', '#a855f7', '#22c55e', '#3b82f6', '#f97316'];

    const shares: ProjectShare[] = Array.from(counts.entries()).map(
      ([projectId, count], index) => {
        const project = projects.find((p) => p.id === projectId);
        const name = project?.name ?? `Projet ${index + 1}`;
        const percent = Math.round((count / total) * 100);
        const color = baseColors[index % baseColors.length];
        return { name, percent, color };
      }
    );

    this.projectShares = shares;

    // construire le conic-gradient pour le « camembert »
    let start = 0;
    const segments: string[] = [];
    shares.forEach((s) => {
      const end = start + s.percent;
      segments.push(`${s.color} ${start}% ${end}%`);
      start = end;
    });
    if (start < 100) {
      segments.push(`#e5e7eb ${start}% 100%`);
    }
    this.pieBackground = `conic-gradient(${segments.join(',')})`;
  }

  private buildMonthlyProgress(doneTasks: number) {
    // on fabrique une progression simple sur 5 semaines
    const base = Math.max(10, doneTasks); // éviter 0
    this.monthlyProgress = [
      { label: 'Sem 1', value: base * 0.6 },
      { label: 'Sem 2', value: base * 0.75 },
      { label: 'Sem 3', value: base * 0.9 },
      { label: 'Sem 4', value: base * 1.0 },
      { label: 'Sem 5', value: base * 1.2 },
    ];
  }

  // ----------- helpers pour les points de la courbe -----------

  get monthlyPolylinePoints(): string {
    if (!this.monthlyProgress.length) return '';

    const max = Math.max(...this.monthlyProgress.map((p) => p.value)) || 1;
    const stepX =
      this.monthlyProgress.length > 1
        ? 100 / (this.monthlyProgress.length - 1)
        : 0;

    return this.monthlyProgress
      .map((p, index) => {
        const x = index * stepX;
        const ratio = p.value / max;
        const y = 100 - ratio * 80 - 5; // laisser un peu de marge en haut
        return `${x},${y}`;
      })
      .join(' ');
  }

  pointLeft(index: number): string {
    if (!this.monthlyProgress.length) return '0%';
    if (this.monthlyProgress.length === 1) return '50%';

    const step = 100 / (this.monthlyProgress.length - 1);
    return `${index * step}%`;
  }

  pointBottom(value: number): string {
    const max = Math.max(...this.monthlyProgress.map((p) => p.value)) || 1;
    const ratio = value / max;
    const bottom = 10 + ratio * 70; // 10–80% de la hauteur
    return `${bottom}%`;
  }
}
