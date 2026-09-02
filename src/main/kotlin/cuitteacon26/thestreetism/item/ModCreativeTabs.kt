package cuitteacon26.thestreetism.item

import cuitteacon26.thestreetism.Thestreetism
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.getValue

object ModCreativeTabs {
    val REGISTRY: DeferredRegister<CreativeModeTab> = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Thestreetism.ID)

    val THESTREETISM by REGISTRY.register("thestreetism", java.util.function.Supplier {
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.thestreetism.thestreetism"))
            .icon { ItemStack(ModItems.SPRAY_CAN) }
            .displayItems { _, output ->
                output.accept(ModItems.SPRAY_CAN)
                output.accept(ModItems.PAINT_SCRAPER)
                output.accept(ModItems.PIGMENT_BAG)
                output.accept(ModItems.BANNER)
                output.accept(ModItems.STITCHING_TOOL)
                output.accept(ModItems.SKATEBOARD)
                output.accept(ModItems.FLAG_POLE)
                output.accept(ModItems.FLAG_CLOTH)
            }
            .withTabsBefore(Identifier.withDefaultNamespace("spawn_eggs"))
            .build()
    })
}
