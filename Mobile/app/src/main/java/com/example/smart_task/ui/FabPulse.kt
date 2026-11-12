package com.example.smart_task.ui

import android.animation.ValueAnimator
import android.view.View
import com.google.android.material.floatingactionbutton.FloatingActionButton

fun startPulse(fab: FloatingActionButton) {
    fab.clearAnimation()

    val anim = ValueAnimator.ofFloat(1f, 1.08f, 1f).apply {
        duration = 900L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        addUpdateListener { va ->
            val v = va.animatedValue as Float
            fab.scaleX = v
            fab.scaleY = v
        }
    }
    anim.start()

    // optionnel : petite respiration d’alpha
    fab.animate().alpha(1f).setDuration(200).start()
}

fun bumpClick(view: View) {
    view.animate()
        .scaleX(0.92f).scaleY(0.92f)
        .setDuration(90)
        .withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }.start()
}
