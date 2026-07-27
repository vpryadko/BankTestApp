package com.example.banktestapp.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import com.example.banktestapp.design.Tokens
import com.example.banktestapp.lib.Format
import com.example.banktestapp.lib.Haptics
import com.example.banktestapp.lib.RulerRenderer
import com.example.banktestapp.lib.Sensitivity
import com.example.banktestapp.lib.Spring
import com.example.banktestapp.lib.ValueModel
import com.example.banktestapp.lib.mapRange
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Экран «Выберите сумму» целиком: шкала, режим точной настройки и вся
 * статика вокруг них.
 *
 * Весь экран нарисован в фиксированной логической коробке 418 × 935 и
 * отмасштабирован под устройство одним множителем — ровно как в прототипе.
 * Симуляция значения живёт в обычных полях и крутится в одном цикле
 * Choreographer, так что во время драга не происходит ни одного layout-прохода.
 */
class AmountPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    // ── константы жеста ──────────────────────────────────────────────────────
    private companion object {
        const val LONG_PRESS_MS = 250L
        const val MOVE_CANCELS_LONG_PRESS_PX = 8.0
        const val INITIAL_VALUE = 25_000_000.0
    }

    private val haptics = Haptics(context)

    // ── масштабирование логической коробки ───────────────────────────────────
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    // ── анимируемая «оболочка» ───────────────────────────────────────────────
    /** 0 — обычный экран, 1 — режим точной настройки. */
    private val progress = Spring(0f, stiffness = 360f, damping = 31f, mass = 0.9f)

    /** Подъём пальца над шкалой, px макета, 0…160. */
    private var elevation = 0.0

    /** Пульс индикатора на проходе зубца. */
    private var pulseStart = -1L
    private var indicatorScale = 1f

    /**
     * Подсказка видна всегда: она объясняет неочевидный жест, поэтому не
     * прячется после первого длинного нажатия. Поставьте `false`, если
     * когда-нибудь понадобится снова скрывать её после обучения.
     */
    private var hintShown = true
    private val hintAlwaysVisible = true
    private var hintOpacity = 1f

    // ── состояние симуляции ──────────────────────────────────────────────────
    private var raw = INITIAL_VALUE
    private var velocity = 0.0          // сум в мс
    private var pendingDx = 0.0         // px, накопленные с прошлого кадра
    private var pointerY = Tokens.RULER_CENTER_Y.toDouble()
    private var dragging = false
    private var precision = false
    private var upp = Sensitivity.BASE_UPP
    private var hapticBucket = Math.round(INITIAL_VALUE / 1_000_000.0)
    private var boundHit = false

    /** Значение, показанное в прошлом кадре — для решения «перерисовывать ли». */
    private var shown = INITIAL_VALUE
    private var lastShown = Double.NaN
    private var lastUpp = Double.NaN
    private var lastProgress = Float.NaN
    private var lastElevation = Double.NaN
    private var lastIndicatorScale = Float.NaN
    private var lastHintOpacity = Float.NaN

    private var downX = 0.0
    private var downY = 0.0
    private var lastX = 0.0
    private var longPressPending = false

    private val longPressRunnable = Runnable { enterPrecision() }

    // ── кисти ────────────────────────────────────────────────────────────────
    private fun paint(size: Float, weight: Int, color: Int, tracking: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = Typeface.create(Typeface.SANS_SERIF, weight, false)
            this.color = color
            letterSpacing = tracking
            fontFeatureSettings = "tnum"
        }

    private val titlePaint = paint(18f, 700, Tokens.ink, -0.2f / 18f)
        .apply { textAlign = Paint.Align.CENTER }
    private val captionPaint = paint(13.5f, 400, Tokens.inkMuted)
        .apply { textAlign = Paint.Align.CENTER }
    private val amountPaint = paint(32f, 800, Tokens.ink, -0.9f / 32f)
        .apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = paint(11.5f, 400, Tokens.inkFaint)
        .apply { textAlign = Paint.Align.CENTER }
    private val chipPaint = paint(15f, 700, Tokens.chipInk, -0.1f / 15f)
        .apply { textAlign = Paint.Align.CENTER }
    private val ctaPaint = paint(17f, 700, Tokens.white, -0.1f / 17f)
        .apply { textAlign = Paint.Align.CENTER }
    private val metaPaint = paint(11f, 500, Tokens.inkFaint, 0.1f / 11f)
    private val panelAmountPaint = paint(34f, 800, Tokens.ink, -1f / 34f)
        .apply { textAlign = Paint.Align.CENTER }

    private val solid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private val baseFrame = RulerRenderer.Frame()
    private val baseStyle = RulerRenderer.Style(
        tickCenterY = Tokens.BASE_TICK_CENTER,
        tickMaxH = Tokens.BASE_TICK_MAX,
        tickW = 2f,
        labelBaselineY = null,
        fade = 26f,
    )
    private val panelFrame = RulerRenderer.Frame()
    private val panelStyle = RulerRenderer.Style(
        tickCenterY = Tokens.PANEL_TICK_CENTER,
        tickMaxH = Tokens.PANEL_TICK_MAX,
        tickW = 2f,
        labelBaselineY = 60f,
        labelSize = 10f,
        fade = 30f,
    )

    private var headerShader: LinearGradient? = null

    // ── цикл кадров ──────────────────────────────────────────────────────────
    private var lastFrameNanos = 0L
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            step(frameTimeNanos)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        isClickable = true
        setBackgroundColor(Tokens.canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scale = min(w / Tokens.SCREEN_W, h / Tokens.SCREEN_H)
        offsetX = (w - Tokens.SCREEN_W * scale) / 2f
        offsetY = (h - Tokens.SCREEN_H * scale) / 2f
        headerShader = LinearGradient(
            0f, 0f, 0f, Tokens.HEADER_GRADIENT_H,
            Tokens.headerTop, Tokens.canvas,
            Shader.TileMode.CLAMP,
        )
    }

    // ── шаг симуляции ────────────────────────────────────────────────────────
    private fun step(nowNanos: Long) {
        if (lastFrameNanos == 0L) {
            lastFrameNanos = nowNanos
            return
        }
        val dt = min(50.0, (nowNanos - lastFrameNanos) / 1_000_000.0)
        lastFrameNanos = nowNanos
        if (dt <= 0) return

        // 1. высота → чувствительность. Слегка сглажено, чтобы дрожащий палец
        //    не заставлял шкалу «дышать», но достаточно быстро, чтобы связь
        //    ощущалась прямой.
        val target = if (precision) {
            ValueModel.clamp(
                Tokens.RULER_CENTER_Y - pointerY,
                0.0,
                Sensitivity.MAX_ELEVATION,
            )
        } else 0.0
        elevation = ValueModel.approach(elevation, target, if (precision) 38.0 else 110.0, dt)
        if (!precision && elevation < 0.4) elevation = 0.0
        upp = Sensitivity.unitsPerPixel(elevation)

        // 2. горизонтальный ввод, применённый в пространстве значений на текущей передаче
        if (dragging) {
            val delta = pendingDx * upp * ValueModel.boundaryResistance(raw)
            raw += delta
            velocity = ValueModel.approach(velocity, delta / dt, 45.0, dt)
        } else {
            // инерция — масштабируется передачей, поэтому точная установка не уползает
            if (abs(velocity) > 0.5) {
                raw += velocity * dt
                velocity *= exp(-dt / 220.0)
            } else {
                velocity = 0.0
                val bounded = ValueModel.clamp(raw, ValueModel.MIN_VALUE, ValueModel.MAX_VALUE)
                raw = if (bounded != raw) {
                    ValueModel.approach(raw, bounded, 70.0, dt)
                } else {
                    ValueModel.approach(raw, ValueModel.snap(raw), 80.0, dt)
                }
                if (abs(raw - ValueModel.snap(raw)) < 20) raw = ValueModel.snap(raw)
            }
        }
        pendingDx = 0.0

        // 3. обратная связь на упоре
        val outside = raw < ValueModel.MIN_VALUE - 1 || raw > ValueModel.MAX_VALUE + 1
        if (outside && !boundHit) {
            boundHit = true
            haptics.bound()
        } else if (!outside) {
            boundHit = false
        }

        // 4. зубцы — функция позиции, без отставания; они исчезают сами, как
        //    только становятся уже, чем различает глаз
        shown = ValueModel.magnetize(raw, upp)

        // 5. пружины и тайминги оболочки
        progress.update((dt / 1000.0).toFloat())
        updatePulse(nowNanos)
        updateHint(dt)

        // 6. щелчки зубцов — миллионы при прокрутке, 100 000 под увеличением
        val hs = ValueModel.hapticStep(upp)
        val bucket = RulerRenderer.bucket(shown, hs)
        if (bucket != hapticBucket) {
            hapticBucket = bucket
            if (!outside) {
                haptics.tick()
                if (ValueModel.STEP / upp > 14) pulseStart = nowNanos
            }
        }

        if (dirty()) invalidate()
    }

    private fun dirty(): Boolean {
        val changed = shown != lastShown ||
                upp != lastUpp ||
                progress.value != lastProgress ||
                elevation != lastElevation ||
                indicatorScale != lastIndicatorScale ||
                hintOpacity != lastHintOpacity
        if (changed) {
            lastShown = shown
            lastUpp = upp
            lastProgress = progress.value
            lastElevation = elevation
            lastIndicatorScale = indicatorScale
            lastHintOpacity = hintOpacity
        }
        return changed
    }

    /** animate(indicatorScale, [1.22, 1], { duration: .26, ease: [.22,1,.36,1] }) */
    private fun updatePulse(nowNanos: Long) {
        if (pulseStart < 0) {
            indicatorScale = 1f
            return
        }
        val t = ((nowNanos - pulseStart) / 1_000_000_000.0f) / 0.26f
        if (t >= 1f) {
            pulseStart = -1
            indicatorScale = 1f
        } else {
            val eased = 1f - (1f - t).pow(5f) // близко к cubic-bezier(.22,1,.36,1)
            indicatorScale = 1.22f + (1f - 1.22f) * eased
        }
    }

    private fun updateHint(dtMs: Double) {
        val target = if (hintShown) 1f else 0f
        if (hintOpacity == target) return
        val stepAmount = (dtMs / 320.0).toFloat()
        hintOpacity = if (target > hintOpacity) {
            min(target, hintOpacity + stepAmount)
        } else {
            max(target, hintOpacity - stepAmount)
        }
    }

    // ── жесты ────────────────────────────────────────────────────────────────
    private fun toDesignX(x: Float) = ((x - offsetX) / scale).toDouble()
    private fun toDesignY(y: Float) = ((y - offsetY) / scale).toDouble()

    private fun enterPrecision() {
        longPressPending = false
        precision = true
        progress.target = 1f
        haptics.engage()
        if (!hintAlwaysVisible) hintShown = false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = toDesignX(event.x)
        val y = toDesignY(event.y)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Зона жеста намеренно больше шкалы, которой она управляет.
                val inSlider = y >= Tokens.RULER_TOP - 34 &&
                        y <= Tokens.RULER_TOP + Tokens.RULER_H + 22
                if (!inSlider) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = x
                downY = y
                lastX = x
                dragging = true
                velocity = 0.0
                pendingDx = 0.0
                pointerY = y
                longPressPending = true
                postDelayed(longPressRunnable, LONG_PRESS_MS)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                if (longPressPending) {
                    val moved = hypot(x - downX, y - downY)
                    if (moved > MOVE_CANCELS_LONG_PRESS_PX) {
                        removeCallbacks(longPressRunnable)
                        longPressPending = false
                    }
                }
                pendingDx += x - lastX
                lastX = x
                pointerY = y
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                endDrag()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun endDrag() {
        if (longPressPending) {
            removeCallbacks(longPressRunnable)
            longPressPending = false
        }
        if (!dragging) return
        dragging = false
        // Отпускание масштабируется передачей: рывок в покое всё ещё
        // прокидывает через диапазон, а установка при 1 px = 1 000 замирает
        // ровно там, где палец её оставил.
        velocity *= upp / Sensitivity.BASE_UPP
        if (precision) {
            precision = false
            progress.target = 0f
            haptics.release()
        }
    }

    // ── отрисовка ────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        val p = progress.value
        val contentDim = mapRange(p, 0f, 1f, 1f, 0.5f)

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        canvas.clipRect(0f, 0f, Tokens.SCREEN_W, Tokens.SCREEN_H)

        drawHeaderGradient(canvas)
        drawStaticContent(canvas, contentDim, p)
        drawBaseRuler(canvas, contentDim, p)
        drawHint(canvas)
        drawChipAndCta(canvas, contentDim)
        drawHomeIndicator(canvas)

        // ── режим точной настройки ───────────────────────────────────────────
        if (p > 0.001f) {
            solid.shader = null
            solid.color = Tokens.dim
            solid.alpha = (0.16f * p * 255).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, Tokens.SCREEN_W, Tokens.SCREEN_H, solid)
            solid.alpha = 255

            drawSpine(canvas, p)
            drawPanel(canvas, p)
        }

        canvas.restore()
    }

    private fun drawHeaderGradient(canvas: Canvas) {
        solid.shader = headerShader
        canvas.drawRect(0f, 0f, Tokens.SCREEN_W, Tokens.HEADER_GRADIENT_H, solid)
        solid.shader = null
    }

    private fun drawStaticContent(canvas: Canvas, contentDim: Float, p: Float) {
        val cx = Tokens.SCREEN_W / 2f

        withAlpha(titlePaint, Tokens.ink, contentDim)
        drawBoxedText(canvas, "Выберите сумму", cx, Tokens.TITLE_TOP, 24f, titlePaint)

        withAlpha(captionPaint, Tokens.inkMuted, contentDim)
        drawBoxedText(canvas, "Максимум 50 млн сум", cx, Tokens.CAPTION_TOP, 18f, captionPaint)

        // Сумма гаснет и слегка увеличивается, уступая место панели.
        val amountOpacity = mapRange(p, 0f, 1f, 1f, 0.12f)
        val amountScale = mapRange(p, 0f, 1f, 1f, 1.05f)
        val committed = ValueModel.clamp(
            ValueModel.snap(shown),
            ValueModel.MIN_VALUE,
            ValueModel.MAX_VALUE,
        )
        canvas.save()
        canvas.scale(amountScale, amountScale, cx, Tokens.AMOUNT_TOP + 21f)
        withAlpha(amountPaint, Tokens.ink, amountOpacity)
        drawBoxedText(canvas, "${Format.amount(committed)} UZS", cx, Tokens.AMOUNT_TOP, 42f, amountPaint)
        canvas.restore()
    }

    private fun drawBaseRuler(canvas: Canvas, contentDim: Float, p: Float) {
        val layer = if (contentDim >= 0.999f) {
            canvas.save()
        } else {
            canvas.saveLayerAlpha(
                0f, Tokens.RULER_TOP, Tokens.SCREEN_W, Tokens.RULER_TOP + Tokens.RULER_H,
                (contentDim * 255).toInt().coerceIn(0, 255),
            )
        }
        canvas.translate(0f, Tokens.RULER_TOP)

        baseFrame.width = Tokens.SCREEN_W
        baseFrame.height = Tokens.RULER_H
        baseFrame.value = shown
        baseFrame.unitsPerPx = Sensitivity.BASE_UPP
        baseFrame.max = ValueModel.MAX_VALUE

        // «Шкала растягивается» — сделано в отрисовке, а не CSS-масштабом,
        // поэтому деления остаются пиксельно чёткими в любой точке пружины.
        baseStyle.tickMaxH = Tokens.BASE_TICK_MAX * (1f + 0.34f * p)

        RulerRenderer.draw(canvas, baseFrame, baseStyle)

        // Зелёный индикатор — единственный элемент, который на этом экране уже
        // означает «здесь».
        solid.shader = null
        solid.color = Tokens.green
        rect.set(
            Tokens.SCREEN_W / 2f - 2.5f, 0f,
            Tokens.SCREEN_W / 2f + 2.5f, Tokens.RULER_H,
        )
        canvas.drawRoundRect(rect, 3f, 3f, solid)

        canvas.restoreToCount(layer)
    }

    private fun drawHint(canvas: Canvas) {
        if (hintOpacity <= 0.005f) return
        withAlpha(hintPaint, Tokens.inkFaint, hintOpacity)
        val y = Tokens.HINT_TOP - 4f * (1f - hintOpacity)
        drawBoxedText(
            canvas,
            "Задержите шкалу и поднимите палец — шаг станет точнее",
            Tokens.SCREEN_W / 2f, y, 16f, hintPaint,
        )
    }

    private fun drawChipAndCta(canvas: Canvas, contentDim: Float) {
        val cx = Tokens.SCREEN_W / 2f

        solid.shader = null
        solid.color = Tokens.chipBg
        solid.alpha = (contentDim * 255).toInt().coerceIn(0, 255)
        rect.set(
            (Tokens.SCREEN_W - Tokens.CHIP_W) / 2f, Tokens.CHIP_TOP,
            (Tokens.SCREEN_W + Tokens.CHIP_W) / 2f, Tokens.CHIP_TOP + Tokens.CHIP_H,
        )
        canvas.drawRoundRect(rect, Tokens.CHIP_H / 2f, Tokens.CHIP_H / 2f, solid)
        withAlpha(chipPaint, Tokens.chipInk, contentDim)
        drawBoxedText(canvas, "Как увеличить свои шансы?", cx, Tokens.CHIP_TOP, Tokens.CHIP_H, chipPaint)

        solid.color = Tokens.green
        solid.alpha = (contentDim * 255).toInt().coerceIn(0, 255)
        rect.set(
            Tokens.CTA_INSET, Tokens.CTA_TOP,
            Tokens.SCREEN_W - Tokens.CTA_INSET, Tokens.CTA_TOP + Tokens.CTA_H,
        )
        canvas.drawRoundRect(rect, Tokens.CTA_H / 2f, Tokens.CTA_H / 2f, solid)
        withAlpha(ctaPaint, Tokens.white, contentDim)
        drawBoxedText(canvas, "Отправить заявку", cx, Tokens.CTA_TOP, Tokens.CTA_H, ctaPaint)
        solid.alpha = 255
    }

    private fun drawHomeIndicator(canvas: Canvas) {
        solid.shader = null
        solid.color = Tokens.navGlyph
        solid.alpha = 90
        val w = 140f
        rect.set(
            (Tokens.SCREEN_W - w) / 2f, Tokens.NAV_TOP + 26f,
            (Tokens.SCREEN_W + w) / 2f, Tokens.NAV_TOP + 31f,
        )
        canvas.drawRoundRect(rect, 2.5f, 2.5f, solid)
        solid.alpha = 255
    }

    // ── позиция панели ───────────────────────────────────────────────────────
    private fun panelTop(): Float = max(
        Tokens.PANEL_MIN_TOP,
        Tokens.PANEL_REST_BOTTOM - Tokens.PANEL_H - Tokens.PANEL_FOLLOW * elevation.toFloat(),
    )

    /**
     * Существующий зелёный индикатор прорастает вверх и несёт панель.
     * Его залитая часть — шкала подъёма, единственный по-настоящему новый
     * элемент на экране.
     */
    private fun drawSpine(canvas: Canvas, p: Float) {
        val top = panelTop() + Tokens.PANEL_H
        val height = max(0f, Tokens.RULER_TOP + 18f - top)
        if (height <= 0f) return

        val cx = Tokens.SCREEN_W / 2f
        val alpha = (p * 255).toInt().coerceIn(0, 255)

        solid.shader = null
        solid.color = Tokens.green
        solid.alpha = (0.20f * p * 255).toInt().coerceIn(0, 255)
        rect.set(cx - 1f, top, cx + 1f, top + height)
        canvas.drawRoundRect(rect, 1f, 1f, solid)

        val fill = (elevation / Sensitivity.MAX_ELEVATION).toFloat().coerceIn(0f, 1f)
        solid.color = Tokens.green
        solid.alpha = alpha
        rect.set(cx - 1f, top + height * (1f - fill), cx + 1f, top + height)
        canvas.drawRoundRect(rect, 1f, 1f, solid)

        // Четыре засечки на опорных точках чувствительности.
        for (f in floatArrayOf(0.25f, 0.5f, 0.75f, 1f)) {
            val threshold = f * Sensitivity.MAX_ELEVATION.toFloat()
            val o = mapRange(elevation.toFloat(), threshold - 14f, threshold + 6f, 0.16f, 1f)
            solid.alpha = (o * p * 255).toInt().coerceIn(0, 255)
            val y = top + height * (1f - f)
            rect.set(cx - 4f, y - 0.75f, cx + 4f, y + 0.75f)
            canvas.drawRoundRect(rect, 1f, 1f, solid)
        }
        solid.alpha = 255
    }

    private fun drawPanel(canvas: Canvas, p: Float) {
        val opacity = mapRange(p, 0f, 0.55f, 0f, 1f)
        if (opacity <= 0.004f) return

        val panelScale = mapRange(p, 0f, 1f, 0.93f, 1f)
        val lift = mapRange(p, 0f, 1f, 22f, 0f)
        val top = panelTop() + lift
        val left = Tokens.PANEL_X
        val right = left + Tokens.PANEL_W
        val bottom = top + Tokens.PANEL_H
        val cx = Tokens.SCREEN_W / 2f

        val layer = canvas.saveLayerAlpha(
            left - 60f, top - 40f, right + 60f, bottom + 90f,
            (opacity * 255).toInt().coerceIn(0, 255),
        )
        // transform-origin: 50% 100%
        canvas.scale(panelScale, panelScale, cx, bottom)

        drawPanelShadow(canvas, left, top, right, bottom)

        solid.shader = null
        solid.color = Color.argb(235, 255, 255, 255)
        rect.set(left, top, right, bottom)
        canvas.drawRoundRect(rect, Tokens.PANEL_RADIUS, Tokens.PANEL_RADIUS, solid)

        solid.style = Paint.Style.STROKE
        solid.strokeWidth = 1f
        solid.color = Color.argb(235, 255, 255, 255)
        rect.set(left + 0.5f, top + 0.5f, right - 0.5f, bottom - 0.5f)
        canvas.drawRoundRect(rect, Tokens.PANEL_RADIUS, Tokens.PANEL_RADIUS, solid)
        solid.style = Paint.Style.FILL

        // ── строка метаданных ────────────────────────────────────────────────
        // «шаг 100 000» получает место на экране только тогда, когда зубец стал
        // достаточно широким, чтобы его действительно чувствовать — около 80 px.
        val detentOpacity = mapRange(elevation.toFloat(), 74f, 104f, 0f, 1f)
        val metaBaseline = baselineIn(top + Tokens.PANEL_PAD, 14f, metaPaint)

        metaPaint.textAlign = Paint.Align.LEFT
        withAlpha(metaPaint, Tokens.inkFaint, detentOpacity)
        canvas.drawText(
            "шаг ${Format.amount(ValueModel.STEP)}",
            left + Tokens.PANEL_PAD, metaBaseline, metaPaint,
        )

        metaPaint.textAlign = Paint.Align.RIGHT
        withAlpha(metaPaint, Tokens.inkFaint, 1f)
        canvas.drawText(
            "1 px · ${Format.sensitivity(upp)}",
            right - Tokens.PANEL_PAD, metaBaseline, metaPaint,
        )

        // ── крупная сумма ────────────────────────────────────────────────────
        val committed = ValueModel.clamp(
            ValueModel.snap(shown),
            ValueModel.MIN_VALUE,
            ValueModel.MAX_VALUE,
        )
        withAlpha(panelAmountPaint, Tokens.ink, 1f)
        drawBoxedText(
            canvas, "${Format.amount(committed)} UZS",
            cx, top + Tokens.PANEL_PAD + 14f + 8f, 42f, panelAmountPaint,
        )

        // ── увеличенная шкала ────────────────────────────────────────────────
        canvas.save()
        canvas.translate(left + Tokens.PANEL_PAD, top + Tokens.PANEL_CANVAS_TOP)

        panelFrame.width = Tokens.PANEL_CANVAS_W
        panelFrame.height = Tokens.PANEL_CANVAS_H
        panelFrame.value = shown
        panelFrame.unitsPerPx = upp
        panelFrame.max = ValueModel.MAX_VALUE
        RulerRenderer.draw(canvas, panelFrame, panelStyle)

        solid.shader = null
        solid.color = Tokens.green
        solid.alpha = 255
        val icx = Tokens.PANEL_CANVAS_W / 2f
        val half = 24f * indicatorScale
        rect.set(
            icx - 2f, Tokens.PANEL_TICK_CENTER - half,
            icx + 2f, Tokens.PANEL_TICK_CENTER + half,
        )
        canvas.drawRoundRect(rect, 2f, 2f, solid)
        canvas.restore()

        canvas.restoreToCount(layer)
    }

    /** box-shadow: 0 26px 64px -14px rgba(12,42,36,.30), собранная из слоёв. */
    private fun drawPanelShadow(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        solid.shader = null
        val steps = 12
        for (i in steps - 1 downTo 0) {
            val f = i / (steps - 1f)
            val grow = -14f + 32f * f
            val a = 0.30f * (1f - f).pow(1.6f) * 0.22f
            solid.color = Color.argb((a * 255).toInt().coerceIn(0, 255), 12, 42, 36)
            rect.set(l - grow, t - grow + 26f, r + grow, b + grow + 26f)
            canvas.drawRoundRect(
                rect,
                Tokens.PANEL_RADIUS + grow,
                Tokens.PANEL_RADIUS + grow,
                solid,
            )
        }
        solid.alpha = 255
    }

    // ── мелкие помощники отрисовки текста ────────────────────────────────────
    private fun withAlpha(paint: Paint, color: Int, opacity: Float) {
        paint.color = color
        paint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
    }

    private fun baselineIn(boxTop: Float, lineHeight: Float, paint: Paint): Float {
        val fm = paint.fontMetrics
        return boxTop + lineHeight / 2f - (fm.ascent + fm.descent) / 2f
    }

    private fun drawBoxedText(
        canvas: Canvas,
        text: String,
        cx: Float,
        boxTop: Float,
        lineHeight: Float,
        paint: Paint,
    ) {
        canvas.drawText(text, cx, baselineIn(boxTop, lineHeight, paint), paint)
    }
}

