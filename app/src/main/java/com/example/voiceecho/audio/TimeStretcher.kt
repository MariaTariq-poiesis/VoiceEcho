package com.example.voiceecho.audio

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Time-stretches audio (changes speed/duration) while preserving pitch,
 * using WSOLA (Waveform-Similarity Overlap-Add).
 *
 * Two things must be correct for this to sound clean instead of
 * metallic/underwater:
 *  1. The window must be the PERIODIC Hann formula (denominator N),
 *     not the symmetric formula (denominator N-1) — using the wrong
 *     one breaks constant-overlap-add and causes a repeating volume
 *     ripple at every blend seam.
 *  2. The alignment search must use ENERGY-NORMALIZED correlation,
 *     so it picks the offset where the waveform SHAPE actually matches
 *     (not just wherever is loudest) — this removes phase-mismatch
 *     seams, which is what causes the underwater/comb-filtered sound.
 */
object TimeStretcher {

    fun stretch(input: ShortArray, channels: Int, factor: Float): ShortArray {
        if (channels <= 0 || input.isEmpty() || abs(factor - 1.0f) < 0.01f) {
            return input
        }

        val frames = input.size / channels
        val frameSize = 2048
        val synthesisHop = frameSize / 4 // 75% overlap for smoother blending
        val searchRadius = 256
        val correlationLength = 512

        if (frames < frameSize * 2) return input

        val channelData = Array(channels) { ch ->
            FloatArray(frames) { i -> input[i * channels + ch].toFloat() }
        }

        // PERIODIC Hann: denominator is frameSize, NOT frameSize - 1.
        val window = FloatArray(frameSize) { i ->
            (0.5f - 0.5f * cos(2.0 * Math.PI * i / frameSize).toFloat())
        }

        // Normalization curve: sum of overlapping windows at 75% overlap,
        // precomputed once so we can divide it out at the end (guarantees
        // flat volume across every seam regardless of overlap amount).
        val normSum = FloatArray(frameSize / 4 * 6 + frameSize)

        val analysisHop = synthesisHop * factor
        val estimatedOutFrames = (frames / factor).toInt() + frameSize + synthesisHop
        val outChannels = Array(channels) { FloatArray(estimatedOutFrames) }
        val outNorm = FloatArray(estimatedOutFrames)

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
            for (i in 0 until frameSize) {
                outNorm[outPos + i] += window[i]
            }

            outPos += synthesisHop
            idealPos += analysisHop
        }

        val finalFrameCount = outPos.coerceAtLeast(1)
        val result = ShortArray(finalFrameCount * channels)

        for (i in 0 until finalFrameCount) {
            val norm = if (outNorm[i] > 0.0001f) outNorm[i] else 1f
            for (ch in 0 until channels) {
                val sample = (outChannels[ch][i] / norm).coerceIn(-32768f, 32767f)
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

        val compareLen = minOf(refLen, correlationLength)

        var refEnergy = 0.0
        for (i in 0 until compareLen) {
            val v = reference[refStart + i].toDouble()
            refEnergy += v * v
        }
        val refNorm = sqrt(refEnergy)
        if (refNorm < 1e-6) return nominalPos

        var bestOffset = nominalPos
        var bestScore = Double.NEGATIVE_INFINITY

        var candStart = lo
        while (candStart <= hi) {
            var dot = 0.0
            var candEnergy = 0.0
            for (i in 0 until compareLen) {
                val a = reference[refStart + i].toDouble()
                val b = candidate[candStart + i].toDouble()
                dot += a * b
                candEnergy += b * b
            }
            val candNorm = sqrt(candEnergy)
            // Normalized cross-correlation: picks the offset with the best
            // MATCHING SHAPE, regardless of which segment is louder.
            val score = if (candNorm > 1e-6) dot / (refNorm * candNorm) else -1.0

            if (score > bestScore) {
                bestScore = score
                bestOffset = candStart
            }
            candStart += 4
        }

        return bestOffset
    }
}