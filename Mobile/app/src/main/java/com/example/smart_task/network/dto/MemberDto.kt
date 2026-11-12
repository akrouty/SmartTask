package com.example.smart_task.network.dto

data class MemberDto(
    val id: String,
    val projectId: String,
    val userId: String,
    val role: Int,          // 0/1/2 selon ton backend
    val joinedAt: String,
    val fullName: String,
    val email: String
)