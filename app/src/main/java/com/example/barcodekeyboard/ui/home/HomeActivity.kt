package com.example.barcodekeyboard.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barcodekeyboard.R
import com.example.barcodekeyboard.core.feedback.BeepSoundManager
import com.example.barcodekeyboard.core.feedback.VibrationHelper
import com.example.barcodekeyboard.core.history.ScanHistoryManager
import com.example.barcodekeyboard.data.preferences.KeyboardPreferences
import com.example.barcodekeyboard.ui.settings.SettingsActivity

/**
 * Modern Bento-Grid Home Screen for One Keyboard.
 * Features:
 * - Test Field Card (Matte Black)
 * - Scan History Card (White/Cream)
 * - Settings Navigation Card (Cream/Blue)
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var preferences: KeyboardPreferences
    private lateinit var historyManager: ScanHistoryManager
    private lateinit var historyAdapter: ScanHistoryAdapter
    private lateinit var beepSoundManager: BeepSoundManager
    private lateinit var vibrationHelper: VibrationHelper

    // Views
    private lateinit var tvHomeActivationStatus: TextView
    private lateinit var cardActivationAlert: View
    private lateinit var etHomeTestInput: EditText
    private lateinit var btnHomeClearInput: TextView
    private lateinit var btnHomeTestSuccess: TextView
    private lateinit var btnHomeTestFailure: TextView
    private lateinit var tvHistoryCount: TextView
    private lateinit var btnHomeClearHistory: TextView
    private lateinit var layoutEmptyHistory: View
    private lateinit var rvScanHistory: RecyclerView
    private lateinit var btnGoToSettings: View

    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = KeyboardPreferences(this)
        applySavedNightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        applyStatusBarTheme()

        historyManager = ScanHistoryManager.getInstance(this)
        beepSoundManager = BeepSoundManager(this)
        vibrationHelper = VibrationHelper(this)

        initViews()
        setupTestField()
        setupScanHistory()
        setupNavigation()
    }

    override fun onResume() {
        super.onResume()
        applySavedNightMode()
        applyStatusBarTheme()
        updateActivationState()
        refreshScanHistory()
    }

    private fun applySavedNightMode() {
        val nightMode = when (preferences.themeMode) {
            KeyboardPreferences.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            KeyboardPreferences.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    private fun applyStatusBarTheme() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.bento_bg)
            val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val decor = window.decorView
            decor.systemUiVisibility = if (isNight) {
                decor.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                decor.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }

    private fun initViews() {
        tvHomeActivationStatus = findViewById(R.id.tvHomeActivationStatus)
        cardActivationAlert = findViewById(R.id.cardActivationAlert)
        etHomeTestInput = findViewById(R.id.etHomeTestInput)
        btnHomeClearInput = findViewById(R.id.btnHomeClearInput)
        btnHomeTestSuccess = findViewById(R.id.btnHomeTestSuccess)
        btnHomeTestFailure = findViewById(R.id.btnHomeTestFailure)
        tvHistoryCount = findViewById(R.id.tvHistoryCount)
        btnHomeClearHistory = findViewById(R.id.btnHomeClearHistory)
        layoutEmptyHistory = findViewById(R.id.layoutEmptyHistory)
        rvScanHistory = findViewById(R.id.rvScanHistory)
        btnGoToSettings = findViewById(R.id.btnGoToSettings)
    }

    private fun setupTestField() {
        btnHomeClearInput.setOnClickListener {
            etHomeTestInput.text.clear()
        }

        btnHomeTestSuccess.setOnClickListener {
            beepSoundManager.playSuccessBeep()
            vibrationHelper.vibrateSuccess()
            val sampleBarcode = "8992761" + (10000..99999).random()
            val prefix = if (etHomeTestInput.text.isNullOrBlank()) "" else "\n"
            etHomeTestInput.append("$prefix[✓ Scan: $sampleBarcode]")

            // Also record into scan history so user sees live update
            historyManager.addScan(sampleBarcode, "EAN_13")
            refreshScanHistory()
            Toast.makeText(this, "Beep Sukses & Tersimpan ke Riwayat", Toast.LENGTH_SHORT).show()
        }

        btnHomeTestFailure.setOnClickListener {
            beepSoundManager.playFailureBeep()
            vibrationHelper.vibrateFailure()
            val prefix = if (etHomeTestInput.text.isNullOrBlank()) "" else "\n"
            etHomeTestInput.append("$prefix[✕ Gagal: Barcode Tidak Terbaca]")
            Toast.makeText(this, "Beep Gagal (Error)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupScanHistory() {
        historyAdapter = ScanHistoryAdapter(emptyList()) { item ->
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Barcode", item.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "✓ '${item.text}' disalin", Toast.LENGTH_SHORT).show()
        }

        rvScanHistory.layoutManager = LinearLayoutManager(this)
        rvScanHistory.adapter = historyAdapter

        btnHomeClearHistory.setOnClickListener {
            val items = historyManager.getHistory()
            if (items.isEmpty()) {
                Toast.makeText(this, "Riwayat sudah kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("Hapus Riwayat Scan?")
                .setMessage("Semua riwayat pemindaian barcode akan dihapus.")
                .setPositiveButton("Hapus") { _, _ ->
                    historyManager.clearHistory()
                    refreshScanHistory()
                    Toast.makeText(this, "Riwayat berhasil dibersihkan", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun refreshScanHistory() {
        val items = historyManager.getHistory()
        tvHistoryCount.text = "(${items.size})"
        if (items.isEmpty()) {
            layoutEmptyHistory.visibility = View.VISIBLE
            rvScanHistory.visibility = View.GONE
        } else {
            layoutEmptyHistory.visibility = View.GONE
            rvScanHistory.visibility = View.VISIBLE
            historyAdapter.updateData(items)
        }
    }

    private fun setupNavigation() {
        btnGoToSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        val activationClickListener = View.OnClickListener {
            handleActivationClick()
        }
        tvHomeActivationStatus.setOnClickListener(activationClickListener)
        cardActivationAlert.setOnClickListener(activationClickListener)
    }

    private fun tryAutoActivateKeyboard(): Boolean {
        return try {
            val cr = contentResolver
            val myImeId = "$packageName/.service.BarcodeKeyboardService"

            // 1. Enable keyboard in secure settings if not already enabled
            val enabledImes = Settings.Secure.getString(cr, Settings.Secure.ENABLED_INPUT_METHODS) ?: ""
            if (!enabledImes.contains(myImeId)) {
                val updated = if (enabledImes.isEmpty()) myImeId else "$enabledImes:$myImeId"
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_INPUT_METHODS, updated)
            }

            // 2. Set as default IME if not already default
            val currentIme = Settings.Secure.getString(cr, Settings.Secure.DEFAULT_INPUT_METHOD)
            if (currentIme != myImeId) {
                Settings.Secure.putString(cr, Settings.Secure.DEFAULT_INPUT_METHOD, myImeId)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun updateActivationState() {
        tryAutoActivateKeyboard()

        val isEnabled = isKeyboardEnabled()
        val isSelected = isKeyboardSelected()

        if (isEnabled && isSelected) {
            tvHomeActivationStatus.text = "✓ Aktif"
            tvHomeActivationStatus.setTextColor(getColor(R.color.bento_blue))
            cardActivationAlert.visibility = View.GONE
        } else if (isEnabled) {
            tvHomeActivationStatus.text = "Pilih Keyboard"
            tvHomeActivationStatus.setTextColor(getColor(R.color.bento_text_primary))
            cardActivationAlert.visibility = View.VISIBLE
        } else {
            tvHomeActivationStatus.text = "Belum Aktif"
            tvHomeActivationStatus.setTextColor(getColor(R.color.bento_text_primary))
            cardActivationAlert.visibility = View.VISIBLE
        }
    }

    private fun handleActivationClick() {
        if (tryAutoActivateKeyboard() && isKeyboardEnabled() && isKeyboardSelected()) {
            updateActivationState()
            Toast.makeText(this, "✓ One Keyboard berhasil diaktifkan secara otomatis!", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isKeyboardEnabled()) {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            Toast.makeText(this, "Aktifkan sakelar 'One Keyboard'", Toast.LENGTH_LONG).show()
        } else if (!isKeyboardSelected()) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        } else {
            Toast.makeText(this, "One Keyboard sudah aktif dan siap digunakan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isKeyboardEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
        val enabledList = imm.enabledInputMethodList
        val myPackage = packageName
        return enabledList.any { it.packageName == myPackage }
    }

    private fun isKeyboardSelected(): Boolean {
        val currentIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return currentIme?.contains(packageName) == true
    }
}
