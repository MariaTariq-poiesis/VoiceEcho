package com.example.voiceecho.ui.record

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val liveAmplitudes = mutableListOf<Float>()
    private var staticAmplitudes: List<Float> = emptyList()
    private var isStaticMode = false

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A7DFF")
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val barWidth = 10f
    private val barSpacing = 6f

    fun addAmplitude(amplitude: Float) {
        isStaticMode = false
        val maxBars = (width / (barWidth + barSpacing)).toInt().coerceAtLeast(1)
        liveAmplitudes.add(amplitude.coerceIn(0.05f, 1f))
        while (liveAmplitudes.size > maxBars) {
            liveAmplitudes.removeAt(0)
        }
        invalidate()
    }

    fun setStaticAmplitudes(amplitudes: List<Float>) {
        isStaticMode = true
        staticAmplitudes = amplitudes
        invalidate()
    }

    fun clear() {
        liveAmplitudes.clear()
        staticAmplitudes = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isStaticMode) drawStatic(canvas) else drawLive(canvas)
    }

    private fun drawLive(canvas: Canvas) {
        if (liveAmplitudes.isEmpty()) return
        val centerY = height / 2f
        var x = width - (barWidth / 2)

        for (i in liveAmplitudes.indices.reversed()) {
            val amp = liveAmplitudes[i]
            val barHeight = max(height * 0.08f, height * 0.9f * amp)
            canvas.drawLine(x, centerY - barHeight / 2, x, centerY + barHeight / 2, barPaint)
            x -= (barWidth + barSpacing)
            if (x < 0) break
        }
    }

    private fun drawStatic(canvas: Canvas) {
        if (staticAmplitudes.isEmpty()) return
        val centerY = height / 2f
        val count = staticAmplitudes.size
        val slot = width.toFloat() / count

        for (i in 0 until count) {
            val amp = staticAmplitudes[i]
            val barHeight = max(height * 0.08f, height * 0.9f * amp)
            val x = i * slot + slot / 2f
            canvas.drawLine(x, centerY - barHeight / 2, x, centerY + barHeight / 2, barPaint)
        }
    }
}