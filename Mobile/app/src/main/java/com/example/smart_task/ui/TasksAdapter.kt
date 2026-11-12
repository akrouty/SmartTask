package com.example.smart_task.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_task.R
import com.google.android.material.checkbox.MaterialCheckBox

class TasksAdapter(
    private val listener: TaskActionsListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface TaskActionsListener {
        fun onToggleDone(task: TaskItem, isDone: Boolean)
        fun onTaskClicked(task: TaskItem)
    }

    private var readOnly: Boolean = false
    private var rows: List<TaskRow> = emptyList()

    fun setReadOnly(value: Boolean) {
        readOnly = value
        notifyDataSetChanged()
    }

    fun submitRows(newRows: List<TaskRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    fun getRowAt(pos: Int): TaskRow? = rows.getOrNull(pos)

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is TaskRow.Header -> VIEW_HEADER
        is TaskRow.Item -> VIEW_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_HEADER) {
            val v = inflater.inflate(R.layout.item_task_header, parent, false)
            HeaderVH(v)
        } else {
            val v = inflater.inflate(R.layout.item_task, parent, false)
            TaskVH(v, listener)
        }
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is TaskRow.Header -> (holder as HeaderVH).bind(row)
            is TaskRow.Item -> (holder as TaskVH).bind(row.task, readOnly)
        }
    }

    class HeaderVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvHeaderTitle)
        private val tvCount: TextView = itemView.findViewById(R.id.tvHeaderCount)

        fun bind(h: TaskRow.Header) {
            tvTitle.text = h.title
            tvCount.text = h.count.toString()
        }
    }

    class TaskVH(
        itemView: View,
        private val listener: TaskActionsListener
    ) : RecyclerView.ViewHolder(itemView) {

        private val cbDone: MaterialCheckBox = itemView.findViewById(R.id.cbDone)

        private val tvTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
        private val tvProject: TextView? = itemView.findViewById(R.id.tvTaskProject)

        private val tvPriorityPill: TextView = itemView.findViewById(R.id.tvPriorityPill)
        private val tvStatusPill: TextView = itemView.findViewById(R.id.tvStatusPill)

        private val tvDue: TextView? =
            itemView.findViewById<View?>(R.id.tvTaskDue) as? TextView

        fun bind(t: TaskItem, readOnly: Boolean) {
            tvTitle.text = t.title
            tvProject?.text = t.projectName.orEmpty()
            tvPriorityPill.text = t.priorityLabel
            tvStatusPill.text = t.statusLabel

            // Due date
            tvDue?.text = t.dueDateLabel
            tvDue?.visibility = if (t.dueDateLabel.isBlank()) View.GONE else View.VISIBLE

            // Pills colors
            tvPriorityPill.backgroundTintList = ContextCompat.getColorStateList(
                itemView.context,
                when (t.priority) {
                    0 -> R.color.st_priority_high
                    1 -> R.color.st_priority_medium
                    else -> R.color.st_priority_low
                }
            )
            tvStatusPill.backgroundTintList = ContextCompat.getColorStateList(
                itemView.context,
                when (t.statusCode.lowercase()) {
                    "done" -> R.color.st_status_done
                    "in-progression", "in-progress" -> R.color.st_status_progress
                    "late" -> R.color.st_status_late
                    "open" -> R.color.st_status_open
                    else -> R.color.st_status_open
                }
            )

            // Checkbox (éviter trigger pendant bind)
            cbDone.setOnCheckedChangeListener(null)
            cbDone.isChecked = t.isDone()

            // ✅ ReadOnly support
            cbDone.isEnabled = !readOnly
            cbDone.alpha = if (readOnly) 0.6f else 1f

            cbDone.setOnCheckedChangeListener { _, checked ->
                if (!readOnly) listener.onToggleDone(t, checked)
            }

            // ✅ Click item: only edit when not readOnly
            itemView.setOnClickListener {
                if (!readOnly) listener.onTaskClicked(t)
            }

            // UI done
            if (t.isDone()) {
                tvTitle.paintFlags = tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                itemView.alpha = if (readOnly) 0.75f else 0.85f
            } else {
                tvTitle.paintFlags = tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                itemView.alpha = if (readOnly) 0.92f else 1f
            }

            // (optionnel) feedback visuel global readOnly
            if (readOnly) {
                itemView.isClickable = false
                itemView.isFocusable = false
            } else {
                itemView.isClickable = true
                itemView.isFocusable = true
            }
        }
    }

    companion object {
        private const val VIEW_HEADER = 0
        private const val VIEW_ITEM = 1
    }
}
