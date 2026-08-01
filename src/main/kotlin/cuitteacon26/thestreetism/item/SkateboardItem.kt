package cuitteacon26.thestreetism.item

import cuitteacon26.thestreetism.entity.ModEntities
import cuitteacon26.thestreetism.entity.SkateboardEntity
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.gameevent.GameEvent

class SkateboardItem(properties: Properties) : Item(properties) {
    override fun useOn(context: UseOnContext): InteractionResult {
        if (context.clickedFace != Direction.UP) return InteractionResult.PASS

        val level = context.level
        val player = context.player ?: return InteractionResult.FAIL
        val clickedPos = context.clickedPos
        if (!level.worldBorder.isWithinBounds(clickedPos) || !level.isLoaded(clickedPos) || !player.isWithinBlockInteractionRange(clickedPos, 0.0)) {
            return InteractionResult.FAIL
        }

        val location = context.clickLocation.add(0.0, PLACEMENT_OFFSET_Y, 0.0)
        val skateboard = SkateboardEntity(ModEntities.SKATEBOARD, level)
        skateboard.setPos(location.x, location.y, location.z)
        skateboard.yRot = player.yRot
        if (!level.noCollision(skateboard, skateboard.boundingBox) || !level.noBorderCollision(skateboard, skateboard.boundingBox)) {
            return InteractionResult.FAIL
        }

        if (!level.isClientSide) {
            skateboard.applyComponentsFromItemStack(context.itemInHand)
            level.addFreshEntity(skateboard)
            context.itemInHand.consume(1, player)
            level.gameEvent(player, GameEvent.ENTITY_PLACE, location)
            level.playSound(null, location.x, location.y, location.z, SoundEvents.WOOD_PLACE, player.soundSource, 0.8f, 1.1f)
        }
        return InteractionResult.SUCCESS
    }

    companion object {
        private const val PLACEMENT_OFFSET_Y = 0.04
    }
}
