package cuitteacon26.thestreetism.client

import cuitteacon26.thestreetism.Thestreetism
import cuitteacon26.thestreetism.banner.BannerGeometry
import cuitteacon26.thestreetism.banner.BannerTextAlignment
import cuitteacon26.thestreetism.entity.BannerEntity
import cuitteacon26.thestreetism.entity.GraffitiEntity
import cuitteacon26.thestreetism.entity.ModEntities
import cuitteacon26.thestreetism.client.font.FontRegistry
import cuitteacon26.thestreetism.client.gui.BannerEditorScreen
import cuitteacon26.thestreetism.client.render.SkateboardRenderer
import cuitteacon26.thestreetism.item.ModItems
import cuitteacon26.thestreetism.menu.ModMenus
import cuitteacon26.thestreetism.item.SprayCanItem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent
import net.neoforged.neoforge.common.NeoForge

object ClientSetup {
    fun register() {
        NeoForge.EVENT_BUS.register(GraffitiPreviewRenderer)
    }

    fun onClientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            FontRegistry.reload()
            net.minecraft.client.renderer.entity.EntityRenderers.register(ModEntities.GRAFFITI, ::GraffitiRenderer)
            net.minecraft.client.renderer.entity.EntityRenderers.register(ModEntities.BANNER, ::BannerRenderer)
            net.minecraft.client.renderer.entity.EntityRenderers.register(ModEntities.SKATEBOARD, ::SkateboardRenderer)
            net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                cuitteacon26.thestreetism.blockentity.ModBlockEntities.FLAG_CONTROLLER,
                net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider { ctx ->
                    cuitteacon26.thestreetism.client.render.FlagRenderer(ctx)
                }
            )
        }
    }

    fun registerMenuScreens(event: RegisterMenuScreensEvent) {
        event.register(ModMenus.BANNER_EDITOR, ::BannerEditorScreen)
    }
}

class GraffitiRenderState : EntityRenderState() {
    var texture: Identifier = SurfaceRenderUtil.previewCenterTexture()
    var width = 1.0f
    var height = 1.0f
    var facing = Direction.NORTH
    var rotation = 0.0f
    var attachedBlockPos = BlockPos.ZERO
}

class BannerRenderState : EntityRenderState() {
    var texture: Identifier = SurfaceRenderUtil.previewTexture()
    lateinit var placement: BannerGeometry.Placement
    var text = ""
    var fontScale = 1.0f
    var textColor = BannerEntity.DEFAULT_TEXT_COLOR
    var textAlignment = BannerTextAlignment.CENTER
    var useMinecraftFontFallback = false
}

class GraffitiRenderer(context: EntityRendererProvider.Context) : EntityRenderer<GraffitiEntity, GraffitiRenderState>(context) {
    override fun createRenderState() = GraffitiRenderState()

    override fun extractRenderState(entity: GraffitiEntity, state: GraffitiRenderState, partialTicks: Float) {
        super.extractRenderState(entity, state, partialTicks)
        state.texture = GraffitiTextures.resolve(entity.textureKey())
        state.width = entity.graffitiWidth()
        state.height = entity.graffitiHeight()
        state.facing = entity.facing()
        state.rotation = entity.graffitiRotation()
        state.attachedBlockPos = entity.attachedBlockPos()
        state.lightCoords = LevelRenderer.getLightCoords(entity.level(), entity.attachedBlockPos().relative(entity.facing()))
    }

    override fun submit(state: GraffitiRenderState, poseStack: PoseStack, submitNodeCollector: SubmitNodeCollector, camera: CameraRenderState) {
        poseStack.pushPose()
        SurfaceRenderUtil.translateRenderOffset(poseStack, state.facing)
        SurfaceRenderUtil.orientToFace(poseStack, state.facing)
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.rotation))
        SurfaceRenderUtil.submitQuad(
            poseStack,
            submitNodeCollector,
            RenderTypes.entityTranslucentCullItemTarget(state.texture),
            state.width,
            state.height,
            state.lightCoords,
            -1,
        )
        poseStack.popPose()
        super.submit(state, poseStack, submitNodeCollector, camera)
    }
}

