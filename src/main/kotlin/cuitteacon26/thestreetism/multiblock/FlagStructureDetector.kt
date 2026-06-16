package cuitteacon26.thestreetism.multiblock

import cuitteacon26.thestreetism.block.FlagClothBlock
import cuitteacon26.thestreetism.block.ModBlocks
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

object FlagStructureDetector {

    /** Flood-fill all connected cloth blocks from [origin] using 6-direction adjacency. */
    fun findConnectedCloth(level: Level, origin: BlockPos): Set<BlockPos> {
        if (level.getBlockState(origin).block !is FlagClothBlock) return emptySet()
        val found = mutableSetOf<BlockPos>()
        val queue = ArrayDeque<BlockPos>()
        queue.add(origin)
        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            if (!found.add(pos)) continue
            for (dir in net.minecraft.core.Direction.entries) {
                val neighbor = pos.relative(dir)
                if (neighbor !in found && level.getBlockState(neighbor).block is FlagClothBlock) {
                    queue.add(neighbor)
                }
            }
        }
        return found
    }

    /** Compute the axis-aligned bounding box of a set of positions. */
    fun boundingBox(positions: Set<BlockPos>): BoundingBox? {
        if (positions.isEmpty()) return null
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE; var maxZ = Int.MIN_VALUE
        for (p in positions) {
            if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
            if (p.z < minZ) minZ = p.z; if (p.z > maxZ) maxZ = p.z
        }
        return BoundingBox(minX, minY, minZ, maxX, maxY, maxZ)
    }

    data class BoundingBox(val minX: Int, val minY: Int, val minZ: Int, val maxX: Int, val maxY: Int, val maxZ: Int) {
        val width get() = maxX - minX + 1
        val height get() = maxY - minY + 1
        val depth get() = maxZ - minZ + 1
    }
}
