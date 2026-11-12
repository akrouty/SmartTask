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
public class InvitesController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly INotificationService _notif;


    public InvitesController(AppDbContext db, INotificationService notif)
    {
        _db = db;
        _notif = notif;
    }

    private string? GetUserId()
    {
        return User.FindFirstValue(JwtRegisteredClaimNames.Sub)
            ?? User.FindFirstValue(ClaimTypes.NameIdentifier);
    }

    public record InviteInfoResponse(
        string ProjectName,
        string Email,
        string Role,
        DateTime ExpiresAt,
        bool IsExpired,
        bool Accepted
    );

    // GET /api/invites/{token}
    [HttpGet("{token}")]
    public async Task<ActionResult<InviteInfoResponse>> GetInvite(string token)
    {
        var invite = await _db.ProjectInvites
            .Include(i => i.Project)
            .FirstOrDefaultAsync(i => i.Token == token);

        if (invite is null) return NotFound();

        var isExpired = invite.ExpiresAt <= DateTime.UtcNow;

        var dto = new InviteInfoResponse(
            ProjectName: invite.Project?.Name ?? "(Projet inconnu)",
            Email: invite.Email,
            Role: invite.Role.ToString(),
            ExpiresAt: invite.ExpiresAt,
            IsExpired: isExpired,
            Accepted: invite.Accepted
        );

        return Ok(dto);
    }

    // POST /api/invites/{token}/accept
    [HttpPost("{token}/accept")]
    [Authorize]
    public async Task<ActionResult> AcceptInvite(string token)
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

#pragma warning disable CS8602 // Dereference of a possibly null reference.
        var invite = await _db.ProjectInvites
            .Include(i => i.Project)
                .ThenInclude(p => p.Members)
            .FirstOrDefaultAsync(i => i.Token == token);
#pragma warning restore CS8602 // Dereference of a possibly null reference.

        if (invite is null) return NotFound();

        if (invite.ExpiresAt <= DateTime.UtcNow)
        {
            return BadRequest(new { error = "Invitation expirée." });
        }

        if (invite.Accepted)
        {
            return BadRequest(new { error = "Invitation déjà acceptée." });
        }


        var inviteEmail = invite.Email.Trim().ToLowerInvariant();
        var userEmail = User.FindFirstValue(ClaimTypes.Email)?.Trim().ToLowerInvariant();

        if (inviteEmail != userEmail)
        {
            // On bloque l'acceptation si l'email ne correspond pas
            return Forbid(); // ou BadRequest avec un message plus explicite si tu préfères
                             // return BadRequest(new { error = "Cette invitation ne correspond pas à votre adresse email." });
        }


        // Vérifier que l'utilisateur n'est pas déjà membre
        var alreadyMember = invite.Project!.Members
            .Any(m => m.UserId == userId);

        if (alreadyMember)
        {
            // On peut considérer que c'est "ok" mais déjà fait
            invite.Accepted = true;
            invite.AcceptedUserId = userId;
            await _db.SaveChangesAsync();
            return NoContent();
        }

        // Créer un ProjectMember
        var member = new ProjectMember
        {
            ProjectId = invite.ProjectId,
            UserId = userId,
            Role = invite.Role,
            JoinedAt = DateTime.UtcNow
        };

        _db.ProjectMembers.Add(member);

        invite.Accepted = true;
        invite.AcceptedUserId = userId;

        await _db.SaveChangesAsync();

        await _notif.NotifyProjectMembersAsync(invite.ProjectId,
    new Notification
    {
        Type = NotificationType.ProjectMemberJoined,
        Level = NotificationLevel.Success,
        Title = "Nouveau membre dans votre équipe",
        Body = $"{invite.Email} a rejoint le projet.",
        ProjectId = invite.ProjectId,
        ActorUserId = userId
    },
    excludeUserId: userId
);




        return NoContent();
    }
}
