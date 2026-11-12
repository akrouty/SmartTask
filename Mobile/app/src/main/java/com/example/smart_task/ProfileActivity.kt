package com.example.smart_task

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.smart_task.R
import com.example.smart_task.SessionManager
import com.example.smart_task.network.ApiClient
import com.example.smart_task.network.dto.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ProfileActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etEmail: EditText
    private lateinit var etFullName: EditText
    private lateinit var etPhone: EditText
    private lateinit var btnSave: Button

    private lateinit var etCurrentPass: EditText
    private lateinit var etNewPass: EditText
    private lateinit var etConfirmPass: EditText
    private lateinit var btnChangePass: Button

    private lateinit var btnLogout: Button
    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        etUsername = findViewById(R.id.etUsername)
        etEmail = findViewById(R.id.etEmail)
        etFullName = findViewById(R.id.etFullName)
        etPhone = findViewById(R.id.etPhone)
        btnSave = findViewById(R.id.btnSaveProfile)

        etCurrentPass = findViewById(R.id.etCurrentPassword)
        etNewPass = findViewById(R.id.etNewPassword)
        etConfirmPass = findViewById(R.id.etConfirmPassword)
        btnChangePass = findViewById(R.id.btnChangePassword)

        btnLogout = findViewById(R.id.btnLogout)
        progress = findViewById(R.id.profileProgress)

        loadMe()

        btnSave.setOnClickListener { updateMe() }
        btnChangePass.setOnClickListener { changePassword() }
        btnLogout.setOnClickListener { logout() }
    }

    private fun authHeader(): String? {
        val token = SessionManager.getToken(this)
        return if (token.isNullOrBlank()) null else "Bearer $token"
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
        btnSave.isEnabled = !loading
        btnChangePass.isEnabled = !loading
    }

    private fun loadMe() {
        val auth = authHeader() ?: return
        setLoading(true)

        ApiClient.api.getMe(auth).enqueue(object : Callback<UserMeDto> {
            override fun onResponse(call: Call<UserMeDto>, response: Response<UserMeDto>) {
                setLoading(false)
                if (!response.isSuccessful) {
                    toast("Erreur chargement profil: ${response.code()}")
                    return
                }
                val me = response.body() ?: return
                etUsername.setText(me.username.orEmpty())
                etEmail.setText(me.email.orEmpty())
                etFullName.setText(me.fullName.orEmpty())
                etPhone.setText(me.phone.orEmpty())
            }

            override fun onFailure(call: Call<UserMeDto>, t: Throwable) {
                setLoading(false)
                toast("Erreur réseau: ${t.message}")
            }
        })
    }

    private fun updateMe() {
        val auth = authHeader() ?: return

        val body = UpdateMeRequest(
            username = etUsername.text.toString().trim().ifBlank { null },
            email = etEmail.text.toString().trim().ifBlank { null },
            fullName = etFullName.text.toString().trim().ifBlank { null },
            phone = etPhone.text.toString().trim().ifBlank { null }
        )

        setLoading(true)
        ApiClient.api.updateMe(auth, body).enqueue(object : Callback<UserMeDto> {
            override fun onResponse(call: Call<UserMeDto>, response: Response<UserMeDto>) {
                setLoading(false)
                if (!response.isSuccessful) {
                    toast("Erreur update: ${response.code()}")
                    return
                }
                toast("Profil mis à jour")
                // Optionnel: recharger pour refléter le backend
                loadMe()
            }

            override fun onFailure(call: Call<UserMeDto>, t: Throwable) {
                setLoading(false)
                toast("Erreur réseau: ${t.message}")
            }
        })
    }

    private fun changePassword() {
        val auth = authHeader() ?: return

        val current = etCurrentPass.text.toString()
        val newPass = etNewPass.text.toString()
        val confirm = etConfirmPass.text.toString()

        if (current.isBlank() || newPass.isBlank() || confirm.isBlank()) {
            toast("Veuillez remplir tous les champs du mot de passe")
            return
        }
        if (newPass.length < 6) {
            toast("Mot de passe trop court (min 6)")
            return
        }
        if (newPass != confirm) {
            toast("Confirmation incorrecte")
            return
        }

        setLoading(true)
        ApiClient.api.changePassword(auth, ChangePasswordRequest(current, newPass))
            .enqueue(object : Callback<Unit> {
                override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                    setLoading(false)
                    if (!response.isSuccessful) {
                        toast("Erreur changement mdp: ${response.code()}")
                        return
                    }
                    toast("Mot de passe modifié")
                    etCurrentPass.text?.clear()
                    etNewPass.text?.clear()
                    etConfirmPass.text?.clear()
                }

                override fun onFailure(call: Call<Unit>, t: Throwable) {
                    setLoading(false)
                    toast("Erreur réseau: ${t.message}")
                }
            })
    }

    private fun logout() {
        SessionManager.clear(this)
        val i = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(i)
        toast("Déconnexion")
        finish()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
