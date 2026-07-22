package com.example.voiceecho.audio

/**
 * Simplified Kotlin port of the Sonic audio processing algorithm
 * (originally by Bill Cox, used in Android's TTS engine).
 * Handles pitch shifting and speed changes on 16-bit PCM audio.
 */
class SonicAudio(
    private var sampleRate: Int,
    private val numChannels: Int
) {
    var speed: Float = 1.0f
    var pitch: Float = 1.0f
    var rate: Float = 1.0f

    private var inputBuffer = ShortArray(0)
    private var inputCount = 0
    private var outputBuffer = ShortArray(0)
    private var outputCount = 0

    private fun ensureInputCapacity(needed: Int) {
        if (inputBuffer.size < inputCount + needed) {
            inputBuffer = inputBuffer.copyOf(inputCount + needed)
        }
    }

    private fun ensureOutputCapacity(needed: Int) {
        if (outputBuffer.size < outputCount + needed) {
            outputBuffer = outputBuffer.copyOf(outputCount + needed)
        }
    }

    fun writeShorts(samples: ShortArray, length: Int) {
        ensureInputCapacity(length)
        System.arraycopy(samples, 0, inputBuffer, inputCount, length)
        inputCount += length
        process()
    }

    private fun process() {
        val combinedPitchRate = pitch * rate
        val samplesPerChannel = inputCount / numChannels

        if (samplesPerChannel <= 0) return

        val outSamplesPerChannel = (samplesPerChannel / (speed / combinedPitchRate).coerceAtLeast(0.01f)).toInt()
        val outTotal = outSamplesPerChannel * numChannels
        if (outTotal <= 0) {
            inputCount = 0
            return
        }

        ensureOutputCapacity(outTotal)

        for (ch in 0 until numChannels) {
            for (i in 0 until outSamplesPerChannel) {
                val srcPos = (i * (speed / combinedPitchRate))
                val srcIndex = srcPos.toInt().coerceIn(0, samplesPerChannel - 1)
                val nextIndex = (srcIndex + 1).coerceAtMost(samplesPerChannel - 1)
                val frac = srcPos - srcIndex

                val s1 = inputBuffer[srcIndex * numChannels + ch].toInt()
                val s2 = inputBuffer[nextIndex * numChannels + ch].toInt()
                val interpolated = (s1 + (s2 - s1) * frac).toInt().toShort()

                outputBuffer[outputCount + i * numChannels + ch] = interpolated
            }
        }

        outputCount += outTotal
        inputCount = 0
    }

    fun readShorts(destination: ShortArray, maxLength: Int): Int {
        val toRead = minOf(maxLength, outputCount)
        System.arraycopy(outputBuffer, 0, destination, 0, toRead)

        val remaining = outputCount - toRead
        if (remaining > 0) {
            System.arraycopy(outputBuffer, toRead, outputBuffer, 0, remaining)
        }
        outputCount = remaining
        return toRead
    }

    fun availableOutput(): Int = outputCount

    fun flush() {
        inputCount = 0
        outputCount = 0
    }
}