package com.example.voiceecho.audio

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaCodec
import com.example.voiceecho.data.VoiceEffect
import java.io.File
import java.nio.ShortBuffer

class VoiceEffectProcessor {

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

        // Step 1: decode the ENTIRE file into one continuous PCM buffer.
        val fullPcm = ArrayList<Short>(sampleRate * channelCount * 5)
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
                if (bufferInfo.size > 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)!!
                    val shortBuffer: ShortBuffer = outputBuffer.asShortBuffer()
                    val chunk = ShortArray(shortBuffer.remaining())
                    shortBuffer.get(chunk)
                    for (s in chunk) fullPcm.add(s)
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

        val fullPcmArray = ShortArray(fullPcm.size)
        for (i in fullPcm.indices) fullPcmArray[i] = fullPcm[i]

        // Step 2: PURE pitch shift via resampling only (speed fixed at 1.0 here —
        // this stage's only job is pitch; it will also change duration as a
        // natural side effect, which we correct for in Step 3).
        val sonic = SonicAudio(sampleRate, channelCount).apply {
            pitch = effect.pitch
            speed = 1.0f
            rate = 1.0f
        }
        sonic.writeShorts(fullPcmArray, fullPcmArray.size)
        val pitchShiftedCount = sonic.availableOutput()
        val pitchShifted = ShortArray(pitchShiftedCount)
        sonic.readShorts(pitchShifted, pitchShiftedCount)

        // Step 3: time-stretch to correct duration/speed to the effect's target
        // SPEED, independent of whatever the pitch shift changed it to, WITHOUT
        // touching pitch again. This is what makes pitch and speed independent.
        val stretchFactor = effect.speed / effect.pitch
        val finalPcm = TimeStretcher.stretch(pitchShifted, channelCount, stretchFactor)

        writeWavFile(outputFile, finalPcm, sampleRate, channelCount)
    }

    private fun writeWavFile(
        outputFile: File,
        pcm: ShortArray,
        sampleRate: Int,
        channelCount: Int
    ) {
        val byteRate = sampleRate * channelCount * 2
        val dataSize = pcm.size * 2
        val chunkSize = 36 + dataSize

        outputFile.outputStream().use { out ->
            val header = ByteArray(44)
            writeWavHeader(header, chunkSize, dataSize, sampleRate, channelCount, byteRate)
            out.write(header)

            val byteBuffer = ByteArray(pcm.size * 2)
            for (i in pcm.indices) {
                val sample = pcm[i].toInt()
                byteBuffer[i * 2] = (sample and 0xFF).toByte()
                byteBuffer[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
            }
            out.write(byteBuffer)
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