using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SmartTask.Api.Data;
using SmartTask.Api.Models;
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;

namespace SmartTask.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize]
public class MessagesController : ControllerBase
{
    private readonly AppDbContext _db;

    public MessagesController(AppDbContext db)
    {
        _db = db;
    }

    private string? GetUserId()
    {
        return User.FindFirstValue(JwtRegisteredClaimNames.Sub)
            ?? User.FindFirstValue(ClaimTypes.NameIdentifier);
    }

    public record ConversationDto(
        string ProjectId,
        string ProjectName,
        int Role,
        string? LastMessagePreview,
        DateTime? LastMessageAt
    );

    [HttpGet("conversations")]
    public async Task<ActionResult<IEnumerable<ConversationDto>>> GetConversations()
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        var rows = await (
            from m in _db.ProjectMembers.AsNoTracking()
            where m.UserId == userId
            join p in _db.Projects.AsNoTracking() on m.ProjectId equals p.Id
            join msg in _db.ProjectMessages.AsNoTracking() on p.Id equals msg.ProjectId into msgs
            from last in msgs.OrderByDescending(x => x.CreatedAt).Take(1).DefaultIfEmpty()
            select new
            {
                ProjectId = p.Id,
                ProjectName = p.Name,
                Role = (int)m.Role,
                LastContent = last != null ? last.Content : null,
                LastAt = (DateTime?)(last != null ? last.CreatedAt : null)
            }
        )
        .OrderByDescending(x => x.LastAt ?? DateTime.MinValue)
        .ToListAsync();

        var convs = rows.Select(x =>
        {
            var preview = x.LastContent;
            if (!string.IsNullOrEmpty(preview) && preview.Length > 60)
                preview = preview.Substring(0, 60) + "…";

            return new ConversationDto(
                x.ProjectId,
                x.ProjectName,
                x.Role,
                preview,
                x.LastAt
            );
        }).ToList();

        return Ok(convs);
    }
}
