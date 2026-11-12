package com.example.smart_task.ui.model

data class ProjectCardUi(
    val id: String,
    val name: String,
    val description: String,
    val createdLabel: String,        // "Créé le 15 déc. 2025"
    val membersLabel: String,        // "AA, CA +1"
    val completedTasks: Int,
    val totalTasks: Int,
    val progressPercent: Int,        // 0..100
    val statusBucket: ProjectBucket  // Pour tes filtres
)

enum class ProjectBucket { ALL, IN_PROGRESS, DONE, LATE }