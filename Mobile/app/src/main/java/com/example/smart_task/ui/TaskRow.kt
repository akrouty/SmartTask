package com.example.smart_task.ui

sealed class TaskRow {
    data class Header(val title: String, val count: Int) : TaskRow()
    data class Item(val task: TaskItem) : TaskRow()
}
