package com.example.smart_task

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.core.view.isVisible
import com.example.smart_task.network.ApiClient
import com.example.smart_task.network.LoginRequest
import com.example.smart_task.network.LoginResponse
import com.example.smart_task.network.RegisterRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response



@Suppress("DEPRECATION")
class LoginActivity : AppCompatActivity() {

    private lateinit var header: FrameLayout
    private lateinit var btnTabLogin: MaterialButton
    private lateinit var btnTabSignup: MaterialButton
    private lateinit var groupLogin: View
    private lateinit var groupSignup: View

    // champs connexion
    private lateinit var tilEmail: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var tilPass: TextInputLayout
    private lateinit var etPass: TextInputEditText
    private lateinit var btnLogin: MaterialButton

    // bouton inscription (placeholder)
    private lateinit var tilNameSignup: TextInputLayout
    private lateinit var etNameSignup: TextInputEditText
    private lateinit var tilEmailSignup: TextInputLayout
    private lateinit var etEmailSignup: TextInputEditText
    private lateinit var tilPassSignup: TextInputLayout
    private lateinit var etPassSignup: TextInputEditText
    private lateinit var tilConfirmSignup: TextInputLayout
    private lateinit var etConfirmSignup: TextInputEditText
    private lateinit var btnDoSignup: MaterialButton

    // 👉 always-valid root to anchor Snackbars/animations
    private val rootView: View get() = findViewById(android.R.id.content)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // refs UI
        header       = findViewById(R.id.header)
        btnTabLogin  = findViewById(R.id.btnTabLogin)
        btnTabSignup = findViewById(R.id.btnTabSignup)
        groupLogin   = findViewById(R.id.groupLogin)
        groupSignup  = findViewById(R.id.groupSignup)

        tilEmail = findViewById(R.id.tilEmailLogin)
        etEmail  = findViewById(R.id.etEmailLogin)
        tilPass  = findViewById(R.id.tilPassLogin)
        etPass   = findViewById(R.id.etPassLogin)
        btnLogin = findViewById(R.id.btnLogin)


        // signup tab
        tilNameSignup     = findViewById(R.id.tilNameSignup)
        etNameSignup      = findViewById(R.id.etNameSignup)
        tilEmailSignup    = findViewById(R.id.tilEmailSignup)
        etEmailSignup     = findViewById(R.id.etEmailSignup)
        tilPassSignup     = findViewById(R.id.tilPassSignup)
        etPassSignup      = findViewById(R.id.etPassSignup)
        tilConfirmSignup  = findViewById(R.id.tilConfirmSignup)
        etConfirmSignup   = findViewById(R.id.etConfirmSignup)
        btnDoSignup       = findViewById(R.id.btnSignup)



        btnDoSignup = findViewById(R.id.btnSignup)

        // 1) header animation
        (header.background as? AnimationDrawable)?.apply {
            setEnterFadeDuration(600)
            setExitFadeDuration(600)
            start()
        }

        // 2) onglet par défaut = Connexion
        selectTab(isLogin = true, animate = false)

        btnTabLogin.setOnClickListener  { selectTab(isLogin = true,  animate = true) }
        btnTabSignup.setOnClickListener { selectTab(isLogin = false, animate = true) }

