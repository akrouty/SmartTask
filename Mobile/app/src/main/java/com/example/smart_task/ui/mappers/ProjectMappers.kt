package com.example.smart_task.ui.mappers

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.smart_task.network.dto.ProjectResponseDto
import com.example.smart_task.ui.model.ProjectBucket
import com.example.smart_task.ui.model.ProjectCardUi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val frLocale = Locale.FRANCE

@RequiresApi(Build.VERSION_CODES.O)
fun ProjectResponseDto.toCardUi(today: LocalDate = LocalDate.now()): ProjectCardUi {
    val total = tasks.size
    val completed = tasks.count { it.status.equals("done", ignoreCase = true) }
    val percent = if (total > 0) ((completed * 100) / total) else 0

    val createdLabel = runCatching {
        val fmtOut = DateTimeFormatter.ofPattern("dd MMM yyyy", frLocale)

        // "2025-12-15T06:02:26.8469677" -> on tronque les nanos à 6
        val fixed = if (createdAt.contains(".")) {
            val head = createdAt.substringBefore(".")
            val frac = createdAt.substringAfter(".").take(6) // microsecondes
            "$head.$frac"
        } else createdAt

        val ldt = LocalDateTime.parse(fixed)
        "Créé le ${ldt.toLocalDate().format(fmtOut)}"
    }.getOrDefault("Créé récemment")

    val membersLabel = buildMembersLabel()

    val bucket = computeBucket(today)

    return ProjectCardUi(
        id = id,
        name = name,
        description = description ?: "",
        createdLabel = createdLabel,
        membersLabel = membersLabel,
        completedTasks = completed,
        totalTasks = total,
        progressPercent = percent,
        statusBucket = bucket
    )
}

private fun ProjectResponseDto.buildMembersLabel(): String {
    if (members.isEmpty()) return "0 membre"
    val initials = members.take(2).map { it.fullName.toInitials() }
    val remaining = members.size - initials.size
    return if (remaining > 0) initials.joinToString(", ") + " +$remaining"
    else initials.joinToString(", ")
}

private fun String.toInitials(): String {
    val parts = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val a = parts.getOrNull(0)?.firstOrNull()?.uppercaseChar()
    val b = parts.getOrNull(1)?.firstOrNull()?.uppercaseChar()
    return listOfNotNull(a, b).joinToString("")
}

@RequiresApi(Build.VERSION_CODES.O)
private fun ProjectResponseDto.computeBucket(today: LocalDate): ProjectBucket {
    if (tasks.isEmpty()) return ProjectBucket.IN_PROGRESS

    val allDone = tasks.all { it.status.equals("done", ignoreCase = true) }
    if (allDone) return ProjectBucket.DONE

    val hasLate = tasks.any {
        val due = it.dueDate?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() }
        val isOpen = !it.status.equals("done", ignoreCase = true)
        due != null && isOpen && due.isBefore(today)
    }
    if (hasLate) return ProjectBucket.LATE

    return ProjectBucket.IN_PROGRESS
}