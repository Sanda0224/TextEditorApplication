package com.example.texteditorapp

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

/**
 * Simple Undo/Redo manager for an EditText.
 * Stores full-text snapshots (simple & robust).
 */
class UndoRedoManager(
    private val editText: EditText,
    private val maxStackSize: Int = 200
) {
    private val undoStack = ArrayList<String>()
    private val redoStack = ArrayList<String>()
    private var isUndoOrRedo = false

    init {
        // Push initial state
        undoStack.add(editText.text.toString())

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!isUndoOrRedo) {
                    // Save current text to undo stack
                    if (undoStack.size >= maxStackSize) {
                        // drop oldest
                        undoStack.removeAt(0)
                    }
                    undoStack.add(s?.toString() ?: "")
                    // clear redo on new edit
                    redoStack.clear()
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    fun undo() {
        if (undoStack.size > 1) {
            isUndoOrRedo = true
            // current state is last element; move it to redo
            val current = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(current)
            // new top of undoStack is the state to restore
            val prev = undoStack[undoStack.size - 1]
            editText.setText(prev)
            editText.setSelection(prev.length.coerceAtMost(editText.text.length))
            isUndoOrRedo = false
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            isUndoOrRedo = true
            val textToRestore = redoStack.removeAt(redoStack.size - 1)
            // push current (before redo) to undo
            undoStack.add(textToRestore)
            editText.setText(textToRestore)
            editText.setSelection(textToRestore.length.coerceAtMost(editText.text.length))
            isUndoOrRedo = false
        }
    }

    fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
        undoStack.add(editText.text.toString())
    }
}
