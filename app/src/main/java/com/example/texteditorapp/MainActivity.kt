package com.example.texteditorapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.os.Handler
import android.os.Looper

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Splash screen
        Handler(Looper.getMainLooper()).postDelayed({
            openEditor()
        }, 2000) // 2000 = 2 seconds delay
    }

    private fun openEditor() {
        val intent = Intent(this, EditorActivity::class.java)
        startActivity(intent)
        finish()
    }
}


