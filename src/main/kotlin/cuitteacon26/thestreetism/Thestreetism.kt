package cuitteacon26.thestreetism

import cuitteacon26.thestreetism.client.ClientSetup
import cuitteacon26.thestreetism.command.ThestreetismCommands
import cuitteacon26.thestreetism.entity.ModEntities
import cuitteacon26.thestreetism.entity.ModEntityDataSerializers
import cuitteacon26.thestreetism.item.ModCreativeTabs
import cuitteacon26.thestreetism.item.ModItems
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.common.NeoForge
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

@Mod(Thestreetism.ID)
object Thestreetism {
    const val ID = "thestreetism"
    val LOGGER: Logger = LogManager.getLogger(ID)

    init {
        ModEntities.REGISTRY.register(MOD_BUS)
        ModEntityDataSerializers.REGISTRY.register(MOD_BUS)
        ModItems.REGISTRY.register(MOD_BUS)
        ModCreativeTabs.REGISTRY.register(MOD_BUS)
        MOD_BUS.addListener(::onCommonSetup)
        MOD_BUS.addListener(ClientSetup::onClientSetup)
        NeoForge.EVENT_BUS.addListener(ThestreetismCommands::register)
        ClientSetup.register()
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOGGER.log(Level.INFO, "Graffiti spray system initialized.")
    }
}
