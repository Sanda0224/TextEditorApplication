package com.example.texteditorapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Make whole screen clickable to open EditorActivity
        findViewById<View>(R.id.appLogo).setOnClickListener {
            openEditor()
        }
        findViewById<View>(R.id.appName).setOnClickListener {
            openEditor()
        }
    }

    private fun openEditor() {
        val intent = Intent(this, EditorActivity::class.java)
        startActivity(intent)
    }
}


