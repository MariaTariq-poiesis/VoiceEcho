package com.example.voiceecho.ui.record

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.voiceecho.R
import com.example.voiceecho.audio.AudioRecorderManager
import com.example.voiceecho.ui.playback.PlaybackActivity
import java.io.File
import java.util.Locale

class RecordActivity : AppCompatActivity() {

    private lateinit var recorderManager: AudioRecorderManager
    private lateinit var waveformView: WaveformView
    private lateinit var tvTimer: TextView
    private lateinit var btnToggleRecord: ImageButton
    private lateinit var btnCancel: ImageButton
    private lateinit var btnConfirm: ImageButton

    private val handler = Handler(Looper.getMainLooper())

    private var isRecording = false
    private var isPaused = false

    private var recordingStartElapsedMs = 0L
    private var pausedAccumulatedMs = 0L
    private var lastPauseStartMs = 0L
    private var lastDisplayedSecond = -1

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (isRecording && !isPaused) {
                val nowElapsedMs = SystemClock.elapsedRealtime()
                val totalMs = nowElapsedMs - recordingStartElapsedMs - pausedAccumulatedMs
                val totalSeconds = (totalMs / 1000L).toInt()

                if (totalSeconds != lastDisplayedSecond) {
                    lastDisplayedSecond = totalSeconds
                    tvTimer.text = formatTime(totalSeconds)
                }

                val amplitude = recorderManager.getMaxAmplitude()
                val normalized = (amplitude / 32767f).coerceIn(0f, 1f)
                waveformView.addAmplitude(normalized)
            }
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record)

        waveformView = findViewById(R.id.waveformView)
        tvTimer = findViewById(R.id.tvTimer)
        btnToggleRecord = findViewById(R.id.btnToggleRecord)
        btnCancel = findViewById(R.id.btnCancel)
        btnConfirm = findViewById(R.id.btnConfirm)

        val outputDir = File(getExternalFilesDir(null), "recordings")
        recorderManager = AudioRecorderManager(this, outputDir)

        startRecording()

        btnToggleRecord.setOnClickListener { toggleRecording() }
        btnCancel.setOnClickListener { cancelRecording() }
        btnConfirm.setOnClickListener { confirmRecording() }
    }

    private fun startRecording() {
        recorderManager.start()
        isRecording = true
        isPaused = false
        recordingStartElapsedMs = SystemClock.elapsedRealtime()
        pausedAccumulatedMs = 0L
        lastDisplayedSecond = -1
        btnToggleRecord.setImageResource(android.R.drawable.ic_media_pause)
        handler.post(tickRunnable)
    }

    private fun toggleRecording() {
        if (!isPaused) {
            recorderManager.pause()
            isPaused = true
            lastPauseStartMs = SystemClock.elapsedRealtime()
            btnToggleRecord.setImageResource(android.R.drawable.ic_media_play)
        } else {
            recorderManager.resume()
            isPaused = false
            pausedAccumulatedMs += SystemClock.elapsedRealtime() - lastPauseStartMs
            btnToggleRecord.setImageResource(android.R.drawable.ic_media_pause)
        }
    }

    private fun cancelRecording() {
        handler.removeCallbacks(tickRunnable)
        recorderManager.cancel()
        finish()
    }

    private fun confirmRecording() {
        handler.removeCallbacks(tickRunnable)
        val savedFile = recorderManager.stopAndSave()
        isRecording = false

        if (savedFile != null) {
            val intent = Intent(this, PlaybackActivity::class.java)
            intent.putExtra(EXTRA_AUDIO_PATH, savedFile.absolutePath)
            startActivity(intent)
        }
        finish()
    }

    private fun formatTime(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        if (isRecording) {
            recorderManager.cancel()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_AUDIO_PATH = "extra_audio_path"
    }
}