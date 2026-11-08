package com.example.jarvis.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.sin

/**
 * Compact, lightweight Siri/Apple-Intelligence style floating pill HUD for Jarvis.
 * Features an elegant dark glass container, animated flowing multi-color neon soundwaves,
 * real-time voice RMS reactivity, and live transcription.
 */
class JarvisHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class HudState {
        LISTENING,
        PROCESSING,
        EXECUTING,
        CALL_RINGING
    }

    var state: HudState = HudState.LISTENING
        set(value) {
            field = value
            invalidate()
        }

    var transcriptionText: String = "Listening..."
        set(value) {
            field = value
            invalidate()
        }

    var actionStatusText: String = "BLUE"
        set(value) {
            field = value
            invalidate()
        }

    var onDismissRequested: (() -> Unit)? = null

    private var animPhase = 0f
    private var rmsLevel = 0f // 0f .. 10f

    // Paints
    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#EE0B1220") // Deep dark glass
    }

    private val pillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * resources.displayMetrics.density
        color = Color.parseColor("#4000E5FF") // Subtle cyan neon edge
    }

    private val wavePaint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    private val wavePaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        textSize = 13f * resources.displayMetrics.density
        letterSpacing = 0.15f
        isFakeBoldText = true
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F0F4F8")
        textSize = 15f * resources.displayMetrics.density
        isFakeBoldText = false
    }

    private val pillRect = RectF()
    private val wavePath1 = Path()
    private val wavePath2 = Path()
    private var animator: ValueAnimator? = null

    init {
        startAnimationLoop()
    }

    fun setRmsLevel(dB: Float) {
        this.rmsLevel = dB.coerceIn(0f, 12f)
        invalidate()
    }

    private fun startAnimationLoop() {
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                animPhase = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // Dimensions of the Siri-style floating pill
        val pillHeight = (76f * resources.displayMetrics.density)
        val pillMarginH = (16f * resources.displayMetrics.density)
        val pillBottom = h - (36f * resources.displayMetrics.density)
        val pillTop = pillBottom - pillHeight
        val pillLeft = pillMarginH
        val pillRight = w - pillMarginH
        val cornerRadius = pillHeight / 2f

        pillRect.set(pillLeft, pillTop, pillRight, pillBottom)

        // 1. Draw Glass Pill Background (100% transparent everywhere else on screen)
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, pillBgPaint)
        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, pillBorderPaint)

        // 2. Siri Glowing Wave Orb / Ribbons on Left Side of the Pill
        val waveCenterX = pillLeft + (38f * resources.displayMetrics.density)
        val waveCenterY = (pillTop + pillBottom) / 2f
        val waveRadius = (22f * resources.displayMetrics.density)

        // Setup Siri Fluid Gradient
        wavePaint1.shader = LinearGradient(
            waveCenterX - waveRadius, waveCenterY,
            waveCenterX + waveRadius, waveCenterY,
            intArrayOf(Color.parseColor("#00E5FF"), Color.parseColor("#7C4DFF"), Color.parseColor("#00B0FF")),
            null,
            Shader.TileMode.CLAMP
        )

        wavePaint2.shader = LinearGradient(
            waveCenterX - waveRadius, waveCenterY,
            waveCenterX + waveRadius, waveCenterY,
            intArrayOf(Color.parseColor("#76FFFF"), Color.parseColor("#FF4081"), Color.parseColor("#69F0AE")),
            null,
            Shader.TileMode.CLAMP
        )

        // Draw Siri Fluid Sine Waves
        val amp1 = (4f + rmsLevel * 1.5f) * resources.displayMetrics.density
        val amp2 = (3f + rmsLevel * 1.2f) * resources.displayMetrics.density

        wavePath1.reset()
        wavePath2.reset()

        val steps = 24
        val stepW = (waveRadius * 2f) / steps

        for (i in 0..steps) {
            val px = (waveCenterX - waveRadius) + i * stepW
            val normX = (i.toFloat() / steps) * 2f * Math.PI.toFloat()
            val envelope = sin(i.toFloat() / steps * Math.PI.toFloat()) // Taper at edges

            val py1 = waveCenterY + sin(normX + Math.toRadians(animPhase.toDouble())).toFloat() * amp1 * envelope
            val py2 = waveCenterY + sin(normX * 1.5f - Math.toRadians((animPhase * 1.3).toDouble())).toFloat() * amp2 * envelope

            if (i == 0) {
                wavePath1.moveTo(px, py1)
                wavePath2.moveTo(px, py2)
            } else {
                wavePath1.lineTo(px, py1)
                wavePath2.lineTo(px, py2)
            }
        }

        canvas.drawPath(wavePath1, wavePaint1)
        canvas.drawPath(wavePath2, wavePaint2)

        // Glowing center core dot
        val coreGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (state == HudState.EXECUTING) Color.parseColor("#00E676") else Color.parseColor("#00E5FF")
            alpha = (180 + (rmsLevel * 7).toInt()).coerceIn(180, 255)
        }
        canvas.drawCircle(waveCenterX, waveCenterY, (4f + rmsLevel * 0.4f) * resources.displayMetrics.density, coreGlow)

        // 3. Text Section inside Pill
        val textStartX = waveCenterX + waveRadius + (16f * resources.displayMetrics.density)
        val maxTextW = pillRight - textStartX - (16f * resources.displayMetrics.density)

        statusPaint.color = when (state) {
            HudState.EXECUTING -> Color.parseColor("#00E676")
            HudState.PROCESSING -> Color.parseColor("#00B0FF")
            HudState.CALL_RINGING -> Color.parseColor("#FFD600")
            else -> Color.parseColor("#00E5FF")
        }

        val statusY = pillTop + (28f * resources.displayMetrics.density)
        canvas.drawText(actionStatusText, textStartX, statusY, statusPaint)

        val transY = pillTop + (53f * resources.displayMetrics.density)
        val truncated = if (textPaint.measureText(transcriptionText) > maxTextW) {
            var len = transcriptionText.length
            while (len > 4 && textPaint.measureText(transcriptionText.take(len) + "...") > maxTextW) {
                len--
            }
            transcriptionText.take(len) + "..."
        } else {
            transcriptionText
        }

        canvas.drawText(truncated, textStartX, transY, textPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            onDismissRequested?.invoke()
            return true
        }
        return true
    }
}
