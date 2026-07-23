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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class ApplyEffectsActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var sourcePath: String? = null

    // The one processed file currently loaded — preview, and Save both use ONLY this file.
    private var currentProcessedFile: File? = null
    private var processingJob: Job? = null

    private lateinit var effectsAdapter: EffectsAdapter
    private lateinit var recyclerEffects: RecyclerView
    private lateinit var tabVoiceEffects: TextView
    private lateinit var tabAmbientSounds: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnPlayPreview: ImageButton
    private lateinit var btnSave: TextView

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

        // sourcePath must ALWAYS be the original raw recording — every caller
        // (PlaybackActivity's "Change Voice" row, FileSavedActivity's "Change" button)
        // passes the ORIGINAL file here, never an already-processed one.
        sourcePath = intent.getStringExtra(EXTRA_AUDIO_PATH)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = null

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        btnSave = findViewById(R.id.btnSave)
        btnSave.setOnClickListener { saveProcessedFile() }

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

        // Process and preview the default-selected effect ("Default") right away,
        // so there's always a currentProcessedFile ready before the user taps anything.
        processAndPreview(VoiceEffects.ALL[0])
    }

    private fun setupVoiceEffectsGrid() {
        effectsAdapter = EffectsAdapter(VoiceEffects.ALL) { effect ->
            processAndPreview(effect)
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

    /**
     * Runs the FULL VoiceEffectProcessor pipeline once for the selected effect,
     * writes the result to a temp file, and plays THAT file for preview.
     * Save() later just copies this exact file — so preview, next screen,
     * and the final saved file are always byte-identical.
     */
    private fun processAndPreview(effect: VoiceEffect) {
        val inputPath = sourcePath ?: return

        processingJob?.cancel()
        stopPlayback()

        btnSave.isEnabled = false
        btnPlayPreview.isEnabled = false

        processingJob = lifecycleScope.launch {
            val previousTemp = currentProcessedFile

            val newTempFile = withContext(Dispatchers.IO) {
                try {
                    val tempDir = File(cacheDir, "effect_previews")
                    if (!tempDir.exists()) tempDir.mkdirs()
                    val tempFile = File(tempDir, "preview_${System.currentTimeMillis()}.wav")
                    VoiceEffectProcessor().applyEffect(File(inputPath), tempFile, effect)
                    tempFile
                } catch (e: Exception) {
                    null
                }
            }

            previousTemp?.delete()

            if (newTempFile != null) {
                currentProcessedFile = newTempFile
                btnSave.isEnabled = true
                btnPlayPreview.isEnabled = true
                loadDurationAndPlay(newTempFile)
            } else {
                Toast.makeText(this@ApplyEffectsActivity, "Failed to process effect", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadDurationAndPlay(file: File) {
        lifecycleScope.launch {
            val durationSeconds = withContext(Dispatchers.IO) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val d = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                    retriever.release()
                    (d / 1000).toInt()
                } catch (e: Exception) {
                    0
                }
            }
            tvTotalTime.text = formatTime(durationSeconds)
            startPreviewPlayback(file)
        }
    }

    /** Plain playback of the already-processed file — NO playbackParams, no runtime pitch tricks. */
    private fun startPreviewPlayback(file: File) {
        stopPlayback()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
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
            currentProcessedFile?.let { startPreviewPlayback(it) }
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

    /**
     * Save = copy the ALREADY-PROCESSED preview file into the permanent recordings
     * folder. No re-processing happens here, so the saved file is byte-identical
     * to what was just previewed.
     */
    private fun saveProcessedFile() {
        val processedFile = currentProcessedFile
        val inputPath = sourcePath
        if (processedFile == null || !processedFile.exists() || inputPath == null) {
            Toast.makeText(this, "Nothing processed yet", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val finalFile = withContext(Dispatchers.IO) {
                try {
                    val outDir = File(getExternalFilesDir(null), "recordings")
                    if (!outDir.exists()) outDir.mkdirs()
                    val outFile = File(outDir, "AudioVoiceEffect${System.currentTimeMillis()}.wav")
                    processedFile.copyTo(outFile, overwrite = true)
                    outFile
                } catch (e: Exception) {
                    null
                }
            }

            if (finalFile != null) {
                val intent = android.content.Intent(this@ApplyEffectsActivity, com.example.voiceecho.ui.saved.FileSavedActivity::class.java)
                intent.putExtra(com.example.voiceecho.ui.saved.FileSavedActivity.EXTRA_SAVED_PATH, finalFile.absolutePath)
                intent.putExtra(com.example.voiceecho.ui.saved.FileSavedActivity.EXTRA_ORIGINAL_PATH, inputPath)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this@ApplyEffectsActivity, "Failed to save file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatTime(totalSeconds: Int): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    override fun onDestroy() {
        processingJob?.cancel()
        stopPlayback()
        // Clean up any leftover temp preview files from this session.
        File(cacheDir, "effect_previews").listFiles()?.forEach { it.delete() }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_AUDIO_PATH = "extra_audio_path"
    }
}