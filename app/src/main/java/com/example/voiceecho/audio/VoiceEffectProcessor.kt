package com.example.voiceecho.audio

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaCodec
import android.media.MediaMuxer
import com.example.voiceecho.data.VoiceEffect
import java.io.File
import java.nio.ByteBuffer

class VoiceEffectProcessor {

    /**
     * Decodes the input audio, applies pitch/speed via SonicAudio, and encodes
     * a new output file. Runs synchronously — call from a background coroutine.
     */
    fun applyEffect(inputFile: File, outputFile: File, effect: VoiceEffect) {
        val extractor = MediaExtractor()
        extractor.setDataSource(inputFile.absolutePath)

        var audioTrackIndex = -1
        var inputFormat: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                inputFormat = format
                break
            }
        }

        if (audioTrackIndex == -1 || inputFormat == null) {
            extractor.release()
            throw IllegalStateException("No audio track found in input file")
        }

        extractor.selectTrack(audioTrackIndex)

        val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(inputFormat, null, null, 0)
        decoder.start()

        val sonic = SonicAudio(sampleRate, channelCount).apply {
            pitch = effect.pitch
            speed = effect.speed
        }

        val pcmChunks = mutableListOf<ShortArray>()
        var totalPcmSamples = 0
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inputBufferIndex = decoder.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputBufferIndex >= 0) {
                val outputBuffer: ByteBuffer = decoder.getOutputBuffer(outputBufferIndex)!!
                if (bufferInfo.size > 0) {
                    val shortArray = ShortArray(bufferInfo.size / 2)
                    outputBuffer.asShortBuffer().get(shortArray)

                    sonic.writeShorts(shortArray, shortArray.size)
                    val available = sonic.availableOutput()
                    if (available > 0) {
                        val processed = ShortArray(available)
                        val read = sonic.readShorts(processed, available)
                        pcmChunks.add(processed.copyOf(read))
                        totalPcmSamples += read
                    }
                }
                decoder.releaseOutputBuffer(outputBufferIndex, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true
                }
            }
        }

        decoder.stop()
        decoder.release()
        extractor.release()

        writeWavFile(outputFile, pcmChunks, totalPcmSamples, sampleRate, channelCount)
    }

    private fun writeWavFile(
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
            writeWavHeader(header, chunkSize, dataSize, sampleRate, channelCount, byteRate)
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

    private fun writeWavHeader(
        header: ByteArray,
        chunkSize: Int,
        dataSize: Int,
        sampleRate: Int,
        channelCount: Int,
        byteRate: Int
    ) {
        "RIFF".toByteArray().copyInto(header, 0)
        intToBytesLE(chunkSize).copyInto(header, 4)
        "WAVE".toByteArray().copyInto(header, 8)
        "fmt ".toByteArray().copyInto(header, 12)
        intToBytesLE(16).copyInto(header, 16)
        shortToBytesLE(1).copyInto(header, 20)
        shortToBytesLE(channelCount.toShort()).copyInto(header, 22)
        intToBytesLE(sampleRate).copyInto(header, 24)
        intToBytesLE(byteRate).copyInto(header, 28)
        shortToBytesLE((channelCount * 2).toShort()).copyInto(header, 32)
        shortToBytesLE(16).copyInto(header, 34)
        "data".toByteArray().copyInto(header, 36)
        intToBytesLE(dataSize).copyInto(header, 40)
    }

    private fun intToBytesLE(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )

    private fun shortToBytesLE(value: Short): ByteArray = byteArrayOf(
        (value.toInt() and 0xFF).toByte(),
        ((value.toInt() shr 8) and 0xFF).toByte()
    )
}