        // 3) logique bouton "Se connecter"
        btnLogin.setOnClickListener {
            tilEmail.error = null
            tilPass.error  = null

            val email = etEmail.text?.toString()?.trim().orEmpty()
            val pass  = etPass.text?.toString()?.trim().orEmpty()

            var ok = true
            if (email.isEmpty()) { tilEmail.error = "Email requis"; ok = false }
            if (pass.isEmpty())  { tilPass.error  = "Mot de passe requis"; ok = false }

            if (!ok) {
                shake(rootView)
                return@setOnClickListener
            }
            btnLogin.isEnabled = false

            // Préparer le body pour l'API
            val body = LoginRequest(email = email, password = pass)


            // Appel Retrofit
            ApiClient.api.login(body).enqueue(object : Callback<LoginResponse> {
                override fun onResponse(
                    call: Call<LoginResponse>,
                    response: Response<LoginResponse>
                ) {
                    btnLogin.isEnabled = true

                    if (response.isSuccessful && response.body() != null) {
                        val data = response.body()!!

                        // session classique
                        SessionManager.saveSession(
                            this@LoginActivity,
                            data.token,
                            data.user.id
                        )


                        val fullNameFromApi = data.user.fullName          // ou data.user.name
                        val emailFromApi    = data.user.email

                        // on choisit dans cet ordre : fullName > userName > email avant @
                        val displayName =
                            fullNameFromApi?.takeIf { it.isNotBlank() }
                                ?: fullNameFromApi?.takeIf { it.isNotBlank() }
                                ?: emailFromApi.substringBefore("@")

                        // 🔹 on sauvegarde ce "beau" nom
                        SessionManager.saveUserName(this@LoginActivity, displayName)
                        SessionManager.saveSession(
                            this@LoginActivity,
                            data.token,
                            data.user.id
                        )

                        Snackbar.make(rootView, "Bienvenue 👋", Snackbar.LENGTH_SHORT).show()

                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))

                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                        finish()
                    }
                    else if (response.code() == 401) {
                        Snackbar.make(rootView, "Identifiants invalides", Snackbar.LENGTH_SHORT).show()
                        shake(rootView)
                        shake(findViewById(R.id.root))
                    } else {
                        Snackbar.make(rootView, "Erreur serveur (${response.code()})", Snackbar.LENGTH_SHORT).show()

                    }
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    btnLogin.isEnabled = true
                    Snackbar.make(rootView, "Erreur réseau : ${t.localizedMessage}", Snackbar.LENGTH_SHORT).show()

                }
            })
        }

        // 4) bouton "S'inscrire" (placeholder)
        btnDoSignup.setOnClickListener {
            performSignup()
        }
    }
    private fun performSignup() {
        // reset erreurs
        tilNameSignup.error = null
        tilEmailSignup.error = null
        tilPassSignup.error = null
        tilConfirmSignup.error = null

        val name     = etNameSignup.text?.toString()?.trim().orEmpty()
        val email    = etEmailSignup.text?.toString()?.trim().orEmpty()
        val pass     = etPassSignup.text?.toString()?.trim().orEmpty()
        val confirm  = etConfirmSignup.text?.toString()?.trim().orEmpty()

        var ok = true

        if (name.isEmpty()) {
            tilNameSignup.error = "Nom requis"
            ok = false
        }
        if (email.isEmpty()) {
            tilEmailSignup.error = "Email requis"
            ok = false
        }
        if (pass.length < 6) {
            tilPassSignup.error = "Mot de passe trop court (min 6)"
            ok = false
        }
        if (confirm != pass) {
            tilConfirmSignup.error = "Les mots de passe ne correspondent pas"
            ok = false
        }

        if (!ok) {
            shake(rootView)
            return
        }

        btnDoSignup.isEnabled = false

        val body = RegisterRequest(
            fullName = name,
            email = email,
            password = pass
        )

        ApiClient.api.register(body).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(
                call: Call<LoginResponse>,
                response: Response<LoginResponse>
            ) {
                btnDoSignup.isEnabled = true

                if (response.isSuccessful && response.body() != null) {
                    // Succès inscription
                    Snackbar.make(rootView,
                        "Compte créé avec succès ✅", Snackbar.LENGTH_SHORT
                    )
                        .setBackgroundTint(color(R.color.st_blue))
                        .setTextColor(Color.WHITE)
                        .show()

                    // Option : pré-remplir le login et repasser sur l’onglet Connexion
                    etEmail.setText(email)
                    selectTab(isLogin = true, animate = true)

                } else if (response.code() == 400 || response.code() == 409) {
                    // par ex: email déjà utilisé
                    Snackbar.make(rootView,
                        "Impossible de créer le compte (email déjà utilisé ?)",
                        Snackbar.LENGTH_LONG
                    ).show()
                } else {
                    Snackbar.make(rootView,
                        "Erreur serveur (${response.code()})",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                btnDoSignup.isEnabled = true
                Snackbar.make(rootView,
                    "Erreur réseau : ${t.localizedMessage}",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        })
    }


    /* ---------- Onglets + transitions ---------- */

    private fun selectTab(isLogin: Boolean, animate: Boolean) {
        styleTab(
            active  = if (isLogin) btnTabLogin else btnTabSignup,
            inactive = if (isLogin) btnTabSignup else btnTabLogin,
            animate = animate
        )
        crossfadeGroups(showLogin = isLogin, animate = animate)
    }

    private fun styleTab(active: MaterialButton, inactive: MaterialButton, animate: Boolean) {
        active.setBackgroundColor(Color.WHITE)
        active.strokeWidth = resources.getDimensionPixelSize(R.dimen.st_tab_stroke)
        active.strokeColor = ColorStateList.valueOf(color(R.color.st_blue))
        active.setTextColor(color(R.color.st_blue))

        val inactiveBg = color(R.color.st_tab_inactive)
        inactive.setBackgroundColor(inactiveBg)
        inactive.strokeWidth = resources.getDimensionPixelSize(R.dimen.st_tab_stroke)
        inactive.strokeColor = ColorStateList.valueOf(inactiveBg)
        inactive.setTextColor(color(R.color.st_text_dark))

        if (animate) {
            active.animate().scaleX(1.06f).scaleY(1.06f).setDuration(160).start()
            inactive.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
        } else {
            active.scaleX = 1.06f; active.scaleY = 1.06f
            inactive.scaleX = 1f;   inactive.scaleY = 1f
        }
    }

    private fun crossfadeGroups(showLogin: Boolean, animate: Boolean) {
        val toShow = if (showLogin) groupLogin else groupSignup
        val toHide = if (showLogin) groupSignup else groupLogin

        if (!animate) {
            toHide.visibility = View.GONE
            toShow.visibility = View.VISIBLE
            toShow.alpha = 1f
            toShow.translationY = 0f
            return
        }

        if (toHide.isVisible) {
            toHide.animate()
                .alpha(0f).translationY(-16f).setDuration(180)
                .withEndAction { toHide.visibility = View.GONE }
                .start()
        }

        toShow.alpha = 0f
        toShow.translationY = 16f
        toShow.visibility = View.VISIBLE
        toShow.animate()
            .alpha(1f).translationY(0f)
            .setDuration(220)
            .setStartDelay(50)
            .start()
    }

    private fun shake(view: View) {
        view.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
    }

    private fun color(res: Int) = ContextCompat.getColor(this, res)
    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
