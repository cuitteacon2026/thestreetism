package cuitteacon26.thestreetism.entity

import net.minecraft.core.UUIDUtil
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.syncher.EntityDataSerializer
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import thedarkcolour.kotlinforforge.neoforge.forge.getValue
import java.util.Optional
import java.util.UUID

object ModEntityDataSerializers {
    val REGISTRY: DeferredRegister<EntityDataSerializer<*>> = DeferredRegister.create(NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, cuitteacon26.thestreetism.Thestreetism.ID)

    val OPTIONAL_UUID by REGISTRY.register("optional_uuid", java.util.function.Supplier {
        EntityDataSerializer.forValueType(UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional)) as EntityDataSerializer<Optional<UUID>>
    })
}
