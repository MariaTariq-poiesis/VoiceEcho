package com.example.voiceecho.ui.trim

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.example.voiceecho.R
import com.example.voiceecho.audio.AudioAmplitudeExtractor
import com.example.voiceecho.audio.AudioTrimmer
import com.example.voiceecho.ui.record.TrimWaveformView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class TrimActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var sourcePath: String? = null
    private var durationMs: Long = 0

    private lateinit var trimWaveformView: TrimWaveformView
    private lateinit var tvSelectedDuration: TextView
    private lateinit var btnPlayTrim: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trim)

        sourcePath = intent.getStringExtra(EXTRA_AUDIO_PATH)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = null
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        trimWaveformView = findViewById(R.id.trimWaveformView)
        tvSelectedDuration = findViewById(R.id.tvSelectedDuration)
        btnPlayTrim = findViewById(R.id.btnPlayTrim)

        trimWaveformView.onTrimChanged = { start, end ->
            updateSelectedDurationLabel(start, end)
        }

        btnPlayTrim.setOnClickListener { togglePlayback() }
        findViewById<ImageButton>(R.id.btnRewind10).setOnClickListener { seekBy(-10000) }
        findViewById<ImageButton>(R.id.btnForward10).setOnClickListener { seekBy(10000) }
        findViewById<TextView>(R.id.btnTrimSelection).setOnClickListener { performTrim() }

        loadWaveformAndDuration()
    }

    private fun loadWaveformAndDuration() {
        val path = sourcePath ?: return
        val file = File(path)

        lifecycleScope.launch {
            val amplitudes = withContext(Dispatchers.IO) {
                AudioAmplitudeExtractor.extractAmplitudes(file, bucketCount = 60)
            }
            durationMs = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                val d = try {
                    retriever.setDataSource(path)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    0L
                } finally {
                    retriever.release()
                }
                d
            }
            trimWaveformView.setAmplitudes(amplitudes)
            updateSelectedDurationLabel(trimWaveformView.startFraction, trimWaveformView.endFraction)
        }
    }

    private fun updateSelectedDurationLabel(startFraction: Float, endFraction: Float) {
        val selectedMs = ((endFraction - startFraction) * durationMs).toLong()
        tvSelectedDuration.text = formatTime((selectedMs / 1000).toInt())
    }

    private fun togglePlayback() {
        val path = sourcePath ?: return

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    this@TrimActivity.isPlaying = false
                    btnPlayTrim.setImageResource(android.R.drawable.ic_media_play)
                }
            }
        }

        if (isPlaying) {
            mediaPlayer?.pause()
            btnPlayTrim.setImageResource(android.R.drawable.ic_media_play)
        } else {
            val startMs = (trimWaveformView.startFraction * durationMs).toInt()
            mediaPlayer?.seekTo(startMs)
            mediaPlayer?.start()
            btnPlayTrim.setImageResource(android.R.drawable.ic_media_pause)
        }
        isPlaying = !isPlaying
    }

    private fun seekBy(deltaMs: Int) {
        val mp = mediaPlayer ?: return
        val newPos = (mp.currentPosition + deltaMs).coerceIn(0, mp.duration)
        mp.seekTo(newPos)
    }

    private fun performTrim() {
        val inputPath = sourcePath ?: return
        val startMs = (trimWaveformView.startFraction * durationMs).toLong()
        val endMs = (trimWaveformView.endFraction * durationMs).toLong()

        Toast.makeText(this, "Trimming…", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val outputFile = withContext(Dispatchers.IO) {
                try {
                    val inputFile = File(inputPath)
                    val outDir = File(getExternalFilesDir(null), "recordings")
                    if (!outDir.exists()) outDir.mkdirs()
                    val outFile = File(outDir, "Trimmed_${System.currentTimeMillis()}.wav")
                    AudioTrimmer.trim(inputFile, outFile, startMs, endMs)
                    outFile
                } catch (e: Exception) {
                    null
                }
            }

            if (outputFile != null) {
                Toast.makeText(this@TrimActivity, "Trimmed file saved: ${outputFile.name}", Toast.LENGTH_LONG).show()
                finish()
            } else {
                Toast.makeText(this@TrimActivity, "Trim failed", Toast.LENGTH_SHORT).show()
            }
        }
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

    companion object {
        const val EXTRA_AUDIO_PATH = "extra_audio_path"
    }
}