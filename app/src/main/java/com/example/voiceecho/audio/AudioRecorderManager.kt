package com.example.voiceecho.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorderManager(
    private val context: Context,
    private val outputDir: File
) {

    private var recorder: MediaRecorder? = null
    private var isPaused = false

    var currentOutputFile: File? = null
        private set

    fun start(): File {
        if (!outputDir.exists()) outputDir.mkdirs()

        val fileName = "Voice_Echo_${System.currentTimeMillis()}.m4a"
        val outputFile = File(outputDir, fileName)
        currentOutputFile = outputFile

        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        newRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }

        recorder = newRecorder
        isPaused = false
        return outputFile
    }

    fun pause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !isPaused) {
            recorder?.pause()
            isPaused = true
        }
    }

    fun resume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isPaused) {
            recorder?.resume()
            isPaused = false
        }
    }

    fun getMaxAmplitude(): Int {
        return try {
            recorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun stopAndSave(): File? {
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            currentOutputFile
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            null
        }
    }

    fun cancel() {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (e: Exception) {
            recorder?.release()
        }
        recorder = null
        currentOutputFile?.delete()
        currentOutputFile = null
    }
}