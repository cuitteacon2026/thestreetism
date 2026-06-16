package cuitteacon26.thestreetism.item

import cuitteacon26.thestreetism.multiblock.FlagMultiblockManager
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.server.level.ServerPlayer

class StitchingToolItem(properties: Properties) : Item(properties) {
    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val pos = context.clickedPos
        val player = context.player ?: return InteractionResult.PASS

        if (level.isClientSide) return InteractionResult.SUCCESS

        val serverLevel = level as? net.minecraft.server.level.ServerLevel ?: return InteractionResult.FAIL
        val existing = FlagMultiblockManager.controllerFor(serverLevel, pos)
        if (existing != null) {
            (player as? ServerPlayer)?.let { FlagMultiblockManager.openEditor(it, existing) }
            return InteractionResult.SUCCESS
        }

        val result = FlagMultiblockManager.tryStitch(level, pos, player)
        if (!result.success) {
            player.sendSystemMessage(Component.translatable(result.errorKey ?: "thestreetism.flag.stitch.invalid"))
        }
        return if (result.success) InteractionResult.SUCCESS else InteractionResult.FAIL
    }
}
