import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { environment } from '../../environments/environment';

export type ProjectRole = 0 | 1 | 2;

export interface ProjectMemberDto {
  id: string;
  userId: string;
  userEmail: string;
  userFullName?: string;
  role: ProjectRole;
}

export interface ProjectInviteDto {
  id: string;
  email: string;
  role: ProjectRole;
  status: 'Pending' | 'Accepted' | 'Expired';
  createdAt: string;
}

export interface ProjectMembersResponse {
  members: ProjectMemberDto[];
  invites: ProjectInviteDto[];
}

@Injectable({ providedIn: 'root' })
export class ProjectMembersService {
  private baseUrl = `${environment.apiUrl}`;

  constructor(private http: HttpClient) {}

  getMembers(projectId: string): Observable<ProjectMembersResponse> {
    const primary = `${this.baseUrl}/projects/${projectId}/members`;

    // ✅ Mets ici les routes alternatives qui existent CHEZ TOI (Swagger)
    const fallbacks = [
      `${this.baseUrl}/projects/${projectId}/team`,
      `${this.baseUrl}/projects/${projectId}/memberships`,
      `${this.baseUrl}/project-members/${projectId}`,
      `${this.baseUrl}/ProjectMembers/${projectId}`,
    ];

    const safeEmpty: ProjectMembersResponse = { members: [], invites: [] };

    return this.http.get<ProjectMembersResponse>(primary).pipe(
      catchError((err: HttpErrorResponse) => {
        // Si 404 → on tente fallback
        if (err.status === 404) {
          return this.tryFallbacks(fallbacks, safeEmpty);
        }
        // autres erreurs → remonter
        return throwError(() => err);
      })
    );
  }

  private tryFallbacks(
    urls: string[],
    fallbackValue: ProjectMembersResponse
  ): Observable<ProjectMembersResponse> {
    if (urls.length === 0) return of(fallbackValue);

    const [first, ...rest] = urls;
    return this.http.get<ProjectMembersResponse>(first).pipe(
      catchError((err: HttpErrorResponse) => {
        if (err.status === 404) return this.tryFallbacks(rest, fallbackValue);
        return throwError(() => err);
      })
    );
  }
}
