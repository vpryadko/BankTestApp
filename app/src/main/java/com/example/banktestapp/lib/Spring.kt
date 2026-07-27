package com.example.banktestapp.lib

import kotlin.math.abs
import kotlin.math.min

/**
 * Пружина с теми же параметрами, что и у Framer Motion (stiffness / damping /
 * mass, время в секундах). Интегрируется полуявным методом Эйлера с шагом 1 мс —
 * при таких жёсткостях этого достаточно, чтобы результат был неотличим от
 * аналитического решения.
 */
class Spring(
    initial: Float,
    private var stiffness: Float,
    private var damping: Float,
    private var mass: Float,
) {
    var value: Float = initial
        private set

    var target: Float = initial

    private var velocity = 0f

    val settled: Boolean
        get() = abs(value - target) < 0.0005f && abs(velocity) < 0.005f

    fun configure(stiffness: Float, damping: Float, mass: Float) {
        this.stiffness = stiffness
        this.damping = damping
        this.mass = mass
    }

    fun snapTo(v: Float) {
        value = v
        target = v
        velocity = 0f
    }

    fun update(dtSeconds: Float) {
        var remaining = dtSeconds
        val h = 0.001f
        while (remaining > 0f) {
            val step = min(h, remaining)
            remaining -= step
            val force = -stiffness * (value - target) - damping * velocity
            velocity += force / mass * step
            value += velocity * step
        }
        if (settled) {
            value = target
            velocity = 0f
        }
    }
}

/** Линейная интерполяция значения x из отрезка [a,b] в [c,d] с ограничением. */
fun mapRange(x: Float, a: Float, b: Float, c: Float, d: Float): Float {
    if (b == a) return c
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return c + (d - c) * t
}

