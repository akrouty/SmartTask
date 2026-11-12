package com.example.smart_task.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_task.ProjectActionsListener
import com.example.smart_task.R
import com.example.smart_task.ui.model.ProjectCardUi
import com.google.android.material.progressindicator.LinearProgressIndicator

class ProjectsAdapter(
    private val listener: ProjectActionsListener,
    private var items: List<ProjectCardUi> = emptyList()
) : RecyclerView.Adapter<ProjectsAdapter.ProjectVH>() {

    fun submitList(newItems: List<ProjectCardUi>?) {
        items = newItems ?: emptyList()
        notifyDataSetChanged()
    }

    inner class ProjectVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val tvProjectName: TextView = itemView.findViewById(R.id.tvProjectName)
        private val tvProjectDescription: TextView = itemView.findViewById(R.id.tvProjectDescription)
        private val tvProgressValue: TextView = itemView.findViewById(R.id.tvProgressValue)
        private val tvMembers: TextView = itemView.findViewById(R.id.tvMembers)
        private val tvCreatedAt: TextView = itemView.findViewById(R.id.tvCreatedAt)
        private val progressProject: LinearProgressIndicator = itemView.findViewById(R.id.progressProject)

        // ⚠️ Doit exister dans item_project.xml
        private val btnMenu: View? = itemView.findViewById(R.id.btnMore)

        fun bind(project: ProjectCardUi) {
            tvProjectName.text = project.name
            tvProjectDescription.text = project.description.orEmpty()
            tvMembers.text = project.membersLabel
            tvCreatedAt.text = project.createdLabel

            tvProgressValue.text = "${project.completedTasks}/${project.totalTasks} tâches"
            progressProject.setProgressCompat(project.progressPercent, true)

            // Clic sur la carte
            itemView.setOnClickListener {
                listener.onProjectClicked(project)
            }

            // Clic sur ⋮
            btnMenu?.setOnClickListener { anchor ->
                listener.onProjectMenuClicked(anchor, project)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_project, parent, false)
        return ProjectVH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ProjectVH, position: Int) {
        holder.bind(items[position])
    }
}
