package com.example.barcodekeyboard.ui.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.barcodekeyboard.R
import com.example.barcodekeyboard.ui.home.HomeActivity

/**
 * Interactive 3-Step Setup Wizard for first-time installation:
 * Step 1: Enable One Keyboard in Android Settings
 * Step 2: Select One Keyboard as default input method
 * Step 3: Grant Camera permission for barcode scanner
 */
class SetupActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 1001

        /**
         * Checks if the onboarding setup is completely finished.
         */
        fun isSetupComplete(context: Context): Boolean {
            val isEnabled = isKeyboardEnabled(context)
            val isSelected = isKeyboardSelected(context)
            val hasCamera = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            return isEnabled && isSelected && hasCamera
        }

        private fun isKeyboardEnabled(context: Context): Boolean {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
            val enabledList = imm.enabledInputMethodList
            val myPackage = context.packageName
            return enabledList.any { it.packageName == myPackage }
        }

        private fun isKeyboardSelected(context: Context): Boolean {
            val currentIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            return currentIme?.contains(context.packageName) == true
        }
    }

    private lateinit var tvSetupProgressBadge: TextView

    // Step 1 views
    private lateinit var cardStep1: LinearLayout
    private lateinit var ivStep1NumberBg: ImageView
    private lateinit var tvStep1Number: TextView
    private lateinit var tvStep1StatusBadge: TextView
    private lateinit var btnStep1Enable: Button

    // Step 2 views
    private lateinit var cardStep2: LinearLayout
    private lateinit var ivStep2NumberBg: ImageView
    private lateinit var tvStep2Number: TextView
    private lateinit var tvStep2StatusBadge: TextView
    private lateinit var btnStep2Select: Button

    // Step 3 views
    private lateinit var cardStep3: LinearLayout
    private lateinit var ivStep3NumberBg: ImageView
    private lateinit var tvStep3Number: TextView
    private lateinit var tvStep3StatusBadge: TextView
    private lateinit var btnStep3Camera: Button

    // Banner & CTAs
    private lateinit var layoutAllDoneBanner: LinearLayout
    private lateinit var btnFinishSetup: Button
    private lateinit var tvSkipSetup: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        setupStatusBar()
        initViews()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshSetupStates()
    }

    private fun setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.bento_bg)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            if (!isNightMode) {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }

    private fun initViews() {
        tvSetupProgressBadge = findViewById(R.id.tvSetupProgressBadge)

        cardStep1 = findViewById(R.id.cardStep1)
        ivStep1NumberBg = findViewById(R.id.ivStep1NumberBg)
        tvStep1Number = findViewById(R.id.tvStep1Number)
        tvStep1StatusBadge = findViewById(R.id.tvStep1StatusBadge)
        btnStep1Enable = findViewById(R.id.btnStep1Enable)

        cardStep2 = findViewById(R.id.cardStep2)
        ivStep2NumberBg = findViewById(R.id.ivStep2NumberBg)
        tvStep2Number = findViewById(R.id.tvStep2Number)
        tvStep2StatusBadge = findViewById(R.id.tvStep2StatusBadge)
        btnStep2Select = findViewById(R.id.btnStep2Select)

        cardStep3 = findViewById(R.id.cardStep3)
        ivStep3NumberBg = findViewById(R.id.ivStep3NumberBg)
        tvStep3Number = findViewById(R.id.tvStep3Number)
        tvStep3StatusBadge = findViewById(R.id.tvStep3StatusBadge)
        btnStep3Camera = findViewById(R.id.btnStep3Camera)

        layoutAllDoneBanner = findViewById(R.id.layoutAllDoneBanner)
        btnFinishSetup = findViewById(R.id.btnFinishSetup)
        tvSkipSetup = findViewById(R.id.tvSkipSetup)
    }

    private fun setupListeners() {
        // Step 1: Enable in Android settings
        btnStep1Enable.setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            Toast.makeText(this, "Nyalakan sakelar 'One Keyboard'", Toast.LENGTH_LONG).show()
        }

        // Step 2: Show input method picker
        btnStep2Select.setOnClickListener {
            if (!isKeyboardEnabled(this)) {
                Toast.makeText(this, "Selesaikan Langkah 1 terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        }

        // Step 3: Request Camera permission
        btnStep3Camera.setOnClickListener {
            if (hasCameraPermission()) {
                Toast.makeText(this, "Izin kamera sudah aktif", Toast.LENGTH_SHORT).show()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CAMERA_PERMISSION
                )
            }
        }

        // Finish CTA
        btnFinishSetup.setOnClickListener {
            goToHome()
        }

        // Skip CTA
        tvSkipSetup.setOnClickListener {
            goToHome()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun refreshSetupStates() {
        val step1Done = isKeyboardEnabled(this)
        val step2Done = isKeyboardSelected(this)
        val step3Done = hasCameraPermission()

        var completedCount = 0
        if (step1Done) completedCount++
        if (step2Done) completedCount++
        if (step3Done) completedCount++

        tvSetupProgressBadge.text = "$completedCount / 3 Selesai"

        // Update Step 1 UI
        if (step1Done) {
            cardStep1.setBackgroundResource(R.drawable.bg_setup_card_done)
            ivStep1NumberBg.setImageResource(R.drawable.bg_step_circle_done)
            tvStep1Number.text = "✓"
            tvStep1Number.setTextColor(ContextCompat.getColor(this, R.color.bento_white))
            tvStep1StatusBadge.text = "✓ Aktif"
            tvStep1StatusBadge.setTextColor(ContextCompat.getColor(this, R.color.bento_blue))
            btnStep1Enable.text = "✓ Sudah Diaktifkan di Sistem"
            btnStep1Enable.setBackgroundResource(R.drawable.bg_button_done)
            btnStep1Enable.setTextColor(ContextCompat.getColor(this, R.color.bento_text_secondary))
        } else {
            cardStep1.setBackgroundResource(R.drawable.bg_setup_card_active)
            ivStep1NumberBg.setImageResource(R.drawable.bg_step_circle_blue)
            tvStep1Number.text = "1"
            tvStep1Number.setTextColor(ContextCompat.getColor(this, R.color.bento_white))
            tvStep1StatusBadge.text = "Belum"
            tvStep1StatusBadge.setTextColor(ContextCompat.getColor(this, R.color.bento_text_primary))
            btnStep1Enable.text = "1. Buka Pengaturan Keyboard"
            btnStep1Enable.setBackgroundResource(R.drawable.bg_button_blue)
            btnStep1Enable.setTextColor(ContextCompat.getColor(this, R.color.bento_white))
        }

        // Update Step 2 UI
        if (step2Done) {
            cardStep2.setBackgroundResource(R.drawable.bg_setup_card_done)
            ivStep2NumberBg.setImageResource(R.drawable.bg_step_circle_done)
            tvStep2Number.text = "✓"
            tvStep2Number.setTextColor(ContextCompat.getColor(this, R.color.bento_white))
            tvStep2StatusBadge.text = "✓ Default"
            tvStep2StatusBadge.setTextColor(ContextCompat.getColor(this, R.color.bento_blue))
            btnStep2Select.text = "✓ Sudah Menjadi Keyboard Utama"
            btnStep2Select.setBackgroundResource(R.drawable.bg_button_done)
            btnStep2Select.setTextColor(ContextCompat.getColor(this, R.color.bento_text_secondary))
        } else if (step1Done) {
            // Step 1 is done, so Step 2 is now active!
            cardStep2.setBackgroundResource(R.drawable.bg_setup_card_active)
            ivStep2NumberBg.setImageResource(R.drawable.bg_step_circle_blue)
            tvStep2Number.text = "2"
            tvStep2Number.setTextColor(ContextCompat.getColor(this, R.color.bento_white))
            tvStep2StatusBadge.text = "Perlu Dipilih"
            tvStep2StatusBadge.setTextColor(ContextCompat.getColor(this, R.color.bento_blue))
            btnStep2Select.text = "2. Pilih One Keyboard"
            btnStep2Select.setBackgroundResource(R.drawable.bg_button_blue)
            btnStep2Select.setTextColor(ContextCompat.getColor(this, R.color.bento_white))
        } else {
            // Step 2 is pending Step 1
            cardStep2.setBackgroundResource(R.drawable.bg_bento_white)
            ivStep2NumberBg.setImageResource(R.drawable.bg_step_circle_muted)
            tvStep2Number.text = "2"
            tvStep2Number.setTextColor(ContextCompat.getColor(this, R.color.bento_text_secondary))
            tvStep2StatusBadge.text = "Tunggu Langkah 1"
            tvStep2StatusBadge.setTextColor(ContextCompat.getColor(this, R.color.bento_text_muted))
            btnStep2Select.text = "2. Pilih One Keyboard"
            btnStep2Select.setBackgroundResource(R.drawable.bg_button_done)
            btnStep2Select.setTextColor(ContextCompat.getColor(this, R.color.bento_text_secondary))
        }

        // Update Step 3 UI
        if (step3Done) {
            cardStep3.setBackgroundResource(R.drawable.bg_setup_card_done)
            ivStep3NumberBg.setImageResource(R.drawable.bg_step_circle_done)
            tvStep3Number.text = "✓"
            tvStep3Number.setTextColor(ContextCompat.getColor(this, R.color.bento_white))
            tvStep3StatusBadge.text = "✓ Diizinkan"
            tvStep3StatusBadge.setTextColor(ContextCompat.getColor(this, R.color.bento_blue))
            btnStep3Camera.text = "✓ Akses Kamera Aktif"
            btnStep3Camera.setBackgroundResource(R.drawable.bg_button_done)
            btnStep3Camera.setTextColor(ContextCompat.getColor(this, R.color.bento_text_secondary))
        } else {
            cardStep3.setBackgroundResource(
                if (step2Done) R.drawable.bg_setup_card_active else R.drawable.bg_bento_white
            )
            ivStep3NumberBg.setImageResource(
                if (step2Done) R.drawable.bg_step_circle_blue else R.drawable.bg_step_circle_muted
            )
            tvStep3Number.text = "3"
            tvStep3Number.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (step2Done) R.color.bento_white else R.color.bento_text_secondary
                )
            )
            tvStep3StatusBadge.text = "Perlu Izin"
            tvStep3StatusBadge.setTextColor(ContextCompat.getColor(this, R.color.bento_text_primary))
            btnStep3Camera.text = "3. Izinkan Akses Kamera"
            btnStep3Camera.setBackgroundResource(R.drawable.bg_button_blue)
            btnStep3Camera.setTextColor(ContextCompat.getColor(this, R.color.bento_white))
        }

        // All done handling
        if (step1Done && step2Done && step3Done) {
            layoutAllDoneBanner.visibility = View.VISIBLE
            btnFinishSetup.text = "Mulai Mengetik Sekarang →"
            tvSkipSetup.visibility = View.GONE
        } else {
            layoutAllDoneBanner.visibility = View.GONE
            btnFinishSetup.text = "Lanjut ke Dashboard"
            tvSkipSetup.visibility = View.VISIBLE
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✓ Izin kamera berhasil diberikan!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Izin kamera diperlukan untuk fitur pemindai barcode", Toast.LENGTH_LONG).show()
            }
            refreshSetupStates()
        }
    }

    private fun goToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
