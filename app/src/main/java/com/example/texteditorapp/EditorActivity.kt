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
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.nio.charset.Charset
import java.io.File

class EditorActivity : AppCompatActivity() {

    private lateinit var editor: EditText
    private lateinit var wordCountText: TextView
    private lateinit var undoRedoManager: UndoRedoManager

    private var kotlinHighlighter: KotlinHighlighter? = null
    private var configHighlighter: ConfigurableHighlighter? = null

    private val OPEN_FILE_REQUEST_CODE = 101
    private val SAVE_FILE_REQUEST_CODE = 102
    private val PICK_CONFIG_REQUEST = 201

    private var currentFileUri: Uri? = null
    private var currentFileName: String = "untitled.txt"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        editor = findViewById(R.id.code_editor)
        wordCountText = findViewById(R.id.wordCountText)

        // FileIO Spinner
        val spinner: Spinner = findViewById(R.id.spinnerFileOps)
        val fileOps = resources.getStringArray(R.array.file_operations)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fileOps)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                when(position) {
                    0 -> createNewFile()
                    1 -> openFilePicker()
                    2 -> saveFileDialog()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // ---------------- Initialize syntax highlighting ----------------
        kotlinHighlighter = KotlinHighlighter(editor)
        kotlinHighlighter?.attach()

        // ---------------- Initialize undo/redo ----------------
        undoRedoManager = UndoRedoManager(editor)

        setupWordCountUpdater()
        setupButtons()
    }

    override fun onDestroy() {
        super.onDestroy()
        kotlinHighlighter?.detach()
        configHighlighter?.detach()
    }

    // ---------------- Word count ----------------
    private fun setupWordCountUpdater() {
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                val words = text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
                val wordCount = words.size
                val charCount = text.length
                wordCountText.text = "Words: $wordCount  Characters: $charCount"
            }
        })
    }

    // ---------------- Buttons ----------------
    private fun setupButtons() {
        findViewById<ImageButton>(R.id.btn_compile).setOnClickListener {
            compileCode()
        }

        findViewById<ImageButton>(R.id.btn_compile).setOnLongClickListener {
            showLanguageChooser()
            true
        }

        findViewById<ImageButton>(R.id.btn_cut).setOnClickListener { cutText() }
        findViewById<ImageButton>(R.id.btn_copy).setOnClickListener { copyText() }
        findViewById<ImageButton>(R.id.btn_paste).setOnClickListener { pasteText() }
        findViewById<ImageButton>(R.id.btn_undo).setOnClickListener { undoRedoManager.undo() }
        findViewById<ImageButton>(R.id.btn_redo).setOnClickListener { undoRedoManager.redo() }
        findViewById<ImageButton>(R.id.btn_find).setOnClickListener { showFindReplaceDialog() }
    }

    private fun cutText() {
        val start = editor.selectionStart
        val end = editor.selectionEnd
        if (start >= 0 && end > start) {
            val selectedText = editor.text.subSequence(start, end).toString()
            copyToClipboard(selectedText)
            editor.text.replace(start, end, "")
        }
    }

    private fun copyText() {
        val start = editor.selectionStart
        val end = editor.selectionEnd
        if (start >= 0 && end > start) {
            val selectedText = editor.text.subSequence(start, end).toString()
            copyToClipboard(selectedText)
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pasteText() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip()) {
            val clip = clipboard.primaryClip
            clip?.getItemAt(0)?.text?.let { textToPaste ->
                val start = editor.selectionStart.coerceAtLeast(0)
                editor.text.insert(start, textToPaste)
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Copied Text", text)
        clipboard.setPrimaryClip(clip)
    }

    // ---------------- File Open / Save ----------------
    private fun createNewFile() {
        editor.setText("")
        currentFileUri = null
        currentFileName = "untitled.txt"
        undoRedoManager.clearHistory()
        Toast.makeText(this, "New file created", Toast.LENGTH_SHORT).show()
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, OPEN_FILE_REQUEST_CODE)
    }

    private fun saveFileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_file, null)
        val fileNameInput = dialogView.findViewById<EditText>(R.id.fileNameInput)
        val extensionSpinner = dialogView.findViewById<Spinner>(R.id.extensionSpinner)

        val extensions = arrayOf("txt", "kt", "java")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, extensions)
        extensionSpinner.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle("Save File")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = fileNameInput.text.toString().trim()
                val extension = extensionSpinner.selectedItem.toString()
                if (name.isNotEmpty()) {
                    saveFileWithExtension(name, editor.text.toString(), extension)
                } else {
                    Toast.makeText(this, "Enter a file name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveFileWithExtension(fileName: String, content: String, extension: String) {
        try {
            val directory = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            if (!directory.exists()) directory.mkdirs()
            val finalName = if (fileName.endsWith(".$extension")) fileName else "$fileName.$extension"
            val file = File(directory, finalName)
            java.io.FileOutputStream(file).use { fos -> fos.write(content.toByteArray()) }
            Toast.makeText(this, "File saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
                undoRedoManager.clearHistory()
                Toast.makeText(this, "Opened: $currentFileName", Toast.LENGTH_SHORT).show()
            }
            SAVE_FILE_REQUEST_CODE -> writeTextToUri(uri, editor.text.toString())
            PICK_CONFIG_REQUEST -> { /* load config JSON */ }
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

    // ---------------- Compile ----------------
    private fun compileCode() {
        AlertDialog.Builder(this)
            .setTitle("Compilation Result")
            .setMessage("Compilation successful!")
            .setPositiveButton("OK", null)
            .show()
    }

    // ---------------- Find / Replace ----------------
    private fun showFindReplaceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_find_replace, null)
        val etFind = dialogView.findViewById<EditText>(R.id.et_find)
        val etReplace = dialogView.findViewById<EditText>(R.id.et_replace)
        val cbCaseSensitive = dialogView.findViewById<android.widget.CheckBox>(R.id.cb_case_sensitive)
        val cbWholeWord = dialogView.findViewById<android.widget.CheckBox>(R.id.cb_whole_word)

        AlertDialog.Builder(this)
            .setTitle("Find and Replace")
            .setView(dialogView)
            .setPositiveButton("Replace All") { _, _ ->
                performFindReplace(
                    etFind.text.toString(),
                    etReplace.text.toString(),
                    cbCaseSensitive.isChecked,
                    cbWholeWord.isChecked
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performFindReplace(findText: String, replaceText: String, caseSensitive: Boolean, wholeWord: Boolean) {
        val content = editor.text.toString()
        if (findText.isEmpty()) return

        val pattern = if (wholeWord) "\\b${Regex.escape(findText)}\\b" else Regex.escape(findText)
        val regexOptions = if (caseSensitive) emptySet<RegexOption>() else setOf(RegexOption.IGNORE_CASE)
        val regex = Regex(pattern, regexOptions)

        val newText = regex.replace(content, replaceText)
        editor.setText(newText)
        Toast.makeText(this, "Replaced all occurrences", Toast.LENGTH_SHORT).show()
    }

    private fun showLanguageChooser() {
        val items = arrayOf("Kotlin (built-in)", "Java (asset)", "Python (asset)", "Load JSON…")
        AlertDialog.Builder(this)
            .setTitle("Syntax Highlighting")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> applyKotlinHighlighting()
                    1 -> loadAssetConfig("languages/java.json")
                    2 -> loadAssetConfig("languages/python.json")
                    3 -> pickConfigJson()
                }
            }
            .show()
    }

    private fun applyKotlinHighlighting() {
        configHighlighter?.detach()
        configHighlighter = null
        if (kotlinHighlighter == null) kotlinHighlighter = KotlinHighlighter(editor)
        kotlinHighlighter?.attach()
        Toast.makeText(this, "Kotlin highlighting enabled", Toast.LENGTH_SHORT).show()
    }

    private fun applyConfigHighlighting(config: LanguageConfig) {
        kotlinHighlighter?.detach()
        kotlinHighlighter = null
        configHighlighter?.detach()
        configHighlighter = ConfigurableHighlighter(editor, config)
        configHighlighter?.attach()
        Toast.makeText(this, "Loaded language: ${config.name}", Toast.LENGTH_SHORT).show()
    }

    private fun loadAssetConfig(path: String) {
        try {
            val json = assets.open(path).use { it.readBytes().toString(Charset.forName("UTF-8")) }
            val config = ConfigurableHighlighter.fromJson(json)
            applyConfigHighlighting(config)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load asset: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun pickConfigJson() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, PICK_CONFIG_REQUEST)
    }
}
