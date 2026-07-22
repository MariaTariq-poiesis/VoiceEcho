package com.example.voiceecho

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.voiceecho.ui.record.RecordActivity
import com.example.voiceecho.utils.PermissionUtils

class MainActivity : AppCompatActivity() {

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                onMicPermissionGranted()
            } else {
                Toast.makeText(
                    this,
                    "Microphone access is required to record your voice.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupHomeScreen()
        checkMicPermission()
    }

    private fun checkMicPermission() {
        if (PermissionUtils.hasMicPermission(this)) {
            onMicPermissionGranted()
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun onMicPermissionGranted() {
        // Mic is ready. Nothing extra needed here — buttons check permission at tap time too.
    }

    private fun setupHomeScreen() {
        val btnRecord = findViewById<ImageButton>(R.id.btnRecord)
        val btnVideo = findViewById<ImageButton>(R.id.btnVideo)

        btnRecord.setOnClickListener {
            if (PermissionUtils.hasMicPermission(this)) {
                startActivity(Intent(this, RecordActivity::class.java))
            } else {
                requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        btnVideo.setOnClickListener {
            Toast.makeText(this, "Video recording not built yet", Toast.LENGTH_SHORT).show()
        }
    }
}