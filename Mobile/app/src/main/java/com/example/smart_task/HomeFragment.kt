package com.example.smart_task

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_task.network.ApiClient
import com.example.smart_task.network.dto.ProjectResponseDto
import com.example.smart_task.network.dto.TasksResponse
import com.example.smart_task.ui.HomeProjectUi
import com.example.smart_task.ui.HomeTaskUi
import com.example.smart_task.ui.RecentProjectsAdapter
import com.example.smart_task.ui.RecentTasksAdapter
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var rvRecentTasks: RecyclerView
    private lateinit var rvRecentProjects: RecyclerView
    private lateinit var recentTasksAdapter: RecentTasksAdapter
    private lateinit var recentProjectsAdapter: RecentProjectsAdapter

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvName   = view.findViewById<TextView>(R.id.tvName)
        val avatarTv = view.findViewById<TextView>(R.id.avatar)

        val savedName = SessionManager.getUserName(requireContext())
        val fullName = savedName?.takeIf { it.isNotBlank() } ?: "Admin"
        tvName.text = fullName

        val initials = fullName
            .trim()
            .split("\\s+".toRegex())
            .filter { it.isNotEmpty() }
            .take(2)
            .map { it[0].uppercaseChar() }
            .joinToString("")
        avatarTv.text = if (initials.isNotEmpty()) initials else fullName.take(2).uppercase()

        // Click avatar -> ProfileActivity (placeholder)
        avatarTv.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }

        // RecyclerViews
        rvRecentProjects = view.findViewById(R.id.rvRecentProjects)
        rvRecentTasks = view.findViewById(R.id.rvRecentTasks)

        recentProjectsAdapter = RecentProjectsAdapter { /* plus tard: ouvrir projet */ }
        rvRecentProjects.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvRecentProjects.adapter = recentProjectsAdapter

        recentTasksAdapter = RecentTasksAdapter { /* plus tard: ouvrir détail tâche */ }
        rvRecentTasks.layoutManager = LinearLayoutManager(requireContext())
        rvRecentTasks.adapter = recentTasksAdapter

        // "Voir tout" -> switch tab
        view.findViewById<TextView>(R.id.tvSeeAllTasks).setOnClickListener {
            (activity as? MainActivity)?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
                ?.selectedItemId = R.id.nav_tasks
        }
        view.findViewById<TextView>(R.id.tvSeeAllProjects).setOnClickListener {
            (activity as? MainActivity)?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNav)
                ?.selectedItemId = R.id.nav_projects
        }

        // Animation existante (header + stats)
        val header = view.findViewById<View>(R.id.headerContainer)
        val statDone         = view.findViewById<View>(R.id.statDone)
        val statInProgress   = view.findViewById<View>(R.id.statInProgress)
        val statTotal        = view.findViewById<View>(R.id.statTotal)
        val statProductivity = view.findViewById<View>(R.id.statProductivity)
        animateStaggered(listOf(header, statDone, statInProgress, statTotal, statProductivity))

        // Load data
        loadDashboard(view)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadDashboard(root: View) {
        val token = SessionManager.getToken(requireContext())
        if (token.isNullOrBlank()) {
            Snackbar.make(root, "Non authentifié – veuillez vous reconnecter", Snackbar.LENGTH_LONG).show()
            return
        }

        (activity as? LoadingHost)?.showLoading()

        var projects: List<ProjectResponseDto> = emptyList()
        var tasksResponse: TasksResponse? = null
        var pending = 2

        fun doneOne() {
            pending--
            if (pending == 0) {
                (activity as? LoadingHost)?.hideLoading()
                if (tasksResponse == null) return
                renderDashboard(root, projects, tasksResponse!!)
            }
        }

        ApiClient.api.getProjects("Bearer $token").enqueue(object : Callback<List<ProjectResponseDto>> {
            override fun onResponse(call: Call<List<ProjectResponseDto>>, response: Response<List<ProjectResponseDto>>) {
                projects = response.body().orEmpty()
                doneOne()
            }
            override fun onFailure(call: Call<List<ProjectResponseDto>>, t: Throwable) {
                Snackbar.make(root, "Erreur réseau (projets): ${t.localizedMessage}", Snackbar.LENGTH_SHORT).show()
                doneOne()
            }
        })

        ApiClient.api.getTasks("Bearer $token").enqueue(object : Callback<TasksResponse> {
            override fun onResponse(call: Call<TasksResponse>, response: Response<TasksResponse>) {
                tasksResponse = response.body()
                doneOne()
            }
            override fun onFailure(call: Call<TasksResponse>, t: Throwable) {
                Snackbar.make(root, "Erreur réseau (tâches): ${t.localizedMessage}", Snackbar.LENGTH_SHORT).show()
                doneOne()
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun renderDashboard(root: View, projects: List<ProjectResponseDto>, tasksRes: TasksResponse) {
        val tasks = tasksRes.items

        fun normStatus(s: String) = s.lowercase()
        fun isDone(s: String) = normStatus(s) == "done"
        fun isInProgress(s: String) = normStatus(s) == "in-progress" || normStatus(s) == "in-progression"

        // --------- Stats globales ----------
        val total = tasks.size
        val doneCount = tasks.count { isDone(it.status) }
        val inProgCount = tasks.count { isInProgress(it.status) }
        val productivityPct = if (total > 0) (doneCount * 100 / total) else 0

        // --------- Hebdo (aujourd’hui -> +6 jours) ----------
        val today = LocalDate.now()
        val weekEnd = today.plusDays(6)

        val tasksWithDueInWeek = tasks.mapNotNull { t ->
            val d = parseDueDate(t.dueDate) ?: return@mapNotNull null
            if (!d.isBefore(today) && !d.isAfter(weekEnd)) t else null
        }
        val weekTotal = tasksWithDueInWeek.size
        val weekDone = tasksWithDueInWeek.count { isDone(it.status) }
        val weekPct = if (weekTotal > 0) (weekDone * 100 / weekTotal) else 0

        // UI stats
        root.findViewById<TextView>(R.id.tvWeeklyCount).text = "$weekDone / $weekTotal tâches"
        root.findViewById<TextView>(R.id.tvProgressPct).text = "${weekPct}%"
        root.findViewById<LinearProgressIndicator>(R.id.progressWeekly).setProgressCompat(weekPct, true)

        val statDone = root.findViewById<View>(R.id.statDone)
        val statInProgress = root.findViewById<View>(R.id.statInProgress)
        val statTotal = root.findViewById<View>(R.id.statTotal)
        val statProductivity = root.findViewById<View>(R.id.statProductivity)

        statDone.findViewById<TextView>(R.id.value).text = doneCount.toString()
        statInProgress.findViewById<TextView>(R.id.value).text = inProgCount.toString()
        statTotal.findViewById<TextView>(R.id.value).text = total.toString()
        statProductivity.findViewById<TextView>(R.id.value).text = "${productivityPct}%"

        // --------- Projets récents (basé sur les tâches) ----------
        val projectNameById = projects.associate { it.id to it.name }

        val tasksByProject = tasks.groupBy { it.projectId }
        val projectUis = tasksByProject.map { (projectId, list) ->
            val totalP = list.size
            val doneP = list.count { isDone(it.status) }
            HomeProjectUi(
                id = projectId,
                name = projectNameById[projectId] ?: "Projet",
                doneCount = doneP,
                totalCount = totalP
            )
        }
        // "récents" pragmatique: afficher les projets les plus “actifs” (plus de tâches)
        val topProjects = projectUis.sortedByDescending { it.totalCount }.take(6)
        recentProjectsAdapter.submitList(topProjects)

        // --------- Tâches récentes (pragmatiquement: à échéance la plus proche) ----------
        val fmt = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())

        val sortedTasks = tasks.sortedWith(compareBy(
            { parseDueDate(it.dueDate) ?: LocalDate.MAX },
            { it.title.lowercase() }
        ))

        val topTasks = sortedTasks.take(6).map { t ->
            val due = parseDueDate(t.dueDate)
            HomeTaskUi(
                id = t.id,
                title = t.title,
                projectName = projectNameById[t.projectId] ?: "",
                dueLabel = due?.format(fmt) ?: "",
                statusCode = normStatus(t.status),
                priority = t.priority
            )
        }
        recentTasksAdapter.submitList(topTasks)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseDueDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    }

    private fun animateStaggered(views: List<View>) {
        var delay = 0L
        views.forEach { v ->
            v.alpha = 0f
            v.translationY = 40f
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(220L)
                .start()
            delay += 60L
        }
    }
}
