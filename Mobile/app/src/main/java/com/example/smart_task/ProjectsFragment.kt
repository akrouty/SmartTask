package com.example.smart_task

import android.R.id.shareText
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_task.R
import com.example.smart_task.SessionManager
import com.example.smart_task.network.ApiClient
import com.example.smart_task.network.dto.InviteRequest
import com.example.smart_task.network.dto.InviteResponseDto
import com.example.smart_task.network.dto.ProjectResponseDto
import com.example.smart_task.network.dto.UpdateProjectRequest
import com.example.smart_task.ui.CreateProjectBottomSheet
import com.example.smart_task.ui.ProjectsAdapter
import com.example.smart_task.ui.bumpClick
import com.example.smart_task.ui.mappers.toCardUi
import com.example.smart_task.ui.model.ProjectBucket
import com.example.smart_task.ui.model.ProjectCardUi
import com.example.smart_task.ui.startPulse
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.OffsetDateTime

interface ProjectActionsListener {
    fun onProjectMenuClicked(anchor: View, project: ProjectCardUi)
    fun onProjectClicked(project: ProjectCardUi)
}

class ProjectsFragment : Fragment(R.layout.fragment_projects) {

    private lateinit var rv: RecyclerView
    private lateinit var adapter: ProjectsAdapter

    private lateinit var tvProjectsCount: TextView
    private lateinit var chipGroupProject: ChipGroup
    private lateinit var etSearchProject: EditText
    private lateinit var btnSeeMore: View
    private lateinit var btnSort: MaterialButton
    private lateinit var fabAdd: FloatingActionButton

    private var allProjects: List<ProjectCardUi> = emptyList()
    private var filteredProjects: List<ProjectCardUi> = emptyList()

    private val createdAtEpochById = mutableMapOf<String, Long>()

    private val pageSize = 7
    private var currentPage = 1
    private var currentQuery = ""
    private val ROLE_MEMBER = 1
    private val ROLE_OBSERVER = 2

    private var currentBucket = ProjectBucket.ALL

    enum class ProjectSort { CREATED_DESC, CREATED_ASC, NAME_ASC, PROGRESS_DESC }
    private var currentSort = ProjectSort.CREATED_DESC

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rv = view.findViewById(R.id.rvProjects)
        tvProjectsCount = view.findViewById(R.id.tvProjectsCount)
        chipGroupProject = view.findViewById(R.id.chipGroupProject)
        etSearchProject = view.findViewById(R.id.etSearchProject)
        btnSeeMore = view.findViewById(R.id.btnSeeMore)
        btnSort = view.findViewById(R.id.btnSort)
        fabAdd = view.findViewById(R.id.fabAdd)

        adapter = ProjectsAdapter(object : ProjectActionsListener {
            override fun onProjectClicked(project: ProjectCardUi) {
                // exemple: ouvrir détail / tasks du projet
            }

            override fun onProjectMenuClicked(anchor: View, project: ProjectCardUi) {
                showProjectPopupMenu(anchor, project)
            }
        })
        rv.adapter = adapter
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        setupInsetsForFab(view)
        setupListeners(view)

