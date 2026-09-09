package cuitteacon26.thestreetism.item

import cuitteacon26.thestreetism.entity.ModEntities
import cuitteacon26.thestreetism.entity.SkateboardEntity
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

class SkateboardItem(properties: Properties) : Item(properties) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        // A board is a single-seat vehicle. Refuse a second placement while the
        // player is riding anything so force-dismounting cannot leave an orphaned
        // board (or unexpectedly displace a horse/boat).
        if (player.isPassenger) return InteractionResult.FAIL
        if (level.isClientSide) return InteractionResult.SUCCESS

        val radians = Math.toRadians(player.yRot.toDouble())
        val forward = Vec3(-sin(radians), 0.0, cos(radians))
        val spawnPos = player.position().add(forward.x * PLACEMENT_DISTANCE, 0.0, forward.z * PLACEMENT_DISTANCE)

        val spawnBlockPos = BlockPos.containing(spawnPos)
        if (!level.worldBorder.isWithinBounds(spawnBlockPos) || !level.isLoaded(spawnBlockPos)) {
            return InteractionResult.FAIL
        }

        val skateboard = SkateboardEntity(ModEntities.SKATEBOARD, level)
        skateboard.setPos(spawnPos.x, spawnPos.y, spawnPos.z)
        skateboard.yRot = player.yRot
        if (!level.noCollision(skateboard, skateboard.boundingBox) || !level.noBorderCollision(skateboard, skateboard.boundingBox)) {
            return InteractionResult.FAIL
        }

        skateboard.applyComponentsFromItemStack(player.getItemInHand(hand))
        if (!level.addFreshEntity(skateboard)) return InteractionResult.FAIL
        if (!player.startRiding(skateboard, true, true)) {
            skateboard.discard()
            return InteractionResult.FAIL
        }
        player.getItemInHand(hand).consume(1, player)
        level.gameEvent(player, GameEvent.ENTITY_PLACE, spawnPos)
        level.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z, SoundEvents.WOOD_PLACE, player.soundSource, 0.8f, 1.1f)
        return InteractionResult.SUCCESS
    }

    companion object {
        private const val PLACEMENT_DISTANCE = 0.9
    }
}
