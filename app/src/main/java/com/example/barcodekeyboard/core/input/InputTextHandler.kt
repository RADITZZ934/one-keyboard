package com.example.barcodekeyboard.core.input

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.KeyEvent
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

    private fun sendKeyEvent(inputConnection: InputConnection, keyCode: Int) {
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        inputConnection.sendKeyEvent(downEvent)
        inputConnection.sendKeyEvent(upEvent)
    }
}
