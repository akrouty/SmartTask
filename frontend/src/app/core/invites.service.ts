// src/app/core/invites.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ProjectMembersResponse } from '../service/project-members.service';

export type ProjectRole = 0 | 1 | 2; // 0=Owner, 1=Member, 2=Observer

export interface CreateInviteRequest {
  email: string;
  role: ProjectRole;
}

export interface InviteDto {
  id: string;
  projectId: string;
  email: string;
  role: ProjectRole;
  token: string;
  expiresAt: string;
  accepted: boolean;
  acceptedUserId?: string | null;
  reused: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class InvitesService {
  private baseUrl = `${environment.apiUrl}`;

  constructor(private http: HttpClient) {}

  // POST /api/projects/{projectId}/invites
  createInvite(
    projectId: string,
    body: CreateInviteRequest
  ): Observable<InviteDto> {
    return this.http.post<InviteDto>(
      `${this.baseUrl}/projects/${projectId}/invites`,
      body
    );
  }

  // GET /api/invites/{token}
  getInvite(token: string): Observable<InviteDto> {
    return this.http.get<InviteDto>(`${this.baseUrl}/invites/${token}`);
  }

  // POST /api/invites/{token}/accept
  acceptInvite(token: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/invites/${token}/accept`, {});
  }

  getMembers(projectId: string): Observable<ProjectMembersResponse> {
    return this.http.get<ProjectMembersResponse>(
      `${this.baseUrl}/projects/${projectId}/members`
    );
  }
}
