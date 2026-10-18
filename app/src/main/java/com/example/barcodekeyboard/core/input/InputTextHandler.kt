package com.example.barcodekeyboard.core.input

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.example.barcodekeyboard.data.model.ScanResult
import com.example.barcodekeyboard.data.preferences.KeyboardPreferences

/**
 * Handles all InputConnection text manipulation:
 * Essential typing, scan commits, cursor navigation, and clipboard paste.
 */
class InputTextHandler {

    companion object {
        private const val TAG = "InputTextHandler"
    }

    /**
     * Commits normal character input (from typing on QWERTY or symbols).
     */
    fun commitChar(inputConnection: InputConnection?, char: String) {
        if (inputConnection == null) return
        inputConnection.commitText(char, 1)
    }

    /**
     * Commits full text string (e.g. from clipboard paste or template).
     */
    fun commitText(inputConnection: InputConnection?, text: String) {
        if (inputConnection == null) return
        inputConnection.commitText(text, 1)
    }

    /**
     * Commits the scanned barcode result into the active input connection,
     * applying any configured prefix, suffix, and auto-enter.
     */
    fun commitScanResult(
        inputConnection: InputConnection?,
        scanResult: ScanResult,
        preferences: KeyboardPreferences
    ): Boolean {
        if (inputConnection == null) {
            Log.w(TAG, "Cannot commit scan result: InputConnection is null")
            return false
        }

        val prefix = preferences.prefixText
        val suffix = preferences.suffixText
        val fullText = "$prefix${scanResult.text}$suffix"

        val committed = inputConnection.commitText(fullText, 1)
        Log.d(TAG, "Committed scan result ($fullText): $committed")

        if (preferences.isAutoEnterEnabled) {
            sendEnter(inputConnection)
        }

        return committed
    }

    /**
     * Inserts a single space character at current cursor position.
     */
     fun sendSpace(inputConnection: InputConnection?) {
        inputConnection?.commitText(" ", 1)
    }

    /**
     * Handles Gboard-style double-space shortcut to insert a period and space (". ").
     * Replaces previous space with ". " if appropriate.
     */
    fun handleDoubleSpacePeriod(inputConnection: InputConnection?): Boolean {
        if (inputConnection == null) return false
        val before = inputConnection.getTextBeforeCursor(2, 0)?.toString()
        if (before != null && before.endsWith(" ")) {
            if (before.length >= 2) {
                val prevChar = before[before.length - 2]
                if (prevChar == '.' || prevChar == '?' || prevChar == '!' || prevChar == ',' || prevChar == ':' || prevChar == ';') {
                    inputConnection.commitText(" ", 1)
                    return false
                }
            }
            inputConnection.deleteSurroundingText(1, 0)
            inputConnection.commitText(". ", 1)
            return true
        } else {
            inputConnection.commitText(" ", 1)
            return false
        }
    }

    /**
     * Deletes one character before the cursor position.
     */
    fun sendBackspace(inputConnection: InputConnection?) {
        if (inputConnection == null) return

        val selectedText = inputConnection.getSelectedText(0)
        if (selectedText.isNullOrEmpty()) {
            val deleted = inputConnection.deleteSurroundingText(1, 0)
            if (!deleted) {
                sendKeyEvent(inputConnection, KeyEvent.KEYCODE_DEL)
            }
        } else {
            inputConnection.commitText("", 1)
        }
    }

    /**
     * Deletes the preceding word (Gboard-style swipe backspace or fast word delete).
     */
    fun deletePreviousWord(inputConnection: InputConnection?): Boolean {
        if (inputConnection == null) return false
        val selectedText = inputConnection.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            inputConnection.commitText("", 1)
            return true
        }

        val textBefore = inputConnection.getTextBeforeCursor(80, 0)?.toString()
        if (textBefore.isNullOrEmpty()) {
            sendBackspace(inputConnection)
            return false
        }

