using System.ComponentModel.DataAnnotations;

namespace SmartTask.Api.Models   // mets le même namespace que ton contrôleur ou que ProjectInvite
{
    public class InviteMemberRequest
    {
        [Required]
        [EmailAddress]
        public string Email { get; set; } = string.Empty;


        [Range(0, 2)]
        public ProjectRole Role { get; set; } = ProjectRole.Member;
    }
}
