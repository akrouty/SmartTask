using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.RazorPages;
using Microsoft.EntityFrameworkCore;
using SmartTask.Api.Data;
using SmartTask.Api.Models;
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Security.Cryptography.X509Certificates;

namespace SmartTask.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
[Authorize] // toutes les actions nécessitent un utilisateur connecté
public class ProjectsController : ControllerBase
{
    private readonly AppDbContext _db;
    public ProjectsController(AppDbContext db) => _db = db;



    // GET /api/projects  -> projets où l'utilisateur est membre
    [HttpGet]
    public async Task<ActionResult<IEnumerable<Project>>> GetAll()
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        var projects = await _db.Projects
        .Include(p => p.Tasks)
        .Include(p => p.Members)
            .ThenInclude(pm => pm.User)    // <-- important
        .Where(p => p.Members.Any(pm => pm.UserId == userId))
        .Select(p => new
        {
            p.Id,
            p.Name,
            p.Description,
            p.CreatedAt,
            // si tu as une dueDate, ajoute-la ici
            Tasks = p.Tasks.Select(t => new
            {
                t.Id,
                t.Title,
                t.Status,
                t.ProjectId,
                t.DueDate,
                t.Priority
            }).ToList(),
            Members = p.Members.Select(pm => new
            {
                pm.Id,
                pm.ProjectId,
                pm.UserId,
                pm.Role,
                pm.JoinedAt,
                FullName = pm.User != null ? pm.User.FullName : null,
                Email    = pm.User != null ? pm.User.Email    : null
            }).ToList()
        })
        .ToListAsync();

    return Ok(projects);
    }

    // GET /api/projects/{id}
    [HttpGet("{id}")]
    public async Task<ActionResult<Project>> GetOne(string id)
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        var member = await _db.ProjectMembers
            .Include(pm => pm.Project!)
                .ThenInclude(p => p.Tasks)
            .FirstOrDefaultAsync(pm => pm.ProjectId == id && pm.UserId == userId);

        if (member is null) return Forbid(); // utilisateur non membre du projet

        return Ok(member.Project);
    }

    public record CreateProjectRequest(string Name, string? Description);

    // POST /api/projects  -> le créateur devient Owner
    [HttpPost]
    public async Task<ActionResult<object>> Create([FromBody] CreateProjectRequest req)
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        if (string.IsNullOrWhiteSpace(req.Name))
            return BadRequest(new { error = "Name required" });

        var p = new Project
        {
            Name = req.Name,
            Description = req.Description,
            CreatedAt = DateTime.UtcNow
        };


        // le créateur devient Owner sur ce projet
        p.Members.Add(new ProjectMember
        {
            ProjectId = p.Id,
            UserId = userId,
            Role = ProjectRole.Owner
        });

        _db.Projects.Add(p);


        await _db.SaveChangesAsync();

        return Created($"/api/projects/{p.Id}", new { id = p.Id });
    }

    public record UpdateProjectRequest(string? Name, string? Description);

    private async Task<ProjectMember?> GetMembershipAsync(string projectId, string userId)
    {
        return await _db.ProjectMembers
            .FirstOrDefaultAsync(pm => pm.ProjectId == projectId && pm.UserId == userId);
    }

    // PUT /api/projects/{id}  -> réservé Owner
    [HttpPut("{id}")]
    public async Task<ActionResult> Update(string id, [FromBody] UpdateProjectRequest req)
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        var membership = await GetMembershipAsync(id, userId);
        if (membership is null || membership.Role != ProjectRole.Owner)
            return Forbid();  // pas le droit

        var p = await _db.Projects.FindAsync(id);
        if (p is null) return NotFound();

        if (!string.IsNullOrWhiteSpace(req.Name))
            p.Name = req.Name;

        if (req.Description != null)
            p.Description = req.Description;

        await _db.SaveChangesAsync();
        return NoContent();
    }

    // DELETE /api/projects/{id}  -> réservé Owner
    [HttpDelete("{id}")]
    public async Task<ActionResult> Delete(string id)
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        var membership = await GetMembershipAsync(id, userId);
        if (membership is null || membership.Role != ProjectRole.Owner)
            return Forbid();  // pas le droit

        var p = await _db.Projects.FindAsync(id);
        if (p is null) return NotFound();

        _db.Projects.Remove(p);
        await _db.SaveChangesAsync();
        return NoContent();
    }

    private string? GetUserId()
    {

        return User.FindFirstValue(ClaimTypes.NameIdentifier)
            ?? User.FindFirstValue("sub")
            ?? User.FindFirstValue("id");
    }


    private ProjectRole ParseRoleOrDefault(string? roleText)
    {
        if (string.IsNullOrWhiteSpace(roleText))
            return ProjectRole.Member;

        return roleText.Trim().ToLowerInvariant() switch
        {
            "owner" => ProjectRole.Owner,
            "observer" => ProjectRole.Observer,
            "member" => ProjectRole.Member,
            _ => ProjectRole.Member
        };
    }



    [HttpPost("{projectId}/invites")]
