package com.example.smart_task

import android.content.Intent
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import android.view.View
import android.view.animation.OvershootInterpolator
import android.window.SplashScreen
import androidx.core.view.children
import kotlinx.coroutines.delay
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen


class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val token = SessionManager.getToken(this)
        if (!token.isNullOrBlank()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.logo)
        val title = findViewById<TextView>(R.id.title)
        val subtitle = findViewById<TextView>(R.id.subtitle)
        val features = findViewById<LinearLayout>(R.id.features)
        val btn = findViewById<MaterialButton>(R.id.btnCommencer)

        // load base anims
        val logoAnim     = AnimationUtils.loadAnimation(this, R.anim.scale_fade_in).apply { startOffset = 0L }
        val titleAnim    = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in).apply { startOffset = 50L }
        val subtitleAnim = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in).apply { startOffset = 150L }



        logo.startAnimation(logoAnim)
        title.startAnimation(titleAnim)
        subtitle.startAnimation(subtitleAnim)


        val baseStart = 450L      // when first pill begins
        val step = 120L           // delay between pills

        for (i in 0 until features.childCount) {
            val child = features.getChildAt(i)
            val pillAnim = AnimationUtils.loadAnimation(this, R.anim.pill_in).apply {
                startOffset = baseStart + i * step
            }
            child.startAnimation(pillAnim)
        }

        // button: start while pills are animating (e.g., after 2 pills)
        val btnStart = baseStart + 2 * step
        val btnAnim = AnimationUtils.loadAnimation(this, R.anim.fade_in_short).apply {
            startOffset = btnStart
        }
        btn.startAnimation(btnAnim)


        val pills: List<View> = (0 until features.childCount).map { features.getChildAt(it) }
        fun pulseOnce(v: View, onDone: () -> Unit) {
            v.animate()
                .scaleX(1.06f).scaleY(1.06f)
                .setDuration(340)

                .withEndAction {
                    v.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(140)
                        .withEndAction { onDone() }
                        .start()
                }
                .start()
        }
        fun popButton() {
            // ensure button is visible (if you fade it in elsewhere, keep that)
            btn.alpha = 0f
            btn.scaleX = 0.9f
            btn.scaleY = 0.9f
            btn.animate()
                .alpha(1f)
                .scaleX(1.06f).scaleY(1.06f)
                .setDuration(820)
                .setInterpolator(OvershootInterpolator(2f))
                .withEndAction {
                    // settle back slightly
                    btn.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
                .start()
        }
        fun playPillSequence(index: Int = 0) {
            if (index >= pills.size) { popButton(); return }  // all done → pop button
            pulseOnce(pills[index]) {
                features.postDelayed({ playPillSequence(index + 1) }, 600L)
            }
        }

        features.postDelayed({ playPillSequence(0) }, 600L)

        // click goes to login (no auto-redirect)
        btn.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
