using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SmartTask.Api.Data;
using SmartTask.Api.Models;
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;

namespace SmartTask.Api.Controllers;

[ApiController]
[Route("api/Projects/{projectId}/messages")]
[Authorize]
public class ProjectMessagesController : ControllerBase
{
    private readonly AppDbContext _db;

    public ProjectMessagesController(AppDbContext db)
    {
        _db = db;
    }

    private string? GetUserId()
    {
        return User.FindFirstValue(JwtRegisteredClaimNames.Sub)
            ?? User.FindFirstValue(ClaimTypes.NameIdentifier);
    }

    private async Task<ProjectMember?> GetMembership(string projectId, string userId)
    {
        return await _db.ProjectMembers
            .AsNoTracking()
            .FirstOrDefaultAsync(m => m.ProjectId == projectId && m.UserId == userId);
    }

    public record MessageDto(
        string Id,
        string ProjectId,
        string UserId,
        string? UserFullName,
        string? UserEmail,
        string Content,
        DateTime CreatedAt
    );

    public record CreateMessageRequest(string Content);

    // GET /api/Projects/{projectId}/messages?before=2025-12-17T00:00:00Z&take=30
    [HttpGet]
    public async Task<ActionResult<IEnumerable<MessageDto>>> GetMessages(
        string projectId,
        [FromQuery] DateTime? before = null,
        [FromQuery] int take = 30
    )
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        var membership = await GetMembership(projectId, userId);
        if (membership is null) return Forbid(); // pas membre => pas accès

        take = Math.Clamp(take, 1, 100);
        var cursor = before ?? DateTime.UtcNow.AddYears(10);

        // On récupère les derniers messages (desc), puis on les remet en ordre chronologique (asc)
        var msgs = await _db.ProjectMessages
            .AsNoTracking()
            .Where(x => x.ProjectId == projectId && x.CreatedAt < cursor)
            .Include(x => x.User)
            .OrderByDescending(x => x.CreatedAt)
            .Take(take)
            .Select(x => new MessageDto(
                x.Id,
                x.ProjectId,
                x.UserId,
                x.User != null ? x.User.FullName : null,
                x.User != null ? x.User.Email : null,
                x.Content,
                x.CreatedAt
            ))
            .ToListAsync();

        msgs.Reverse();
        return Ok(msgs);
    }

    // POST /api/Projects/{projectId}/messages
    [HttpPost]
    public async Task<ActionResult<MessageDto>> CreateMessage(string projectId, [FromBody] CreateMessageRequest body)
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        var membership = await GetMembership(projectId, userId);
        if (membership is null) return Forbid();

        // IMPORTANT: Observer peut écrire => pas de restriction par rôle ici
        var content = (body.Content ?? "").Trim();
        if (string.IsNullOrWhiteSpace(content))
            return BadRequest(new { error = "Le message ne peut pas être vide." });

        if (content.Length > 2000)
            return BadRequest(new { error = "Le message est trop long (max 2000 caractères)." });

        var message = new ProjectMessage
        {
            ProjectId = projectId,
            UserId = userId,
            Content = content,
            CreatedAt = DateTime.UtcNow
        };

        _db.ProjectMessages.Add(message);
        await _db.SaveChangesAsync();

        // DTO retour (avec user info)
        var userEmail = User.FindFirstValue(ClaimTypes.Email);
        var userFullName = User.FindFirstValue(ClaimTypes.Name);

        var dto = new MessageDto(
            message.Id,
            message.ProjectId,
            message.UserId,
            userFullName,
            userEmail,
            message.Content,
            message.CreatedAt
        );

        return Ok(dto);
    }
}