        var i = textBefore.length - 1
        while (i >= 0 && textBefore[i].isWhitespace()) {
            i--
        }
        while (i >= 0 && !textBefore[i].isWhitespace()) {
            i--
        }
        val charsToDelete = textBefore.length - (i + 1)
        if (charsToDelete > 0) {
            inputConnection.deleteSurroundingText(charsToDelete, 0)
            return true
        } else {
            sendBackspace(inputConnection)
            return false
        }
    }

    /**
     * Determines whether Shift should automatically be activated (like Gboard)
     * based on EditorInfo flags and preceding text (start of field, newlines, sentence ends).
     */
    fun shouldAutoCapitalize(inputConnection: InputConnection?, editorInfo: EditorInfo?): Boolean {
        if (inputConnection == null) return false

        val inputType = editorInfo?.inputType ?: 0
        val classType = inputType and InputType.TYPE_MASK_CLASS
        if (classType != InputType.TYPE_CLASS_TEXT) {
            return false
        }

        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_URI ||
            variation == InputType.TYPE_TEXT_VARIATION_FILTER
        ) {
            return false
        }

        if ((inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
            return true
        }

        val textBefore = inputConnection.getTextBeforeCursor(4, 0)?.toString()
        if (textBefore.isNullOrEmpty()) {
            return true
        }

        val trimmed = textBefore.trimEnd(' ', '\t')
        if (trimmed.isEmpty()) {
            return true
        }

        val lastChar = trimmed.last()
        if (lastChar == '\n' || lastChar == '\r') {
            return true
        }

        if ((lastChar == '.' || lastChar == '?' || lastChar == '!') && textBefore.endsWith(" ")) {
            return true
        }

        if ((inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0 && textBefore.endsWith(" ")) {
            return true
        }

        return false
    }

    /**
     * Sends an Enter action.
     */
    fun sendEnter(inputConnection: InputConnection?) {
        if (inputConnection == null) return
        sendKeyEvent(inputConnection, KeyEvent.KEYCODE_ENTER)
    }

    /**
     * Moves cursor left by one character.
     */
    fun moveCursorLeft(inputConnection: InputConnection?) {
        if (inputConnection == null) return
        sendKeyEvent(inputConnection, KeyEvent.KEYCODE_DPAD_LEFT)
    }

    /**
     * Moves cursor right by one character.
     */
    fun moveCursorRight(inputConnection: InputConnection?) {
        if (inputConnection == null) return
        sendKeyEvent(inputConnection, KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    /**
     * Pastes text from the system clipboard.
     */
    fun pasteFromClipboard(context: Context, inputConnection: InputConnection?): Boolean {
        if (inputConnection == null) return false
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false

        if (clipboard.hasPrimaryClip() &&
            (clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true ||
             clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) == true)
        ) {
            val item = clipboard.primaryClip?.getItemAt(0)
            val textToPaste = item?.text?.toString()
            if (!textToPaste.isNullOrEmpty()) {
                inputConnection.commitText(textToPaste, 1)
                return true
            }
        }
        return false
    }

    /**
     * Moves cursor up by one line.
     */
    fun moveCursorUp(inputConnection: InputConnection?) {
        if (inputConnection == null) return
        sendKeyEvent(inputConnection, KeyEvent.KEYCODE_DPAD_UP)
    }

    /**
     * Moves cursor down by one line.
     */
    fun moveCursorDown(inputConnection: InputConnection?) {
        if (inputConnection == null) return
        sendKeyEvent(inputConnection, KeyEvent.KEYCODE_DPAD_DOWN)
    }

    /**
     * Selects all text in the active field.
     */
    fun selectAll(inputConnection: InputConnection?) {
        inputConnection?.performContextMenuAction(android.R.id.selectAll)
    }

    /**
     * Copies the currently selected text.
     */
    fun copySelectedText(inputConnection: InputConnection?) {
        inputConnection?.performContextMenuAction(android.R.id.copy)
    }

    /**
     * Cuts the currently selected text.
     */
    fun cutSelectedText(inputConnection: InputConnection?) {
        inputConnection?.performContextMenuAction(android.R.id.cut)
    }

    private fun sendKeyEvent(inputConnection: InputConnection, keyCode: Int) {
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        inputConnection.sendKeyEvent(downEvent)
        inputConnection.sendKeyEvent(upEvent)
    }
}
