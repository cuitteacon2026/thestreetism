package cuitteacon26.thestreetism.client.render

import cuitteacon26.thestreetism.multiblock.FlagStructureValidator.Plane
import net.minecraft.world.phys.Vec3

/**
 * Builds a subdivided cloth mesh for a flag.
 *
 * The mesh is a grid of (cols+1) × (rows+1) vertices, where
 *   cols = flagWidth  * SUBDIVISIONS_PER_BLOCK
 *   rows = flagHeight * SUBDIVISIONS_PER_BLOCK
 *
 * Vertex positions are in world space relative to the top-left corner.
 * Animation offsets are applied in [FlagAnimation] at render time.
 */
object FlagMeshBuilder {

    const val SUBDIVISIONS_PER_BLOCK = 10

    data class Mesh(
        val cols: Int,
        val rows: Int,
        /** Base (unanimated) vertex positions. Index = row * (cols+1) + col. */
        val basePositions: Array<Vec3>,
    ) {
        val vertexCount get() = (cols + 1) * (rows + 1)
        fun index(col: Int, row: Int) = row * (cols + 1) + col
    }

    /**
 * Build a flat mesh for a flag of [flagWidth] × [flagHeight] cloth blocks.
 * The controller block is the top-left cloth block.
 */
    fun build(flagWidth: Int, flagHeight: Int, plane: Plane): Mesh {
        val cols = flagWidth * SUBDIVISIONS_PER_BLOCK
        val rows = flagHeight * SUBDIVISIONS_PER_BLOCK
        val positions = Array((cols + 1) * (rows + 1)) { Vec3.ZERO }
        for (row in 0..rows) {
            val v = row.toDouble() / rows
            for (col in 0..cols) {
                val u = col.toDouble() / cols
                positions[row * (cols + 1) + col] = if (plane == Plane.XY) {
                    Vec3(0.5, 1.0 - v * flagHeight, u * flagWidth)
                } else {
                    Vec3(u * flagWidth, 1.0 - v * flagHeight, 0.5)
                }
            }
        }
        return Mesh(cols, rows, positions)
    }
}