class BannerRenderer(context: EntityRendererProvider.Context) : EntityRenderer<BannerEntity, BannerRenderState>(context) {
    override fun createRenderState() = BannerRenderState()

    override fun extractRenderState(entity: BannerEntity, state: BannerRenderState, partialTicks: Float) {
        super.extractRenderState(entity, state, partialTicks)
        state.placement = entity.placement()
        state.texture = GraffitiTextures.resolve(entity.textureKey())
        state.text = entity.text()
        state.fontScale = entity.fontScale()
        state.textColor = entity.textColor()
        state.textAlignment = entity.textAlignment()
        state.useMinecraftFontFallback = !GraffitiTextures.hasPreferredSystemFont()
        state.lightCoords = LevelRenderer.getLightCoords(entity.level(), entity.anchorA().pos.relative(state.placement.normal))
    }

    override fun submit(state: BannerRenderState, poseStack: PoseStack, submitNodeCollector: SubmitNodeCollector, camera: CameraRenderState) {
        submitBannerFace(poseStack, submitNodeCollector, state.texture, state.placement, state.lightCoords, false)
        if (state.useMinecraftFontFallback) submitBannerTextFallback(poseStack, submitNodeCollector, state, getFont(), false)
        submitBannerFace(poseStack, submitNodeCollector, state.texture, state.placement, state.lightCoords, true)
        if (state.useMinecraftFontFallback) submitBannerTextFallback(poseStack, submitNodeCollector, state, getFont(), true)
        super.submit(state, poseStack, submitNodeCollector, camera)
    }
}

@EventBusSubscriber(modid = Thestreetism.ID, value = [Dist.CLIENT])
object GraffitiPreviewRenderer {
    @SubscribeEvent
    fun onSubmitCustomGeometry(event: SubmitCustomGeometryEvent) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        if (player.mainHandItem.item != ModItems.SPRAY_CAN && player.offhandItem.item != ModItems.SPRAY_CAN) return
        val hit = minecraft.hitResult as? BlockHitResult ?: return
        if (hit.type != HitResult.Type.BLOCK) return

        val level = minecraft.level ?: return
        val sprayCan = when {
            player.mainHandItem.item == ModItems.SPRAY_CAN -> player.mainHandItem
            player.offhandItem.item == ModItems.SPRAY_CAN -> player.offhandItem
            else -> return
        }
        val size = SprayCanItem.getGraffitiSize(sprayCan)
        val valid = level.getBlockState(hit.blockPos).isCollisionShapeFullBlock(level, hit.blockPos) && player.isWithinBlockInteractionRange(hit.blockPos, 0.0)
        val position = hit.location
        val camera = event.levelRenderState.cameraRenderState.pos
        val color = if (valid) 0x6600FF00 else 0x66FF0000
        val centerColor = if (valid) 0xCCFFFFFF.toInt() else 0xCCFF5555.toInt()

        val poseStack = event.poseStack
        poseStack.pushPose()
        poseStack.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z)
        SurfaceRenderUtil.translateRenderOffset(poseStack, hit.direction)
        SurfaceRenderUtil.orientToFace(poseStack, hit.direction)
        SurfaceRenderUtil.submitQuad(
            poseStack,
            event.submitNodeCollector,
            RenderTypes.entityTranslucent(SurfaceRenderUtil.previewTexture(), false),
            size.first,
            size.second,
            15728880,
            color,
        )
        SurfaceRenderUtil.submitCenterIndicator(poseStack, event.submitNodeCollector, 15728880, centerColor)
        poseStack.popPose()
    }
}

