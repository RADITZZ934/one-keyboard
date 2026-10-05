package com.example.barcodekeyboard.ui.settings

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import com.example.barcodekeyboard.core.feedback.BeepSoundManager
import com.example.barcodekeyboard.core.feedback.VibrationHelper
import com.example.barcodekeyboard.core.importer.ExcelImporter
import com.example.barcodekeyboard.data.preferences.KeyboardPreferences
import com.example.barcodekeyboard.data.repository.BarcodeItemRepository
import com.example.barcodekeyboard.ui.catalog.CatalogActivity

/**
 * Main Settings, Activation, and Offline Excel Importer Activity.
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CAMERA = 1001
        private const val REQUEST_PICK_EXCEL_FILE = 2001
    }

    private lateinit var repository: BarcodeItemRepository
    private var tvImportStatus: TextView? = null

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

        repository = BarcodeItemRepository.getInstance(this)

        setupButtons()
        checkCameraPermission()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.preferencesContainer, SettingsFragment())
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        updateImportStatusView()
    }

    private fun setupButtons() {
        val btnEnable: Button = findViewById(R.id.btnEnableKeyboard)
        val btnSelect: Button = findViewById(R.id.btnSelectKeyboard)
        val btnImportExcel: Button? = findViewById(R.id.btnImportExcel)
        val btnOpenCatalog: Button? = findViewById(R.id.btnOpenCatalog)
        val btnResetCatalog: Button? = findViewById(R.id.btnResetCatalog)
        tvImportStatus = findViewById(R.id.tvImportStatus)

        val btnClear: Button? = findViewById(R.id.btnClearTestInput)
        val etTestInput: EditText? = findViewById(R.id.etTestInput)
        val btnTestSuccess: Button? = findViewById(R.id.btnTestSuccess)
        val btnTestFailure: Button? = findViewById(R.id.btnTestFailure)

        updateImportStatusView()

        btnImportExcel?.setOnClickListener {
            openFilePicker()
        }

        btnOpenCatalog?.setOnClickListener {
            val intent = Intent(this, CatalogActivity::class.java)
            startActivity(intent)
        }

        btnResetCatalog?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset ke Data Bawaan")
                .setMessage("Apakah Anda yakin ingin mengembalikan daftar barcode ke 466 item bawaan?")
                .setPositiveButton("Reset") { _, _ ->
                    repository.resetToDefault()
                    updateImportStatusView()
                    Toast.makeText(this, "Katalog berhasil dikembalikan ke data bawaan", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

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

    private fun updateImportStatusView() {
        val summary = repository.getLastImportSummary()
        tvImportStatus?.text = summary
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            val mimeTypes = arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "text/csv",
                "text/comma-separated-values",
                "text/tab-separated-values",
                "text/plain",
                "application/octet-stream",
                "*/*"
            )
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
        try {
            startActivityForResult(
                Intent.createChooser(intent, "Pilih File Barcode (.xlsx / .xls / .csv)"),
                REQUEST_PICK_EXCEL_FILE
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak ada aplikasi file manager yang tersedia", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_EXCEL_FILE && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                handleExcelImport(uri)
            }
        }
    }

    private fun handleExcelImport(uri: Uri) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Memproses File")
            .setMessage("Sedang membaca barcode dan item dari file secara offline...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        Thread {
            val result = ExcelImporter.importFromUri(this, uri)
            runOnUiThread {
                progressDialog.dismiss()
                if (result.isSuccess) {
                    val count = repository.importItems(
                        newItems = result.items,
                        sourceFileName = result.fileName,
                        replaceExisting = true
                    )
                    updateImportStatusView()
                    AlertDialog.Builder(this)
                        .setTitle("Import Berhasil! 🎉")
                        .setMessage("Berhasil memuat $count item barcode dari file '${result.fileName}'.\n\nData sudah langsung aktif dan siap digunakan di keyboard tanpa perlu ngetik!")
                        .setPositiveButton("Lihat Katalog") { _, _ ->
                            startActivity(Intent(this, CatalogActivity::class.java))
                        }
                        .setNegativeButton("Selesai", null)
                        .show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Gagal Membaca File")
                        .setMessage(result.errorMessage ?: "Format file tidak dapat dikenali.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
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
