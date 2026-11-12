import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Task, TaskListResponse } from '../model/task.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class TaskService {
  private baseUrl = `${environment.apiUrl}/Tasks`; // /api/Tasks

  constructor(private http: HttpClient) {}

  // on prépare déjà les filtres optionnels (status, projectId)
  getTasks(filter?: {
    status?: string;
    projectId?: string;
  }): Observable<TaskListResponse> {
    let params = new HttpParams();

    if (filter?.status) {
      params = params.set('status', filter.status);
    }
    if (filter?.projectId) {
      params = params.set('projectId', filter.projectId);
    }

    return this.http.get<TaskListResponse>(this.baseUrl, { params });
  }

  createTask(data: Partial<Task>): Observable<Task> {
    return this.http.post<Task>(this.baseUrl, data);
  }
  updateTask(id: string, payload: Partial<Task>): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/${id}`, payload);
  }
  deleteTask(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
