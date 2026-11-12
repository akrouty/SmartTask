namespace SmartTask.Api.Models;

public class User
{
    public string Id { get; set; } = Guid.NewGuid().ToString();

    public string FullName { get; set; } = default!;
    public string Email { get; set; } = default!;
    public string Password { get; set; } = default!;
    public string Role { get; set; } = "user";
}
