package cuitteacon26.thestreetism.network

import cuitteacon26.thestreetism.Thestreetism
import cuitteacon26.thestreetism.banner.BannerTextAlignment
import cuitteacon26.thestreetism.entity.BannerEntity
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

object BannerNetwork {
    fun handleBannerUpdate(payload: BannerUpdatePayload, context: IPayloadContext) {
        val player = context.player() as? net.minecraft.server.level.ServerPlayer ?: return
        context.enqueueWork {
            val entity = player.level().getEntity(payload.entityId) as? BannerEntity ?: return@enqueueWork
            entity.setBackgroundColor(payload.backgroundColor)
            entity.setTextColor(payload.textColor)
            entity.setText(payload.text)
            entity.setFontScale(payload.fontScale)
            entity.setTextAlignment(payload.textAlignment)
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, BannerStatePayload.fromEntity(entity))
        }
    }

    fun handleBannerState(payload: BannerStatePayload, context: IPayloadContext) {
        context.enqueueWork {
            val player = context.player()
            val entity = player.level().getEntity(payload.entityId) as? BannerEntity ?: return@enqueueWork
            entity.setBackgroundColor(payload.backgroundColor)
            entity.setTextColor(payload.textColor)
            entity.setText(payload.text)
            entity.setFontScale(payload.fontScale)
            entity.setTextAlignment(payload.textAlignment)
        }
    }
}

data class BannerUpdatePayload(
    val entityId: Int,
    val backgroundColor: Int,
    val textColor: Int,
    val text: String,
    val fontScale: Float,
    val textAlignment: BannerTextAlignment,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<BannerUpdatePayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<BannerUpdatePayload>(Identifier.fromNamespaceAndPath(Thestreetism.ID, "banner_update"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, BannerUpdatePayload> = StreamCodec.composite(
            ByteBufCodecs.INT,
            BannerUpdatePayload::entityId,
            ByteBufCodecs.INT,
            BannerUpdatePayload::backgroundColor,
            ByteBufCodecs.INT,
            BannerUpdatePayload::textColor,
            ByteBufCodecs.STRING_UTF8,
            BannerUpdatePayload::text,
            ByteBufCodecs.FLOAT,
            BannerUpdatePayload::fontScale,
            ByteBufCodecs.STRING_UTF8.map(BannerTextAlignment::bySerializedName, BannerTextAlignment::serializedName),
            BannerUpdatePayload::textAlignment,
            ::BannerUpdatePayload,
        )
    }
}

data class BannerStatePayload(
    val entityId: Int,
    val backgroundColor: Int,
    val textColor: Int,
    val text: String,
    val fontScale: Float,
    val textAlignment: BannerTextAlignment,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<BannerStatePayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<BannerStatePayload>(Identifier.fromNamespaceAndPath(Thestreetism.ID, "banner_state"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, BannerStatePayload> = StreamCodec.composite(
            ByteBufCodecs.INT,
            BannerStatePayload::entityId,
            ByteBufCodecs.INT,
            BannerStatePayload::backgroundColor,
            ByteBufCodecs.INT,
            BannerStatePayload::textColor,
            ByteBufCodecs.STRING_UTF8,
            BannerStatePayload::text,
            ByteBufCodecs.FLOAT,
            BannerStatePayload::fontScale,
            ByteBufCodecs.STRING_UTF8.map(BannerTextAlignment::bySerializedName, BannerTextAlignment::serializedName),
            BannerStatePayload::textAlignment,
            ::BannerStatePayload,
        )

        fun fromEntity(entity: BannerEntity): BannerStatePayload {
            return BannerStatePayload(
                entityId = entity.id,
                backgroundColor = entity.backgroundColor(),
                textColor = entity.textColor(),
                text = entity.text(),
                fontScale = entity.fontScale(),
                textAlignment = entity.textAlignment(),
            )
        }
    }
}
