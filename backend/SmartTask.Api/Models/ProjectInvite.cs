using System.Text.Json.Serialization;

namespace SmartTask.Api.Models
{
    public class ProjectInvite
    {
        public string Id { get; set; } = Guid.NewGuid().ToString();

        // Projet ciblé
        public string ProjectId { get; set; } = default!;
        [JsonIgnore]
        public Project? Project { get; set; }

        // Email invité
        public string Email { get; set; } = default!; // stocké en lower-case de préférence

        // Rôle proposé pour cette invitation
        public ProjectRole Role { get; set; } = ProjectRole.Member;

        // Token envoyé dans le lien d’invitation
        public string Token { get; set; } = Guid.NewGuid().ToString("N");

        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
        public DateTime ExpiresAt { get; set; } = DateTime.UtcNow.AddDays(7);

        public bool Accepted { get; set; } = false;

        // User qui a accepté (si déjà accepté)
        public string? AcceptedUserId { get; set; }
        [JsonIgnore]
        public User? AcceptedUser { get; set; }
    }

}
