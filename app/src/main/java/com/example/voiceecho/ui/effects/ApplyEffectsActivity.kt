package com.example.voiceecho.ui.effects

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.voiceecho.R
import com.example.voiceecho.audio.VoiceEffectProcessor
import com.example.voiceecho.data.AmbientSounds
import com.example.voiceecho.data.VoiceEffect
import com.example.voiceecho.data.VoiceEffects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class ApplyEffectsActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var sourcePath: String? = null

    private lateinit var effectsAdapter: EffectsAdapter
    private lateinit var recyclerEffects: RecyclerView
    private lateinit var tabVoiceEffects: TextView
    private lateinit var tabAmbientSounds: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnPlayPreview: ImageButton

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { mp ->
                if (isPlaying) {
                    val current = mp.currentPosition
                    val total = mp.duration.coerceAtLeast(1)
                    progressBar.progress = (current * 100) / total
                    tvCurrentTime.text = formatTime(current / 1000)
                }
            }
            handler.postDelayed(this, 200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apply_effects)

        // sourcePath must ALWAYS be the original raw recording. Every caller of this
        // Activity (PlaybackActivity's "Change Voice" row, and FileSavedActivity's
        // "Change" button) is responsible for passing the ORIGINAL file here, never
        // an already-effect-processed file, so effects never stack on top of each other.
        sourcePath = intent.getStringExtra(EXTRA_AUDIO_PATH)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = null

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnSave).setOnClickListener { saveWithEffect() }

        progressBar = findViewById(R.id.progressBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        btnPlayPreview = findViewById(R.id.btnPlayPreview)
        btnPlayPreview.setOnClickListener { togglePreviewPlayback() }

        findViewById<ImageButton>(R.id.btnScissors).setOnClickListener {
            val intent = android.content.Intent(this, com.example.voiceecho.ui.trim.TrimActivity::class.java)
            intent.putExtra(com.example.voiceecho.ui.trim.TrimActivity.EXTRA_AUDIO_PATH, sourcePath)
            startActivity(intent)
        }

        recyclerEffects = findViewById(R.id.recyclerEffects)
        recyclerEffects.layoutManager = GridLayoutManager(this, 3)

        tabVoiceEffects = findViewById(R.id.tabVoiceEffects)
        tabAmbientSounds = findViewById(R.id.tabAmbientSounds)

        setupVoiceEffectsGrid()

        tabVoiceEffects.setOnClickListener { switchTab(showVoiceEffects = true) }
        tabAmbientSounds.setOnClickListener { switchTab(showVoiceEffects = false) }

        loadDuration()
    }

    private fun setupVoiceEffectsGrid() {
        effectsAdapter = EffectsAdapter(VoiceEffects.ALL) { effect ->
            previewEffect(effect)
        }
        recyclerEffects.adapter = effectsAdapter
    }

    private fun setupAmbientGrid() {
        val ambientAdapter = AmbientAdapter(AmbientSounds.ALL) { sound ->
            Toast.makeText(this, "${sound.name} selected (ambient mixing coming once audio files are added)", Toast.LENGTH_SHORT).show()
        }
        recyclerEffects.adapter = ambientAdapter
    }

    private fun switchTab(showVoiceEffects: Boolean) {
        if (showVoiceEffects) {
            tabVoiceEffects.setBackgroundResource(R.drawable.bg_tab_pill_selected)
            tabVoiceEffects.setTextColor(getColor(R.color.white))
            tabAmbientSounds.background = null
            tabAmbientSounds.setTextColor(getColor(R.color.text_dark))
            recyclerEffects.adapter = effectsAdapter
        } else {
            tabAmbientSounds.setBackgroundResource(R.drawable.bg_tab_pill_selected)
            tabAmbientSounds.setTextColor(getColor(R.color.white))
            tabVoiceEffects.background = null
            tabVoiceEffects.setTextColor(getColor(R.color.text_dark))
            setupAmbientGrid()
        }
    }

    private fun previewEffect(effect: VoiceEffect) {
        stopPlayback()
        val path = sourcePath ?: return
        mediaPlayer = MediaPlayer().apply {
            setDataSource(path)
            prepare()
            try {
                playbackParams = playbackParams.setPitch(effect.pitch).setSpeed(effect.speed)
            } catch (e: Exception) {
                // Fallback to normal playback if the device doesn't support runtime pitch changes.
            }
            setOnCompletionListener {
                this@ApplyEffectsActivity.isPlaying = false
                btnPlayPreview.setImageResource(android.R.drawable.ic_media_play)
            }
        }
        mediaPlayer?.start()
        isPlaying = true
        btnPlayPreview.setImageResource(android.R.drawable.ic_media_pause)
        handler.post(progressRunnable)
    }

    private fun togglePreviewPlayback() {
        val mp = mediaPlayer
        if (mp == null) {
            previewEffect(effectsAdapter.getSelectedEffect())
            return
        }
        if (isPlaying) {
            mp.pause()
            isPlaying = false
            btnPlayPreview.setImageResource(android.R.drawable.ic_media_play)
        } else {
            mp.start()
            isPlaying = true
            btnPlayPreview.setImageResource(android.R.drawable.ic_media_pause)
        }
    }

    private fun stopPlayback() {
        handler.removeCallbacks(progressRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        progressBar.progress = 0
        tvCurrentTime.text = "00:00"
    }

    private fun loadDuration() {
        val path = sourcePath ?: return
        lifecycleScope.launch {
            val durationSeconds = withContext(Dispatchers.IO) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(path)
                    val d = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                    retriever.release()
                    (d / 1000).toInt()
                } catch (e: Exception) {
                    0
                }
            }
            tvTotalTime.text = formatTime(durationSeconds)
        }
    }

    private fun saveWithEffect() {
        val inputPath = sourcePath ?: return
        val selectedEffect = effectsAdapter.getSelectedEffect()

        Toast.makeText(this, "Applying ${selectedEffect.displayName} effect…", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val outputFile = withContext(Dispatchers.IO) {
                val inputFile = File(inputPath)
                val outDir = File(getExternalFilesDir(null), "recordings")
                if (!outDir.exists()) outDir.mkdirs()
                val outFile = File(outDir, "AudioVoiceEffect${System.currentTimeMillis()}.wav")
                try {
                    VoiceEffectProcessor().applyEffect(inputFile, outFile, selectedEffect)
                    outFile
                } catch (e: Exception) {
                    null
                }
            }

            if (outputFile != null) {
                val intent = android.content.Intent(this@ApplyEffectsActivity, com.example.voiceecho.ui.saved.FileSavedActivity::class.java)
                intent.putExtra(com.example.voiceecho.ui.saved.FileSavedActivity.EXTRA_SAVED_PATH, outputFile.absolutePath)
                // Always forward the ORIGINAL source (never the just-created output) so that
                // if the user taps "Change" from the next screen, effects apply fresh again.
                intent.putExtra(com.example.voiceecho.ui.saved.FileSavedActivity.EXTRA_ORIGINAL_PATH, inputPath)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this@ApplyEffectsActivity, "Failed to apply effect", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatTime(totalSeconds: Int): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_AUDIO_PATH = "extra_audio_path"
    }
}