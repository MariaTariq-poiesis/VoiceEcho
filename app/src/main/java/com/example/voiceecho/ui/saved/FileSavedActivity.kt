package com.example.voiceecho.ui.saved

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.voiceecho.MainActivity
import com.example.voiceecho.R
import com.example.voiceecho.ui.effects.ApplyEffectsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class FileSavedActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var savedPath: String? = null
    private var originalPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_saved)

        savedPath = intent.getStringExtra(EXTRA_SAVED_PATH)
        originalPath = intent.getStringExtra(EXTRA_ORIGINAL_PATH)
        val file = savedPath?.let { File(it) }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = null
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<TextView>(R.id.tvFileName).text = file?.name ?: "recording"
        findViewById<TextView>(R.id.tvFileSize).text = formatFileSize(file?.length() ?: 0L)

        setupPlayback(file)

        findViewById<android.widget.LinearLayout>(R.id.btnExploreSavedFiles).setOnClickListener {
            startActivity(Intent(this, SavedFilesActivity::class.java))
        }

        findViewById<android.widget.LinearLayout>(R.id.btnExploreShare).setOnClickListener {
            shareFile(file)
        }

        findViewById<android.widget.LinearLayout>(R.id.btnExploreChange).setOnClickListener {
            // IMPORTANT: always re-open Apply Effects with the ORIGINAL recording,
            // never with the already-processed file, so effects never stack on each other.
            val intent = Intent(this, ApplyEffectsActivity::class.java)
            intent.putExtra(ApplyEffectsActivity.EXTRA_AUDIO_PATH, originalPath ?: savedPath)
            startActivity(intent)
            finish()
        }

        findViewById<android.widget.LinearLayout>(R.id.btnExploreHome).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun setupPlayback(file: File?) {
        val btnPlay = findViewById<ImageButton>(R.id.btnPlaySaved)
        val seekBar = findViewById<SeekBar>(R.id.seekBarSaved)
        val tvCurrent = findViewById<TextView>(R.id.tvCurrentSaved)
        val tvTotal = findViewById<TextView>(R.id.tvTotalSaved)

        if (file == null || !file.exists()) return

        lifecycleScope.launch {
            val durationMs = withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                    retriever.release()
                    d
                } catch (e: Exception) {
                    0L
                }
            }
            tvTotal.text = formatTime((durationMs / 1000).toInt())
            seekBar.max = durationMs.toInt().coerceAtLeast(1)
        }

        btnPlay.setOnClickListener {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    setOnCompletionListener {
                        this@FileSavedActivity.isPlaying = false
                        btnPlay.setImageResource(android.R.drawable.ic_media_play)
                    }
                }
            }
            if (isPlaying) {
                mediaPlayer?.pause()
                btnPlay.setImageResource(android.R.drawable.ic_media_play)
            } else {
                mediaPlayer?.start()
                btnPlay.setImageResource(android.R.drawable.ic_media_pause)
                trackProgress(seekBar, tvCurrent)
            }
            isPlaying = !isPlaying
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) mediaPlayer?.seekTo(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun trackProgress(seekBar: SeekBar, tvCurrent: TextView) {
        val mp = mediaPlayer ?: return
        Thread {
            while (isPlaying) {
                try {
                    val pos = mp.currentPosition
                    runOnUiThread {
                        seekBar.progress = pos
                        tvCurrent.text = formatTime(pos / 1000)
                    }
                    Thread.sleep(300)
                } catch (e: Exception) {
                    break
                }
            }
        }.start()
    }

    private fun shareFile(file: File?) {
        if (file == null || !file.exists()) return
        val uri: Uri = FileProvider.getUriForFile(
            this, "$packageName.fileprovider", file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share recording"))
    }

    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        return if (kb < 1024) String.format(Locale.US, "%.1f KB", kb)
        else String.format(Locale.US, "%.1f MB", kb / 1024.0)
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
        const val EXTRA_SAVED_PATH = "extra_saved_path"
        const val EXTRA_ORIGINAL_PATH = "extra_original_path"
    }
}