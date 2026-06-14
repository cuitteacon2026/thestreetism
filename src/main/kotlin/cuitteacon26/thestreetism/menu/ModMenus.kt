package cuitteacon26.thestreetism.menu

import cuitteacon26.thestreetism.Thestreetism
import cuitteacon26.thestreetism.banner.BannerTextAlignment
import cuitteacon26.thestreetism.entity.BannerEntity
import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.neoforged.neoforge.common.extensions.IMenuProviderExtension
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.getValue

object ModMenus {
    val REGISTRY: DeferredRegister<MenuType<*>> = DeferredRegister.create(Registries.MENU, Thestreetism.ID)

    val BANNER_EDITOR by REGISTRY.register("banner_editor", java.util.function.Supplier {
        IMenuTypeExtension.create(::BannerEditorMenu)
    })
}

class BannerEditorMenu private constructor(
    containerId: Int,
    playerInventory: Inventory,
    val bannerEntityId: Int,
    val initialBackgroundColor: Int,
    val initialTextColor: Int,
    val initialText: String,
    val initialFontScale: Float,
    val initialTextAlignment: BannerTextAlignment,
) : AbstractContainerMenu(ModMenus.BANNER_EDITOR, containerId) {
    constructor(containerId: Int, playerInventory: Inventory, extraData: RegistryFriendlyByteBuf?) : this(
        containerId,
        playerInventory,
        extraData?.readVarInt() ?: -1,
        extraData?.readInt() ?: BannerEntity.DEFAULT_BACKGROUND_COLOR,
        extraData?.readInt() ?: BannerEntity.DEFAULT_TEXT_COLOR,
        extraData?.readUtf(BannerEntity.MAX_TEXT_LENGTH) ?: "",
        extraData?.readFloat() ?: BannerEntity.DEFAULT_FONT_SCALE,
        BannerTextAlignment.bySerializedName(extraData?.readUtf(16) ?: BannerTextAlignment.CENTER.serializedName),
    )

    constructor(containerId: Int, playerInventory: Inventory, banner: BannerEntity) : this(
        containerId,
        playerInventory,
        banner.id,
        banner.backgroundColor(),
        banner.textColor(),
        banner.text(),
        banner.fontScale(),
        banner.textAlignment(),
    )

    constructor(containerId: Int, playerInventory: Inventory) : this(containerId, playerInventory, null)

    override fun quickMoveStack(player: Player, index: Int): net.minecraft.world.item.ItemStack {
        return net.minecraft.world.item.ItemStack.EMPTY
    }

    override fun stillValid(player: Player): Boolean {
        val entity = player.level().getEntity(bannerEntityId) as? BannerEntity ?: return false
        return entity.isAlive && player.distanceToSqr(entity) <= 64.0
    }
}

class BannerMenuProvider(
    private val banner: BannerEntity,
) : MenuProvider, IMenuProviderExtension {
    override fun getDisplayName(): Component = Component.translatable("entity.thestreetism.banner")

    override fun createMenu(containerId: Int, playerInventory: Inventory, player: Player): AbstractContainerMenu {
        return BannerEditorMenu(containerId, playerInventory, banner)
    }

    override fun writeClientSideData(menu: AbstractContainerMenu, buffer: RegistryFriendlyByteBuf) {
        buffer.writeVarInt(banner.id)
        buffer.writeInt(banner.backgroundColor())
        buffer.writeInt(banner.textColor())
        buffer.writeUtf(banner.text(), BannerEntity.MAX_TEXT_LENGTH)
        buffer.writeFloat(banner.fontScale())
        buffer.writeUtf(banner.textAlignment().serializedName, 16)
    }
}
