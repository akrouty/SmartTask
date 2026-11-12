import { ProjectRole } from './project.model';

export interface ConversationDto {
  projectId: string;
  projectName: string;
  role: ProjectRole; // 0 Owner, 1 Member, 2 Observer
  lastMessagePreview?: string | null;
  lastMessageAt?: string | null; // ISO
}
