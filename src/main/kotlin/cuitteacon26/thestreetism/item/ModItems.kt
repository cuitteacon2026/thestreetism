package cuitteacon26.thestreetism.item

import cuitteacon26.thestreetism.Thestreetism
import cuitteacon26.thestreetism.entity.GraffitiEntity
import cuitteacon26.thestreetism.graffiti.GraffitiRegistry
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.EntityHitResult
import net.neoforged.neoforge.registries.DeferredRegister
import thedarkcolour.kotlinforforge.neoforge.forge.getValue

object ModItems {
    val REGISTRY = DeferredRegister.createItems(Thestreetism.ID)

    val SPRAY_CAN by REGISTRY.registerItem("spray_can", { props -> SprayCanItem(props) }, java.util.function.UnaryOperator<Item.Properties> { it.stacksTo(1).durability(100) })
    val PAINT_SCRAPER by REGISTRY.registerItem("paint_scraper", { props -> PaintScraperItem(props) }, java.util.function.UnaryOperator<Item.Properties> { it.stacksTo(1) })
}

class SprayCanItem(properties: Properties) : Item(properties) {
    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val player = context.player ?: return InteractionResult.FAIL
        val face = context.clickedFace
        val targetPos = context.clickedPos
        if (!level.worldBorder.isWithinBounds(targetPos) || !level.hasChunkAt(targetPos) || !player.isWithinBlockInteractionRange(targetPos, 0.0)) {
            return InteractionResult.FAIL
        }
        val state = level.getBlockState(targetPos)
        if (!state.isSolid) return InteractionResult.FAIL
        if (level.isClientSide) return InteractionResult.SUCCESS

        val definition = selectedDefinition(context.itemInHand)
        val size = selectedSize(context.itemInHand)
        val position = context.clickLocation
        val graffiti = GraffitiEntity(level, position, targetPos, face, definition.copy(width = size.first, height = size.second), player.uuid)
        selectedTextureKey(context.itemInHand)?.let { graffiti.setTextureKey(it) }
        if (!level.noBlockCollision(graffiti, graffiti.boundingBox) || !level.noBorderCollision(graffiti, graffiti.boundingBox) || !graffiti.hasSupport()) {
            return InteractionResult.FAIL
        }
        level.addFreshEntity(graffiti)
        context.itemInHand.hurtAndBreak(1, player, context.hand)
        player.swing(context.hand, true)
        level.playSound(null, position.x, position.y, position.z, SoundEvents.DYE_USE, player.soundSource, 1.0f, 1.0f)
        return InteractionResult.SUCCESS
    }

    private fun selectedDefinition(stack: ItemStack): GraffitiRegistry.GraffitiDefinition {
        val data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        if (data.getStringOr(SOURCE_KEY, LOCAL_SOURCE) != LOCAL_SOURCE) return GraffitiRegistry.DEFAULT
        val id = Identifier.tryParse(data.getStringOr(VALUE_KEY, "")) ?: return GraffitiRegistry.DEFAULT
        return GraffitiRegistry.get(id)
    }

    private fun selectedTextureKey(stack: ItemStack): String? {
        val data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        val source = data.getStringOr(SOURCE_KEY, LOCAL_SOURCE)
        val value = data.getStringOr(VALUE_KEY, "")
        if (value.isBlank()) return null
        return "$source:$value"
    }

    private fun selectedSize(stack: ItemStack): Pair<Float, Float> = getGraffitiSize(stack)

    companion object {
        private const val SOURCE_KEY = "thestreetism_graffiti_source"
        private const val VALUE_KEY = "thestreetism_graffiti_value"
        private const val WIDTH_KEY = "thestreetism_graffiti_width"
        private const val HEIGHT_KEY = "thestreetism_graffiti_height"
        private const val LOCAL_SOURCE = "local"

        fun getGraffitiSize(stack: ItemStack): Pair<Float, Float> {
            val data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
            return Pair(data.getFloatOr(WIDTH_KEY, 1.0f), data.getFloatOr(HEIGHT_KEY, 1.0f))
        }

        fun setGraffitiSelection(stack: ItemStack, source: String, value: String) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack) { tag ->
                tag.putString(SOURCE_KEY, source)
                tag.putString(VALUE_KEY, value)
            }
        }

        fun setGraffitiSize(stack: ItemStack, width: Float, height: Float) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack) { tag ->
                tag.putFloat(WIDTH_KEY, width)
                tag.putFloat(HEIGHT_KEY, height)
            }
        }
    }
}

class PaintScraperItem(properties: Properties) : Item(properties) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        val hit = findTargetGraffiti(player)
        if (hit == null) return InteractionResult.PASS
        if (level.isClientSide) return InteractionResult.SUCCESS
        hit.entity.discard()
        player.swing(hand, true)
        level.playSound(null, hit.location.x, hit.location.y, hit.location.z, SoundEvents.PAINTING_BREAK, player.soundSource, 1.0f, 1.0f)
        return InteractionResult.SUCCESS
    }

    private fun findTargetGraffiti(player: Player): EntityHitResult? {
        val reach = player.entityInteractionRange()
        val from = player.eyePosition
        val to = from.add(player.getViewVector(1.0f).scale(reach))
        return ProjectileUtil.getEntityHitResult(
            player.level(),
            player,
            from,
            to,
            AABB(from, to).inflate(1.0),
            { entity: Entity -> entity is GraffitiEntity && !entity.isRemoved },
            0.0f,
        )
    }
}
