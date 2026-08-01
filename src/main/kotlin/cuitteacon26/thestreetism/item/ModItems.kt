package cuitteacon26.thestreetism.item

import cuitteacon26.thestreetism.Thestreetism
import cuitteacon26.thestreetism.block.ModBlocks
import cuitteacon26.thestreetism.item.StitchingToolItem
import cuitteacon26.thestreetism.banner.BannerGeometry
import cuitteacon26.thestreetism.banner.BannerTextAlignment
import cuitteacon26.thestreetism.entity.BannerEntity
import cuitteacon26.thestreetism.entity.GraffitiEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
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
    val REGISTRY: DeferredRegister.Items = DeferredRegister.createItems(Thestreetism.ID)

    val SPRAY_CAN by REGISTRY.registerItem("spray_can", { props -> SprayCanItem(props) }, java.util.function.UnaryOperator<Item.Properties> { it.stacksTo(1).durability(100) })
    val PAINT_SCRAPER by REGISTRY.registerItem("paint_scraper", { props -> PaintScraperItem(props) }, java.util.function.UnaryOperator<Item.Properties> { it.stacksTo(1) })
    val PIGMENT_BAG by REGISTRY.registerItem("pigment_bag", { props -> PigmentBagItem(props) }, java.util.function.UnaryOperator<Item.Properties> { it.stacksTo(1).durability(5) })
    val BANNER by REGISTRY.registerItem("banner", { props -> BannerItem(props) }, java.util.function.UnaryOperator<Item.Properties> { it.stacksTo(1).durability(100) })
    val STITCHING_TOOL by REGISTRY.registerItem("stitching_tool", { props -> StitchingToolItem(props) }, java.util.function.UnaryOperator<Item.Properties> { it.stacksTo(1).durability(50) })
    val SKATEBOARD by REGISTRY.registerItem("skateboard", { props -> SkateboardItem(props) }, java.util.function.UnaryOperator<Item.Properties> { it.stacksTo(1) })
    val FLAG_POLE = REGISTRY.registerSimpleBlockItem(ModBlocks.FLAG_POLE)
    val FLAG_CLOTH = REGISTRY.registerSimpleBlockItem(ModBlocks.FLAG_CLOTH)
}

class SprayCanItem(properties: Properties) : Item(properties) {
    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val player = context.player ?: return InteractionResult.FAIL
        val face = context.clickedFace
        val targetPos = context.clickedPos
        if (!level.worldBorder.isWithinBounds(targetPos) || !level.isLoaded(targetPos) || !player.isWithinBlockInteractionRange(targetPos, 0.0)) {
            return InteractionResult.FAIL
        }
        val state = level.getBlockState(targetPos)
        if (!state.isCollisionShapeFullBlock(level, targetPos)) return InteractionResult.FAIL
        val textureKey = selectedRemoteTextureKey(context.itemInHand)
        if (textureKey == null) {
            if (!level.isClientSide) {
                player.sendSystemMessage(Component.literal("请设置喷漆纹理。"))
            }
            return InteractionResult.FAIL
        }
        if (level.isClientSide) return InteractionResult.SUCCESS

        val size = selectedSize(context.itemInHand)
        val position = context.clickLocation
        val graffiti = GraffitiEntity(level, position, targetPos, face, textureKey, size.first, size.second, player.uuid)
        if (!level.noBlockCollision(graffiti, graffiti.boundingBox) || !level.noBorderCollision(graffiti, graffiti.boundingBox) || !graffiti.hasSupport()) {
            return InteractionResult.FAIL
        }
        level.addFreshEntity(graffiti)
        context.itemInHand.hurtAndBreak(1, player, context.hand)
        player.swing(context.hand, true)
        level.playSound(null, position.x, position.y, position.z, SoundEvents.DYE_USE, player.soundSource, 1.0f, 1.0f)
        return InteractionResult.SUCCESS
    }

    private fun selectedRemoteTextureKey(stack: ItemStack): String? {
        val data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        val source = data.getStringOr(SOURCE_KEY, "")
        if (source != REMOTE_SOURCE) return null
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
        private const val REMOTE_SOURCE = "remote"
        private const val DEFAULT_GRAFFITI_SIZE = 1.0f

        fun getGraffitiSize(stack: ItemStack): Pair<Float, Float> {
            val data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
            val width = sanitizeStoredGraffitiSize(data.getFloatOr(WIDTH_KEY, DEFAULT_GRAFFITI_SIZE))
            val height = sanitizeStoredGraffitiSize(data.getFloatOr(HEIGHT_KEY, DEFAULT_GRAFFITI_SIZE))
            return Pair(width, height)
        }

        fun setRemoteGraffitiUrl(stack: ItemStack, url: String) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack) { tag ->
                tag.putString(SOURCE_KEY, REMOTE_SOURCE)
                tag.putString(VALUE_KEY, url)
            }
        }

        fun setGraffitiSize(stack: ItemStack, width: Float, height: Float) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack) { tag ->
                tag.putFloat(WIDTH_KEY, sanitizeStoredGraffitiSize(width))
                tag.putFloat(HEIGHT_KEY, sanitizeStoredGraffitiSize(height))
            }
        }

        private fun sanitizeStoredGraffitiSize(size: Float): Float {
            return if (size.isFinite() && size > 0.0f) size else DEFAULT_GRAFFITI_SIZE
        }
    }
}