public async Task<ActionResult<object>> InviteMember(
    string projectId,
    [FromBody] InviteMemberRequest req)
{
    var userId = GetUserId();
    if (userId is null) return Unauthorized();

    // Charger le projet + memberships
    var project = await _db.Projects
        .Include(p => p.Members)
        .FirstOrDefaultAsync(p => p.Id == projectId);

    if (project is null) return NotFound();

    // Vérifier que l'utilisateur courant est Owner de ce projet
    var membership = project.Members
        .FirstOrDefault(m => m.UserId == userId);

    if (membership is null || membership.Role != ProjectRole.Owner)
    {
        return Forbid();
    }

    if (string.IsNullOrWhiteSpace(req.Email))
    {
        return BadRequest(new { error = "Email is required." });
    }

    var email = req.Email.Trim().ToLowerInvariant();
    var role = req.Role;

    // 1) Chercher une invitation ACTIVE existante pour ce projet + cet email
    var existingInvite = await _db.ProjectInvites
        .FirstOrDefaultAsync(i =>
            i.ProjectId == projectId &&
            i.Email == email &&
            !i.Accepted &&
            i.ExpiresAt > DateTime.UtcNow);

    if (existingInvite is not null)
    {
        // 🔁 On réutilise cette invitation
        var existingAcceptUrl = $"http://localhost:4200/accept-invite/{existingInvite.Token}";

        return Ok(new
        {
            existingInvite.Id,
            existingInvite.Token,
            existingInvite.Email,
            Role = existingInvite.Role.ToString(),
            existingInvite.CreatedAt,
            existingInvite.ExpiresAt,
            AcceptUrl = existingAcceptUrl,
            Reused = true   // info optionnelle pour le front
        });
    }

    // 2) Sinon, on crée une NOUVELLE invitation
    var invite = new ProjectInvite
    {
        ProjectId = projectId,
        Email = email,
        Role = role,
    };

    _db.ProjectInvites.Add(invite);
    await _db.SaveChangesAsync();

    var acceptUrl = $"http://localhost:4200/accept-invite/{invite.Token}";

    return Ok(new
    {
        invite.Id,
        invite.Token,
        invite.Email,
        Role = invite.Role.ToString(),
        invite.CreatedAt,
        invite.ExpiresAt,
        AcceptUrl = acceptUrl,
        Reused = false
    });
}
    // GET /api/Projects/{projectId}/members
    [HttpGet("{projectId}/members")]
    [Authorize]
    public async Task<ActionResult<ProjectMembersResponseDto>> GetMembers(string projectId)
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        // sécurité: seul un membre du projet peut lire les membres
        var isMember = await _db.ProjectMembers
            .AnyAsync(m => m.ProjectId == projectId && m.UserId == userId);

        if (!isMember) return Forbid();

        var members = await _db.ProjectMembers
            .Where(m => m.ProjectId == projectId)
            .Include(m => m.User)
            .Select(m => new ProjectMemberDto
            {
                Id = m.Id,
                UserId = m.UserId,
                UserEmail = m.User != null ? m.User.Email : "",
                UserFullName = m.User != null ? m.User.FullName : null,
                Role = m.Role
            })
            .ToListAsync();

        return Ok(new ProjectMembersResponseDto { Members = members });
    }
}
