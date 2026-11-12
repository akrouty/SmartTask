package com.example.smart_task.network.dto

data class ProjectMembersResponse(val members: List<ProjectMemberDto>)

data class ProjectMemberDto(
    val id: String,
    val userId: String,
    val userEmail: String,
    val userFullName: String,
    val role: Int // 0/1/2...
)
