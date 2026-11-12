package com.example.smart_task

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_task.network.ApiClient
import com.example.smart_task.network.dto.ProjectMembersResponse
import com.example.smart_task.network.dto.ProjectResponseDto
import com.example.smart_task.network.dto.TaskApiDto
import com.example.smart_task.network.dto.TasksResponse
import com.example.smart_task.network.dto.UpdateTaskRequest
import com.example.smart_task.network.dto.UserMeDto
import com.example.smart_task.ui.CreateTaskBottomSheet
import com.example.smart_task.ui.TaskItem
import com.example.smart_task.ui.TaskRow
import com.example.smart_task.ui.TasksAdapter
import com.example.smart_task.ui.WeekAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Suppress("DEPRECATION")
class TasksFragment : Fragment(R.layout.fragment_tasks) {

    private lateinit var rvTasks: RecyclerView
    private lateinit var rvWeek: RecyclerView
    private lateinit var tasksAdapter: TasksAdapter
    private lateinit var weekAdapter: WeekAdapter

    private lateinit var actProjectFilter: MaterialAutoCompleteTextView
    private lateinit var chipGroupStatus: com.google.android.material.chip.ChipGroup
    private lateinit var tvEmpty: TextView
    private lateinit var btnSort: MaterialButton
    private lateinit var switchHideDone: SwitchMaterial

    private val allTasks = mutableListOf<TaskItem>()
    private val projectNameById = mutableMapOf<String, String>()
    private val allProjectNames = mutableListOf<String>()

    // Pour pouvoir revenir au statut précédent quand on décoche "Terminée"
    private val lastNonDoneStatusById = mutableMapOf<String, String>()

    // ---- Roles / members ----
    private var myUserId: String? = null
    private val projectIdByName = mutableMapOf<String, String>()
    private var selectedProjectId: String? = null

    private val roleByProjectId = mutableMapOf<String, Int>()
    private var currentRole: Int? = null

    private lateinit var fabAddTask: com.google.android.material.floatingactionbutton.FloatingActionButton

    private val ROLE_ADMIN = 0
    private val ROLE_MEMBER = 1
    private val ROLE_OBSERVER = 2

    private fun canEdit(role: Int?): Boolean = role == ROLE_ADMIN || role == ROLE_MEMBER

    // UI state
    private var hideDone = false
    private var selectedDay: LocalDate? = null

    enum class TaskSort { DUE_ASC, PRIORITY_DESC, TITLE_ASC }
    private var currentSort = TaskSort.DUE_ASC

    // swipe helper reference (optional)
    private var swipeHelper: ItemTouchHelper? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvTasks = view.findViewById(R.id.rvTasks)
        rvWeek = view.findViewById(R.id.rvWeek)
        actProjectFilter = view.findViewById(R.id.actProjectFilter)
        chipGroupStatus = view.findViewById(R.id.chipGroupStatus)
        tvEmpty = view.findViewById(R.id.tvEmptyTasks)
        btnSort = view.findViewById(R.id.btnSortTasks)
        switchHideDone = view.findViewById(R.id.switchHideDone)

        fabAddTask = view.findViewById(R.id.fabAddTask)
        startPulse(fabAddTask)

        // ✅ Adapter avec listener
        tasksAdapter = TasksAdapter(object : TasksAdapter.TaskActionsListener {
            override fun onToggleDone(task: TaskItem, isDone: Boolean) {
                toggleDone(task, isDone)
            }

            override fun onTaskClicked(task: TaskItem) {
                showEditTaskDialog(task)
            }
        })

        rvTasks.layoutManager = LinearLayoutManager(requireContext())
        rvTasks.adapter = tasksAdapter

        setupSwipeToDelete()

        weekAdapter = WeekAdapter { day ->
            selectedDay = if (selectedDay == day) null else day
            render()
            submitWeek()
        }
        rvWeek.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvWeek.adapter = weekAdapter

