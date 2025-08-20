package com.example.texteditorapp

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.widget.EditText
import java.util.regex.Pattern

/**
 * Lightweight Kotlin syntax highlighter for an EditText.
 * No text replacement, spans only, debounced for performance.
 */
class KotlinHighlighter(
    private val editText: EditText,
    keywordColorHex: String = "#1565C0",   // blue
    stringColorHex: String = "#2E7D32",    // green
    numberColorHex: String = "#D32F2F",    // red
    commentColorHex: String = "#9E9E9E",   // gray
    fnColorHex: String = "#8E24AA"         // purple
) {

    private val keywordColor = Color.parseColor(keywordColorHex)
    private val stringColor = Color.parseColor(stringColorHex)
    private val numberColor = Color.parseColor(numberColorHex)
    private val commentColor = Color.parseColor(commentColorHex)
    private val fnColor = Color.parseColor(fnColorHex)

    // --- Patterns ---
    private val KEYWORDS = listOf(
        "as","break","class","continue","do","else","false","for","fun","if","in","interface",
        "is","null","object","package","return","super","this","throw","true","try","typealias",
        "typeof","val","var","when","while","by","catch","constructor","delegate","dynamic",
        "field","file","finally","get","import","init","param","private","public","protected",
        "set","setparam","where","actual","abstract","annotation","companion","const","crossinline",
        "data","enum","expect","external","final","infix","inline","inner","internal","lateinit",
        "noinline","open","operator","out","override","reified","sealed","suspend","tailrec","vararg"
    )

    private val KEYWORD_PATTERN = Pattern.compile(
        "\\b(?:${KEYWORDS.joinToString("|") { Pattern.quote(it) }})\\b"
    )

    // Strings: "..." and '...' with escapes; triple-quoted strings """..."""
    private val STRING_PATTERN = Pattern.compile(
        // triple quoted OR normal double OR single
        "(\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*)"
    )

    // Line & block comments
    private val LINE_COMMENT_PATTERN = Pattern.compile("//.*?$", Pattern.MULTILINE)
    private val BLOCK_COMMENT_PATTERN = Pattern.compile("/\\*[\\s\\S]*?\\*/")

    // Numbers (decimal, hex, underscores)
    private val NUMBER_PATTERN = Pattern.compile(
        "\\b(0[xX][0-9A-Fa-f_]+|\\d[\\d_]*(?:\\.\\d[\\d_]*)?(?:[eE][+-]?\\d+)?)\\b"
    )

    // Function names after `fun`
    private val FUN_NAME_PATTERN = Pattern.compile("\\bfun\\s+([A-Za-z_]\\w*)")

    // Debounce to avoid re-highlighting every keystroke
    private val handler = Handler(Looper.getMainLooper())
    private val highlightRunnable = Runnable { applyHighlighting() }

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            schedule()
        }
        override fun afterTextChanged(s: Editable?) {}
    }

    fun attach() {
        editText.addTextChangedListener(watcher)
        schedule()
    }

    fun detach() {
        editText.removeTextChangedListener(watcher)
        handler.removeCallbacks(highlightRunnable)
        clearAllSpans()
    }

    private fun schedule() {
        handler.removeCallbacks(highlightRunnable)
        handler.postDelayed(highlightRunnable, 120)
    }

    private fun clearAllSpans() {
        val text = editText.text
        val spans = text.getSpans(0, text.length, ForegroundColorSpan::class.java)
        for (sp in spans) {
            text.removeSpan(sp)
        }
    }

    private fun colorize(pattern: Pattern, color: Int, groupIndex: Int = 0) {
        val text = editText.text
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val start = matcher.start(if (groupIndex == 0) 0 else groupIndex)
            val end = matcher.end(if (groupIndex == 0) 0 else groupIndex)
            if (start in 0..end && end <= text.length) {
                text.setSpan(
                    ForegroundColorSpan(color),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
    }

    private fun applyHighlighting() {
        // Preserve cursor/selection
        val selStart = editText.selectionStart
        val selEnd = editText.selectionEnd

        clearAllSpans()

        colorize(BLOCK_COMMENT_PATTERN, commentColor)
        colorize(LINE_COMMENT_PATTERN, commentColor)
        colorize(STRING_PATTERN, stringColor)
        colorize(NUMBER_PATTERN, numberColor)
        colorize(KEYWORD_PATTERN, keywordColor)
        // Function name is group(1)
        colorize(FUN_NAME_PATTERN, fnColor, groupIndex = 1)

        // Restore selection
        if (selStart >= 0 && selEnd >= 0 && selStart <= editText.length() && selEnd <= editText.length()) {
            editText.setSelection(selStart, selEnd)
        }
    }
}
