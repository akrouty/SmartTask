using System.Text.Json;

namespace SmartTask.Api.Models;

public enum NotificationType
{
    ProjectMemberJoined = 1,
    TaskAssigned = 2,
    TaskStatusChanged = 3,
    TaskDueSoon = 4,
    ProjectUpdated = 5,
    TaskCreated = 6,
    TaskDeleted = 7,
    MessagePosted = 8
}

public enum NotificationLevel
{
    Info = 1,
    Success = 2,
    Warning = 3
}

public class Notification
{
    public string Id { get; set; } = Guid.NewGuid().ToString();

    public string UserId { get; set; } = default!;
    public User? User { get; set; }

    public NotificationType Type { get; set; }
    public NotificationLevel Level { get; set; } = NotificationLevel.Info;

    public string Title { get; set; } = default!;
    public string Body { get; set; } = default!;

    public string? ProjectId { get; set; }
    public string? TaskId { get; set; }

    // Qui a fait l'action (optionnel)
    public string? ActorUserId { get; set; }

    // JSON (route, infos UI...)
    public string? DataJson { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime? ReadAt { get; set; }

    public bool IsRead => ReadAt != null;
}