private fun submitBannerFace(
    poseStack: PoseStack,
    collector: SubmitNodeCollector,
    texture: Identifier,
    placement: BannerGeometry.Placement,
    light: Int,
    reverse: Boolean,
) {
    val frontOffset = if (reverse) -0.005 else 0.005
    val offset = Vec3(
        placement.normal.stepX * frontOffset,
        placement.normal.stepY * frontOffset,
        placement.normal.stepZ * frontOffset,
    )
    val topLeft = placement.topLeft.add(offset).subtract(placement.center)
    val topRight = placement.topRight.add(offset).subtract(placement.center)
    val bottomRight = placement.bottomRight.add(offset).subtract(placement.center)
    val bottomLeft = placement.bottomLeft.add(offset).subtract(placement.center)
    SurfaceRenderUtil.submitWorldQuad(
        poseStack,
        collector,
        RenderTypes.entityTranslucentCullItemTarget(texture),
        if (reverse) topRight else topLeft,
        if (reverse) topLeft else topRight,
        if (reverse) bottomLeft else bottomRight,
        if (reverse) bottomRight else bottomLeft,
        light,
        -1,
        reverse,
        true,
    )
}

private fun submitBannerTextFallback(
    poseStack: PoseStack,
    collector: SubmitNodeCollector,
    state: BannerRenderState,
    font: Font,
    reverse: Boolean,
) {
    val lines = state.text.take(BannerEntity.MAX_TEXT_LENGTH).trimEnd().lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return
    val scale = (0.014f * state.fontScale.coerceIn(0.5f, 4.0f)).coerceAtLeast(0.004f)
    val maxWidth = (state.placement.length / scale * 0.84f).toInt().coerceAtLeast(16)
    val wrapped = lines.flatMap { line -> font.split(Component.literal(line), maxWidth).map(::sequenceToString) }.take(8)
    if (wrapped.isEmpty()) return

    val textOffset = if (reverse) -0.012 else 0.012
    poseStack.pushPose()
    poseStack.translate(
        state.placement.normal.stepX * textOffset,
        state.placement.normal.stepY * textOffset,
        state.placement.normal.stepZ * textOffset,
    )
    SurfaceRenderUtil.orientToFace(poseStack, state.placement.normal)
    if (reverse) poseStack.mulPose(Axis.YP.rotationDegrees(180.0f))
    poseStack.translate(-state.placement.length / 2.0, state.placement.height / 2.0, 0.0)
    poseStack.scale(scale, -scale, scale)
    val pixelWidth = state.placement.length / scale
    val startY = ((state.placement.height / scale - wrapped.size * font.lineHeight) / 2.0f).coerceAtLeast(0.0f)
    wrapped.forEachIndexed { index, line ->
        val width = font.width(line)
        val x = state.placement.length / scale * 0.08f + when (state.textAlignment) {
            BannerTextAlignment.LEFT -> 0.0f
            BannerTextAlignment.CENTER -> (pixelWidth * 0.84f - width) / 2.0f
            BannerTextAlignment.RIGHT -> pixelWidth * 0.84f - width
        }.coerceAtLeast(0.0f)
        collector.submitText(poseStack, x, startY + index * font.lineHeight, Component.literal(line).visualOrderText, false, Font.DisplayMode.POLYGON_OFFSET, state.lightCoords, state.textColor, 0, 0)
    }
    poseStack.popPose()
}

private fun sequenceToString(sequence: FormattedCharSequence): String {
    val builder = StringBuilder()
    sequence.accept { _, _, codePoint ->
        builder.appendCodePoint(codePoint)
        true
    }
    return builder.toString()
}

object SurfaceRenderUtil {
    private val PREVIEW_TEXTURE = Identifier.fromNamespaceAndPath(Thestreetism.ID, "textures/graffiti/prev.png")
    private val PREVIEW_CENTER_TEXTURE = Identifier.fromNamespaceAndPath(Thestreetism.ID, "textures/graffiti/prevcent.png")

    fun previewTexture(): Identifier = PREVIEW_TEXTURE

    fun previewCenterTexture(): Identifier = PREVIEW_CENTER_TEXTURE

