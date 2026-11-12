package com.example.smart_task.network.dto

import com.example.smart_task.ui.TaskItem

sealed class TaskRow {
    data class Header(val title: String, val count: Int) : TaskRow()
    data class Item(val task: TaskItem) : TaskRow()
}
