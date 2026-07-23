package com.example.voiceecho.audio

import kotlin.math.abs
import kotlin.math.cos

/**
 * Time-stretches audio (changes speed/duration) while preserving pitch,
 * using WSOLA (Waveform-Similarity Overlap-Add).
 *
 * Plain overlap-add blends chunks at fixed, evenly-spaced positions —
 * if the waveform shapes don't line up at a seam, you hear a faint
 * repeating "echo"/ripple at every blend point. WSOLA fixes this by
 * searching a small window near each nominal chunk position for the
 * offset where the waveform actually lines up with what's already
 * been blended, and extracting the chunk from there instead — removing
 * the phase mismatch that causes the echo.
 */
object TimeStretcher {

    /**
     * @param input interleaved 16-bit PCM samples
     * @param channels number of audio channels
     * @param factor >1.0 = faster/shorter output, <1.0 = slower/longer output, 1.0 = no change
     */
    fun stretch(input: ShortArray, channels: Int, factor: Float): ShortArray {
        if (channels <= 0 || input.isEmpty() || abs(factor - 1.0f) < 0.01f) {
            return input
        }

        val frames = input.size / channels
        val frameSize = 2048
        val synthesisHop = frameSize / 2
        val searchRadius = 256
        val correlationLength = 512 // how much overlap we check when matching, kept short for speed

        if (frames < frameSize * 2) return input

        val channelData = Array(channels) { ch ->
            FloatArray(frames) { i -> input[i * channels + ch].toFloat() }
        }

        val window = FloatArray(frameSize) { i ->
            (0.5f - 0.5f * cos(2.0 * Math.PI * i / (frameSize - 1)).toFloat())
        }

        val analysisHop = synthesisHop * factor
        val estimatedOutFrames = (frames / factor).toInt() + frameSize + synthesisHop
        val outChannels = Array(channels) { FloatArray(estimatedOutFrames) }

        var idealPos = 0f
        var outPos = 0
        var isFirstFrame = true

        while (true) {
            val nominalPos = idealPos.toInt()
            if (nominalPos + frameSize > frames) break

            val extractPos = if (isFirstFrame) {
                isFirstFrame = false
                nominalPos
            } else {
                findBestAlignment(
                    reference = outChannels[0],
                    refStart = (outPos - correlationLength).coerceAtLeast(0),
                    refEnd = outPos,
                    candidate = channelData[0],
                    nominalPos = nominalPos,
                    searchRadius = searchRadius,
                    candidateFrames = frames,
                    correlationLength = correlationLength
                )
            }

            for (ch in 0 until channels) {
                val src = channelData[ch]
                val dst = outChannels[ch]
                for (i in 0 until frameSize) {
                    dst[outPos + i] += src[extractPos + i] * window[i]
                }
            }

            outPos += synthesisHop
            idealPos += analysisHop
        }

        val finalFrameCount = outPos.coerceAtLeast(1)
        val result = ShortArray(finalFrameCount * channels)

        for (i in 0 until finalFrameCount) {
            for (ch in 0 until channels) {
                val sample = outChannels[ch][i].coerceIn(-32768f, 32767f)
                result[i * channels + ch] = sample.toInt().toShort()
            }
        }

        return result
    }

    private fun findBestAlignment(
        reference: FloatArray,
        refStart: Int,
        refEnd: Int,
        candidate: FloatArray,
        nominalPos: Int,
        searchRadius: Int,
        candidateFrames: Int,
        correlationLength: Int
    ): Int {
        val refLen = refEnd - refStart
        if (refLen <= 0) return nominalPos

        val lo = (nominalPos - searchRadius).coerceAtLeast(0)
        val hi = (nominalPos + searchRadius).coerceAtMost(candidateFrames - correlationLength - 1)
        if (hi <= lo) return nominalPos

        var bestOffset = nominalPos
        var bestScore = Double.NEGATIVE_INFINITY
        val compareLen = minOf(refLen, correlationLength)

        var candStart = lo
        while (candStart <= hi) {
            var score = 0.0
            for (i in 0 until compareLen) {
                val a = reference[refStart + i]
                val b = candidate[candStart + i]
                score += (a * b).toDouble()
            }
            if (score > bestScore) {
                bestScore = score
                bestOffset = candStart
            }
            candStart += 4 // small step for speed; fine enough resolution for phase alignment
        }

        return bestOffset
    }
}