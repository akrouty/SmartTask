import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ConversationDto } from '../model/conversation.model';
import { ProjectMessageDto } from '../model/project-message.model';

@Injectable({ providedIn: 'root' })
export class MessagesService {
  private baseUrl = `${environment.apiUrl}`;

  constructor(private http: HttpClient) {}

  // Liste des projets où je suis membre + last message
  getConversations(): Observable<ConversationDto[]> {
    return this.http.get<ConversationDto[]>(
      `${this.baseUrl}/messages/conversations`
    );
  }

  // Messages d’un projet (optionnel: pagination)
  getProjectMessages(
    projectId: string,
    take = 50,
    before?: string
  ): Observable<ProjectMessageDto[]> {
    let params = new HttpParams().set('take', String(take));
    if (before) params = params.set('before', before);

    return this.http.get<ProjectMessageDto[]>(
      `${this.baseUrl}/projects/${projectId}/messages`,
      { params }
    );
  }

  sendProjectMessage(
    projectId: string,
    content: string
  ): Observable<ProjectMessageDto> {
    return this.http.post<ProjectMessageDto>(
      `${this.baseUrl}/projects/${projectId}/messages`,
      { content }
    );
  }
}