        loadProjects(view)



    }
    private fun showProjectPopupMenu(anchor: View, project: ProjectCardUi) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, "Modifier")
        popup.menu.add(0, 2, 1, "Supprimer")
        popup.menu.add(0, 3, 2, "Inviter")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showEditProjectDialog(project)
                2 -> confirmDeleteProject(project)
                3 -> showInviteDialog(project.id)
            }
            true
        }
        popup.show()
    }

    private fun confirmDeleteProject(project: ProjectCardUi) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Supprimer le projet")
            .setMessage("Supprimer “${project.name}” ? Cette action est irréversible.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer") { _, _ ->
                deleteProject(project.id)
            }
            .show()
    }

    private fun deleteProject(projectId: String) {
        val token = SessionManager.getToken(requireContext()) ?: return
        ApiClient.api.deleteProject("Bearer $token", projectId)
            .enqueue(object : Callback<Unit> {
                override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                    if (response.isSuccessful) {
                        // retirer localement + refresh UI
                        allProjects = allProjects.filterNot { it.id == projectId }
                        currentPage = 1
                        applyFiltersAndRender()
                        Snackbar.make(requireView(), "Projet supprimé", Snackbar.LENGTH_SHORT).show()
                    } else {
                        Snackbar.make(requireView(), "Erreur suppression (${response.code()})", Snackbar.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<Unit>, t: Throwable) {
                    Snackbar.make(requireView(), "Réseau: ${t.localizedMessage}", Snackbar.LENGTH_LONG).show()
                }
            })
    }
    private fun showInviteDialog(projectId: String) {
        val ctx = requireContext()

        val input = com.google.android.material.textfield.TextInputEditText(ctx).apply {
            hint = "Email"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        // default role
        var selectedRole = ROLE_MEMBER
        val roleLabels = arrayOf("Membre", "Observateur")

        MaterialAlertDialogBuilder(ctx)
            .setTitle("Inviter un membre")
            .setView(input)
            .setSingleChoiceItems(roleLabels, 0) { _, which ->
                selectedRole = if (which == 0) ROLE_MEMBER else ROLE_OBSERVER
            }
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Envoyer", null) // on override pour valider avant dismiss
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val email = input.text?.toString()?.trim().orEmpty()
                        if (email.isBlank()) {
                            input.error = "Email obligatoire"
                            return@setOnClickListener
                        }
                        // (optionnel) check rapide email
                        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                            input.error = "Email invalide"
                            return@setOnClickListener
                        }

                        inviteMember(projectId = projectId, email = email, role = selectedRole)
                        dialog.dismiss()
                    }
                }
                dialog.show()
            }
    }


    private fun inviteMember(projectId: String, email: String, role: Int) {
        val root = view ?: return
        val token = SessionManager.getToken(requireContext())

        if (token.isNullOrBlank()) {
            Snackbar.make(root, "Non authentifié – veuillez vous reconnecter", Snackbar.LENGTH_LONG).show()
            return
        }

        val body = InviteRequest(email = email, role = role)

        ApiClient.api.inviteMember("Bearer $token", projectId, body)
            .enqueue(object : Callback<InviteResponseDto> {

                override fun onResponse(
                    call: Call<InviteResponseDto>,
                    response: Response<InviteResponseDto>
                ) {
                    if (!response.isSuccessful) {
                        val msg = when (response.code()) {
                            403 -> "Accès refusé : admin requis"
                            404 -> "Projet introuvable"
                            else -> "Erreur invitation (${response.code()})"
                        }
                        Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show()
                        return
                    }

                    val res = response.body()
                    if (res == null || res.acceptUrl.isBlank()) {
                        Snackbar.make(root, "Invitation créée, mais lien non reçu", Snackbar.LENGTH_LONG).show()
                        return
                    }

                    showInviteSuccessDialog(res.acceptUrl, res.email, res.expiresAt)
                }

                override fun onFailure(call: Call<InviteResponseDto>, t: Throwable) {
                    Snackbar.make(root, "Erreur réseau: ${t.localizedMessage}", Snackbar.LENGTH_LONG).show()
                }
            })
    }

    private fun showInviteSuccessDialog(link: String, email: String, expiresAt: String) {
        val ctx = requireContext()

        MaterialAlertDialogBuilder(ctx)
            .setTitle("Invitation créée")
            .setMessage(
                "Invitation pour : $email\n" +
                        "Expire le : ${expiresAt.take(10)}\n\n" +
                        "Lien :\n$link"
            )
            .setNegativeButton("Fermer", null)
            .setNeutralButton("Copier") { _, _ -> copyToClipboard(link) }
            .setPositiveButton("Partager") { _, _ -> shareText("Invitation SmartTask", link) }
            .show()
    }

    private fun copyToClipboard(text: String) {
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("invite_link", text))
        Snackbar.make(requireView(), "Lien copié", Snackbar.LENGTH_SHORT).show()
    }

    private fun shareText(title: String, text: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, title)
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        startActivity(android.content.Intent.createChooser(intent, "Partager via"))
    }








    private fun setupListeners(root: View) {
        btnSeeMore.setOnClickListener { onSeeMore() }
        btnSort.setOnClickListener { showSortDialog() }
         startPulse(fabAdd)
        fabAdd.setOnClickListener {
            bumpClick(it)
            CreateProjectBottomSheet(
                onProjectCreated = { loadProjects(requireView()) }
            ).show(parentFragmentManager, "CreateProjectBottomSheet")
        }

        chipGroupProject.setOnCheckedChangeListener { _, checkedId ->
            currentBucket = when (checkedId) {
                R.id.chipDone -> ProjectBucket.DONE
                R.id.chipLate -> ProjectBucket.LATE
                R.id.chipInProgress -> ProjectBucket.IN_PROGRESS
                else -> ProjectBucket.ALL
            }
            currentPage = 1
            applyFiltersAndRender()
        }

        etSearchProject.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString().orEmpty()
                currentPage = 1
                applyFiltersAndRender()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun loadProjects(root: View) {
        val token = SessionManager.getToken(requireContext())
        if (token.isNullOrEmpty()) {
            Snackbar.make(root, "Non authentifié – veuillez vous reconnecter", Snackbar.LENGTH_LONG).show()
            return
        }

        ApiClient.api.getProjects("Bearer $token")
            .enqueue(object : Callback<List<ProjectResponseDto>> {
                override fun onResponse(
                    call: Call<List<ProjectResponseDto>>,
                    response: Response<List<ProjectResponseDto>>
                ) {
                    if (!response.isSuccessful || response.body() == null) {
                        Snackbar.make(root, "Erreur serveur (${response.code()})", Snackbar.LENGTH_SHORT).show()
                        return
                    }

                    val dtoList = response.body()!!

                    createdAtEpochById.clear()
                    dtoList.forEach { dto ->
                        val epoch = runCatching {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                OffsetDateTime.parse(dto.createdAt).toInstant().toEpochMilli()
                            } else 0L
                        }.getOrDefault(0L)
                        createdAtEpochById[dto.id] = epoch
                    }

                    allProjects = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        dtoList.map { it.toCardUi() }
                    } else {
                        dtoList.map {
                            ProjectCardUi(
                                id = it.id,
                                name = it.name,
                                description = it.description ?: "",
                                createdLabel = "Créé récemment",
                                membersLabel = "…",
                                completedTasks = 0,
                                totalTasks = it.tasks.size,
                                progressPercent = 0,
                                statusBucket = ProjectBucket.ALL
                            )
                        }
                    }

                    currentPage = 1
                    applyFiltersAndRender()
                }

                override fun onFailure(call: Call<List<ProjectResponseDto>>, t: Throwable) {
                    Snackbar.make(root, "Erreur réseau : ${t.localizedMessage}", Snackbar.LENGTH_LONG).show()
                }
            })
    }

    private fun applyFiltersAndRender() {
        val q = currentQuery.trim()

        val base = allProjects
            .asSequence()
            .filter { p -> currentBucket == ProjectBucket.ALL || p.statusBucket == currentBucket }
            .filter { p ->
                q.isBlank() ||
                        p.name.contains(q, true) ||
                        p.description.contains(q, true)
            }
            .toList()

        filteredProjects = when (currentSort) {
            ProjectSort.CREATED_DESC -> base.sortedByDescending { createdAtEpochById[it.id] ?: 0L }
            ProjectSort.CREATED_ASC -> base.sortedBy { createdAtEpochById[it.id] ?: 0L }
            ProjectSort.NAME_ASC -> base.sortedBy { it.name.lowercase() }
            ProjectSort.PROGRESS_DESC -> base.sortedByDescending { it.progressPercent }
        }

        tvProjectsCount.text = "Projets (${filteredProjects.size})"

        val toIndex = (currentPage * pageSize).coerceAtMost(filteredProjects.size)
        adapter.submitList(filteredProjects.take(toIndex))

        btnSeeMore.visibility =
            if (filteredProjects.size > pageSize && toIndex < filteredProjects.size) View.VISIBLE else View.GONE
    }

    private fun showSortDialog() {
        val items = arrayOf("Date (récent)", "Date (ancien)", "Nom (A-Z)", "Progression")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Trier par")
            .setItems(items) { _, which ->
                currentSort = when (which) {
                    0 -> ProjectSort.CREATED_DESC
                    1 -> ProjectSort.CREATED_ASC
                    2 -> ProjectSort.NAME_ASC
                    else -> ProjectSort.PROGRESS_DESC
                }
                currentPage = 1
                applyFiltersAndRender()
            }
            .show()
    }

    private fun showEditProjectDialog(project: ProjectCardUi) {
        val v = layoutInflater.inflate(R.layout.dialog_edit_project, null)
        val etName = v.findViewById<EditText>(R.id.etProjectName)
        val etDesc = v.findViewById<EditText>(R.id.etProjectDesc)

        etName.setText(project.name)
        etDesc.setText(project.description)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Modifier le projet")
            .setView(v)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer") { _, _ ->
                val name = etName.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                updateProject(project.id, name, desc)
            }
            .show()
    }

    private fun updateProject(id: String, name: String, desc: String) {
        val root = view ?: return
        val token = SessionManager.getToken(requireContext())

        if (token.isNullOrBlank()) {
            Snackbar.make(root, "Non authentifié", Snackbar.LENGTH_LONG).show()
            return
        }

        val body = UpdateProjectRequest(
            name = name,
            description = desc.ifBlank { null }
        )

        ApiClient.api.updateProject("Bearer $token", id, body)
            .enqueue(object : Callback<Unit> {
                override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                    if (response.isSuccessful) {
                        // ✅ 200/204 = SUCCESS
                        Snackbar.make(root, "Projet mis à jour", Snackbar.LENGTH_SHORT).show()
                        loadProjects(root)

                    } else {
                        Snackbar.make(root, "Erreur update (${response.code()})", Snackbar.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<Unit>, t: Throwable) {
                    Snackbar.make(root, "Erreur réseau: ${t.localizedMessage}", Snackbar.LENGTH_LONG).show()
                }
            })
    }



    private fun onSeeMore() {
        currentPage += 1
        applyFiltersAndRender()
    }

    private fun setupInsetsForFab(root: View) {
        val base = dp(16)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(sys.bottom, ime.bottom)

            val lp = fabAdd.layoutParams
            if (lp is ViewGroup.MarginLayoutParams) {
                lp.bottomMargin = base + bottomInset
                lp.marginEnd = base
                fabAdd.layoutParams = lp
            }
            insets
        }
    }

    private fun dp(v: Int): Int = (v * requireContext().resources.displayMetrics.density).toInt()

    private fun vibrateSoft() {
        val vib = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") vib.vibrate(25)
        }
    }

    private fun animateFabHintSafe() {
        runCatching {
            fabAdd.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.fab_pulse))
        }
    }

    private fun animateFabClickSafe() {
        runCatching {
            fabAdd.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.fab_bump))
        }
    }
}
