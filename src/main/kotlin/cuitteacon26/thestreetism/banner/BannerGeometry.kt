package cuitteacon26.thestreetism.banner

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.sqrt

enum class BannerTextAlignment(val serializedName: String) {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right");

    companion object {
        fun bySerializedName(name: String): BannerTextAlignment {
            return entries.firstOrNull { it.serializedName == name } ?: CENTER
        }
    }
}

object BannerGeometry {
    data class Anchor(
        val pos: BlockPos,
        val face: Direction,
    )

    data class Placement(
        val anchorA: Anchor,
        val anchorB: Anchor,
        val topLeft: Vec3,
        val topRight: Vec3,
        val bottomLeft: Vec3,
        val bottomRight: Vec3,
        val center: Vec3,
        val horizontalDirection: Vec3,
        val normal: Direction,
        val length: Float,
        val height: Float,
    )

    fun create(anchorA: Anchor, anchorB: Anchor, height: Float): Placement {
        val topLeft = surfaceCenter(anchorA)
        val topRight = surfaceCenter(anchorB)
        val horizontalSpan = topRight.subtract(topLeft)
        val horizontalDirection = normalizeOrDefault(horizontalSpan, Vec3(1.0, 0.0, 0.0))
        val length = max(horizontalSpan.length().toFloat(), MIN_SPAN_LENGTH)
        val resolvedHeight = sanitizeHeight(height)
        val downward = Vec3(0.0, -resolvedHeight.toDouble(), 0.0)
        val bottomLeft = topLeft.add(downward)
        val bottomRight = topRight.add(downward)
        val center = topLeft.add(topRight).add(bottomLeft).add(bottomRight).scale(0.25)
        return Placement(
            anchorA = anchorA,
            anchorB = anchorB,
            topLeft = topLeft,
            topRight = topRight,
            bottomLeft = bottomLeft,
            bottomRight = bottomRight,
            center = center,
            horizontalDirection = horizontalDirection,
            normal = anchorA.face,
            length = length,
            height = resolvedHeight,
        )
    }

    fun previewHeight(anchorA: Anchor, anchorB: Anchor, sample: Vec3): Float {
        val topY = max(surfaceCenter(anchorA).y, surfaceCenter(anchorB).y)
        return sanitizeHeight((topY - sample.y).toFloat())
    }

    fun supportBlocks(anchorA: Anchor, anchorB: Anchor): List<BlockPos> {
        return listOf(anchorA.pos, anchorB.pos).distinct()
    }

    fun surfaceCenter(anchor: Anchor): Vec3 = surfaceCenter(anchor.pos, anchor.face)

    fun surfaceCenter(pos: BlockPos, face: Direction): Vec3 {
        return Vec3(
            pos.x + 0.5 + face.stepX * 0.5,
            pos.y + 0.5 + face.stepY * 0.5,
            pos.z + 0.5 + face.stepZ * 0.5,
        )
    }

    fun isValidAnchorFace(face: Direction): Boolean {
        return face != Direction.UP && face != Direction.DOWN
    }

    fun canShareSurface(anchorA: Anchor, anchorB: Anchor): Boolean {
        if (anchorA.face != anchorB.face || !isValidAnchorFace(anchorA.face)) return false
        return when (anchorA.face) {
            Direction.NORTH, Direction.SOUTH -> anchorA.pos.z == anchorB.pos.z
            Direction.EAST, Direction.WEST -> anchorA.pos.x == anchorB.pos.x
            Direction.UP, Direction.DOWN -> false
        } && horizontalSpanLength(anchorA, anchorB) >= MIN_SPAN_LENGTH
    }

    fun boundingBox(placement: Placement, depth: Double): AABB {
        val minX = minOf(placement.topLeft.x, placement.topRight.x, placement.bottomLeft.x, placement.bottomRight.x)
        val maxX = maxOf(placement.topLeft.x, placement.topRight.x, placement.bottomLeft.x, placement.bottomRight.x)
        val minY = minOf(placement.topLeft.y, placement.topRight.y, placement.bottomLeft.y, placement.bottomRight.y)
        val maxY = maxOf(placement.topLeft.y, placement.topRight.y, placement.bottomLeft.y, placement.bottomRight.y)
        val minZ = minOf(placement.topLeft.z, placement.topRight.z, placement.bottomLeft.z, placement.bottomRight.z)
        val maxZ = maxOf(placement.topLeft.z, placement.topRight.z, placement.bottomLeft.z, placement.bottomRight.z)
        val surfaceCenter = placement.center.add(
            placement.normal.stepX * depth / 2.0,
            placement.normal.stepY * depth / 2.0,
            placement.normal.stepZ * depth / 2.0,
        )

        return when (placement.normal) {
            Direction.NORTH, Direction.SOUTH -> AABB(minX, minY, surfaceCenter.z - depth / 2.0, maxX, maxY, surfaceCenter.z + depth / 2.0)
            Direction.EAST, Direction.WEST -> AABB(surfaceCenter.x - depth / 2.0, minY, minZ, surfaceCenter.x + depth / 2.0, maxY, maxZ)
            Direction.UP, Direction.DOWN -> AABB(minX, surfaceCenter.y - depth / 2.0, minZ, maxX, surfaceCenter.y + depth / 2.0, maxZ)
        }
    }

    fun rotationDegrees(direction: Vec3): Float {
        return Math.toDegrees(kotlin.math.atan2(direction.x, direction.z)).toFloat()
    }

    private fun horizontalSpanLength(anchorA: Anchor, anchorB: Anchor): Double {
        val span = surfaceCenter(anchorB).subtract(surfaceCenter(anchorA))
        return sqrt(span.x * span.x + span.z * span.z)
    }

    private fun normalizeOrDefault(value: Vec3, fallback: Vec3): Vec3 {
        val length = sqrt(value.x * value.x + value.y * value.y + value.z * value.z)
        if (length <= 1.0E-5) return fallback
        return Vec3(value.x / length, value.y / length, value.z / length)
    }

    private fun sanitizeHeight(height: Float): Float {
        return if (height.isFinite() && height > 0.25f) height else DEFAULT_HEIGHT
    }

    const val DEFAULT_HEIGHT = 1.0f
    const val MIN_SPAN_LENGTH = 0.5f
    const val BOUNDING_BOX_DEPTH = 0.01
}
