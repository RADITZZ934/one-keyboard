package com.example.barcodekeyboard.core.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.barcodekeyboard.data.model.BarcodeItem
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * High-performance, 100% offline Excel & Spreadsheet file importer.
 * Supports:
 * 1. Standard modern Excel (.xlsx / OOXML zip archive)
 * 2. HTML-based Excel tables (.xls / web archive tables)
 * 3. Delimited text files (.csv / .tsv / .txt)
 *
 * Runs completely locally on Android with zero external dependencies.
 */
object ExcelImporter {

    private const val TAG = "ExcelImporter"

    data class ImportResult(
        val isSuccess: Boolean,
        val items: List<BarcodeItem>,
        val fileName: String,
        val totalRowsParsed: Int,
        val successCount: Int,
        val errorMessage: String? = null
    )

    /**
     * Imports barcode items from an Android content Uri (picked by user).
     */
    fun importFromUri(context: Context, uri: Uri): ImportResult {
        val fileName = getFileNameFromUri(context, uri) ?: "imported_file.xlsx"
        val tempFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.bin")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return ImportResult(
                isSuccess = false,
                items = emptyList(),
                fileName = fileName,
                totalRowsParsed = 0,
                successCount = 0,
                errorMessage = "Gagal membaca file dari penyimpanan perangkat."
            )

            return importFromFile(tempFile, fileName)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing from URI: ${e.message}", e)
            return ImportResult(
                isSuccess = false,
                items = emptyList(),
                fileName = fileName,
                totalRowsParsed = 0,
                successCount = 0,
                errorMessage = e.localizedMessage ?: "Terjadi kesalahan saat membaca file."
            )
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Imports from a local file. Automatically detects format.
     */
    fun importFromFile(file: File, displayFileName: String): ImportResult {
        return try {
            if (isZipFile(file)) {
                parseXlsxFile(file, displayFileName)
            } else {
                // Check if HTML or CSV
                val sampleText = readInitialText(file, 2048)
                if (sampleText.contains("<tr", ignoreCase = true) ||
                    sampleText.contains("<table", ignoreCase = true) ||
                    sampleText.contains("<html", ignoreCase = true)
                ) {
                    parseHtmlXlsFile(file, displayFileName)
                } else {
                    parseCsvFile(file, displayFileName)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse spreadsheet file: ${e.message}", e)
            ImportResult(
                isSuccess = false,
                items = emptyList(),
                fileName = displayFileName,
                totalRowsParsed = 0,
                successCount = 0,
                errorMessage = "Gagal memproses struktur file: ${e.message}"
            )
        }
    }

    /**
     * Checks if the file starts with ZIP magic bytes (0x50, 0x4B, 0x03, 0x04)
     */
    private fun isZipFile(file: File): Boolean {
        FileInputStream(file).use { input ->
            val header = ByteArray(4)
            val read = input.read(header)
            return read == 4 &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() &&
                    header[3] == 0x04.toByte()
        }
    }

    private fun readInitialText(file: File, maxBytes: Int): String {
        return try {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(maxBytes)
                val read = input.read(buffer)
                if (read > 0) String(buffer, 0, read, Charsets.UTF_8) else ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    // =========================================================================
    // 1. XLSX PARSER (Standard OpenXML / ZIP)
    // =========================================================================

    private fun parseXlsxFile(file: File, fileName: String): ImportResult {
        val zipFile = ZipFile(file)
        try {
            // 1. Parse sharedStrings.xml if present
            val sharedStrings = parseSharedStrings(zipFile)

            // 2. Find the first sheet XML
            val sheetEntry = zipFile.entries().asSequence().firstOrNull { entry ->
                entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml")
            } ?: return ImportResult(
                isSuccess = false,
                items = emptyList(),
                fileName = fileName,
                totalRowsParsed = 0,
                successCount = 0,
                errorMessage = "Format file XLSX tidak memiliki worksheet (sheet.xml tidak ditemukan)."
            )

            val rawRows = parseSheetXml(zipFile.getInputStream(sheetEntry), sharedStrings)
            return buildItemsFromRawRows(rawRows, fileName)
        } finally {
            zipFile.close()
        }
    }

    private fun parseSharedStrings(zipFile: ZipFile): List<String> {
        val entry: ZipEntry = zipFile.getEntry("xl/sharedStrings.xml") ?: return emptyList()
        val strings = ArrayList<String>()

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(zipFile.getInputStream(entry), "UTF-8")

        var eventType = parser.eventType
        var inSi = false
        var inT = false
        val currentSiText = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tagName.equals("si", ignoreCase = true)) {
                        inSi = true
                        currentSiText.setLength(0)
                    } else if (inSi && tagName.equals("t", ignoreCase = true)) {
                        inT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inT) {
                        currentSiText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tagName.equals("t", ignoreCase = true)) {
                        inT = false
                    } else if (tagName.equals("si", ignoreCase = true)) {
                        strings.add(currentSiText.toString().trim())
                        inSi = false
                    }
                }
            }
            eventType = parser.next()
        }

        return strings
    }

