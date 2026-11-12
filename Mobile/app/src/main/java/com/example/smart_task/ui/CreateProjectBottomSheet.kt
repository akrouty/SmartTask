package com.example.smart_task.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.smart_task.R
import com.example.smart_task.SessionManager
import com.example.smart_task.network.ApiClient
import com.example.smart_task.network.dto.CreateProjectRequest
import com.example.smart_task.network.dto.ProjectResponseDto
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class CreateProjectBottomSheet(
    private val onProjectCreated: () -> Unit
) : BottomSheetDialogFragment() {

    override fun getTheme(): Int = R.style.SmartTask_BottomSheetDialog

    private lateinit var tilName: TextInputLayout
    private lateinit var etName: TextInputEditText
    private lateinit var etDesc: TextInputEditText
    private lateinit var btnCreate: MaterialButton
    private lateinit var progress: CircularProgressIndicator

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottomsheet_create_project, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tilName = view.findViewById(R.id.tilName)
        etName = view.findViewById(R.id.etName)
        etDesc = view.findViewById(R.id.etDesc)
        btnCreate = view.findViewById(R.id.btnCreate)
        progress = view.findViewById(R.id.progress)

        btnCreate.setOnClickListener { submit(view) }
    }

    private fun submit(root: View) {
        val name = etName.text?.toString()?.trim().orEmpty()
        val desc = etDesc.text?.toString()?.trim().orEmpty()

        if (name.isBlank()) {
            tilName.error = "Nom obligatoire"
            return
        } else {
            tilName.error = null
        }

        val token = SessionManager.getToken(requireContext())
        if (token.isNullOrEmpty()) {
            Snackbar.make(root, "Non authentifié", Snackbar.LENGTH_LONG).show()
            return
        }

        setLoading(true)

        val body = CreateProjectRequest(name = name, description = desc.ifBlank { null })

        ApiClient.api.createProject("Bearer $token", body)
            .enqueue(object : Callback<ProjectResponseDto> {
                override fun onResponse(
                    call: Call<ProjectResponseDto>,
                    response: Response<ProjectResponseDto>
                ) {
                    setLoading(false)
                    if (response.isSuccessful) {
                        onProjectCreated()
                        dismiss()
                    } else {
                        Snackbar.make(root, "Erreur création (${response.code()})", Snackbar.LENGTH_LONG).show()
                    }
                }

                override fun onFailure(call: Call<ProjectResponseDto>, t: Throwable) {
                    setLoading(false)
                    Snackbar.make(root, "Réseau: ${t.localizedMessage}", Snackbar.LENGTH_LONG).show()
                }
            })
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnCreate.isEnabled = !loading
    }
}