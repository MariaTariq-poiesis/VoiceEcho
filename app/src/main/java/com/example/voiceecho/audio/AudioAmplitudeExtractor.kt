package com.example.voiceecho.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import kotlin.math.abs
import kotlin.math.max

object AudioAmplitudeExtractor {

    fun extractAmplitudes(file: File, bucketCount: Int = 40): List<Float> {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)

        var audioTrackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                format = f
                break
            }
        }

        if (audioTrackIndex == -1 || format == null) {
            extractor.release()
            return emptyList()
        }

        extractor.selectTrack(audioTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val allPeaks = mutableListOf<Float>()
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inIndex = decoder.dequeueInputBuffer(10000)
                if (inIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (outIndex >= 0) {
                val outputBuffer = decoder.getOutputBuffer(outIndex)!!
                if (bufferInfo.size > 0) {
                    val shortBuffer = outputBuffer.asShortBuffer()
                    val chunkSize = 512
                    var i = 0
                    val remaining = shortBuffer.remaining()
                    while (i < remaining) {
                        var peak = 0
                        val end = minOf(i + chunkSize, remaining)
                        for (j in i until end) {
                            peak = max(peak, abs(shortBuffer.get(j).toInt()))
                        }
                        allPeaks.add(peak / 32767f)
                        i = end
                    }
                }
                decoder.releaseOutputBuffer(outIndex, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true
                }
            }
        }

        decoder.stop()
        decoder.release()
        extractor.release()

        return downsample(allPeaks, bucketCount)
    }

    private fun downsample(peaks: List<Float>, bucketCount: Int): List<Float> {
        if (peaks.isEmpty()) return emptyList()
        if (peaks.size <= bucketCount) return peaks

        val result = mutableListOf<Float>()
        val groupSize = peaks.size / bucketCount
        for (i in 0 until bucketCount) {
            val start = i * groupSize
            val end = if (i == bucketCount - 1) peaks.size else start + groupSize
            var maxVal = 0f
            for (j in start until end) {
                maxVal = max(maxVal, peaks[j])
            }
            result.add(maxVal.coerceIn(0.05f, 1f))
        }
        return result
    }
}