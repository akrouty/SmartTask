using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SmartTask.Api.Data;
using SmartTask.Api.Models;
using SmartTask.Api.Services;
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;

namespace SmartTask.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize]
public class TasksController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly INotificationService _notif;
    public TasksController(AppDbContext db,INotificationService notif) {
    _db = db;
    _notif = notif;
}


    private string? GetUserId()
    {
        // On cherche d'abord le "sub" du JWT, sinon NameIdentifierprivate readonly INotificationService _notif;
        return User.FindFirstValue(JwtRegisteredClaimNames.Sub)
               ?? User.FindFirstValue(ClaimTypes.NameIdentifier);
    }

    private async Task<ProjectMember?> GetMembershipAsync(string projectId, string userId)
    {
        return await _db.ProjectMembers
            .FirstOrDefaultAsync(pm => pm.ProjectId == projectId && pm.UserId == userId);
    }
    private TaskPriority ParsePriorityOrDefault(string? priorityText)
    {
        if (string.IsNullOrWhiteSpace(priorityText))
            return TaskPriority.Medium;

        return priorityText.Trim().ToLowerInvariant() switch
        {
            "low" or "basse" => TaskPriority.Low,
            "high" or "haute" => TaskPriority.High,
            "medium" or "moyenne" => TaskPriority.Medium,
            _ => TaskPriority.Medium
        };
    }

    // GET /api/tasks
    //  retourne uniquement les t�ches des projets o� l'utilisateur est membre
    [HttpGet]
    public async Task<ActionResult<object>> Get(
        [FromQuery] string? status = null,
        [FromQuery] string? projectId = null)
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        // Jointure Tasks + ProjectMembers pour filtrer sur l'utilisateur
        var q = from t in _db.Tasks.AsNoTracking()
                join pm in _db.ProjectMembers
                    on t.ProjectId equals pm.ProjectId
                where pm.UserId == userId
                select t;

        if (!string.IsNullOrWhiteSpace(status))
            q = q.Where(t => t.Status == status);

        if (!string.IsNullOrWhiteSpace(projectId))
            q = q.Where(t => t.ProjectId == projectId);

        var items = await q
            .OrderByDescending(t => t.DueDate)
            .ToListAsync();

        return Ok(new { items, total = items.Count });
    }
    //DTo create a new task
    public record CreateTaskRequest(
        string Title,
        string? Description,
        string Status,
        string? DueDate,
        string ProjectId,
        TaskPriority? Priority
    );

    // POST /api/tasks
    [HttpPost]
    public async Task<ActionResult<object>> Create([FromBody] CreateTaskRequest req)
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        if (string.IsNullOrWhiteSpace(req.Title))
            return BadRequest(new { error = "Title required" });

        // V�rifier que l'utilisateur est au moins Member sur le projet
        var membership = await GetMembershipAsync(req.ProjectId, userId);
        if (membership is null || membership.Role == ProjectRole.Observer)
            return Forbid();

        var item = new TaskItem
        {
            Title = req.Title,
            Description = req.Description,
            Status = string.IsNullOrWhiteSpace(req.Status) ? "open" : req.Status,
            DueDate = req.DueDate,
            ProjectId = req.ProjectId,
            Priority = req.Priority ?? TaskPriority.Medium
        };

        _db.Tasks.Add(item);
        await _db.SaveChangesAsync();
        await _notif.NotifyProjectMembersAsync(item.ProjectId,
    new Notification
    {
        Type = NotificationType.TaskAssigned,
        Level = NotificationLevel.Info,
        Title = "Tâche assignée",
        Body = $"Vous avez une mise à jour sur la tâche '{item.Title}'.",
        ProjectId = item.ProjectId,
        TaskId = item.Id,
        ActorUserId = userId
    },
    excludeUserId: userId
);

        return Created($"/api/tasks/{item.Id}", new { id = item.Id });
    }

    // GET /api/tasks/{id}
    [HttpGet("{id}")]
    public async Task<ActionResult<TaskItem>> GetOne(string id)
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        var t = await _db.Tasks.FindAsync(id);
        if (t is null) return NotFound();

        // V�rifier que l'utilisateur est membre du projet de cette t�che
        var membership = await GetMembershipAsync(t.ProjectId, userId);
        if (membership is null)
            return Forbid();

        return Ok(t);
    }

    // DTO pour la mise � jour partielle d'une t�che
    public record UpdateTaskRequest(
        string? Title,
        string? Description,
        string? Status,
        string? DueDate,
        string? ProjectId,
        TaskPriority? Priority
    );

    [HttpPut("{id}")]
    public async Task<ActionResult> Update(string id, [FromBody] UpdateTaskRequest req)
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        var t = await _db.Tasks.FindAsync(id);
        if (t is null) return NotFound();

        var projectId = req.ProjectId ?? t.ProjectId;

        // L'utilisateur doit �tre au moins Member sur le projet
        var membership = await GetMembershipAsync(projectId, userId);
        if (membership is null || membership.Role == ProjectRole.Observer)
            return Forbid();

        if (!string.IsNullOrWhiteSpace(req.Title)) t.Title = req.Title;
        if (!string.IsNullOrWhiteSpace(req.Description)) t.Description = req.Description;
        if (!string.IsNullOrWhiteSpace(req.Status)) t.Status = req.Status;
        if (!string.IsNullOrWhiteSpace(req.DueDate)) t.DueDate = req.DueDate;
        if (!string.IsNullOrWhiteSpace(req.ProjectId)) t.ProjectId = req.ProjectId;
        if (req.Priority.HasValue)
            t.Priority = req.Priority.Value;

        await _db.SaveChangesAsync();
        await _notif.NotifyProjectMembersAsync(t.ProjectId,
    new Notification
    {
        Type = NotificationType.TaskStatusChanged,
        Level = NotificationLevel.Success,
        Title = "Statut de tâche modifié",
        Body = $"La tâche '{t.Title}' est passée à '{t.Status}'.",
        ProjectId = t.ProjectId,
        TaskId = t.Id,
        ActorUserId = userId
    },
    excludeUserId: userId
);
        return NoContent();
    }

    [HttpDelete("{id}")]
    public async Task<ActionResult> Delete(string id)
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        var t = await _db.Tasks.FindAsync(id);
        if (t is null) return NotFound();

        var membership = await GetMembershipAsync(t.ProjectId, userId);
        if (membership is null || membership.Role != ProjectRole.Owner)
            return Forbid();

        _db.Tasks.Remove(t);
        await _db.SaveChangesAsync();
        return NoContent();
    }
}
