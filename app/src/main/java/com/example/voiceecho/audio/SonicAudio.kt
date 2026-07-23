package com.example.voiceecho.audio

/**
 * Simplified Kotlin port of the Sonic pitch-shifting algorithm.
 * Shifts pitch by resampling, using CUBIC interpolation (Catmull-Rom)
 * instead of linear — linear interpolation between samples adds a
 * grainy/aliased quality that gets worse at strong pitch shifts (like
 * Helium or Monster); cubic interpolation follows the actual curve of
 * the waveform instead of a straight line between points, which is a
 * major contributor to removing the "robotic/metallic" texture.
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
        val playbackRate = (pitch * rate * speed).coerceAtLeast(0.01f)
        val samplesPerChannel = inputCount / numChannels

        if (samplesPerChannel <= 0) return

        val outSamplesPerChannel = (samplesPerChannel / playbackRate).toInt()
        val outTotal = outSamplesPerChannel * numChannels
        if (outTotal <= 0) {
            inputCount = 0
            return
        }

        ensureOutputCapacity(outTotal)

        for (ch in 0 until numChannels) {
            for (i in 0 until outSamplesPerChannel) {
                val srcPos = i * playbackRate
                val i1 = srcPos.toInt().coerceIn(0, samplesPerChannel - 1)
                val frac = srcPos - i1

                val i0 = (i1 - 1).coerceAtLeast(0)
                val i2 = (i1 + 1).coerceAtMost(samplesPerChannel - 1)
                val i3 = (i1 + 2).coerceAtMost(samplesPerChannel - 1)

                val p0 = inputBuffer[i0 * numChannels + ch].toFloat()
                val p1 = inputBuffer[i1 * numChannels + ch].toFloat()
                val p2 = inputBuffer[i2 * numChannels + ch].toFloat()
                val p3 = inputBuffer[i3 * numChannels + ch].toFloat()

                val interpolated = catmullRom(p0, p1, p2, p3, frac)
                    .coerceIn(-32768f, 32767f)

                outputBuffer[outputCount + i * numChannels + ch] = interpolated.toInt().toShort()
            }
        }

        outputCount += outTotal
        inputCount = 0
    }

    /** Catmull-Rom cubic interpolation between p1 and p2, using p0/p3 as neighbors for curve shape. */
    private fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val a0 = -0.5f * p0 + 1.5f * p1 - 1.5f * p2 + 0.5f * p3
        val a1 = p0 - 2.5f * p1 + 2f * p2 - 0.5f * p3
        val a2 = -0.5f * p0 + 0.5f * p2
        val a3 = p1
        return ((a0 * t + a1) * t + a2) * t + a3
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