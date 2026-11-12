export type NotificationLevel = 'Info' | 'Success' | 'Warning' | 'Error';
export type NotificationType =
  | 'TaskCreated'
  | 'TaskUpdated'
  | 'TaskStatusChanged'
  | 'TaskDeleted'
  | 'ProjectUpdated'
  | 'ProjectInvite'
  | 'Generic';

export interface NotificationDto {
  id: string;
  type: NotificationType | string;
  level: NotificationLevel | string;
  title: string;
  body: string;

  projectId?: string | null;
  taskId?: string | null;

  actorUserId?: string | null;
  actorEmail?: string | null;
  actorFullName?: string | null;

  createdAt: string; // ISO
  readAt?: string | null; // ISO | null
}
