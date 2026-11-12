package com.example.smart_task.network

data class RegisterRequest(
    val fullName: String,
    val email: String,
    val password: String
)