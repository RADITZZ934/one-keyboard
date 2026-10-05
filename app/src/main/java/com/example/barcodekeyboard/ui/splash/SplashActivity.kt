package com.example.barcodekeyboard.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.barcodekeyboard.R
import com.example.barcodekeyboard.ui.settings.SettingsActivity

/**
 * Splash Screen Activity displaying One-Keyboard-Splash.png
 * before transitioning to SettingsActivity.
 */
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var hasNavigated = false

    private val navigateRunnable = Runnable {
        navigateToMain()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Make status bar icons dark on light background if supported
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        val ivSplash: ImageView = findViewById(R.id.ivSplash)
        val fadeIn = AlphaAnimation(0.2f, 1.0f).apply {
            duration = 400
            fillAfter = true
        }
        ivSplash.startAnimation(fadeIn)

        // Tap to skip
        ivSplash.setOnClickListener {
            navigateToMain()
        }

        // Automatic transition after 1.5 seconds
        handler.postDelayed(navigateRunnable, 1500)
    }

    private fun navigateToMain() {
        if (hasNavigated) return
        hasNavigated = true
        handler.removeCallbacks(navigateRunnable)

        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(navigateRunnable)
    }
}
