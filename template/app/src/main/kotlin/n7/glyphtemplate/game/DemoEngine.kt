package n7.glyphtemplate.game

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import n7.glyph.ui.matrix.MatrixCanvas
import n7.glyph.ui.toy.Haptic
import n7.glyph.ui.toy.Motion
import n7.glyph.ui.toy.ToyEngine

/**
 * Example toy, replace it with the real one. Exercises the whole kit:
 * - a ball rolls the way the phone is tilted ([Motion.downX], [Motion.downY]) and bounces off the round edge with a TICK;
 * - a long press sends a small ring pulse out of the centre, and while the button is held the rim fills up (charge);
 * - letting go fires a ring from the centre, as bright as the charge, and kicks the ball outwards.
 *
 * Coordinates are in LEDs, (12, 12) is the centre of the matrix, y grows down. Time moves only through [frame].
 */
class DemoEngine(private val random: Random) : ToyEngine {

    /** Ball centre, LEDs. Read-only view for tests and the app screen. */
    var ballX = CENTRE
        private set
    var ballY = CENTRE
        private set

    /** Rim charge 0..1 while the button is held. */
    var charge = 0f
        private set

    var holding = false
        private set

    /** Rings now on the matrix (the pulse of a long press and fired rings). */
    val ringCount: Int get() = rings.size

    private var vx: Float
    private var vy: Float
    private var downX = 0f
    private var downY = 0f
    private val rings = mutableListOf<Ring>()
    private val haptics = mutableListOf<Haptic>()
    private val canvas = MatrixCanvas()

    init {
        // A gentle push in a random direction, so the ball moves even on a table.
        val angle = random.nextDouble(0.0, 2 * PI)
        vx = (cos(angle) * START_SPEED).toFloat()
        vy = (sin(angle) * START_SPEED).toFloat()
    }

    override fun onMotion(motion: Motion) {
        downX = motion.downX
        downY = motion.downY
    }

    override fun onLongPress() {
        holding = true
        charge = 0f
        rings += Ring(speed = PULSE_SPEED, maxRadius = PULSE_RADIUS, level = PULSE_LEVEL)
        haptics += Haptic.CLICK
    }

    override fun onRelease() {
        if (!holding) return
        holding = false
        rings += Ring(speed = FIRE_SPEED, maxRadius = FIRE_RADIUS, level = (MatrixCanvas.MAX * (0.35f + 0.65f * charge)).toInt())
        kickBall(KICK_SPEED * charge)
        haptics += if (charge >= 1f) Haptic.THUD else Haptic.CLICK
        charge = 0f
    }

    override fun frame(dtMs: Long): IntArray {
        var left = dtMs.coerceAtLeast(0L)
        while (left > 0) {
            val step = min(left, STEP_MS)
            step(step / 1000f)
            left -= step
        }
        return draw(canvas.clear())
    }

    override fun still(): IntArray = MatrixCanvas().apply { disc(CENTRE, CENTRE, BALL_RADIUS, MatrixCanvas.MAX) }.frame()

    override fun drainHaptics(): List<Haptic> = haptics.toList().also { haptics.clear() }

    private fun step(dt: Float) {
        if (holding) charge = (charge + dt * 1000f / CHARGE_MS).coerceAtMost(1f)
        rings.forEach { it.radius += it.speed * dt }
        rings.removeAll { it.radius > it.maxRadius }

        vx += downX * GRAVITY_SCALE * dt
        vy += downY * GRAVITY_SCALE * dt
        val damping = (1f - FRICTION * dt).coerceAtLeast(0f)
        vx *= damping
        vy *= damping
        val speed = sqrt(vx * vx + vy * vy)
        if (speed > MAX_SPEED) {
            vx *= MAX_SPEED / speed
            vy *= MAX_SPEED / speed
        }
        ballX += vx * dt
        ballY += vy * dt
        bounce()
    }

    /** Keeps the ball inside the round edge; a hit reflects the speed towards the centre and ticks. */
    private fun bounce() {
        val dx = ballX - CENTRE
        val dy = ballY - CENTRE
        val dist = sqrt(dx * dx + dy * dy)
        if (dist <= LIMIT) return
        val nx = dx / dist
        val ny = dy / dist
        ballX = CENTRE + nx * LIMIT
        ballY = CENTRE + ny * LIMIT
        val into = vx * nx + vy * ny
        if (into > 0) {
            vx -= (1f + BOUNCE) * into * nx
            vy -= (1f + BOUNCE) * into * ny
            if (into > TICK_SPEED) haptics += Haptic.TICK
        }
    }

    private fun kickBall(speed: Float) {
        if (speed <= 0f) return
        val dx = ballX - CENTRE
        val dy = ballY - CENTRE
        val dist = sqrt(dx * dx + dy * dy)
        val (nx, ny) = if (dist > 0.5f) {
            dx / dist to dy / dist
        } else {
            val angle = random.nextDouble(0.0, 2 * PI)
            cos(angle).toFloat() to sin(angle).toFloat()
        }
        vx += nx * speed
        vy += ny * speed
    }

    private fun draw(c: MatrixCanvas): IntArray {
        for (ring in rings) {
            val fade = 1f - ring.radius / ring.maxRadius
            c.circle(CENTRE, CENTRE, ring.radius, (ring.level * (0.3f + 0.7f * fade)).toInt())
        }
        if (holding) c.rim(charge, RIM_LEVEL)
        // Filled shapes overwrite: the ball stays solid on top of the rings.
        c.disc(ballX, ballY, BALL_RADIUS, MatrixCanvas.MAX)
        return c.frame()
    }

    private class Ring(val speed: Float, val maxRadius: Float, val level: Int) {
        var radius = 0f
    }

    companion object {
        const val CENTRE = 12f
        const val BALL_RADIUS = 2f
        /** Farthest the ball centre gets from the matrix centre: the ball stays inside the visible circle. */
        const val LIMIT = 9.5f
        /** LEDs/s2 per m/s2 of gravity: upright, the ball falls about 40 LEDs/s2. */
        const val GRAVITY_SCALE = 4f
        const val FRICTION = 0.8f
        const val BOUNCE = 0.55f
        const val MAX_SPEED = 45f
        const val START_SPEED = 6f
        const val TICK_SPEED = 4f
        const val KICK_SPEED = 30f
        const val CHARGE_MS = 1200f
        const val PULSE_SPEED = 18f
        const val PULSE_RADIUS = 6f
        const val PULSE_LEVEL = 2600
        const val FIRE_SPEED = 30f
        const val FIRE_RADIUS = 13f
        const val RIM_LEVEL = 2400
        private const val STEP_MS = 16L
    }
}
