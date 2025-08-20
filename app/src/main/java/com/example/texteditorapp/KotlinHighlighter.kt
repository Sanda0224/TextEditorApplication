package com.example.texteditorapp

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.widget.EditText
import androidx.core.content.ContextCompat
import java.util.regex.Pattern

/**
 * Lightweight Kotlin syntax highlighter for EditText (no libraries).
 * Applies color spans with a small debounce for performance.
 */
class KotlinHighlighter(
    private val editText: EditText,
    private val config: Config = Config.from(editText)
) {

    data class Config(
        val keywordColor: Int,
        val stringColor: Int,
        val commentColor: Int,
        val numberColor: Int,
        val typeColor: Int,
        val functionColor: Int,
        val annotationColor: Int,
        val delayMs: Long = 120L
    ) {
        companion object {
            fun from(view: EditText) = Config(
                keywordColor = ContextCompat.getColor(view.context, R.color.code_keyword),
                stringColor = ContextCompat.getColor(view.context, R.color.code_string),
                commentColor = ContextCompat.getColor(view.context, R.color.code_comment),
                numberColor = ContextCompat.getColor(view.context, R.color.code_number),
                typeColor = ContextCompat.getColor(view.context, R.color.code_type),
                functionColor = ContextCompat.getColor(view.context, R.color.code_function),
                annotationColor = ContextCompat.getColor(view.context, R.color.code_annotation)
            )
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isHighlighting = false

    // Compile patterns once
    private object P {
        // Strings
        val TRIPLE_STRING: Pattern =
            Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"", Pattern.MULTILINE)
        val STRING: Pattern =
            Pattern.compile("\"([^\"\\\\]|\\\\.)*\"")

        // Comments
        val LINE_COMMENT: Pattern =
            Pattern.compile("//.*", Pattern.MULTILINE)
        val BLOCK_COMMENT: Pattern =
            Pattern.compile("/\\*[\\s\\S]*?\\*/")

        // Annotations
        val ANNOTATION: Pattern =
            Pattern.compile("@[A-Za-z_]\\w*")

        // Numbers (supports underscores and decimals/exponents)
        val NUMBER: Pattern =
            Pattern.compile("\\b\\d+(?:_\\d+)*(?:\\.\\d+(?:_\\d+)*)?(?:[eE][+-]?\\d+)?[fFdDlL]?\\b")

        // Kotlin keywords
        private const val KW = "(abstract|annotation|as|break|by|catch|class|companion|const|constructor|continue|" +
                "crossinline|data|do|else|enum|expect|external|false|final|finally|for|fun|get|if|import|in|infix|" +
                "init|inline|inner|interface|internal|is|it|lateinit|noinline|null|object|open|operator|out|override|" +
                "package|private|protected|public|reified|return|sealed|set|super|suspend|tailrec|this|throw|true|try|" +
                "typealias|val|var|when|where|while)"
        val KEYWORDS: Pattern = Pattern.compile("\\b$KW\\b")

        // Common types (you can expand)
        val TYPES: Pattern = Pattern.compile(
            "\\b(Boolean|Byte|Short|Int|Long|Float|Double|Char|String|Unit|Any|Nothing|Array|List|MutableList|Set|MutableSet|Map|MutableMap)\\b"
        )

        // Function names right after `fun `
        val FUN_NAME: Pattern = Pattern.compile("\\bfun\\s+([A-Za-z_]\\w*)")    }

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            schedule()
        }
        override fun afterTextChanged(s: Editable?) {}
    }

    fun start() {
        editText.addTextChangedListener(watcher)
        schedule()
    }

    fun stop() {
        editText.removeTextChangedListener(watcher)
        handler.removeCallbacksAndMessages(null)
    }

    private fun schedule() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ highlightNow() }, config.delayMs)
    }

    private fun highlightNow() {
        if (isHighlighting) return
        val editable = editText.editableText ?: return
        isHighlighting = true
        try {
            // 1) clear previous ForegroundColorSpans
            val spans = editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
            for (span in spans) editable.removeSpan(span)

            // 2) apply spans in priority order (later ones override visually)
            colorMatches(editable, P.KEYWORDS, config.keywordColor)
            colorMatches(editable, P.TYPES, config.typeColor)
            colorMatches(editable, P.NUMBER, config.numberColor)
            colorMatches(editable, P.ANNOTATION, config.annotationColor)
            colorMatches(editable, P.FUN_NAME, config.functionColor)

            // Strings & comments last so they override any inner matches
            colorMatches(editable, P.TRIPLE_STRING, config.stringColor)
            colorMatches(editable, P.STRING, config.stringColor)
            colorMatches(editable, P.BLOCK_COMMENT, config.commentColor)
            colorMatches(editable, P.LINE_COMMENT, config.commentColor)
        } finally {
            isHighlighting = false
        }
    }

    private fun colorMatches(
        text: Editable,
        pattern: Pattern,
        color: Int
    ) {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            text.setSpan(
                ForegroundColorSpan(color),
                matcher.start(),
                matcher.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
}
