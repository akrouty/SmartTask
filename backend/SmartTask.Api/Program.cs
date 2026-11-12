using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using Microsoft.OpenApi.Models;
using SmartTask.Api.Data;
using SmartTask.Api.Models;
using SmartTask.Api.Services;
using System.Text;


var builder = WebApplication.CreateBuilder(args);

// ================== CONFIG DB + CORS ==================

var MyCors = "SmartTaskCors";
var cs = builder.Configuration.GetConnectionString("sqlite") ?? "Data Source=smarttask.db";

builder.Services.AddDbContext<AppDbContext>(options =>
    options.UseSqlite(cs));

builder.Services.AddCors(options =>
{
    options.AddPolicy(MyCors, policy =>
        policy
            .AllowAnyHeader()
            .AllowAnyMethod()
            .WithOrigins(
                "http://10.0.2.2:8080",
                "http://localhost:4200",
                "http://localhost:5173",
                "http://localhost:44368"
            )
    );
});

// ================== SERVICES ==================

builder.Services.AddControllers();
// Auth + JWT
var jwtSection = builder.Configuration.GetSection("Jwt");
var jwtKey = jwtSection["Key"];

if (string.IsNullOrWhiteSpace(jwtKey))
{
    throw new InvalidOperationException("JWT signing key is not configured. Use user secrets or an environment-specific secret store.");
}

builder.Services
    .AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.TokenValidationParameters = new TokenValidationParameters
        {
            // En DEV : on ne valide pas Issuer/Audience pour �viter les mismatch
            ValidateIssuer = false,
            ValidateAudience = false,

            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtKey!))
        };
    });

builder.Services.AddAuthorization();

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();
// ================== BUILD APP ==================
builder.Services.AddSwaggerGen(c =>
{
    c.SwaggerDoc("v1", new OpenApiInfo
    {
        Title = "SmartTask.Api",
        Version = "v1"
    });

    // D�claration du sch�ma JWT Bearer
    var jwtSecurityScheme = new OpenApiSecurityScheme
    {
        Scheme = "bearer",
        BearerFormat = "JWT",
        Name = "Authorization",
        In = ParameterLocation.Header,
        Type = SecuritySchemeType.Http,
        Description = "Put **_ONLY_** your JWT Bearer token here (without the word Bearer).",

        Reference = new OpenApiReference
        {
            Id = "Bearer",
            Type = ReferenceType.SecurityScheme
        }
    };

    // Ajoute le sch�ma
    c.AddSecurityDefinition("Bearer", jwtSecurityScheme);

    // Et demande ce sch�ma par d�faut pour toutes les op�rations
    c.AddSecurityRequirement(new OpenApiSecurityRequirement
    {
        { jwtSecurityScheme, Array.Empty<string>() }
    });
});

builder.Services.AddScoped<INotificationService, NotificationService>();




var app = builder.Build();

// ================== MIGRATIONS + SEEDING DB ==================

using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();

    db.Database.Migrate();

    if (app.Environment.IsDevelopment())
    {
        var adminPassword = builder.Configuration["SMARTTASK_ADMIN_PASSWORD"];
        var demoPassword = builder.Configuration["SMARTTASK_DEMO_PASSWORD"];

        if (string.IsNullOrWhiteSpace(adminPassword) || string.IsNullOrWhiteSpace(demoPassword))
        {
            app.Logger.LogWarning("Development demo users were not seeded because local demo passwords are not configured.");
        }
        else
        {

    // 1) Garantir l'existence des utilisateurs
    var adminUser = await db.Users
        .FirstOrDefaultAsync(u => u.Email == "admin@smarttask.local");

    if (adminUser == null)
    {
        adminUser = new User
        {
            Id = Guid.NewGuid().ToString(),
            Email = "admin@smarttask.local",
            Password = adminPassword,
            Role = "user"            // r�le global simple
        };
        db.Users.Add(adminUser);
    }

    var demoUser = await db.Users
        .FirstOrDefaultAsync(u => u.Email == "demo@smarttask.local");

    if (demoUser == null)
    {
        demoUser = new User
        {
            Id = Guid.NewGuid().ToString(),
            Email = "demo@smarttask.local",
            Password = demoPassword,
            Role = "user"
        };
        db.Users.Add(demoUser);
    }

    await db.SaveChangesAsync();

    // 2) Projet par d�faut
    Project defaultProject;

#pragma warning disable CS8600 // Converting null literal or possible null value to non-nullable type.
    defaultProject = await db.Projects.FirstOrDefaultAsync();
#pragma warning restore CS8600 // Converting null literal or possible null value to non-nullable type.
    if (defaultProject == null)
    {
        defaultProject = new Project
        {
            Id = "p_1",
            Name = "Demo Project",
            Description = "Projet de d�monstration SmartTask"
        };
        db.Projects.Add(defaultProject);
        await db.SaveChangesAsync();
    }

    // 3) Membership (r�les par projet)
    if (!await db.ProjectMembers.AnyAsync())
    {
        db.ProjectMembers.AddRange(
            new ProjectMember
            {
                ProjectId = defaultProject.Id,
                UserId = adminUser.Id,
                Role = ProjectRole.Owner
            },
            new ProjectMember
            {
                ProjectId = defaultProject.Id,
                UserId = demoUser.Id,
                Role = ProjectRole.Observer   // ou Member, selon ton test
            }
        );
        await db.SaveChangesAsync();
    }

    // 4) T�ches de d�mo
    if (!await db.Tasks.AnyAsync())
    {
        db.Tasks.AddRange(
            new TaskItem
            {
                Title = "Study Kotlin",
                Status = "open",
                ProjectId = defaultProject.Id,
                Priority = TaskPriority.High
            },
            new TaskItem
            {
                Title = "Review .NET",
                Status = "done",
                DueDate = "2025-10-22T12:00:00Z",
                ProjectId = defaultProject.Id,
                Priority = TaskPriority.Medium
            }
        );
        await db.SaveChangesAsync();
    }
        }
    }
}

// ================== PIPELINE HTTP ==================

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();
app.UseCors(MyCors);
app.UseAuthentication();
app.UseAuthorization();

// Controllers: /api/tasks, /api/projects, /api/auth, etc.
app.MapControllers();

// ================== ENDPOINT DEMO WEATHER ==================

var summaries = new[]
{
    "Freezing", "Bracing", "Chilly", "Cool", "Mild",
    "Warm", "Balmy", "Hot", "Sweltering", "Scorching"
};

app.MapGet("/weatherforecast", () =>
{
    var forecast = Enumerable.Range(1, 5).Select(index =>
        new WeatherForecast(
            DateOnly.FromDateTime(DateTime.Now.AddDays(index)),
            Random.Shared.Next(-20, 55),
            summaries[Random.Shared.Next(summaries.Length)]
        )
    ).ToArray();

    return forecast;
})
.WithName("GetWeatherForecast");

app.Run();

// ================== RECORD WEATHER ==================

record WeatherForecast(DateOnly Date, int TemperatureC, string? Summary)
{
    public int TemperatureF => 32 + (int)(TemperatureC / 0.5556);
}