        // listeners UI
        chipGroupStatus.setOnCheckedChangeListener { _, _ -> render() }
        switchHideDone.setOnCheckedChangeListener { _, isChecked ->
            hideDone = isChecked
            render()
        }
        btnSort.setOnClickListener { showSortDialog() }

        fabAddTask.setOnClickListener {
            if (selectedProjectId == null) {
                Snackbar.make(requireView(), "Choisissez un projet pour ajouter une tâche", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!canEdit(currentRole)) {
                Snackbar.make(requireView(), "Mode lecture seule (Observateur)", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }

            CreateTaskBottomSheet.newInstance(projectId = selectedProjectId!!) { createdApi ->
                if (!isAdded || view == null) return@newInstance

                runCatching {
                    val ui = createdApi.toUiModelSafe()   // 👈 fonction safe ci-dessous
                    allTasks.add(0, ui)

                    if (!ui.isDone()) {
                        lastNonDoneStatusById[ui.id] = ui.statusCode
                    }

                    render()
                    rvTasks.post { rvTasks.scrollToPosition(0) }
                }.onFailure {
                    // fallback: recharge depuis serveur (la tâche existe déjà)
                    val token = SessionManager.getToken(requireContext()).orEmpty()
                    if (token.isNotBlank()) loadTasks(token)
                }
            }.show(parentFragmentManager, "CreateTaskBottomSheet")
        }

        // Load data
        loadMe()
        loadProjectsThenTasks()
        submitWeek()

        // UI safe initial state
        setReadOnlyUi(true)
        fabAddTask.hide()
    }

    // -------------------- Auth / user --------------------
    @RequiresApi(Build.VERSION_CODES.O)
    private fun TaskApiDto.toUiModelSafe(): TaskItem {
        val statusCode = (status ?: "open").lowercase()
        val dueLocal = parseDueDate(dueDate)
        val dueLabel = formatDueDateLabel(dueLocal)
        val projectName = projectNameById[projectId]

        val safePriority = (priority ?: 1) // si backend renvoie null parfois

        return TaskItem(
            id = id,
            title = title ?: "",
            description = description,
            statusCode = statusCode,
            dueDate = dueLocal,
            dueDateLabel = dueLabel,
            projectId = projectId,
            projectName = projectName,
            priority = safePriority
        )
    }
    private fun loadMe() {
        val token = SessionManager.getToken(requireContext()).orEmpty()
        if (token.isBlank()) return

        ApiClient.api.getMe("Bearer $token").enqueue(object : Callback<UserMeDto> {
            override fun onResponse(call: Call<UserMeDto>, response: Response<UserMeDto>) {
                if (response.isSuccessful) {
                    myUserId = response.body()?.id
                    // si un projet est déjà sélectionné => refresh role
                    updateRoleAndUi()
                }
            }

            override fun onFailure(call: Call<UserMeDto>, t: Throwable) {}
        })
    }

    // -------------------- Week header --------------------

    @RequiresApi(Build.VERSION_CODES.O)
    private fun submitWeek() {
        val today = LocalDate.now()
        val week = (0..6).map { today.plusDays(it.toLong()) }
        weekAdapter.submitWeek(week, selectedDay)
    }

    // -------------------- Load projects + tasks --------------------

    private fun loadProjectsThenTasks() {
        val root = view ?: return
        val token = SessionManager.getToken(requireContext())

        if (token.isNullOrEmpty()) {
            Snackbar.make(root, "Non authentifié – veuillez vous reconnecter", Snackbar.LENGTH_LONG).show()
            return
        }

        ApiClient.api.getProjects("Bearer $token")
            .enqueue(object : Callback<List<ProjectResponseDto>> {
                @RequiresApi(Build.VERSION_CODES.O)
                override fun onResponse(
                    call: Call<List<ProjectResponseDto>>,
                    response: Response<List<ProjectResponseDto>>
                ) {
                    if (!response.isSuccessful || response.body() == null) {
                        Snackbar.make(root, "Erreur chargement projets (${response.code()})", Snackbar.LENGTH_SHORT).show()
                        return
                    }

                    val projects = response.body()!!
                    projectNameById.clear()
                    projectNameById.putAll(projects.associate { it.id to it.name })

                    projectIdByName.clear()
                    projectIdByName.putAll(projects.associate { it.name to it.id })

                    allProjectNames.clear()
                    allProjectNames.addAll(projects.map { it.name }.distinct())

                    setupProjectFilter()

                    loadTasks(token)
                }

                override fun onFailure(call: Call<List<ProjectResponseDto>>, t: Throwable) {
                    Snackbar.make(root, "Erreur réseau (projets) : ${t.localizedMessage}", Snackbar.LENGTH_LONG).show()
                }
            })
    }

    private fun loadTasks(token: String) {
        val root = view ?: return

        ApiClient.api.getTasks("Bearer $token")
            .enqueue(object : Callback<TasksResponse> {
                @RequiresApi(Build.VERSION_CODES.O)
                override fun onResponse(call: Call<TasksResponse>, response: Response<TasksResponse>) {
                    if (!response.isSuccessful || response.body() == null) {
                        Snackbar.make(root, "Erreur serveur tâches (${response.code()})", Snackbar.LENGTH_SHORT).show()
                        return
                    }

                    val apiTasks = response.body()!!.items
                    allTasks.clear()
                    allTasks.addAll(apiTasks.map { it.toUiModel() })

                    // init lastNonDone
                    allTasks.forEach { t ->
                        if (!t.isDone()) lastNonDoneStatusById[t.id] = t.statusCode
                    }

                    render()
                }

                override fun onFailure(call: Call<TasksResponse>, t: Throwable) {
                    Snackbar.make(root, "Erreur réseau tâches: ${t.localizedMessage}", Snackbar.LENGTH_LONG).show()
                }
            })
    }

    // -------------------- Project filter + role handling --------------------

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupProjectFilter() {
        val ctx = requireContext()
        val items = listOf("Tous les projets") + allProjectNames

        val adapterProjects = ArrayAdapter(ctx, android.R.layout.simple_list_item_1, items)
        actProjectFilter.setAdapter(adapterProjects)

        if (actProjectFilter.text.isNullOrEmpty()) {
            actProjectFilter.setText("Tous les projets", false)
        }

        actProjectFilter.setOnItemClickListener { _, _, _, _ ->
            val selectedName = actProjectFilter.text?.toString().orEmpty()
            selectedProjectId = if (selectedName == "Tous les projets") null else projectIdByName[selectedName]

            updateRoleAndUi()
            render()
        }

        // initial role state (if default already set)
        val selectedName = actProjectFilter.text?.toString().orEmpty()
        selectedProjectId = if (selectedName == "Tous les projets") null else projectIdByName[selectedName]
        updateRoleAndUi()
    }

    private fun updateRoleAndUi() {
        val pid = selectedProjectId

        if (pid == null) {
            currentRole = null
            fabAddTask.hide()
            setReadOnlyUi(true)
            return
        }

        // cached role
        roleByProjectId[pid]?.let { cached ->
            currentRole = cached
            applyRoleToUi()
            return
        }

        val token = SessionManager.getToken(requireContext()).orEmpty()
        val uid = myUserId

        if (token.isBlank() || uid.isNullOrBlank()) {
            // safe fallback: read-only
            currentRole = ROLE_OBSERVER
            applyRoleToUi()
            return
        }

        ApiClient.api.getProjectMembers("Bearer $token", pid)
            .enqueue(object : Callback<ProjectMembersResponse> {
                override fun onResponse(
                    call: Call<ProjectMembersResponse>,
                    response: Response<ProjectMembersResponse>
                ) {
                    val members = response.body()?.members.orEmpty()
                    val role = members.firstOrNull { it.userId == uid }?.role ?: ROLE_OBSERVER

                    roleByProjectId[pid] = role
                    currentRole = role
                    applyRoleToUi()
                }

                override fun onFailure(call: Call<ProjectMembersResponse>, t: Throwable) {
                    currentRole = ROLE_OBSERVER
                    applyRoleToUi()
                }
            })
    }

    private fun applyRoleToUi() {
        val editable = canEdit(currentRole)

        if (editable && selectedProjectId != null) fabAddTask.show() else fabAddTask.hide()
        setReadOnlyUi(!editable)
    }

    private fun setReadOnlyUi(readOnly: Boolean) {
        // requires TasksAdapter.setReadOnly(readOnly)
        tasksAdapter.setReadOnly(readOnly)
        // swipe blocking handled by getSwipeDirs check
    }

    // -------------------- Mapping --------------------

    @RequiresApi(Build.VERSION_CODES.O)
    private fun TaskApiDto.toUiModel(): TaskItem {
        val statusCode = status.lowercase()
        val dueLocal = parseDueDate(dueDate)
        val dueLabel = formatDueDateLabel(dueLocal)
        val projectName = projectNameById[projectId]

        return TaskItem(
            id = id,
            title = title,
            description = description,
            statusCode = statusCode,
            dueDate = dueLocal,
            dueDateLabel = dueLabel,
            projectId = projectId,
            projectName = projectName,
            priority = priority
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseDueDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun formatDueDateLabel(date: LocalDate?): String {
        if (date == null) return ""
        val fmt = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
        return date.format(fmt)
    }

    // -------------------- Render --------------------

    @RequiresApi(Build.VERSION_CODES.O)
    private fun render() {
        val selectedProject = actProjectFilter.text?.toString().orEmpty()
        val today = LocalDate.now()

        var seq = allTasks.asSequence()

        // filtre projet
        if (selectedProject.isNotBlank() && selectedProject != "Tous les projets") {
            seq = seq.filter { it.projectName == selectedProject }
        }

        // filtre statut chips
        seq = when (chipGroupStatus.checkedChipId) {
            R.id.chipInProgress -> seq.filter { it.isInProgress() }
            R.id.chipDone -> seq.filter { it.isDone() }
            R.id.chipLate -> seq.filter { it.dueDate != null && !it.isDone() && it.dueDate.isBefore(today) }
            else -> seq
        }

        // masquer terminées
        if (hideDone) seq = seq.filterNot { it.isDone() }

        // filtre jour
        selectedDay?.let { day -> seq = seq.filter { it.dueDate == day } }

        // tri
        val list = seq.toList().let { l ->
            when (currentSort) {
                TaskSort.DUE_ASC ->
                    l.sortedWith(compareBy<TaskItem>({ it.dueDate ?: LocalDate.MAX }, { it.title.lowercase() }))
                TaskSort.PRIORITY_DESC ->
                    l.sortedBy { it.priority } // 0 haute d'abord
                TaskSort.TITLE_ASC ->
                    l.sortedBy { it.title.lowercase() }
            }
        }

        val rows = buildGroupedRows(list)
        tasksAdapter.submitRows(rows)
        tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildGroupedRows(tasks: List<TaskItem>): List<TaskRow> {
        val today = LocalDate.now()
        val weekEnd = today.plusDays(6)

        fun bucket(t: TaskItem): String = when {
            t.dueDate == null -> "Sans date"
            !t.isDone() && t.dueDate.isBefore(today) -> "En retard"
            t.dueDate.isEqual(today) -> "Aujourd’hui"
            !t.dueDate.isAfter(weekEnd) -> "Cette semaine"
            else -> "Plus tard"
        }

        val grouped = tasks.groupBy { bucket(it) }
        val order = listOf("En retard", "Aujourd’hui", "Cette semaine", "Plus tard", "Sans date")

        val rows = mutableListOf<TaskRow>()
        for (k in order) {
            val list = grouped[k].orEmpty()
            if (list.isNotEmpty()) {
                rows += TaskRow.Header(k, list.size)
                rows += list.map { TaskRow.Item(it) }
            }
        }
        return rows
    }

    // -------------------- Swipe delete (role protected) --------------------

    private fun setupSwipeToDelete() {
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                // ✅ Role protection
                if (!canEdit(currentRole)) return 0

                val pos = viewHolder.bindingAdapterPosition
                val row = tasksAdapter.getRowAt(pos)
                return if (row is TaskRow.Header) 0 else super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // ✅ Role protection (double safety)
                if (!canEdit(currentRole)) {
                    render()
                    Snackbar.make(requireView(), "Lecture seule (Observateur)", Snackbar.LENGTH_LONG).show()
                    return
                }

                val pos = viewHolder.bindingAdapterPosition
                val row = tasksAdapter.getRowAt(pos) as? TaskRow.Item
                if (row == null) {
                    render()
                    return
                }

                val task = row.task
                val removedIndex = allTasks.indexOfFirst { it.id == task.id }
                if (removedIndex == -1) {
                    render()
                    return
                }

                val removed = allTasks.removeAt(removedIndex)
                render()

                val sb = Snackbar.make(requireView(), "Tâche supprimée", Snackbar.LENGTH_LONG)
                sb.setAction("Annuler") {
                    allTasks.add(removedIndex.coerceAtMost(allTasks.size), removed)
                    render()
                }
                sb.addCallback(object : Snackbar.Callback() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        if (event != DISMISS_EVENT_ACTION) {
                            deleteTaskOnServer(task.id)
                        }
                    }
                })
                sb.show()
            }
        })

