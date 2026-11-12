namespace SmartTask.Api.Models;

public record MeDto(
    string Id,
    string FullName,
    string Email,
    string Role
);

public record UpdateMeRequest(
    string FullName
);

public record ChangePasswordRequest(
    string CurrentPassword,
    string NewPassword
);
