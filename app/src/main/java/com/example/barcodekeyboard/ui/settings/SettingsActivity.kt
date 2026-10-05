package com.example.barcodekeyboard.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.barcodekeyboard.R
import com.example.barcodekeyboard.core.clipboard.ClipboardHistoryManager
import com.example.barcodekeyboard.core.feedback.BeepSoundManager
import com.example.barcodekeyboard.core.feedback.VibrationHelper
import com.example.barcodekeyboard.data.preferences.KeyboardPreferences

/**
 * Main Settings and Activation Activity for Barcode Keyboard.
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CAMERA = 1001
    }

    private lateinit var clipboardHistoryManager: ClipboardHistoryManager
    private var tvSettingsClipboardCount: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val preferences = KeyboardPreferences(this)
        val initialNightMode = when (preferences.themeMode) {
            KeyboardPreferences.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            KeyboardPreferences.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(initialNightMode)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        setupButtons()
        checkCameraPermission()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.preferencesContainer, SettingsFragment())
                .commit()
        }
    }

    private lateinit var btnEnableKeyboard: Button
    private lateinit var btnSelectKeyboard: Button
    private var tvActivationStatus: TextView? = null
    private var tvActivationGuide: TextView? = null
    private var tvEnableHint: TextView? = null

    private var isReturningFromSettings = false

    private fun setupButtons() {
        btnEnableKeyboard = findViewById(R.id.btnEnableKeyboard)
        btnSelectKeyboard = findViewById(R.id.btnSelectKeyboard)
        tvActivationStatus = findViewById(R.id.tvActivationStatus)
        tvActivationGuide = findViewById(R.id.tvActivationGuide)
        tvEnableHint = findViewById(R.id.tvEnableHint)

        val btnClear: Button? = findViewById(R.id.btnClearTestInput)
        val etTestInput: EditText? = findViewById(R.id.etTestInput)
        val btnTestSuccess: Button? = findViewById(R.id.btnTestSuccess)
        val btnTestFailure: Button? = findViewById(R.id.btnTestFailure)

        val beepManager = BeepSoundManager(this)
        val vibrationHelper = VibrationHelper(this)

        btnEnableKeyboard.setOnClickListener {
            // 1. Try direct programmatic bypass if permission exists
            if (tryDirectBypass()) {
                Toast.makeText(this, "✓ One Keyboard berhasil diaktifkan otomatis!", Toast.LENGTH_SHORT).show()
                updateActivationUI()
                return@setOnClickListener
            }

            // 2. Fallback: Open system settings and enable seamless auto-advance on return
            isReturningFromSettings = true
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }

        btnSelectKeyboard.setOnClickListener {
            if (!isKeyboardEnabled()) {
                // If direct bypass can activate it, do it immediately
                if (tryDirectBypass()) {
                    updateActivationUI()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showInputMethodPicker()
                    return@setOnClickListener
                }

                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("One Keyboard Belum Aktif")
                    .setMessage("Sebelum memilih One Keyboard di popup metode masukan, Anda perlu mengaktifkan sakelarnya (toggle) terlebih dahulu di Pengaturan Sistem.\n\nBuka Pengaturan Sistem sekarang?")
                    .setPositiveButton("Buka Pengaturan") { _, _ ->
                        isReturningFromSettings = true
                        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            } else {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showInputMethodPicker()
            }
        }

        updateActivationUI()

        btnClear?.setOnClickListener {
            etTestInput?.text?.clear()
        }

        btnTestSuccess?.setOnClickListener {
            beepManager.playSuccessBeep()
            vibrationHelper.vibrateSuccess()
            val text = if (etTestInput?.text.isNullOrBlank()) "[✓ SUKSES: 899276111122]" else "\n[✓ SUKSES: 899276111122]"
            etTestInput?.append(text)
            Toast.makeText(this, "Bunyi Beep Sukses (Nada Tinggi)", Toast.LENGTH_SHORT).show()
        }

        btnTestFailure?.setOnClickListener {
            beepManager.playFailureBeep()
            vibrationHelper.vibrateFailure()
            val text = if (etTestInput?.text.isNullOrBlank()) "[✕ GAGAL: Barcode Tidak Terbaca]" else "\n[✕ GAGAL: Barcode Tidak Terbaca]"
            etTestInput?.append(text)
            Toast.makeText(this, "Bunyi Beep Gagal (Nada Rendah / Error)", Toast.LENGTH_SHORT).show()
        }

        // Clipboard History section
        clipboardHistoryManager = ClipboardHistoryManager.getInstance(this)
        tvSettingsClipboardCount = findViewById(R.id.tvSettingsClipboardCount)
        val btnCopySampleClip: Button? = findViewById(R.id.btnCopySampleClip)
        val btnClearAllClips: Button? = findViewById(R.id.btnClearAllClips)

        updateClipboardCount()

        btnCopySampleClip?.setOnClickListener {
            val sampleText = "Contoh Teks Papan Klip [899276111122]"
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("Sample", sampleText))
            clipboardHistoryManager.addClip(sampleText)
            updateClipboardCount()
            Toast.makeText(this, "Teks berhasil disalin ke papan klip!", Toast.LENGTH_SHORT).show()
        }

        btnClearAllClips?.setOnClickListener {
            clipboardHistoryManager.clearAll(keepPinned = false)
            updateClipboardCount()
            Toast.makeText(this, "Riwayat papan klip dibersihkan", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateActivationUI()
        updateClipboardCount()

        // Auto-advance bypass: If user just turned on toggle in settings and returned,
        // automatically trigger the input method picker so user doesn't need to click Step 2!
        if (isReturningFromSettings) {
            isReturningFromSettings = false
            if (isKeyboardEnabled() && !isKeyboardSelected()) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showInputMethodPicker()
            }
        }
    }

    /**
     * Attempts to programmatically enable and select One Keyboard
     * if WRITE_SECURE_SETTINGS permission has been granted (e.g. via ADB or enterprise MDM).
     */
    private fun tryDirectBypass(): Boolean {
        return try {
            val myIme = "$packageName/.service.BarcodeKeyboardService"
            val cr = contentResolver
            val currentEnabled = Settings.Secure.getString(cr, Settings.Secure.ENABLED_INPUT_METHODS) ?: ""
            if (!currentEnabled.contains(myIme)) {
                val newEnabled = if (currentEnabled.isBlank()) myIme else "$currentEnabled:$myIme"
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_INPUT_METHODS, newEnabled)
            }
            Settings.Secure.putString(cr, Settings.Secure.DEFAULT_INPUT_METHOD, myIme)
            true
        } catch (e: Throwable) {
            false
        }
    }

    private fun isKeyboardEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
        val enabledMethods = imm.enabledInputMethodList
        return enabledMethods.any { it.packageName == packageName }
    }

    private fun isKeyboardSelected(): Boolean {
        val currentIme = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
        return currentIme != null && currentIme.contains(packageName)
    }

    private fun updateActivationUI() {
        if (!::btnEnableKeyboard.isInitialized || !::btnSelectKeyboard.isInitialized) return

        val enabled = isKeyboardEnabled()
        val selected = isKeyboardSelected()

        if (!enabled) {
            tvActivationStatus?.text = "Belum Aktif"
            (tvActivationStatus?.background as? android.graphics.drawable.GradientDrawable)?.setColor(
                android.graphics.Color.parseColor("#E53935")
            )
            tvActivationGuide?.text = "Langkah 1: Aktifkan terlebih dahulu One Keyboard di pengaturan sistem HP Anda."
            tvEnableHint?.visibility = android.view.View.VISIBLE
            btnEnableKeyboard.text = "Langkah 1: Aktifkan di Pengaturan HP"
            btnSelectKeyboard.text = "Langkah 2: Pilih Metode Masukan"
        } else if (!selected) {
            tvActivationStatus?.text = "Sudah Aktif di Sistem"
            (tvActivationStatus?.background as? android.graphics.drawable.GradientDrawable)?.setColor(
                android.graphics.Color.parseColor("#FB8C00")
            )
            tvActivationGuide?.text = "Langkah 2: One Keyboard sudah aktif di sistem! Sekarang ketuk tombol di bawah untuk memilih One Keyboard sebagai keyboard utama."
            tvEnableHint?.visibility = android.view.View.GONE
            btnEnableKeyboard.text = "✓ Langkah 1 Selesai (Sudah Diaktifkan)"
            btnSelectKeyboard.text = "Langkah 2: Pilih One Keyboard Sekarang"
        } else {
            tvActivationStatus?.text = "Sedang Digunakan"
            (tvActivationStatus?.background as? android.graphics.drawable.GradientDrawable)?.setColor(
                android.graphics.Color.parseColor("#43A047")
            )
            tvActivationGuide?.text = "🎉 One Keyboard sudah aktif dan sedang digunakan sebagai papan ketik utama Anda."
            tvEnableHint?.visibility = android.view.View.GONE
            btnEnableKeyboard.text = "✓ Langkah 1 Selesai"
            btnSelectKeyboard.text = "✓ One Keyboard Digunakan (Ganti)"
        }
    }

    private fun updateClipboardCount() {
        if (::clipboardHistoryManager.isInitialized) {
            val count = clipboardHistoryManager.getClipCount()
            tvSettingsClipboardCount?.text = "$count klip"
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                PERMISSION_REQUEST_CAMERA
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Izin kamera berhasil diberikan", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "Izin kamera dibutuhkan agar keyboard bisa memindai barcode",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
