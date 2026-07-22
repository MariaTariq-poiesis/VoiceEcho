package com.example.voiceecho.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

object AudioTrimmer {

    /**
     * Decodes the input file, keeps only samples between startMs and endMs,
     * and writes a WAV file with just that selection.
     */
    fun trim(inputFile: File, outputFile: File, startMs: Long, endMs: Long) {
        val extractor = MediaExtractor()
        extractor.setDataSource(inputFile.absolutePath)

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
            throw IllegalStateException("No audio track found")
        }

        extractor.selectTrack(audioTrackIndex)
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val mime = format.getString(MediaFormat.KEY_MIME)!!

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val pcmChunks = mutableListOf<ShortArray>()
        var totalSamples = 0
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        val startUs = startMs * 1000
        val endUs = endMs * 1000

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
                val presentationUs = bufferInfo.presentationTimeUs
                val outputBuffer = decoder.getOutputBuffer(outIndex)!!

                if (bufferInfo.size > 0 && presentationUs in startUs..endUs) {
                    val shortArray = ShortArray(bufferInfo.size / 2)
                    outputBuffer.asShortBuffer().get(shortArray)
                    pcmChunks.add(shortArray)
                    totalSamples += shortArray.size
                }

                decoder.releaseOutputBuffer(outIndex, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || presentationUs > endUs) {
                    sawOutputEOS = true
                }
            }
        }

        decoder.stop()
        decoder.release()
        extractor.release()

        writeWav(outputFile, pcmChunks, totalSamples, sampleRate, channelCount)
    }

    private fun writeWav(
        outputFile: File,
        pcmChunks: List<ShortArray>,
        totalSamples: Int,
        sampleRate: Int,
        channelCount: Int
    ) {
        val byteRate = sampleRate * channelCount * 2
        val dataSize = totalSamples * 2
        val chunkSize = 36 + dataSize

        outputFile.outputStream().use { out ->
            val header = ByteArray(44)
            "RIFF".toByteArray().copyInto(header, 0)
            intLE(chunkSize).copyInto(header, 4)
            "WAVE".toByteArray().copyInto(header, 8)
            "fmt ".toByteArray().copyInto(header, 12)
            intLE(16).copyInto(header, 16)
            shortLE(1).copyInto(header, 20)
            shortLE(channelCount.toShort()).copyInto(header, 22)
            intLE(sampleRate).copyInto(header, 24)
            intLE(byteRate).copyInto(header, 28)
            shortLE((channelCount * 2).toShort()).copyInto(header, 32)
            shortLE(16).copyInto(header, 34)
            "data".toByteArray().copyInto(header, 36)
            intLE(dataSize).copyInto(header, 40)
            out.write(header)

            val byteBuffer = ByteArray(2)
            for (chunk in pcmChunks) {
                for (sample in chunk) {
                    byteBuffer[0] = (sample.toInt() and 0xFF).toByte()
                    byteBuffer[1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                    out.write(byteBuffer)
                }
            }
        }
    }

    private fun intLE(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )

    private fun shortLE(value: Short): ByteArray = byteArrayOf(
        (value.toInt() and 0xFF).toByte(),
        ((value.toInt() shr 8) and 0xFF).toByte()
    )
}