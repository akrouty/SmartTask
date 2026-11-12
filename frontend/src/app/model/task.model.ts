export type TaskPriority = 0 | 1 | 2; // 0 = basse, 1 = moyenne, 2 = haute
export interface Task {
  id: string;
  title: string;
  description?: string;
  status: string;       // "open", "in-progress", "done"...
  projectId: string;
  priority?: TaskPriority;    // "High", "Medium", "Low" (optionnel)
  dueDate?: string | null;     // ISO string optionnelle
}
export interface TaskListResponse {
  items: Task[];
  total: number;
}
