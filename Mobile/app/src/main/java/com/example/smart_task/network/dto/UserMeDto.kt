package com.example.smart_task.network.dto

data class UserMeDto(
    val id: String?,
    val username: String?,
    val email: String?,
    val fullName: String?,   // si ton backend a ce champ
    val phone: String?       // optionnel
)

data class UpdateMeRequest(
    val username: String?,
    val email: String?,
    val fullName: String?,
    val phone: String?
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)
