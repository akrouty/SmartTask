using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SmartTask.Api.Data;
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;

namespace SmartTask.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize]
public class NotificationsController : ControllerBase
{
    private readonly AppDbContext _db;
    public NotificationsController(AppDbContext db) { _db = db; }

    private string? GetUserId() =>
        User.FindFirstValue(JwtRegisteredClaimNames.Sub)
        ?? User.FindFirstValue(ClaimTypes.NameIdentifier);

    public record NotificationDto(
        string Id,
        int Type,
        int Level,
        string Title,
        string Body,
        string? ProjectId,
        string? TaskId,
        string? ActorUserId,
        string? DataJson,
        DateTime CreatedAt,
        DateTime? ReadAt
    );

    [HttpGet]
    public async Task<ActionResult<IEnumerable<NotificationDto>>> Get([FromQuery] bool onlyUnread = false, [FromQuery] int take = 30)
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        var q = _db.Notifications.AsNoTracking().Where(n => n.UserId == userId);
        if (onlyUnread) q = q.Where(n => n.ReadAt == null);

        var items = await q
            .OrderByDescending(n => n.CreatedAt)
            .Take(Math.Clamp(take, 1, 200))
            .Select(n => new NotificationDto(
                n.Id, (int)n.Type, (int)n.Level, n.Title, n.Body,
                n.ProjectId, n.TaskId, n.ActorUserId, n.DataJson,
                n.CreatedAt, n.ReadAt
            ))
            .ToListAsync();

        return Ok(items);
    }

    [HttpGet("unread-count")]
    public async Task<ActionResult<object>> UnreadCount()
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        var count = await _db.Notifications.CountAsync(n => n.UserId == userId && n.ReadAt == null);
        return Ok(new { count });
    }

    [HttpPut("{id}/read")]
    public async Task<ActionResult> MarkRead(string id)
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        var n = await _db.Notifications.FirstOrDefaultAsync(x => x.Id == id && x.UserId == userId);
        if (n is null) return NotFound();

        n.ReadAt ??= DateTime.UtcNow;
        await _db.SaveChangesAsync();
        return NoContent();
    }

    [HttpPut("mark-all-read")]
    public async Task<ActionResult> MarkAllRead()
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        var items = await _db.Notifications.Where(n => n.UserId == userId && n.ReadAt == null).ToListAsync();
        foreach (var n in items) n.ReadAt = DateTime.UtcNow;

        await _db.SaveChangesAsync();
        return NoContent();
    }

    [HttpDelete("{id}")]
    public async Task<ActionResult> DeleteOne(string id)
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        var n = await _db.Notifications.FirstOrDefaultAsync(x => x.Id == id && x.UserId == userId);
        if (n is null) return NotFound();

        _db.Notifications.Remove(n);
        await _db.SaveChangesAsync();
        return NoContent();
    }

    [HttpDelete("clear")]
    public async Task<ActionResult> ClearAll()
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        var items = await _db.Notifications.Where(n => n.UserId == userId).ToListAsync();
        _db.Notifications.RemoveRange(items);

        await _db.SaveChangesAsync();
        return NoContent();
    }
}