        swipeHelper = helper
        helper.attachToRecyclerView(rvTasks)
    }

    private fun deleteTaskOnServer(taskId: String) {
        if (!canEdit(currentRole)) {
            Snackbar.make(requireView(), "Lecture seule (Observateur)", Snackbar.LENGTH_LONG).show()
            return
        }

        val token = SessionManager.getToken(requireContext()).orEmpty()
        if (token.isBlank()) return

        ApiClient.api.deleteTask("Bearer $token", taskId)
            .enqueue(object : Callback<Unit> {
                override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                    if (response.isSuccessful) {
                        Snackbar.make(requireView(), "Supprimée", Snackbar.LENGTH_SHORT).show()
                    } else {
                        Snackbar.make(requireView(), "Erreur suppression (${response.code()})", Snackbar.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<Unit>, t: Throwable) {
                    Snackbar.make(requireView(), "Réseau: ${t.localizedMessage}", Snackbar.LENGTH_LONG).show()
                }
            })
    }

    // -------------------- Toggle done (role protected) --------------------

    @RequiresApi(Build.VERSION_CODES.O)
    private fun toggleDone(task: TaskItem, isDone: Boolean) {
        if (!canEdit(currentRole)) {
            Snackbar.make(requireView(), "Lecture seule (Observateur)", Snackbar.LENGTH_LONG).show()
            render()
            return
        }

        val token = SessionManager.getToken(requireContext()).orEmpty()
        if (token.isBlank()) {
            Snackbar.make(requireView(), "Non authentifié", Snackbar.LENGTH_LONG).show()
            render()
            return
        }

        val old = task

        if (!old.isDone()) {
            lastNonDoneStatusById[old.id] = old.statusCode
        }

        val newStatus = if (isDone) "done" else (lastNonDoneStatusById[old.id] ?: "open")
        val updatedLocal = old.copy(statusCode = newStatus)

        replaceLocalTask(updatedLocal)
        render()

        val body = UpdateTaskRequest(
            title = updatedLocal.title,
            description = updatedLocal.description,
            status = updatedLocal.statusCode,
            dueDate = updatedLocal.dueDate?.toString(),
            projectId = updatedLocal.projectId,
            priority = updatedLocal.priority
        )

        ApiClient.api.updateTask("Bearer $token", updatedLocal.id, body)
            .enqueue(object : Callback<TaskApiDto> {
                override fun onResponse(call: Call<TaskApiDto>, response: Response<TaskApiDto>) {
                    if (response.isSuccessful) {
                        Snackbar.make(requireView(), "Mis à jour", Snackbar.LENGTH_SHORT).show()
                    } else {
                        replaceLocalTask(old)
                        render()
                        Snackbar.make(requireView(), "Erreur update (${response.code()})", Snackbar.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<TaskApiDto>, t: Throwable) {
                    replaceLocalTask(old)
                    render()
                    Snackbar.make(requireView(), "Réseau: ${t.localizedMessage}", Snackbar.LENGTH_LONG).show()
                }
            })
    }

    private fun replaceLocalTask(updated: TaskItem) {
        val idx = allTasks.indexOfFirst { it.id == updated.id }
        if (idx != -1) allTasks[idx] = updated
    }

    // -------------------- Edit dialog (role protected) --------------------

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showEditTaskDialog(task: TaskItem) {
        if (!canEdit(currentRole)) {
            Snackbar.make(requireView(), "Lecture seule (Observateur)", Snackbar.LENGTH_LONG).show()
            return
        }

        val etTitle = TextInputEditText(requireContext()).apply {
            setText(task.title)
            hint = "Titre"
        }
        val etDesc = TextInputEditText(requireContext()).apply {
            setText(task.description ?: "")
            hint = "Description"
        }

        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
            addView(etTitle)
            addView(etDesc)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Modifier la tâche")
            .setView(container)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer") { _, _ ->
                val newTitle = etTitle.text?.toString()?.trim().orEmpty()
                val newDesc = etDesc.text?.toString()?.trim().orEmpty()

                if (newTitle.isBlank()) {
                    Snackbar.make(requireView(), "Titre obligatoire", Snackbar.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                val updated = task.copy(
                    title = newTitle,
                    description = newDesc.ifBlank { null }
                )

                updateTaskOnServer(updated, old = task)
            }
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateTaskOnServer(updated: TaskItem, old: TaskItem) {
        if (!canEdit(currentRole)) {
            Snackbar.make(requireView(), "Lecture seule (Observateur)", Snackbar.LENGTH_LONG).show()
            return
        }

        val token = SessionManager.getToken(requireContext()).orEmpty()
        if (token.isBlank()) return

        replaceLocalTask(updated)
        render()

        val body = UpdateTaskRequest(
            title = updated.title,
            description = updated.description,
            status = updated.statusCode,
            dueDate = updated.dueDate?.toString(),
            projectId = updated.projectId,
            priority = updated.priority
        )

        ApiClient.api.updateTask("Bearer $token", updated.id, body)
            .enqueue(object : Callback<TaskApiDto> {
                override fun onResponse(call: Call<TaskApiDto>, response: Response<TaskApiDto>) {
                    if (response.isSuccessful) {
                        Snackbar.make(requireView(), "Modifiée", Snackbar.LENGTH_SHORT).show()
                    } else {
                        replaceLocalTask(old)
                        render()
                        Snackbar.make(requireView(), "Erreur modification (${response.code()})", Snackbar.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<TaskApiDto>, t: Throwable) {
                    replaceLocalTask(old)
                    render()
                    Snackbar.make(requireView(), "Réseau: ${t.localizedMessage}", Snackbar.LENGTH_LONG).show()
                }
            })
    }

    // -------------------- Sort --------------------

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showSortDialog() {
        val items = arrayOf("Échéance (asc)", "Priorité (haute d’abord)", "Titre (A-Z)")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Trier par")
            .setItems(items) { _, which ->
                currentSort = when (which) {
                    0 -> TaskSort.DUE_ASC
                    1 -> TaskSort.PRIORITY_DESC
                    else -> TaskSort.TITLE_ASC
                }
                render()
            }
            .show()
    }

    // -------------------- Pulse animation --------------------

    private fun startPulse(v: View) {
        v.animate().cancel()
        v.scaleX = 1f
        v.scaleY = 1f

        v.animate()
            .scaleX(1.08f)
            .scaleY(1.08f)
            .setDuration(600)
            .withEndAction {
                v.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(600)
                    .withEndAction { startPulse(v) }
                    .start()
            }
            .start()
    }
}
