using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SmartTask.Api.Data;
using SmartTask.Api.Models;
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;

namespace SmartTask.Api.Controllers;

[ApiController]
[Route("api/users")]
[Authorize]
public class UsersController : ControllerBase
{
    private readonly AppDbContext _db;

    public UsersController(AppDbContext db)
    {
        _db = db;
    }

    private string? GetUserId()
    {
        return User.FindFirstValue(JwtRegisteredClaimNames.Sub)
               ?? User.FindFirstValue(ClaimTypes.NameIdentifier);
    }

    // GET /api/users/me
    [HttpGet("me")]
    public async Task<ActionResult<MeDto>> Me()
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        var u = await _db.Users.AsNoTracking().FirstOrDefaultAsync(x => x.Id == userId);
        if (u is null) return NotFound();

        return Ok(new MeDto(u.Id, u.FullName, u.Email, u.Role));
    }

    // PUT /api/users/me
    [HttpPut("me")]
    public async Task<ActionResult<MeDto>> UpdateMe([FromBody] UpdateMeRequest req)
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        var u = await _db.Users.FirstOrDefaultAsync(x => x.Id == userId);
        if (u is null) return NotFound();

        if (string.IsNullOrWhiteSpace(req.FullName))
            return BadRequest(new { error = "FullName required" });

        u.FullName = req.FullName.Trim();
        await _db.SaveChangesAsync();

        return Ok(new MeDto(u.Id, u.FullName, u.Email, u.Role));
    }

    // PUT /api/users/me/password
    // (version compatible avec ton système actuel: compare simple)
    // IMPORTANT: idéalement => password hash plus tard
    [HttpPut("me/password")]
    public async Task<ActionResult> ChangePassword([FromBody] ChangePasswordRequest req)
    {
        var userId = GetUserId();
        if (userId is null) return Unauthorized();

        var u = await _db.Users.FirstOrDefaultAsync(x => x.Id == userId);
        if (u is null) return NotFound();

        if (string.IsNullOrWhiteSpace(req.CurrentPassword) || string.IsNullOrWhiteSpace(req.NewPassword))
            return BadRequest(new { error = "CurrentPassword and NewPassword are required" });

        if (req.NewPassword.Length < 6)
            return BadRequest(new { error = "NewPassword must be at least 6 chars" });

        // Si ton auth compare en clair actuellement, ceci restera compatible
        if (u.Password != req.CurrentPassword)
            return BadRequest(new { error = "Current password is incorrect" });

        u.Password = req.NewPassword;
        await _db.SaveChangesAsync();

        return NoContent();
    }
}
