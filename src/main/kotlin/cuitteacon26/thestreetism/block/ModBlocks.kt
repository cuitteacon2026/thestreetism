package cuitteacon26.thestreetism.block

import cuitteacon26.thestreetism.Thestreetism
import net.minecraft.world.level.block.SoundType
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredRegister

object ModBlocks {
    val REGISTRY: DeferredRegister.Blocks = DeferredRegister.createBlocks(Thestreetism.ID)

    val FLAG_POLE: DeferredBlock<FlagPoleBlock> = REGISTRY.registerBlock("flag_pole", ::FlagPoleBlock) { properties ->
        properties
            .strength(2.0f, 6.0f)
            .sound(SoundType.METAL)
            .noOcclusion()
    }

    val FLAG_CLOTH: DeferredBlock<FlagClothBlock> = REGISTRY.registerBlock("flag_cloth", ::FlagClothBlock) { properties ->
        properties
            .strength(0.5f)
            .sound(SoundType.WOOL)
            .noOcclusion()
    }
}
