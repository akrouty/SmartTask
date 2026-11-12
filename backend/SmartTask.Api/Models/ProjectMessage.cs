using System.ComponentModel.DataAnnotations;
using System.Text.Json.Serialization;

namespace SmartTask.Api.Models;

public class ProjectMessage
{
    public string Id { get; set; } = Guid.NewGuid().ToString();

    [Required]
    public string ProjectId { get; set; } = default!;
    [JsonIgnore]
    public Project? Project { get; set; }

    [Required]
    public string UserId { get; set; } = default!;
    [JsonIgnore]
    public User? User { get; set; }

    [Required]
    [MaxLength(2000)]
    public string Content { get; set; } = default!;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
