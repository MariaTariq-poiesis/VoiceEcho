package com.example.voiceecho.ui.saved

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.voiceecho.R
import com.example.voiceecho.data.SavedRecording
import java.io.File

class SavedFilesActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPath: String? = null
    private lateinit var adapter: SavedFilesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_files)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = null
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerSavedFiles)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = SavedFilesAdapter(
            items = mutableListOf(),
            onPlayClicked = { recording -> togglePlay(recording) },
            onRenameClicked = { recording -> showRenameDialog(recording) },
            onDeleteClicked = { recording -> showDeleteDialog(recording) },
            onShareClicked = { recording -> shareRecording(recording) }
        )
        recyclerView.adapter = adapter

        loadRecordings()
    }

    override fun onResume() {
        super.onResume()
        loadRecordings()
    }

    private fun loadRecordings() {
        val dir = File(getExternalFilesDir(null), "recordings")
        val files = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()

        val recordings = files.map {
            SavedRecording(
                file = it,
                name = it.name,
                sizeBytes = it.length(),
                lastModified = it.lastModified()
            )
        }

        adapter.updateItems(recordings)
        adapter.setPlayingPath(currentlyPlayingPath)

        val tvNoFiles = findViewById<TextView>(R.id.tvNoFiles)
        tvNoFiles.visibility = if (recordings.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun togglePlay(recording: SavedRecording) {
        val tappedPath = recording.file.absolutePath

        if (currentlyPlayingPath == tappedPath) {
            // Tapped the file that's already playing -> stop it
            stopPlayback()
            return
        }

        // A different file was tapped (or nothing was playing) -> switch to it
        stopPlayback()

        mediaPlayer = MediaPlayer().apply {
            setDataSource(tappedPath)
            prepare()
            setOnCompletionListener {
                stopPlayback()
            }
            start()
        }
        currentlyPlayingPath = tappedPath
        adapter.setPlayingPath(currentlyPlayingPath)
    }

    private fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        currentlyPlayingPath = null
        adapter.setPlayingPath(null)
    }

    private fun showRenameDialog(recording: SavedRecording) {
        val input = EditText(this)
        input.setText(recording.name.substringBeforeLast("."))

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_rename_title)
            .setView(input)
            .setPositiveButton(R.string.btn_ok) { _, _ ->
                val newBaseName = input.text.toString().trim()
                if (newBaseName.isNotEmpty()) {
                    val extension = recording.name.substringAfterLast(".", "")
                    val newFile = File(recording.file.parentFile, "$newBaseName.$extension")
                    recording.file.renameTo(newFile)
                    loadRecordings()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showDeleteDialog(recording: SavedRecording) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(R.string.dialog_delete_message)
            .setPositiveButton(R.string.btn_ok) { _, _ ->
                if (currentlyPlayingPath == recording.file.absolutePath) stopPlayback()
                recording.file.delete()
                loadRecordings()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun shareRecording(recording: SavedRecording) {
        val uri: Uri = FileProvider.getUriForFile(
            this, "$packageName.fileprovider", recording.file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share recording"))
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}