class BannerItem(properties: Properties) : Item(properties) {
    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val player = context.player ?: return InteractionResult.FAIL
        val targetPos = context.clickedPos
        val clickedAnchor = BannerGeometry.Anchor(targetPos, context.clickedFace)
        if (!level.worldBorder.isWithinBounds(targetPos) || !level.isLoaded(targetPos) || !player.isWithinBlockInteractionRange(targetPos, 0.0)) {
            clearPlacement(context.itemInHand)
            return InteractionResult.FAIL
        }

        val blockState = level.getBlockState(targetPos)
        if (!blockState.isCollisionShapeFullBlock(level, targetPos) || !BannerGeometry.isValidAnchorFace(clickedAnchor.face)) {
            clearPlacement(context.itemInHand)
            return InteractionResult.FAIL
        }

        val placement = getPlacementState(context.itemInHand)
        if (placement.anchorA == null) {
            setAnchorA(context.itemInHand, clickedAnchor)
            player.swing(context.hand, true)
            level.playSound(null, context.clickLocation.x, context.clickLocation.y, context.clickLocation.z, SoundEvents.DYE_USE, player.soundSource, 0.8f, 1.2f)
            return InteractionResult.SUCCESS
        }

        if (placement.anchorB == null) {
            if (!BannerGeometry.canShareSurface(placement.anchorA, clickedAnchor)) {
                return InteractionResult.FAIL
            }
            setAnchorB(context.itemInHand, clickedAnchor)
            player.swing(context.hand, true)
            level.playSound(null, context.clickLocation.x, context.clickLocation.y, context.clickLocation.z, SoundEvents.DYE_USE, player.soundSource, 0.9f, 1.0f)
            return InteractionResult.SUCCESS
        }

        if (level.isClientSide) return InteractionResult.SUCCESS

        val anchorA = placement.anchorA
        val anchorB = placement.anchorB

        if (!BannerGeometry.canShareSurface(anchorA, anchorB)) {
            clearPlacement(context.itemInHand)
            return InteractionResult.FAIL
        }

        val height = BannerGeometry.placementHeight(anchorA, anchorB, context.clickLocation)
        val banner = BannerEntity(
            level,
            anchorA,
            anchorB,
            height,
            DEFAULT_BACKGROUND_COLOR,
            BannerEntity.DEFAULT_TEXT_COLOR,
            DEFAULT_TEXT,
            DEFAULT_FONT_SCALE,
            BannerTextAlignment.CENTER,
            player.uuid,
        )
        if (!level.noBorderCollision(banner, banner.boundingBox) || !banner.hasSupport()) {
            return InteractionResult.FAIL
        }

