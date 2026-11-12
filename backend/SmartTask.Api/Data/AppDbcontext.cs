using Microsoft.EntityFrameworkCore;
using SmartTask.Api.Models;

namespace SmartTask.Api.Data;

public class AppDbContext : DbContext
{
    public AppDbContext(DbContextOptions<AppDbContext> options) : base(options) { }

    public DbSet<TaskItem> Tasks => Set<TaskItem>();
    public DbSet<Project> Projects => Set<Project>();
    public DbSet<User> Users { get; set; } = default!;

    public DbSet<ProjectMember> ProjectMembers => Set<ProjectMember>();

    public DbSet<ProjectInvite> ProjectInvites { get; set; } = default!;

    public DbSet<ProjectMessage> ProjectMessages { get; set; } = default!;
    public DbSet<Notification> Notifications => Set<Notification>();



    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);
        // Configurations supplémentaires si nécessaire
        modelBuilder.Entity<ProjectMember>()
            .HasIndex(pm => new { pm.ProjectId, pm.UserId })
            .IsUnique();
        modelBuilder.Entity<ProjectInvite>()
            .HasIndex(pi => pi.Token)
            .IsUnique();
        modelBuilder.Entity<ProjectMessage>()
            .HasIndex(x => new { x.ProjectId, x.CreatedAt });

    }


}
