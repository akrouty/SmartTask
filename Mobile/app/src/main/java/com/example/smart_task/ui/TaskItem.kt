package com.example.smart_task.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TaskItem(
    val id: String,
    val title: String,
    val description: String?,
    val statusCode: String,      // open | in-progression | done | late
    val dueDate: LocalDate?,     // ✅ pour filtres & groupage
    val dueDateLabel: String,    // "15 Jan" ou ""
    val projectId: String,
    val projectName: String?,
    val priority: Int            // 0 haute, 1 moyenne, 2 basse
) {
    fun isDone(): Boolean = statusCode == "done"
    fun isInProgress(): Boolean = statusCode == "in-progression"

    val statusLabel: String
        get() = when (statusCode) {
            "open" -> "À faire"
            "in-progression" -> "En cours"
            "done" -> "Terminée"
            "late" -> "En retard"
            else -> "À faire"
        }

    val priorityLabel: String
        get() = when (priority) {
            0 -> "Haute"
            1 -> "Moyenne"
            2 -> "Basse"
            else -> "Moyenne"
        }

    companion object {
        fun formatDue(date: LocalDate?): String {
            if (date == null) return ""
            return date.format(DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault()))
        }
    }
}
