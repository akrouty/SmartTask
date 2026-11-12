export interface ProjectMessageDto {
  id: string;
  projectId: string;

  userId: string;
  userFullName: string;
  userEmail: string;

  content: string;
  createdAt: string;
}