    fun translateRenderOffset(poseStack: PoseStack, facing: Direction) {
        poseStack.translate(facing.stepX * 0.02, facing.stepY * 0.02, facing.stepZ * 0.02)
    }

    fun orientToFace(
        poseStack: PoseStack,
        facing: Direction
    ) {
        when (facing.opposite) {
            Direction.NORTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180f))
            Direction.SOUTH -> Unit
            Direction.EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(90f))
            Direction.WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(-90f))
            Direction.UP -> poseStack.mulPose(Axis.XP.rotationDegrees(-90f))
            Direction.DOWN -> poseStack.mulPose(Axis.XP.rotationDegrees(90f))
        }
    }

    fun submitQuad(
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        renderType: net.minecraft.client.renderer.rendertype.RenderType,
        width: Float,
        height: Float,
        light: Int,
        color: Int,
    ) {
        collector.submitCustomGeometry(poseStack, renderType) { pose, buffer ->
            val halfWidth = width / 2.0f
            val halfHeight = height / 2.0f
            vertex(pose, buffer, -halfWidth, halfHeight, 1f, 0f, light, color)
            vertex(pose, buffer, halfWidth, halfHeight, 0f, 0f, light, color)
            vertex(pose, buffer, halfWidth, -halfHeight, 0f, 1f, light, color)
            vertex(pose, buffer, -halfWidth, -halfHeight, 1f, 1f, light, color)
        }
    }

    fun submitWorldQuad(
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        renderType: net.minecraft.client.renderer.rendertype.RenderType,
        topLeft: Vec3,
        topRight: Vec3,
        bottomRight: Vec3,
        bottomLeft: Vec3,
        light: Int,
        color: Int,
        reverseNormal: Boolean,
        flipU: Boolean = false,
    ) {
        collector.submitCustomGeometry(poseStack, renderType) { pose, buffer ->
            val normal = if (reverseNormal) -1.0f else 1.0f
            val leftU = if (flipU) 1f else 0f
            val rightU = if (flipU) 0f else 1f
            vertex(pose, buffer, topLeft.x.toFloat(), topLeft.y.toFloat(), topLeft.z.toFloat(), leftU, 0f, light, color, normal)
            vertex(pose, buffer, topRight.x.toFloat(), topRight.y.toFloat(), topRight.z.toFloat(), rightU, 0f, light, color, normal)
            vertex(pose, buffer, bottomRight.x.toFloat(), bottomRight.y.toFloat(), bottomRight.z.toFloat(), rightU, 1f, light, color, normal)
            vertex(pose, buffer, bottomLeft.x.toFloat(), bottomLeft.y.toFloat(), bottomLeft.z.toFloat(), leftU, 1f, light, color, normal)
        }
    }

    fun submitCenterIndicator(
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        light: Int,
        color: Int,
    ) {
        val size = 0.12f
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(PREVIEW_CENTER_TEXTURE, false)) { pose, buffer ->
            vertex(pose, buffer, -size, size, 0.0f, 1f, 0f, light, color, 1.0f)
            vertex(pose, buffer, size, size, 0.0f, 0f, 0f, light, color, 1.0f)
            vertex(pose, buffer, size, -size, 0.0f, 0f, 1f, light, color, 1.0f)
            vertex(pose, buffer, -size, -size, 0.0f, 1f, 1f, light, color, 1.0f)
        }
    }

    private fun vertex(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        x: Float,
        y: Float,
        u: Float,
        v: Float,
        light: Int,
        color: Int
    ) {
        vertex(pose, buffer, x, y, 0.0f, u, v, light, color, 1.0f)
    }

    private fun vertex(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        x: Float,
        y: Float,
        z: Float,
        u: Float,
        v: Float,
        light: Int,
        color: Int,
        normalZ: Float,
    ) {
        buffer.addVertex(pose, x, y, z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, 0.0f, 0.0f, normalZ)
    }
}
