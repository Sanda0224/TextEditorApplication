package com.example.texteditorapp

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.widget.EditText
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

data class LanguageConfig(
    val name: String,
    val keywords: List<String>,
    val lineComment: String?,              // e.g. // or #  (null = none)
    val blockCommentStart: String?,        // e.g. /* or """ (null = none)
    val blockCommentEnd: String?,          // e.g. */ or """ (null = none)
    val stringDelimiters: List<String>,    // e.g. ["\"", "'"]
    val numberRegex: String? = null        // optional custom number regex
)

class ConfigurableHighlighter(
    private val editText: EditText,
    private val config: LanguageConfig,
    keywordColorHex: String = "#1565C0",
    stringColorHex: String = "#2E7D32",
    numberColorHex: String = "#D32F2F",
    commentColorHex: String = "#9E9E9E"
) {
    private val keywordColor = Color.parseColor(keywordColorHex)
    private val stringColor = Color.parseColor(stringColorHex)
    private val numberColor = Color.parseColor(numberColorHex)
    private val commentColor = Color.parseColor(commentColorHex)

    // Build patterns from config
    private val keywordPattern: Pattern? = if (config.keywords.isNotEmpty()) {
        Pattern.compile("\\b(?:${config.keywords.joinToString("|") { Pattern.quote(it) }})\\b")
    } else null

    private val lineCommentPattern: Pattern? = config.lineComment?.let {
        Pattern.compile(Pattern.quote(it) + ".*?$", Pattern.MULTILINE)
    }

    private val blockCommentPattern: Pattern? =
        if (!config.blockCommentStart.isNullOrEmpty() && !config.blockCommentEnd.isNullOrEmpty()) {
            Pattern.compile(
                Pattern.quote(config.blockCommentStart) + "[\\s\\S]*?" + Pattern.quote(config.blockCommentEnd)
            )
        } else null

    private val stringPatterns: List<Pattern> =
        if (config.stringDelimiters.isNotEmpty()) {
            config.stringDelimiters.map { quote ->
                when (quote) {
                    "\"\"\"" -> Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"")
                    "'''" -> Pattern.compile("'''[\\s\\S]*?'''")
                    "\"" -> Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"")
                    "'" -> Pattern.compile("'(?:\\\\.|[^'\\\\])*'")
                    else -> Pattern.compile(
                        Pattern.quote(quote) + "(?:\\\\.|(?!"+ Pattern.quote(quote) +").)*" + Pattern.quote(quote)
                    )
                }
            }
        } else emptyList()

    private val numberPattern: Pattern? = config.numberRegex?.let { Pattern.compile(it) }
        ?: Pattern.compile("\\b(0[xX][0-9A-Fa-f_]+|\\d[\\d_]*(?:\\.\\d[\\d_]*)?(?:[eE][+-]?\\d+)?)\\b")

    private val handler = Handler(Looper.getMainLooper())
    private val highlightRunnable = Runnable { applyHighlighting() }

    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { schedule() }
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
        for (sp in spans) text.removeSpan(sp)
    }

    private fun colorize(pattern: Pattern?, color: Int) {
        pattern ?: return
        val text = editText.text
        val m = pattern.matcher(text)
        while (m.find()) {
            val start = m.start()
            val end = m.end()
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
        val selStart = editText.selectionStart
        val selEnd = editText.selectionEnd

        clearAllSpans()
        colorize(blockCommentPattern, commentColor)
        colorize(lineCommentPattern, commentColor)
        stringPatterns.forEach { colorize(it, stringColor) }
        colorize(numberPattern, numberColor)
        colorize(keywordPattern, keywordColor)

        if (selStart >= 0 && selEnd >= 0 &&
            selStart <= editText.length() && selEnd <= editText.length()) {
            editText.setSelection(selStart, selEnd)
        }
    }

    companion object {
        fun fromJson(json: String): LanguageConfig {
            val obj = JSONObject(json)
            val name = obj.optString("name", "custom")
            val keywords = obj.optJSONArray("keywords")?.toList() ?: emptyList()
            val lineComment = obj.optStringOrNull("lineComment")
            val blockStart = obj.optStringOrNull("blockCommentStart")
            val blockEnd = obj.optStringOrNull("blockCommentEnd")
            val strings = obj.optJSONArray("strings")?.toList() ?: listOf("\"", "'")
            val numberRegex = obj.optStringOrNull("numberRegex")
            return LanguageConfig(
                name = name,
                keywords = keywords,
                lineComment = lineComment,
                blockCommentStart = blockStart,
                blockCommentEnd = blockEnd,
                stringDelimiters = strings,
                numberRegex = numberRegex
            )
        }

        // JSONArray → List<String>
        private fun JSONArray.toList(): List<String> {
            val out = mutableListOf<String>()
            for (i in 0 until length()) out += optString(i)
            return out
        }

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (has(key) && !isNull(key)) optString(key) else null
    }
}
