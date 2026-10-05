package com.example.barcodekeyboard.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.barcodekeyboard.R
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

    private fun setupButtons() {
        val btnEnable: Button = findViewById(R.id.btnEnableKeyboard)
        val btnSelect: Button = findViewById(R.id.btnSelectKeyboard)
        val btnClear: Button? = findViewById(R.id.btnClearTestInput)
        val etTestInput: EditText? = findViewById(R.id.etTestInput)
        val btnTestSuccess: Button? = findViewById(R.id.btnTestSuccess)
        val btnTestFailure: Button? = findViewById(R.id.btnTestFailure)

        val beepManager = BeepSoundManager(this)
        val vibrationHelper = VibrationHelper(this)

        btnEnable.setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }

        btnSelect.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        }

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
