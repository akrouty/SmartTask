package com.example.smart_task.network.dto


data class TaskApiDto(
    val id: String,
    val title: String,
    val description: String?,
    val status: String,     // "open" | "done" | "in-progression"
    val dueDate: String?,   // "yyyy-MM-dd" ou null
    val projectId: String,
    val priority: Int
)
data class TasksResponse(
    val items: List<TaskApiDto>,
    val total: Int
)
