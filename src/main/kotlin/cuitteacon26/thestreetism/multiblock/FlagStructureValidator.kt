package cuitteacon26.thestreetism.multiblock

import cuitteacon26.thestreetism.block.FlagPoleBlock
import cuitteacon26.thestreetism.multiblock.FlagStructureDetector.BoundingBox
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level

/**
 * Validates a flood-filled cloth set against the real flag system rules.
 *
 * Top is always world +Y.
 * The flag must be a one-block-thick complete rectangle on either the XY or ZY plane.
 */
object FlagStructureValidator {

    enum class SupportType { TYPE_A, TYPE_B }

    /**
     * `XY` means the rectangle spans Z/Y at a constant X.
     * `ZY` means the rectangle spans X/Y at a constant Z.
     */
    enum class Plane { XY, ZY }

    data class Corners(
        val topLeft: BlockPos,
        val topRight: BlockPos,
        val bottomLeft: BlockPos,
        val bottomRight: BlockPos,
    )

    data class ValidationResult(
        val valid: Boolean,
        val supportType: SupportType? = null,
        val plane: Plane? = null,
        val width: Int = 0,
        val height: Int = 0,
        val corners: Corners? = null,
        val supportPositions: Set<BlockPos> = emptySet(),
        val errorKey: String? = null,
    )

    fun validate(level: Level, cloth: Set<BlockPos>, box: BoundingBox): ValidationResult {
        if (cloth.isEmpty()) return ValidationResult(valid = false, errorKey = "thestreetism.flag.error.empty")

        val plane = when {
            box.depth == 1 -> Plane.ZY
            box.width == 1 -> Plane.XY
            else -> return ValidationResult(valid = false, errorKey = "thestreetism.flag.error.not_flat")
        }

        val width: Int
        val height: Int
        val corners: Corners

        when (plane) {
            Plane.ZY -> {
                width = box.width
                height = box.height
                for (x in box.minX..box.maxX) {
                    for (y in box.minY..box.maxY) {
                        if (BlockPos(x, y, box.minZ) !in cloth) {
                            return ValidationResult(valid = false, errorKey = "thestreetism.flag.error.not_rectangle")
                        }
                    }
                }

                if (cloth.size != width * height) {
                    return ValidationResult(valid = false, errorKey = "thestreetism.flag.error.not_rectangle")
                }

                corners = Corners(
                    topLeft = BlockPos(box.minX, box.maxY, box.minZ),
                    topRight = BlockPos(box.maxX, box.maxY, box.minZ),
                    bottomLeft = BlockPos(box.minX, box.minY, box.minZ),
                    bottomRight = BlockPos(box.maxX, box.minY, box.minZ),
                )
            }

            Plane.XY -> {
                width = box.depth
                height = box.height
                for (z in box.minZ..box.maxZ) {
                    for (y in box.minY..box.maxY) {
                        if (BlockPos(box.minX, y, z) !in cloth) {
                            return ValidationResult(valid = false, errorKey = "thestreetism.flag.error.not_rectangle")
                        }
                    }
                }

                if (cloth.size != width * height) {
                    return ValidationResult(valid = false, errorKey = "thestreetism.flag.error.not_rectangle")
                }

                corners = Corners(
                    topLeft = BlockPos(box.minX, box.maxY, box.minZ),
                    topRight = BlockPos(box.minX, box.maxY, box.maxZ),
                    bottomLeft = BlockPos(box.minX, box.minY, box.minZ),
                    bottomRight = BlockPos(box.minX, box.minY, box.maxZ),
                )
            }
        }

        val topLeftPole = adjacentPole(level, corners.topLeft)
        val topRightPole = adjacentPole(level, corners.topRight)
        val bottomLeftPole = adjacentPole(level, corners.bottomLeft)
        val bottomRightPole = adjacentPole(level, corners.bottomRight)

        val supports = linkedSetOf<BlockPos>()
        topLeftPole?.let(supports::add)
        topRightPole?.let(supports::add)
        bottomLeftPole?.let(supports::add)
        bottomRightPole?.let(supports::add)

        return when {
            topLeftPole != null && topRightPole != null && bottomLeftPole != null && bottomRightPole != null -> {
                ValidationResult(
                    valid = true,
                    supportType = SupportType.TYPE_A,
                    plane = plane,
                    width = width,
                    height = height,
                    corners = corners,
                    supportPositions = supports,
                )
            }

            topLeftPole != null && topRightPole != null -> {
                ValidationResult(
                    valid = true,
                    supportType = SupportType.TYPE_B,
                    plane = plane,
                    width = width,
                    height = height,
                    corners = corners,
                    supportPositions = supports,
                )
            }

            else -> ValidationResult(valid = false, errorKey = "thestreetism.flag.error.no_pole_support")
        }
    }

    private fun adjacentPole(level: Level, pos: BlockPos): BlockPos? {
        for (direction in Direction.entries) {
            val neighbor = pos.relative(direction)
            if (level.getBlockState(neighbor).block is FlagPoleBlock) {
                return neighbor
            }
        }
        return null
    }
}
