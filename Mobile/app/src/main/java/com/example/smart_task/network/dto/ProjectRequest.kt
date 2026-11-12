package com.example.smart_task.network.dto
data class CreateProjectRequest(
    val name: String,
    val description: String? = null
)
