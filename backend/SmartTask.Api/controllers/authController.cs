using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using SmartTask.Api.Data;
using SmartTask.Api.Models;

namespace SmartTask.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class AuthController : ControllerBase
{
    private readonly AppDbContext _db;
    private readonly IConfiguration _config;

    public AuthController(AppDbContext db, IConfiguration config)
    {
        _db = db;
        _config = config;
    }

    public record LoginRequest(string Email, string Password);

    [HttpPost("login")]
    public async Task<ActionResult<object>> Login([FromBody] LoginRequest req)
    {
        var user = await _db.Users.FirstOrDefaultAsync(u => u.Email == req.Email);

        if (user is null || user.Password != req.Password)
            return Unauthorized(new { error = "Invalid credentials" });

        var token = GenerateJwtToken(user);

        return Ok(new
        {
            token,
            user = new
            {
                user.Id,
                user.FullName,   // <= ajouté
                user.Email,
                user.Role
            }
        });
    }

    public record RegisterRequest(string FullName, string Email, string Password);

    [HttpPost("register")]
    public async Task<ActionResult<object>> Register([FromBody] RegisterRequest req)
    {
        // 1) Vérifier si l'email existe déjà
        var emailExists = await _db.Users.AnyAsync(u => u.Email == req.Email);
        if (emailExists)
        {
            return BadRequest(new { error = "Un compte avec cet email existe déjà." });
        }

        // 2) Créer l'utilisateur
        var user = new User
        {
            FullName = req.FullName,
            Email = req.Email,
            Password = req.Password, // ⚠️ en vrai projet : à hasher !
            Role = "User"
        };

        _db.Users.Add(user);
        await _db.SaveChangesAsync();

        // 3) Générer le token
        var token = GenerateJwtToken(user);

        // 4) Retourner la même forme de réponse que pour le login
        return Ok(new
        {
            token,
            user = new
            {
                user.Id,
                user.FullName,
                user.Email,
                user.Role
            }
        });
    }


    private string GenerateJwtToken(User user)
    {
        var jwtSection = _config.GetSection("Jwt");
        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtSection["Key"]!));
        var creds = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);

        var claims = new[]
        {
            new Claim(JwtRegisteredClaimNames.Sub, user.Id),
            new Claim(JwtRegisteredClaimNames.Email, user.Email),
            new Claim(ClaimTypes.Role, user.Role)
        };

        var token = new JwtSecurityToken(
            issuer: jwtSection["Issuer"],
            audience: jwtSection["Audience"],
            claims: claims,
            expires: DateTime.UtcNow.AddHours(3),
            signingCredentials: creds
        );

        return new JwtSecurityTokenHandler().WriteToken(token);
    }
}
