package cuitteacon26.thestreetism.blockentity

import cuitteacon26.thestreetism.Thestreetism
import cuitteacon26.thestreetism.block.ModBlocks
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.getValue

object ModBlockEntities {
    val REGISTRY = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Thestreetism.ID)

    val FLAG_CONTROLLER by REGISTRY.register("flag_controller", java.util.function.Supplier {
        BlockEntityType(::FlagControllerBlockEntity, ModBlocks.FLAG_CLOTH.value())
    })
}
