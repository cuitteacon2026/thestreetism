package cuitteacon26.thestreetism

import cuitteacon26.thestreetism.client.ClientSetup
import cuitteacon26.thestreetism.command.ThestreetismCommands
import cuitteacon26.thestreetism.entity.ModEntities
import cuitteacon26.thestreetism.entity.ModEntityDataSerializers
import cuitteacon26.thestreetism.item.ModCreativeTabs
import cuitteacon26.thestreetism.item.ModItems
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import net.neoforged.fml.loading.FMLEnvironment
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
        NeoForge.EVENT_BUS.addListener(ThestreetismCommands::register)

        when (FMLEnvironment.getDist()) {
            Dist.CLIENT -> {
                MOD_BUS.addListener(::onClientSetup)
                MOD_BUS.addListener(ClientSetup::registerEntityRenderers)
                ClientSetup.registerGameEvents()
            }
            Dist.DEDICATED_SERVER -> MOD_BUS.addListener(::onServerSetup)
        }
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOGGER.log(Level.INFO, "Initializing client graffiti system...")
    }

    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {
        LOGGER.log(Level.INFO, "Server graffiti system ready...")
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOGGER.log(Level.INFO, "Graffiti spray system initialized.")
    }
}
