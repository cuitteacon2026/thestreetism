package cuitteacon26.thestreetism

import cuitteacon26.thestreetism.block.ModBlocks
import cuitteacon26.thestreetism.blockentity.ModBlockEntities
import cuitteacon26.thestreetism.client.ClientSetup
import cuitteacon26.thestreetism.command.ThestreetismCommands
import cuitteacon26.thestreetism.entity.ModEntities
import cuitteacon26.thestreetism.entity.ModEntityDataSerializers
import cuitteacon26.thestreetism.item.ModCreativeTabs
import cuitteacon26.thestreetism.item.ModItems
import cuitteacon26.thestreetism.menu.ModMenus
import cuitteacon26.thestreetism.network.BannerNetwork
import cuitteacon26.thestreetism.network.BannerStatePayload
import cuitteacon26.thestreetism.network.BannerUpdatePayload
import cuitteacon26.thestreetism.network.FlagEditorOpenPayload
import cuitteacon26.thestreetism.network.FlagUpdatePayload
import cuitteacon26.thestreetism.network.FlagSyncPayload
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
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
        ModBlocks.REGISTRY.register(MOD_BUS)
        ModBlockEntities.REGISTRY.register(MOD_BUS)
        ModItems.REGISTRY.register(MOD_BUS)
        ModMenus.REGISTRY.register(MOD_BUS)
        ModCreativeTabs.REGISTRY.register(MOD_BUS)
        MOD_BUS.addListener(::onCommonSetup)
        MOD_BUS.addListener(::registerPayloads)
        NeoForge.EVENT_BUS.addListener(ThestreetismCommands::register)
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            registerClientSetup()
        }
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOGGER.log(Level.INFO, "Flag system and graffiti system initialized.")
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        // Legacy banner payloads
        registrar.playToServer(BannerUpdatePayload.TYPE, BannerUpdatePayload.STREAM_CODEC, BannerNetwork::handleBannerUpdate)
        registrar.playToClient(BannerStatePayload.TYPE, BannerStatePayload.STREAM_CODEC, BannerNetwork::handleBannerState)
        // Flag payloads
        registrar.playToServer(FlagUpdatePayload.TYPE, FlagUpdatePayload.STREAM_CODEC, FlagUpdatePayload.Companion::handle)
        registrar.playToClient(FlagSyncPayload.TYPE, FlagSyncPayload.STREAM_CODEC, FlagSyncPayload.Companion::handle)
        registrar.playToClient(FlagEditorOpenPayload.TYPE, FlagEditorOpenPayload.STREAM_CODEC, FlagEditorOpenPayload.Companion::handle)
    }

    private fun registerClientSetup() {
        MOD_BUS.addListener(ClientSetup::onClientSetup)
        MOD_BUS.addListener(ClientSetup::registerMenuScreens)
        MOD_BUS.addListener(ClientSetup::registerRenderStateModifiers)
        ClientSetup.register()
    }
}