    private fun parseSheetXml(inputStream: InputStream, sharedStrings: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var currentRowMap: MutableMap<Int, String>? = null
        var currentCellRef: String? = null
        var currentCellType: String? = null
        var inV = false
        var inIsT = false
        val currentCellText = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tagName.equals("row", ignoreCase = true)) {
                        currentRowMap = HashMap()
                    } else if (tagName.equals("c", ignoreCase = true)) {
                        currentCellRef = parser.getAttributeValue(null, "r")
                        currentCellType = parser.getAttributeValue(null, "t")
                        currentCellText.setLength(0)
                    } else if (tagName.equals("v", ignoreCase = true)) {
                        inV = true
                    } else if (tagName.equals("t", ignoreCase = true)) {
                        inIsT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inV || inIsT) {
                        currentCellText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tagName.equals("v", ignoreCase = true)) {
                        inV = false
                    } else if (tagName.equals("t", ignoreCase = true)) {
                        inIsT = false
                    } else if (tagName.equals("c", ignoreCase = true)) {
                        val colIdx = extractColumnIndex(currentCellRef)
                        var value = currentCellText.toString().trim()

                        if (currentCellType == "s") {
                            val sIdx = value.toIntOrNull()
                            if (sIdx != null && sIdx in sharedStrings.indices) {
                                value = sharedStrings[sIdx]
                            }
                        }

                        if (colIdx >= 0 && currentRowMap != null) {
                            currentRowMap[colIdx] = value
                        }
                        currentCellRef = null
                        currentCellType = null
                    } else if (tagName.equals("row", ignoreCase = true)) {
                        if (currentRowMap != null && currentRowMap.isNotEmpty()) {
                            var maxCol = 0
                            for (k in currentRowMap.keys) {
                                if (k > maxCol) maxCol = k
                            }
                            val rowList = ArrayList<String>(maxCol + 1)
                            for (i in 0..maxCol) {
                                rowList.add(currentRowMap[i] ?: "")
                            }
                            rows.add(rowList)
                        }
                        currentRowMap = null
                    }
                }
            }
            eventType = parser.next()
        }

        return rows
    }

    private fun extractColumnIndex(cellRef: String?): Int {
        if (cellRef.isNullOrEmpty()) return -1
        val letters = cellRef.takeWhile { it.isLetter() }.toUpperCase()
        if (letters.isEmpty()) return -1
        var idx = 0
        for (char in letters) {
            if (char in 'A'..'Z') {
                idx = idx * 26 + (char - 'A' + 1)
            }
        }
        return idx - 1
    }

    // =========================================================================
    // 2. HTML-BASED XLS PARSER (.xls / web archive tables)
    // =========================================================================

    private fun parseHtmlXlsFile(file: File, fileName: String): ImportResult {
        val rows = ArrayList<List<String>>()
        val content = file.readText(Charsets.UTF_8)

        val trRegex = Regex("<tr[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
        val tdRegex = Regex("<t[dh][^>]*>(.*?)</t[dh]>", RegexOption.DOT_MATCHES_ALL)
        val tagStripRegex = Regex("<[^>]+>")

        for (trMatch in trRegex.findAll(content)) {
            val trInner = trMatch.groupValues[1]
            val cells = ArrayList<String>()

            for (tdMatch in tdRegex.findAll(trInner)) {
                val tdInner = tdMatch.groupValues[1]
                val cleanText = tagStripRegex.replace(tdInner, "")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .trim()
                // Normalize spaces
                val normalizedText = cleanText.split(Regex("\\s+")).joinToString(" ")
                cells.add(normalizedText)
            }

            if (cells.isNotEmpty() && cells.any { it.isNotBlank() }) {
                rows.add(cells)
            }
        }

        return buildItemsFromRawRows(rows, fileName)
    }

    // =========================================================================
    // 3. CSV / TSV PARSER
    // =========================================================================

    private fun parseCsvFile(file: File, fileName: String): ImportResult {
        val rows = ArrayList<List<String>>()

        BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8)).use { reader ->
            var line = reader.readLine()
            var delimiter: Char? = null

            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    if (delimiter == null) {
                        delimiter = detectDelimiter(trimmed)
                    }
                    val cells = parseDelimitedLine(trimmed, delimiter)
                    if (cells.isNotEmpty() && cells.any { it.isNotBlank() }) {
                        rows.add(cells)
                    }
                }
                line = reader.readLine()
            }
        }

        return buildItemsFromRawRows(rows, fileName)
    }

    private fun detectDelimiter(line: String): Char {
        val commaCount = line.count { it == ',' }
        val semicolonCount = line.count { it == ';' }
        val tabCount = line.count { it == '\t' }

        return when {
            tabCount > commaCount && tabCount > semicolonCount -> '\t'
            semicolonCount > commaCount -> ';'
            else -> ','
        }
    }

    private fun parseDelimitedLine(line: String, delimiter: Char): List<String> {
        val tokens = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when {
                ch == '\"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    // =========================================================================
    // SMART COLUMN MAPPING & ITEM CONVERSION
    // =========================================================================

    private fun buildItemsFromRawRows(rows: List<List<String>>, fileName: String): ImportResult {
        if (rows.isEmpty()) {
            return ImportResult(
                isSuccess = false,
                items = emptyList(),
                fileName = fileName,
                totalRowsParsed = 0,
                successCount = 0,
                errorMessage = "File Excel kosong atau tidak memiliki baris data."
            )
        }

        var headerRowIndex = -1
        var colBarcode = -1
        var colName = -1
        var colUom = -1

        // Scan first 10 rows to detect header
        for (i in 0 until minOf(rows.size, 10)) {
            val row = rows[i]
            val bCol = row.indexOfFirst { it.contains("barcode", ignoreCase = true) || it.contains("plu", ignoreCase = true) || it.equals("kode", ignoreCase = true) || it.equals("code", ignoreCase = true) }
            val nCol = row.indexOfFirst { it.contains("nama", ignoreCase = true) || it.contains("item", ignoreCase = true) || it.contains("barang", ignoreCase = true) || it.contains("deskripsi", ignoreCase = true) || it.contains("description", ignoreCase = true) }
            val uCol = row.indexOfFirst { it.contains("satuan", ignoreCase = true) || it.contains("uom", ignoreCase = true) || it.equals("sat", ignoreCase = true) || it.equals("unit", ignoreCase = true) }

            if (bCol >= 0 && nCol >= 0) {
                headerRowIndex = i
                colBarcode = bCol
                colName = nCol
                colUom = uCol
                break
            }
        }

        // If no explicit header found, deduce from first data row
        val startRow = if (headerRowIndex >= 0) headerRowIndex + 1 else 0

        if (colBarcode == -1 || colName == -1) {
            val sample = rows.getOrNull(startRow) ?: rows.first()
            if (sample.size >= 2) {
                if (sample.size == 3) {
                    if (isLikelyBarcode(sample[0])) {
                        colBarcode = 0
                        colName = 1
                        colUom = 2
                    } else if (isLikelyBarcode(sample[1])) {
                        colBarcode = 1
                        colName = 2
                        colUom = -1
                    } else {
                        colBarcode = 0
                        colName = 1
                    }
                } else if (sample.size >= 4 && isLikelyBarcode(sample[1])) {
                    colBarcode = 1
                    colName = 2
                    colUom = 3
                } else {
                    colBarcode = sample.indexOfFirst { isLikelyBarcode(it) }.takeIf { it >= 0 } ?: 0
                    colName = sample.indices.firstOrNull { it != colBarcode && sample[it].length > 2 } ?: 1
                    colUom = sample.indices.firstOrNull { it != colBarcode && it != colName && sample[it].length in 1..6 } ?: -1
                }
            }
        }

        val items = ArrayList<BarcodeItem>()
        var totalParsed = 0

        for (i in startRow until rows.size) {
            val row = rows[i]
            if (row.isEmpty() || row.all { it.isBlank() }) continue
            totalParsed++

            val rawBarcode = row.getOrNull(colBarcode).orEmpty()
            val rawName = row.getOrNull(colName).orEmpty()
            val rawUom = if (colUom in row.indices) row[colUom] else "PCS"

            val cleanBarcode = normalizeBarcode(rawBarcode)
            val cleanName = rawName.trim()
            val cleanUom = if (rawUom.isBlank()) "PCS" else rawUom.trim().toUpperCase()

            if (cleanBarcode.isBlank() || cleanName.isBlank()) continue
            if (cleanBarcode.equals("barcode", ignoreCase = true) || cleanBarcode.equals("kode", ignoreCase = true)) continue

            items.add(
                BarcodeItem(
                    id = items.size + 1L,
                    code = cleanBarcode,
                    name = cleanName,
                    uom = cleanUom
                )
            )
        }

        if (items.isEmpty()) {
            return ImportResult(
                isSuccess = false,
                items = emptyList(),
                fileName = fileName,
                totalRowsParsed = totalParsed,
                successCount = 0,
                errorMessage = "Tidak ditemukan kolom barcode & nama item yang valid dalam file."
            )
        }

        return ImportResult(
            isSuccess = true,
            items = items,
            fileName = fileName,
            totalRowsParsed = totalParsed,
            successCount = items.size
        )
    }

    private fun isLikelyBarcode(value: String): Boolean {
        val clean = value.trim()
        if (clean.contains("E+", ignoreCase = true)) return true
        val digitsOnly = clean.filter { it.isDigit() }
        return digitsOnly.length in 4..18 && digitsOnly.length >= clean.length - 2
    }

    /**
     * Converts scientific notation (e.g. 8.885E+12) and decimal numbers (899276111122.0)
     * into clean integer barcode strings.
     */
    fun normalizeBarcode(raw: String): String {
        val clean = raw.trim()
        if (clean.isBlank()) return ""

        if (clean.contains("E+", ignoreCase = true) || clean.contains("E-", ignoreCase = true)) {
            return try {
                BigDecimal(clean).toPlainString().substringBefore('.')
            } catch (e: Exception) {
                clean
            }
        }

        if (clean.endsWith(".0") && clean.substringBefore(".0").all { it.isDigit() }) {
            return clean.substringBefore(".0")
        }

        return clean
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        name = it.getString(nameIdx)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        return name
    }
}
