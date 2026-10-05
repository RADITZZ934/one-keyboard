package com.example.barcodekeyboard.ui.catalog

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.barcodekeyboard.R
import com.example.barcodekeyboard.core.importer.ExcelImporter
import com.example.barcodekeyboard.data.model.BarcodeItem
import com.example.barcodekeyboard.data.preferences.KeyboardPreferences
import com.example.barcodekeyboard.data.repository.BarcodeItemRepository

/**
 * Full-screen Catalog Management Activity allowing employees/managers to:
 * - Search through imported non-fruit item barcodes
 * - Import new XLSX/XLS spreadsheet files offline
 * - Add new items
 * - Edit existing product names, barcodes, and UOMs
 * - Delete items or reset to default list
 */
class CatalogActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_PICK_EXCEL = 3001
    }

    private lateinit var repository: BarcodeItemRepository
    private lateinit var adapter: BarcodeItemAdapter

    private lateinit var etSearch: EditText
    private lateinit var btnClear: ImageButton
    private lateinit var tvTotalHeader: TextView
    private lateinit var rvList: RecyclerView
    private lateinit var tvEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalog)

        val toolbar: Toolbar = findViewById(R.id.catalogToolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        repository = BarcodeItemRepository.getInstance(this)

        initViews()
        setupRecyclerView()
        setupSearch()
        setupButtons()

        refreshData()
    }

    private fun initViews() {
        etSearch = findViewById(R.id.etSearchQuery)
        btnClear = findViewById(R.id.btnClearQuery)
        tvTotalHeader = findViewById(R.id.tvTotalItemHeader)
        rvList = findViewById(R.id.rvCatalogList)
        tvEmpty = findViewById(R.id.tvEmptyNotice)
    }

    private fun setupRecyclerView() {
        val preferences = KeyboardPreferences(this)
        val isDark = preferences.isDarkTheme(this)

        adapter = BarcodeItemAdapter(
            items = emptyList(),
            isDarkMode = isDark,
            onItemClicked = { item ->
                showEditItemDialog(item)
            }
        )

        rvList.layoutManager = LinearLayoutManager(this)
        rvList.adapter = adapter
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                btnClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                filterList(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClear.setOnClickListener {
            etSearch.text.clear()
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnImportXlsx)?.setOnClickListener {
            openFilePicker()
        }

        findViewById<Button>(R.id.btnAddNewItem)?.setOnClickListener {
            showAddItemDialog()
        }

        findViewById<Button>(R.id.btnResetDefault)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset Katalog")
                .setMessage("Apakah Anda yakin ingin mengembalikan daftar ke data bawaan?")
                .setPositiveButton("Reset") { _, _ ->
                    repository.resetToDefault()
                    etSearch.text.clear()
                    refreshData()
                    Toast.makeText(this, "Katalog berhasil direset ke data bawaan", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Batal", null)
                .show()
        }
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
                REQUEST_PICK_EXCEL
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak ada aplikasi file picker", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_EXCEL && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                handleExcelImport(uri)
            }
        }
    }

    private fun handleExcelImport(uri: Uri) {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Memproses File")
            .setMessage("Sedang membaca barcode dan data produk dari file...")
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
                    etSearch.text.clear()
                    refreshData()
                    AlertDialog.Builder(this)
                        .setTitle("Import Berhasil! 🎉")
                        .setMessage("Berhasil memuat $count item barcode dari file '${result.fileName}'.")
                        .setPositiveButton("OK", null)
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

    private fun filterList(query: String) {
        val results = repository.searchItems(query)
        adapter.updateData(results)
        tvTotalHeader.text = "Menampilkan ${results.size} item"
        tvEmpty.visibility = if (results.isEmpty()) View.VISIBLE else View.GONE
        rvList.visibility = if (results.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun refreshData() {
        filterList(etSearch.text.toString())
    }

    private fun showAddItemDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_item, null)
        val etName = dialogView.findViewById<EditText>(R.id.etDialogItemName)
        val etCode = dialogView.findViewById<EditText>(R.id.etDialogItemCode)
        val etUom = dialogView.findViewById<EditText>(R.id.etDialogItemUom)

        AlertDialog.Builder(this)
            .setTitle("Tambah Item Baru")
            .setView(dialogView)
            .setPositiveButton("Simpan") { _, _ ->
                val name = etName.text.toString().trim()
                val code = etCode.text.toString().trim()
                val uom = etUom.text.toString().trim().ifBlank { "PCS" }

                if (name.isNotEmpty() && code.isNotEmpty()) {
                    val newItem = BarcodeItem(
                        id = System.currentTimeMillis(),
                        code = code,
                        name = name,
                        uom = uom
                    )
                    repository.saveItem(newItem)
                    refreshData()
                    Toast.makeText(this, "Item '$name' berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Nama dan Kode Barcode wajib diisi", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showEditItemDialog(item: BarcodeItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_item, null)
        val etName = dialogView.findViewById<EditText>(R.id.etDialogItemName)
        val etCode = dialogView.findViewById<EditText>(R.id.etDialogItemCode)
        val etUom = dialogView.findViewById<EditText>(R.id.etDialogItemUom)

        etName.setText(item.name)
        etCode.setText(item.code)
        etUom.setText(item.uom)

        AlertDialog.Builder(this)
            .setTitle("Edit Item Barcode")
            .setView(dialogView)
            .setPositiveButton("Simpan") { _, _ ->
                val name = etName.text.toString().trim()
                val code = etCode.text.toString().trim()
                val uom = etUom.text.toString().trim().ifBlank { "PCS" }

                if (name.isNotEmpty() && code.isNotEmpty()) {
                    val updated = item.copy(name = name, code = code, uom = uom)
                    repository.saveItem(updated)
                    refreshData()
                    Toast.makeText(this, "Perubahan berhasil disimpan", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Hapus") { _, _ ->
                repository.deleteItem(item.id)
                refreshData()
                Toast.makeText(this, "Item dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
