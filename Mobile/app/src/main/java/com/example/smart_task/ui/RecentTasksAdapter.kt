package com.example.smart_task.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_task.R
import com.google.android.material.card.MaterialCardView

data class HomeTaskUi(
    val id: String,
    val title: String,
    val projectName: String,
    val dueLabel: String,
    val statusCode: String,
    val priority: Int
)

class RecentTasksAdapter(
    private val onClick: (HomeTaskUi) -> Unit
) : RecyclerView.Adapter<RecentTasksAdapter.VH>() {

    private var items: List<HomeTaskUi> = emptyList()
    private val animatedIds = HashSet<String>()

    fun submitList(list: List<HomeTaskUi>) {
        items = list
        animatedIds.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_task, parent, false)
        return VH(v, onClick)
    }

    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position], animatedIds)

    class VH(
        itemView: View,
        private val onClick: (HomeTaskUi) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
        private val tvDue: TextView = itemView.findViewById(R.id.tvDuePill)
        private val tvProject: TextView = itemView.findViewById(R.id.tvProjectName)
        private val tvPriority: TextView = itemView.findViewById(R.id.tvPriorityPill)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatusPill)
        private val card: MaterialCardView = itemView as MaterialCardView

        fun bind(t: HomeTaskUi, animatedIds: MutableSet<String>) {
            tvTitle.text = t.title
            tvProject.text = t.projectName
            tvDue.text = t.dueLabel
            tvDue.visibility = if (t.dueLabel.isBlank()) View.GONE else View.VISIBLE

            // Priority pill
            tvPriority.text = when (t.priority) {
                0 -> "Haute"
                1 -> "Moyenne"
                else -> "Basse"
            }
            tvPriority.setBackgroundResource(
                when (t.priority) {
                    0 -> R.drawable.bg_pill_priority_high
                    1 -> R.drawable.bg_pill_priority_medium
                    else -> R.drawable.bg_pill_priority_low
                }
            )

            // Status pill
            val (label, bg) = when (t.statusCode) {
                "done" -> "Terminée" to R.drawable.bg_pill_status_done
                "in-progress", "in-progression" -> "En cours" to R.drawable.bg_pill_status_progress
                "late" -> "En retard" to R.drawable.bg_pill_status_late
                else -> "À faire" to R.drawable.bg_pill_status_open
            }
            tvStatus.text = label
            tvStatus.setBackgroundResource(bg)

            card.setOnClickListener { onClick(t) }

            // Animation slide-up fade-in (une seule fois par item)
            if (animatedIds.add(t.id)) {
                itemView.alpha = 0f
                itemView.translationY = 30f
                itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(720L)
                    .start()
            } else {
                itemView.alpha = 1f
                itemView.translationY = 0f
            }
        }
    }
}
