package cuitteacon26.thestreetism.network

import cuitteacon26.thestreetism.Thestreetism
import cuitteacon26.thestreetism.blockentity.FlagControllerBlockEntity
import cuitteacon26.thestreetism.client.gui.FlagEditorScreen
import cuitteacon26.thestreetism.multiblock.FlagMultiblockManager
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

data class FlagUpdatePayload(
    val controllerPos: BlockPos,
    val richTextJson: String,
    val fontId: String,
    val styleJson: String,
    val customName: String,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<FlagUpdatePayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<FlagUpdatePayload>(
            Identifier.fromNamespaceAndPath(Thestreetism.ID, "flag_update")
        )
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, FlagUpdatePayload> = StreamCodec.composite(
            BlockPos.STREAM_CODEC, FlagUpdatePayload::controllerPos,
            ByteBufCodecs.STRING_UTF8, FlagUpdatePayload::richTextJson,
            ByteBufCodecs.STRING_UTF8, FlagUpdatePayload::fontId,
            ByteBufCodecs.STRING_UTF8, FlagUpdatePayload::styleJson,
            ByteBufCodecs.STRING_UTF8, FlagUpdatePayload::customName,
            ::FlagUpdatePayload,
        )

        fun handle(payload: FlagUpdatePayload, ctx: IPayloadContext) {
            val player = ctx.player() as? ServerPlayer ?: return
            ctx.enqueueWork {
                FlagMultiblockManager.applyTextUpdate(
                    level = player.level(),
                    controllerPos = payload.controllerPos,
                    richTextJson = payload.richTextJson.take(FlagControllerBlockEntity.MAX_TEXT_LENGTH),
                    fontId = payload.fontId.take(FlagControllerBlockEntity.MAX_FONT_ID_LENGTH),
                    styleJson = payload.styleJson.take(FlagControllerBlockEntity.MAX_STYLE_LENGTH),
                    customName = payload.customName.take(FlagControllerBlockEntity.MAX_NAME_LENGTH),
                )
            }
        }
    }
}

data class FlagSyncPayload(
    val controllerPos: BlockPos,
    val richTextJson: String,
    val fontId: String,
    val styleJson: String,
    val customName: String,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<FlagSyncPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<FlagSyncPayload>(
            Identifier.fromNamespaceAndPath(Thestreetism.ID, "flag_sync")
        )
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, FlagSyncPayload> = StreamCodec.composite(
            BlockPos.STREAM_CODEC, FlagSyncPayload::controllerPos,
            ByteBufCodecs.STRING_UTF8, FlagSyncPayload::richTextJson,
            ByteBufCodecs.STRING_UTF8, FlagSyncPayload::fontId,
            ByteBufCodecs.STRING_UTF8, FlagSyncPayload::styleJson,
            ByteBufCodecs.STRING_UTF8, FlagSyncPayload::customName,
            ::FlagSyncPayload,
        )

        fun fromController(controller: FlagControllerBlockEntity): FlagSyncPayload = FlagSyncPayload(
            controllerPos = controller.blockPos,
            richTextJson = controller.richTextJson,
            fontId = controller.fontId,
            styleJson = controller.styleJson,
            customName = controller.customName,
        )

        fun handle(payload: FlagSyncPayload, ctx: IPayloadContext) {
            ctx.enqueueWork {
                val level = Minecraft.getInstance().level ?: return@enqueueWork
                val controller = level.getBlockEntity(payload.controllerPos) as? FlagControllerBlockEntity ?: return@enqueueWork
                controller.updateTextData(payload.richTextJson, payload.fontId, payload.styleJson, payload.customName)
            }
        }
    }
}

data class FlagEditorOpenPayload(
    val controllerPos: BlockPos,
    val richTextJson: String,
    val fontId: String,
    val styleJson: String,
    val customName: String,
    val width: Int,
    val height: Int,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<FlagEditorOpenPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<FlagEditorOpenPayload>(
            Identifier.fromNamespaceAndPath(Thestreetism.ID, "flag_editor_open")
        )
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, FlagEditorOpenPayload> = StreamCodec.composite(
            BlockPos.STREAM_CODEC, FlagEditorOpenPayload::controllerPos,
            ByteBufCodecs.STRING_UTF8, FlagEditorOpenPayload::richTextJson,
            ByteBufCodecs.STRING_UTF8, FlagEditorOpenPayload::fontId,
            ByteBufCodecs.STRING_UTF8, FlagEditorOpenPayload::styleJson,
            ByteBufCodecs.STRING_UTF8, FlagEditorOpenPayload::customName,
            ByteBufCodecs.INT, FlagEditorOpenPayload::width,
            ByteBufCodecs.INT, FlagEditorOpenPayload::height,
            ::FlagEditorOpenPayload,
        )

        fun fromController(controller: FlagControllerBlockEntity): FlagEditorOpenPayload = FlagEditorOpenPayload(
            controllerPos = controller.blockPos,
            richTextJson = controller.richTextJson,
            fontId = controller.fontId,
            styleJson = controller.styleJson,
            customName = controller.customName,
            width = controller.flagWidth,
            height = controller.flagHeight,
        )

        fun handle(payload: FlagEditorOpenPayload, ctx: IPayloadContext) {
            ctx.enqueueWork {
                Minecraft.getInstance().setScreen(
                    FlagEditorScreen(
                        controllerPos = payload.controllerPos,
                        initialRichTextJson = payload.richTextJson,
                        initialFont = payload.fontId,
                        initialStyleJson = payload.styleJson,
                        initialName = payload.customName,
                        flagWidth = payload.width,
                        flagHeight = payload.height,
                    )
                )
            }
        }
    }
}
