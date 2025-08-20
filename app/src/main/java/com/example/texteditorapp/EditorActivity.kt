package com.example.texteditorapp

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class EditorActivity : AppCompatActivity() {

    private lateinit var editor: EditText
    private lateinit var wordCountText: TextView
    private lateinit var undoRedoManager: UndoRedoManager

    private lateinit var kotlinHighlighter: KotlinHighlighter

    private val OPEN_FILE_REQUEST_CODE = 101
    private val SAVE_FILE_REQUEST_CODE = 102

    private var currentFileUri: Uri? = null
    private var currentFileName: String = "untitled.txt"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        editor = findViewById(R.id.code_editor)
        wordCountText = findViewById(R.id.wordCountText)

        kotlinHighlighter = KotlinHighlighter(editor)
        kotlinHighlighter.start()

        // Create undo/redo manager and attach to editText
        undoRedoManager = UndoRedoManager(editor)

        setupWordCountUpdater()
        setupButtons()
    }

    override fun onDestroy() {
        super.onDestroy()
        // optional, cleanup
        kotlinHighlighter.stop()
    }

    private fun setupWordCountUpdater() {
        // Keep word/char count updated
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Undo/Redo manager handles history internally, nothing needed here
            }

            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                val words = text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
                val wordCount = words.size
                val charCount = text.length
                wordCountText.text = "Words: $wordCount  Characters: $charCount"
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupButtons() {
        findViewById<ImageButton>(R.id.btn_new).setOnClickListener {
            editor.setText("")
            currentFileUri = null
            currentFileName = "untitled.txt"
            undoRedoManager.clearHistory()
            Toast.makeText(this, "New file created", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.btn_open).setOnClickListener {
            openFilePicker()
        }

        findViewById<ImageButton>(R.id.btn_save).setOnClickListener {
            saveFilePicker()
        }

        findViewById<ImageButton>(R.id.btn_compile).setOnClickListener {
            compileCode()
        }

        findViewById<ImageButton>(R.id.btn_cut).setOnClickListener {
            val start = editor.selectionStart
            val end = editor.selectionEnd
            if (start >= 0 && end > start) {
                val selectedText = editor.text.subSequence(start, end).toString()
                copyToClipboard(selectedText)
                editor.text.replace(start, end, "")
            }
        }

        findViewById<ImageButton>(R.id.btn_copy).setOnClickListener {
            val start = editor.selectionStart
            val end = editor.selectionEnd
            if (start >= 0 && end > start) {
                val selectedText = editor.text.subSequence(start, end).toString()
                copyToClipboard(selectedText)
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<ImageButton>(R.id.btn_paste).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val clip = clipboard.primaryClip
                clip?.getItemAt(0)?.text?.let { textToPaste ->
                    val start = editor.selectionStart.coerceAtLeast(0)
                    editor.text.insert(start, textToPaste)
                }
            }
        }

        findViewById<ImageButton>(R.id.btn_undo).setOnClickListener {
            undoRedoManager.undo()
        }

        findViewById<ImageButton>(R.id.btn_redo).setOnClickListener {
            undoRedoManager.redo()
        }

        findViewById<ImageButton>(R.id.btn_find).setOnClickListener {
            showFindReplaceDialog()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Copied Text", text)
        clipboard.setPrimaryClip(clip)
    }

    // ---------------------- SAF open/save ----------------------

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, OPEN_FILE_REQUEST_CODE)
    }

    private fun saveFilePicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, currentFileName)
        }
        startActivityForResult(intent, SAVE_FILE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val uri = data.data!!

        when (requestCode) {
            OPEN_FILE_REQUEST_CODE -> {
                currentFileUri = uri
                readTextFromUri(uri)
                currentFileName = getFileNameFromUri(uri) ?: "untitled.txt"
                undoRedoManager.clearHistory() // reset history after loading file
                Toast.makeText(this, "Opened: $currentFileName", Toast.LENGTH_SHORT).show()
            }
            SAVE_FILE_REQUEST_CODE -> {
                currentFileUri = uri
                writeTextToUri(uri, editor.text.toString())
                // Update filename from URI if available
                currentFileName = getFileNameFromUri(uri) ?: currentFileName
                Toast.makeText(this, "Saved as $currentFileName", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun readTextFromUri(uri: Uri) {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            val content = inputStream.bufferedReader().readText()
            editor.setText(content)
        }
    }

    private fun writeTextToUri(uri: Uri, text: String) {
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(text.toByteArray())
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    // ---------------------- Compile stub ----------------------

    private fun compileCode() {
        AlertDialog.Builder(this)
            .setTitle("Compilation Result")
            .setMessage("Compilation successful! (Simulation)")
            .setPositiveButton("OK", null)
            .show()
    }

    // ---------------------- Find & Replace ----------------------

    private fun showFindReplaceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_find_replace, null)
        val etFind = dialogView.findViewById<EditText>(R.id.et_find)
        val etReplace = dialogView.findViewById<EditText>(R.id.et_replace)
        val cbCaseSensitive =
            dialogView.findViewById<android.widget.CheckBox>(R.id.cb_case_sensitive)
        val cbWholeWord = dialogView.findViewById<android.widget.CheckBox>(R.id.cb_whole_word)

        AlertDialog.Builder(this)
            .setTitle("Find and Replace")
            .setView(dialogView)
            .setPositiveButton("Replace All") { _, _ ->
                val findText = etFind.text.toString()
                val replaceText = etReplace.text.toString()
                val caseSensitive = cbCaseSensitive.isChecked
                val wholeWord = cbWholeWord.isChecked
                performFindReplace(findText, replaceText, caseSensitive, wholeWord)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performFindReplace(
        findText: String,
        replaceText: String,
        caseSensitive: Boolean,
        wholeWord: Boolean
    ) {
        val content = editor.text.toString()
        if (findText.isEmpty()) return

        val pattern = if (wholeWord) "\\b${Regex.escape(findText)}\\b" else Regex.escape(findText)
        val regexOptions = if (caseSensitive) emptySet<RegexOption>() else setOf(RegexOption.IGNORE_CASE)
        val regex = Regex(pattern, regexOptions)

        val newText = regex.replace(content, replaceText)
        editor.setText(newText)
        Toast.makeText(this, "Replaced all occurrences", Toast.LENGTH_SHORT).show()
    }
}
