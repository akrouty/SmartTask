package com.example.smart_task.network.dto

data class UpdateTaskRequest(
    val title: String,
    val description: String?,
    val status: String,
    val dueDate: String?,
    val projectId: String,
    val priority: Int
)
data class CreateTaskRequest(
    val title: String,
    val description: String?,
    val status: String,      // open | in-progression | done
    val dueDate: String?,    // "yyyy-MM-dd" ou null
    val projectId: String,
    val priority: Int        // 0..2
)