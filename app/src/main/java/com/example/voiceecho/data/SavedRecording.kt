package com.example.voiceecho.data

import java.io.File

data class SavedRecording(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long
)