package com.example.voiceecho.ui.playback

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.example.voiceecho.R
import com.example.voiceecho.audio.AudioAmplitudeExtractor
import com.example.voiceecho.ui.record.RecordActivity
import com.example.voiceecho.ui.record.WaveformView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class PlaybackActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var audioPath: String? = null
    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback)

        audioPath = intent.getStringExtra(RecordActivity.EXTRA_AUDIO_PATH)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = null

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val tvFileName = findViewById<TextView>(R.id.tvFileName)
        tvFileName.text = File(audioPath ?: "recording").name

        val waveform = findViewById<WaveformView>(R.id.waveformStatic)
        val tvDuration = findViewById<TextView>(R.id.tvDuration)
        val btnPlay = findViewById<ImageButton>(R.id.btnPlay)

        loadWaveformAndDuration(waveform, tvDuration)

        btnPlay.setOnClickListener {
            togglePlayback(btnPlay, tvDuration)
        }

        findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.rowChangeVoice)
            .setOnClickListener {
                val intent = Intent(this, com.example.voiceecho.ui.effects.ApplyEffectsActivity::class.java)
                intent.putExtra(com.example.voiceecho.ui.effects.ApplyEffectsActivity.EXTRA_AUDIO_PATH, audioPath)
                startActivity(intent)
            }

        findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.rowTrimVoice)
            .setOnClickListener {
                val intent = Intent(this, com.example.voiceecho.ui.trim.TrimActivity::class.java)
                intent.putExtra(com.example.voiceecho.ui.trim.TrimActivity.EXTRA_AUDIO_PATH, audioPath)
                startActivity(intent)
            }

        findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.rowRecordNew)
            .setOnClickListener {
                startActivity(Intent(this, RecordActivity::class.java))
                finish()
            }
    }

    private fun loadWaveformAndDuration(waveform: WaveformView, tvDuration: TextView) {
        val path = audioPath ?: return
        val file = File(path)

        lifecycleScope.launch {
            val amplitudes = withContext(Dispatchers.IO) {
                AudioAmplitudeExtractor.extractAmplitudes(file, bucketCount = 45)
            }
            val durationSeconds = withContext(Dispatchers.IO) {
                getDurationSeconds(path)
            }

            waveform.setStaticAmplitudes(amplitudes)
            tvDuration.text = formatTime(durationSeconds)
        }
    }

    private fun getDurationSeconds(path: String): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            (durationMs / 1000).toInt()
        } catch (e: Exception) {
            0
        } finally {
            retriever.release()
        }
    }

    private fun togglePlayback(btnPlay: ImageButton, tvDuration: TextView) {
        val path = audioPath ?: return

        if (isPlaying) {
            mediaPlayer?.pause()
            btnPlay.setImageResource(android.R.drawable.ic_media_play)
            isPlaying = false
            return
        }

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    btnPlay.setImageResource(android.R.drawable.ic_media_play)
                    this@PlaybackActivity.isPlaying = false
                }
            }
        }

        mediaPlayer?.start()
        btnPlay.setImageResource(android.R.drawable.ic_media_pause)
        isPlaying = true
    }

    private fun formatTime(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}