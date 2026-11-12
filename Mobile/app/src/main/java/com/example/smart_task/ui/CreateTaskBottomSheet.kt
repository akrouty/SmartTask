package com.example.smart_task.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import com.example.smart_task.R
import com.example.smart_task.SessionManager
import com.example.smart_task.network.ApiClient
import com.example.smart_task.network.dto.CreateTaskRequest
import com.example.smart_task.network.dto.TaskApiDto
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CreateTaskBottomSheet : BottomSheetDialogFragment() {

    private lateinit var etTitle: TextInputEditText
    private lateinit var etDesc: TextInputEditText
    private lateinit var etDue: TextInputEditText

    private lateinit var tilDue: TextInputLayout
    private lateinit var actStatus: MaterialAutoCompleteTextView
    private lateinit var actPriority: MaterialAutoCompleteTextView

    private lateinit var btnCreate: View

    private var projectId: String = ""
    private var onCreated: ((TaskApiDto) -> Unit)? = null

    companion object {
        fun newInstance(projectId: String, onCreated: (TaskApiDto) -> Unit): CreateTaskBottomSheet {
            return CreateTaskBottomSheet().apply {
                this.projectId = projectId
                this.onCreated = onCreated
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.bottomsheet_create_task, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        etTitle = view.findViewById(R.id.etTitle)
        etDesc = view.findViewById(R.id.etDesc)
        etDue = view.findViewById(R.id.etDue)

        tilDue = view.findViewById(R.id.tilDue)
        actStatus = view.findViewById(R.id.actStatus)
        actPriority = view.findViewById(R.id.actPriority)

        // ✅ IMPORTANT: ton XML a btnCreateTask (pas btnCreate)
        btnCreate = view.findViewById(R.id.btnCreateTask)

        setupStatusDropdown()
        setupPriorityDropdown()
        setupDueDatePicker()

        btnCreate.setOnClickListener { createTask() }

        // (optionnel) clear erreurs quand user tape
        etTitle.doAfterTextChanged { view.findViewById<TextInputLayout>(R.id.tilTitle).error = null }
        etDue.doAfterTextChanged { tilDue.error = null }
    }

    private fun setupStatusDropdown() {
        val statuses = listOf(
            "open",
            "in-progression",
            "done"
        )
        actStatus.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, statuses))
        actStatus.setText("open", false)
    }

    private fun setupPriorityDropdown() {
        // Backend: 0=haute, 1=moyenne, 2=basse (si tu utilises 3, garde-le)
        val priorities = listOf(
            "0 - Haute",
            "1 - Moyenne",
            "2 - Basse"
        )
        actPriority.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, priorities))
        actPriority.setText("1 - Moyenne", false)
    }

    private fun setupDueDatePicker() {
        // Ouvre le date picker quand on clique sur le champ OU l’icône calendrier
        val openPicker = {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Choisir une échéance")
                .build()

            picker.addOnPositiveButtonClickListener { millis ->
                etDue.setText(formatMillisToYyyyMmDd(millis))
            }

            picker.show(parentFragmentManager, "DUE_DATE_PICKER")
        }

        etDue.setOnClickListener { openPicker() }
        tilDue.setEndIconOnClickListener { openPicker() }
    }

    private fun createTask() {
        val root = view ?: return
        val token = SessionManager.getToken(requireContext()).orEmpty()

        if (token.isBlank()) {
            Snackbar.make(root, "Non authentifié", Snackbar.LENGTH_LONG).show()
            return
        }

        val title = etTitle.text?.toString()?.trim().orEmpty()
        if (title.isBlank()) {
            view?.findViewById<TextInputLayout>(R.id.tilTitle)?.error = "Titre obligatoire"
            return
        }

        val desc = etDesc.text?.toString()?.trim().orEmpty().ifBlank { null }

        val dueText = etDue.text?.toString()?.trim().orEmpty()
        val due = dueText.ifBlank { null }

        // Validation simple (évite erreurs backend)
        if (due != null && !Regex("""\d{4}-\d{2}-\d{2}""").matches(due)) {
            tilDue.error = "Format attendu : yyyy-MM-dd"
            return
        }

        val status = actStatus.text?.toString()?.trim().orEmpty().ifBlank { "open" }

        // "1 - Moyenne" -> 1
        val prText = actPriority.text?.toString().orEmpty()
        val priority = prText.takeWhile { it.isDigit() }.toIntOrNull() ?: 1

        val body = CreateTaskRequest(
            title = title,
            description = desc,
            status = status,
            dueDate = due,
            projectId = projectId,
            priority = priority
        )

        ApiClient.api.createTask("Bearer $token", body)
            .enqueue(object : Callback<TaskApiDto> {
                override fun onResponse(call: Call<TaskApiDto>, response: Response<TaskApiDto>) {
                    if (!isAdded) return

                    val root = view ?: return

                    if (response.isSuccessful) {
                        val created = response.body()
                        if (created != null) {
                            // ✅ protège l'appel UI
                            runCatching { onCreated?.invoke(created) }
                                .onFailure {
                                    Snackbar.make(root, "Tâche créée, mais erreur d’affichage", Snackbar.LENGTH_LONG).show()
                                }

                            // ✅ évite IllegalStateException (state déjà sauvegardé)
                            dismissAllowingStateLoss()
                        } else {
                            Snackbar.make(root, "Tâche créée, mais réponse vide", Snackbar.LENGTH_LONG).show()
                            dismissAllowingStateLoss()
                        }
                    } else {
                        Snackbar.make(root, "Erreur création (${response.code()})", Snackbar.LENGTH_LONG).show()
                    }

                }



                override fun onFailure(call: Call<TaskApiDto>, t: Throwable) {
                    Snackbar.make(root, "Réseau: ${t.localizedMessage}", Snackbar.LENGTH_LONG).show()
                }
            })
    }

    private fun formatMillisToYyyyMmDd(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }
}
