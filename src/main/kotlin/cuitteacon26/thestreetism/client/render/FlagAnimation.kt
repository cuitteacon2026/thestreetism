package cuitteacon26.thestreetism.client.render

import net.minecraft.world.phys.Vec3
import kotlin.math.sin

/**
 * Procedural cloth animation — vertex-displacement only, client-side.
 *
 * Each vertex is displaced along the flag normal using:
 *   offset = sin(time * speed + localX * phaseX + localY * phaseY + seed) * amplitude
 *
 * Amplitude scales with distance from the attached pole column (left edge),
 * producing a realistic hanging-cloth effect.
 */
object FlagAnimation {

    private const val SPEED = 1.8f
    private const val PHASE_X = 2.1f
    private const val PHASE_Y = 0.7f
    private const val MAX_AMPLITUDE = 0.18f

    /**
     * Apply animation to [basePositions], writing displaced positions into [out].
     * [time] is game time + partial tick in seconds.
     * [seed] is the per-flag random seed (stored in controller).
     * [cols]/[rows] define mesh resolution.
     * [normal] is the flag's face normal (unit vector perpendicular to cloth plane).
     */
    fun animate(
        basePositions: Array<Vec3>,
        out: Array<Vec3>,
        cols: Int,
        rows: Int,
        time: Float,
        seed: Long,
        normal: Vec3,
    ) {
        val seedOffset = (seed and 0xFFFFL).toFloat() / 65536f * (Math.PI * 2).toFloat()
        for (row in 0..rows) {
            for (col in 0..cols) {
                val idx = row * (cols + 1) + col
                // Normalised U (0 = left/pole, 1 = free edge)
                val u = col.toFloat() / cols
                // Amplitude: near pole (u≈0) → near zero; far from pole (u≈1) → max
                val amplitude = MAX_AMPLITUDE * u * u
                val phase = time * SPEED + u * PHASE_X + (row.toFloat() / rows) * PHASE_Y + seedOffset
                val displacement = sin(phase.toDouble()).toFloat() * amplitude
                val base = basePositions[idx]
                out[idx] = base.add(normal.scale(displacement.toDouble()))
            }
        }
    }
}
