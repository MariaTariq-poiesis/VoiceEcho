package com.example.voiceecho.ui.record

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

class TrimWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var amplitudes: List<Float> = emptyList()

    var startFraction = 0f
        private set
    var endFraction = 1f
        private set

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A7DFF")
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D0D8F5")
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        strokeWidth = 8f
    }

    private val handleTouchWidth = 60f
    private var draggingHandle: Int = NONE // 0 = start, 1 = end, -1 = none

    var onTrimChanged: ((Float, Float) -> Unit)? = null

    fun setAmplitudes(values: List<Float>) {
        amplitudes = values
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val startX = startFraction * width
        val endX = endFraction * width

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                draggingHandle = when {
                    kotlin.math.abs(event.x - startX) < handleTouchWidth -> START
                    kotlin.math.abs(event.x - endX) < handleTouchWidth -> END
                    else -> NONE
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingHandle == START) {
                    val newFraction = (event.x / width).coerceIn(0f, endFraction - 0.05f)
                    startFraction = newFraction
                    onTrimChanged?.invoke(startFraction, endFraction)
                    invalidate()
                } else if (draggingHandle == END) {
                    val newFraction = (event.x / width).coerceIn(startFraction + 0.05f, 1f)
                    endFraction = newFraction
                    onTrimChanged?.invoke(startFraction, endFraction)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingHandle = NONE
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (amplitudes.isEmpty()) return

        val centerY = height / 2f
        val count = amplitudes.size
        val slot = width.toFloat() / count
        val startX = startFraction * width
        val endX = endFraction * width

        for (i in 0 until count) {
            val amp = amplitudes[i]
            val barHeight = max(height * 0.08f, height * 0.85f * amp)
            val x = i * slot + slot / 2f
            val paint = if (x in startX..endX) barPaint else dimPaint
            canvas.drawLine(x, centerY - barHeight / 2, x, centerY + barHeight / 2, paint)
        }

        canvas.drawLine(startX, 0f, startX, height.toFloat(), handlePaint)
        canvas.drawLine(endX, 0f, endX, height.toFloat(), handlePaint)
    }

    companion object {
        private const val NONE = -1
        private const val START = 0
        private const val END = 1
    }
}