        level.addFreshEntity(banner)
        clearPlacement(context.itemInHand)
        context.itemInHand.hurtAndBreak(1, player, context.hand)
        player.swing(context.hand, true)
        level.playSound(null, banner.x, banner.y, banner.z, SoundEvents.WOOL_PLACE, player.soundSource, 1.0f, 1.0f)
        return InteractionResult.SUCCESS
    }

    companion object {
        private const val ANCHOR_A_X = "thestreetism_banner_anchor_a_x"
        private const val ANCHOR_A_Y = "thestreetism_banner_anchor_a_y"
        private const val ANCHOR_A_Z = "thestreetism_banner_anchor_a_z"
        private const val ANCHOR_A_FACE = "thestreetism_banner_anchor_a_face"
        private const val ANCHOR_B_X = "thestreetism_banner_anchor_b_x"
        private const val ANCHOR_B_Y = "thestreetism_banner_anchor_b_y"
        private const val ANCHOR_B_Z = "thestreetism_banner_anchor_b_z"
        private const val ANCHOR_B_FACE = "thestreetism_banner_anchor_b_face"
        private const val DEFAULT_BACKGROUND_COLOR = 0xFFF5E0B5.toInt()
        private const val DEFAULT_TEXT = "Streetism"
        private const val DEFAULT_FONT_SCALE = 1.0f

        data class PlacementState(
            val anchorA: BannerGeometry.Anchor?,
            val anchorB: BannerGeometry.Anchor?,
        )

        fun getPlacementState(stack: ItemStack): PlacementState {
            val data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
            return PlacementState(
                anchorA = readAnchor(data, ANCHOR_A_X, ANCHOR_A_Y, ANCHOR_A_Z, ANCHOR_A_FACE),
                anchorB = readAnchor(data, ANCHOR_B_X, ANCHOR_B_Y, ANCHOR_B_Z, ANCHOR_B_FACE),
            )
        }

        fun setAnchorA(stack: ItemStack, anchor: BannerGeometry.Anchor) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack) { tag ->
                writeAnchor(tag, ANCHOR_A_X, ANCHOR_A_Y, ANCHOR_A_Z, ANCHOR_A_FACE, anchor)
                clearAnchor(tag, ANCHOR_B_X, ANCHOR_B_Y, ANCHOR_B_Z, ANCHOR_B_FACE)
            }
        }

        fun setAnchorB(stack: ItemStack, anchor: BannerGeometry.Anchor) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack) { tag ->
                writeAnchor(tag, ANCHOR_B_X, ANCHOR_B_Y, ANCHOR_B_Z, ANCHOR_B_FACE, anchor)
            }
        }

        fun clearPlacement(stack: ItemStack) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack) { tag ->
                clearAnchor(tag, ANCHOR_A_X, ANCHOR_A_Y, ANCHOR_A_Z, ANCHOR_A_FACE)
                clearAnchor(tag, ANCHOR_B_X, ANCHOR_B_Y, ANCHOR_B_Z, ANCHOR_B_FACE)
            }
        }

        private fun readAnchor(tag: net.minecraft.nbt.CompoundTag, xKey: String, yKey: String, zKey: String, faceKey: String): BannerGeometry.Anchor? {
            if (!tag.contains(xKey) || !tag.contains(yKey) || !tag.contains(zKey) || !tag.contains(faceKey)) return null
            val face = Direction.byName(tag.getStringOr(faceKey, "")) ?: return null
            return BannerGeometry.Anchor(BlockPos(tag.getIntOr(xKey, 0), tag.getIntOr(yKey, 0), tag.getIntOr(zKey, 0)), face)
        }

        private fun writeAnchor(tag: net.minecraft.nbt.CompoundTag, xKey: String, yKey: String, zKey: String, faceKey: String, anchor: BannerGeometry.Anchor) {
            tag.putInt(xKey, anchor.pos.x)
            tag.putInt(yKey, anchor.pos.y)
            tag.putInt(zKey, anchor.pos.z)
            tag.putString(faceKey, anchor.face.serializedName)
        }

        private fun clearAnchor(tag: net.minecraft.nbt.CompoundTag, xKey: String, yKey: String, zKey: String, faceKey: String) {
            tag.remove(xKey)
            tag.remove(yKey)
            tag.remove(zKey)
            tag.remove(faceKey)
        }
    }
}

class PaintScraperItem(properties: Properties) : Item(properties) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        val hit = findTargetGraffiti(player) ?: return InteractionResult.PASS
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
            { entity: Entity -> (entity is GraffitiEntity || entity is BannerEntity) && !entity.isRemoved },
            0.0f,
        )
    }
}

class PigmentBagItem(properties: Properties) : Item(properties) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        val pigmentBag = player.getItemInHand(hand)
        val targets = (0 until player.inventory.containerSize)
            .map(player.inventory::getItem)
            .filter(::shouldRestore)
        if (targets.isEmpty()) return InteractionResult.PASS
        if (level.isClientSide) return InteractionResult.SUCCESS

        targets.forEach(::restoreDurability)
        pigmentBag.hurtAndBreak(1, player, hand)
        player.swing(hand, true)
        level.playSound(null, player.x, player.y, player.z, SoundEvents.DYE_USE, player.soundSource, 1.0f, 1.0f)
        return InteractionResult.SUCCESS
    }

    private fun shouldRestore(stack: ItemStack): Boolean {
        if (stack.item != ModItems.SPRAY_CAN && stack.item != ModItems.BANNER) return false
        val remainingDurability = stack.maxDamage - stack.damageValue
        return remainingDurability < MIN_TARGET_DURABILITY
    }

    private fun restoreDurability(stack: ItemStack) {
        stack.damageValue = (stack.damageValue - RESTORE_AMOUNT).coerceAtLeast(0)
    }

    companion object {
        private const val MIN_TARGET_DURABILITY = 75
        private const val RESTORE_AMOUNT = 25
    }
}
