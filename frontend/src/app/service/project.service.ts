import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Project } from '../model/project.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class ProjectService {
  private baseUrl = `${environment.apiUrl}/projects`; // => /api/projects côté backend

  constructor(private http: HttpClient) {}

  getProjects(): Observable<Project[]> {
    return this.http.get<Project[]>(this.baseUrl);
  }
  createProject(payload: {
    name: string;
    description?: string | null;
  }): Observable<Project> {
    return this.http.post<Project>(this.baseUrl, payload);
  }

  updateProject(
    id: string,
    payload: { name: string; description?: string | null }
  ): Observable<Project> {
    return this.http.put<Project>(`${this.baseUrl}/${id}`, payload);
  }

  deleteProject(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
  // on préparera plus tard :
  // getProjectById(id: string): Observable<Project> { ... }
  // createProject(data: Partial<Project>): Observable<Project> { ... }
}
