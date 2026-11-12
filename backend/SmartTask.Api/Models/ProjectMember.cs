using System.Text.Json.Serialization;

namespace SmartTask.Api.Models;

public enum ProjectRole
{
    Owner = 0,    // créateur du projet
    Member = 1,   // peut gérer les tâches
    Observer = 2  // lecture seule
}

public class ProjectMember
{
    public string Id { get; set; } = Guid.NewGuid().ToString();

    public string ProjectId { get; set; } = default!;
    [JsonIgnore]                   // pour éviter les cycles JSON
    public Project? Project { get; set; }

    public string UserId { get; set; } = default!;
    [JsonIgnore]
    public User? User { get; set; }

    public ProjectRole Role { get; set; } = ProjectRole.Member;
    public DateTime JoinedAt { get; set; } = DateTime.UtcNow;
}
