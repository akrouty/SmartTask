package com.example.smart_task

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.smart_task.ProjectsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarMenuView

interface LoadingHost {
    fun showLoading()
    fun hideLoading()
}

class MainActivity : AppCompatActivity(), LoadingHost {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var loadingOverlay: View

    private var loadingStartMs: Long = 0L
    private val minLoadingMs = 850L

    // Petit délai pour laisser le loader apparaître avant le replace
    private val transitionDelayMs = 120L

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadingOverlay = findViewById(R.id.loadingOverlay)
        bottomNav = findViewById(R.id.bottomNav)

        // Enlever la bulle grise
        try { bottomNav.isItemActiveIndicatorEnabled = false } catch (_: Throwable) {}
        bottomNav.itemBackground = null
        bottomNav.setItemBackgroundResource(android.R.color.transparent)

        // Auto-hide du loader quand un fragment a créé sa vue (transition)
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    f: Fragment,
                    v: View,
                    savedInstanceState: Bundle?
                ) {
                    // On laisse passer 1 frame pour éviter un “flash”
                    v.post { hideLoading() }
                }
            },
            true
        )

        // Premier écran : Home
        if (savedInstanceState == null) {
            openFragment(HomeFragment(), animate = false)
            bottomNav.selectedItemId = R.id.nav_home
        }

        bottomNav.doOnLayout {
            val menuView = bottomNav.getChildAt(0) as NavigationBarMenuView

            fun indexOf(menuId: Int): Int =
                (0 until bottomNav.menu.size())
                    .firstOrNull { bottomNav.menu.getItem(it).itemId == menuId } ?: 0

            fun animateScale(selectedId: Int) {
                val selectedIndex = indexOf(selectedId)
                for (i in 0 until menuView.childCount) {
                    val v = menuView.getChildAt(i)
                    val target = if (i == selectedIndex) 1.18f else 1f
                    v.animate()
                        .scaleX(target)
                        .scaleY(target)
                        .setDuration(260)
                        .start()
                }
            }

            // scale initial sur Accueil
            animateScale(bottomNav.selectedItemId)

            bottomNav.setOnItemSelectedListener { item ->
                // Optionnel : éviter de recharger le même onglet
                if (item.itemId == bottomNav.selectedItemId) {
                    animateScale(item.itemId)
                    return@setOnItemSelectedListener true
                }

                val fragment: Fragment? = when (item.itemId) {
                    R.id.nav_home     -> HomeFragment()
                    R.id.nav_projects -> ProjectsFragment()
                    R.id.nav_tasks    -> TasksFragment()
                    R.id.nav_stats  -> StatsFragment()
                    else -> null
                }

                fragment?.let { openFragment(it, animate = true) }

                animateScale(item.itemId)
                true
            }
        }
    }

    override fun showLoading() {
        loadingStartMs = SystemClock.elapsedRealtime()
        loadingOverlay.visibility = View.VISIBLE
    }

    override fun hideLoading() {
        val elapsed = SystemClock.elapsedRealtime() - loadingStartMs
        val delay = (minLoadingMs - elapsed).coerceAtLeast(0L)

        loadingOverlay.postDelayed({
            loadingOverlay.visibility = View.GONE
        }, delay)
    }

    private fun openFragment(fragment: Fragment, animate: Boolean) {
        // Loader uniquement lors d’une navigation (pas au premier load)
        if (animate) showLoading()

        val delayMs = if (animate) transitionDelayMs else 0L

        loadingOverlay.postDelayed({
            val tx = supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)

            // ✅ Plus de slide. Si tu veux un petit fade discret :
            if (animate) {
                tx.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            }

            tx.replace(R.id.fragmentContainer, fragment)
            tx.commit()
        }, delayMs)
    }
}
