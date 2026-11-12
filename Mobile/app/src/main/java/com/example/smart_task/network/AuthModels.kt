package com.example.smart_task.network

data class LoginRequest(
    val email: String,
    val password: String
)

data class UserDto(
    val id: String,
    val fullName: String?,
    val email: String,
    val role: String
)

data class LoginResponse(
    val token: String,
    val user: UserDto
)
