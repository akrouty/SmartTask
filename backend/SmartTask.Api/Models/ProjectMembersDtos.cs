using SmartTask.Api.Models;

public class ProjectMemberDto
{
    public string Id { get; set; } = default!;
    public string UserId { get; set; } = default!;
    public string UserEmail { get; set; } = default!;
    public string? UserFullName { get; set; }
    public ProjectRole Role { get; set; }
}

public class ProjectMembersResponseDto
{
    public List<ProjectMemberDto> Members { get; set; } = new();
}
