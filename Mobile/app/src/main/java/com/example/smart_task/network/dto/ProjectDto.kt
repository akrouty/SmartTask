package com.example.smart_task.network.dto

import android.R
import android.view.View
import com.example.smart_task.ui.model.ProjectCardUi

data class ProjectResponseDto(
    val id: String,
    val name: String,
    val description: String?,
    val createdAt: String,
    val tasks: List<TaskApiDto> = emptyList(),
    val members: List<MemberDto> = emptyList()
)

data class UpdateProjectRequest(
    val name: String,
    val description: String?
)
private const val ROLE_MEMBER = 1
private const val ROLE_OBSERVER = 2

data class InviteRequest(
    val email: String,
    val role: Int?
)
// com.example.smart_task.network.dto.InviteResponseDto
data class InviteResponseDto(
    val id: String,
    val token: String,
    val email: String,
    val role: String,          // "Member" / "Observer"
    val createdAt: String,
    val expiresAt: String,
    val acceptUrl: String,     // ✅ le lien à partager
    val reused: Boolean
)
