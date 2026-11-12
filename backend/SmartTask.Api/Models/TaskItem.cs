using System.Text.Json.Serialization;

namespace SmartTask.Api.Models;

public enum TaskPriority
{
    Low = 0,
    Medium = 1,
    High = 2
}

public class TaskItem
{
    public string Id { get; set; } = Guid.NewGuid().ToString();
    public string Title { get; set; } = default!;
    public string? Description { get; set; }

    public string Status { get; set; } = "open";
    public string? DueDate { get; set; }

    public string ProjectId { get; set; } = default!;
    [JsonIgnore]
    public Project? Project { get; set; }


    public TaskPriority Priority { get; set; } = TaskPriority.Medium;
}
