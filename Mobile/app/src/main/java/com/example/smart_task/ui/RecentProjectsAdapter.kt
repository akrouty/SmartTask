package com.example.smart_task.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_task.R
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlin.math.roundToInt

data class HomeProjectUi(
    val id: String,
    val name: String,
    val doneCount: Int,
    val totalCount: Int
)

class RecentProjectsAdapter(
    private val onClick: (HomeProjectUi) -> Unit
) : RecyclerView.Adapter<RecentProjectsAdapter.VH>() {

    private var items: List<HomeProjectUi> = emptyList()
    private val animatedIds = HashSet<String>()

    fun submitList(list: List<HomeProjectUi>) {
        items = list
        animatedIds.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_project, parent, false)
        return VH(v, onClick)
    }

    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(items[position], animatedIds)

    class VH(
        itemView: View,
        private val onClick: (HomeProjectUi) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvName: TextView = itemView.findViewById(R.id.tvProjectName)
        private val tvMeta: TextView = itemView.findViewById(R.id.tvProjectMeta)
        private val tvPct: TextView = itemView.findViewById(R.id.tvProjectProgressPct)
        private val progress: LinearProgressIndicator = itemView.findViewById(R.id.progressProject)

        fun bind(p: HomeProjectUi, animatedIds: MutableSet<String>) {
            tvName.text = p.name
            tvMeta.text = "${p.doneCount}/${p.totalCount} tâches terminées"

            val pct = computePct(p.doneCount, p.totalCount)
            tvPct.text = "$pct%"
            progress.setProgressCompat(pct, true)

            itemView.setOnClickListener { onClick(p) }

            if (animatedIds.add(p.id)) {
                itemView.alpha = 0f
                itemView.translationY = 30f
                itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(620L)
                    .start()
            } else {
                itemView.alpha = 1f
                itemView.translationY = 0f
            }
        }

        private fun computePct(done: Int, total: Int): Int {
            if (total <= 0) return 0
            val raw = (done.toFloat() / total.toFloat()) * 100f
            return raw.roundToInt().coerceIn(0, 100)
        }
    }
}
