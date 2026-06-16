package cuitteacon26.thestreetism.multiblock

import cuitteacon26.thestreetism.Thestreetism
import cuitteacon26.thestreetism.block.FlagClothBlock
import cuitteacon26.thestreetism.block.FlagPoleBlock
import net.minecraft.server.level.ServerLevel
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.level.block.BreakBlockEvent

@EventBusSubscriber(modid = Thestreetism.ID)
object FlagStructureEvents {
    @SubscribeEvent
    fun onBreak(event: BreakBlockEvent) {
        val level = event.level as? ServerLevel ?: return
        FlagMultiblockManager.handleBlockBroken(level, event.pos, event.state)
    }

    @SubscribeEvent
    fun onPlace(event: BlockEvent.EntityPlaceEvent) {
        val level = event.level as? ServerLevel ?: return
        when (event.placedBlock.block) {
            is FlagClothBlock -> FlagMultiblockManager.handleClothPlaced(level, event.pos)
            is FlagPoleBlock -> Unit
        }
    }
}
