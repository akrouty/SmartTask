using Microsoft.EntityFrameworkCore;
using SmartTask.Api.Data;
using SmartTask.Api.Models;

namespace SmartTask.Api.Services;

public interface INotificationService
{
    Task NotifyProjectMembersAsync(string projectId, Notification notif, string? excludeUserId = null);
    Task NotifyUserAsync(string userId, Notification notif);
}

public class NotificationService : INotificationService
{
    private readonly AppDbContext _db;
    public NotificationService(AppDbContext db) { _db = db; }

    public async Task NotifyUserAsync(string userId, Notification notif)
    {
        notif.UserId = userId;
        _db.Notifications.Add(notif);
        await _db.SaveChangesAsync();
    }

    public async Task NotifyProjectMembersAsync(string projectId, Notification notif, string? excludeUserId = null)
    {
        var memberIds = await _db.ProjectMembers
            .AsNoTracking()
            .Where(m => m.ProjectId == projectId)
            .Select(m => m.UserId)
            .ToListAsync();

        if (!string.IsNullOrWhiteSpace(excludeUserId))
            memberIds = memberIds.Where(id => id != excludeUserId).ToList();

        foreach (var uid in memberIds)
        {
            _db.Notifications.Add(new Notification
            {
                UserId = uid,
                Type = notif.Type,
                Level = notif.Level,
                Title = notif.Title,
                Body = notif.Body,
                ProjectId = notif.ProjectId,
                TaskId = notif.TaskId,
                ActorUserId = notif.ActorUserId,
                DataJson = notif.DataJson,
                CreatedAt = DateTime.UtcNow
            });
        }

        await _db.SaveChangesAsync();
    }
}
