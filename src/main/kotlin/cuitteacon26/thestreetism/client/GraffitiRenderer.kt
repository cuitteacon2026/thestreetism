package cuitteacon26.thestreetism.client

import cuitteacon26.thestreetism.Thestreetism
import cuitteacon26.thestreetism.entity.GraffitiEntity
import cuitteacon26.thestreetism.entity.ModEntities
import cuitteacon26.thestreetism.item.ModItems
import cuitteacon26.thestreetism.item.SprayCanItem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.Direction
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent
import net.neoforged.neoforge.common.NeoForge

object ClientSetup {
    fun register() {
        NeoForge.EVENT_BUS.register(GraffitiPreviewRenderer)
    }

    fun onClientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            net.minecraft.client.renderer.entity.EntityRenderers.register(ModEntities.GRAFFITI, ::GraffitiRenderer)
        }
    }
}

class GraffitiRenderState : EntityRenderState() {
    var texture: Identifier = PREVIEW_CENTER_TEXTURE
    var width = 1.0f
    var height = 1.0f
    var facing = Direction.NORTH
    var rotation = 0.0f
    var attachedBlockPos = net.minecraft.core.BlockPos.ZERO
}

class GraffitiRenderer(context: EntityRendererProvider.Context) : EntityRenderer<GraffitiEntity, GraffitiRenderState>(context) {
    override fun createRenderState() = GraffitiRenderState()

    override fun extractRenderState(entity: GraffitiEntity, state: GraffitiRenderState, partialTicks: Float) {
        super.extractRenderState(entity, state, partialTicks)
        state.texture = GraffitiTextures.resolve(
            entity.textureKey()
        )
        state.width = entity.graffitiWidth()
        state.height = entity.graffitiHeight()
        state.facing = entity.facing()
        state.rotation = entity.graffitiRotation()
        state.attachedBlockPos = entity.attachedBlockPos()
        state.lightCoords = LevelRenderer.getLightCoords(entity.level(), entity.attachedBlockPos().relative(entity.facing()))
    }

    override fun submit(state: GraffitiRenderState, poseStack: PoseStack, submitNodeCollector: SubmitNodeCollector, camera: CameraRenderState) {
        poseStack.pushPose()
        translateRenderOffset(poseStack, state.facing)
        orientToFace(poseStack, state.facing)
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.rotation))
        submitQuad(poseStack, submitNodeCollector, RenderTypes.entityTranslucentCullItemTarget(state.texture), state.width, state.height, state.lightCoords, -1)
        poseStack.popPose()
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
        translateRenderOffset(poseStack, hit.direction)
        orientToFace(poseStack, hit.direction)
        submitQuad(poseStack, event.submitNodeCollector, RenderTypes.entityTranslucent(PREVIEW_TEXTURE, false), size.first, size.second, 15728880, color)
        submitCenterIndicator(poseStack, event.submitNodeCollector, 15728880, centerColor)
        poseStack.popPose()
    }

    private val PREVIEW_TEXTURE = Identifier.fromNamespaceAndPath(Thestreetism.ID, "textures/graffiti/prev.png")
}

private val PREVIEW_CENTER_TEXTURE = Identifier.fromNamespaceAndPath(Thestreetism.ID, "textures/graffiti/prevcent.png")

private fun translateRenderOffset(poseStack: PoseStack, facing: Direction) {
    poseStack.translate(facing.stepX * 0.02, facing.stepY * 0.02, facing.stepZ * 0.02)
}

private fun orientToFace(
    poseStack: PoseStack,
    facing: Direction
) {
    when (facing.opposite) {
        Direction.NORTH ->
            poseStack.mulPose(Axis.YP.rotationDegrees(180f))

        Direction.SOUTH ->
            Unit

        Direction.EAST ->
            poseStack.mulPose(Axis.YP.rotationDegrees(90f))

        Direction.WEST ->
            poseStack.mulPose(Axis.YP.rotationDegrees(-90f))

        Direction.UP ->
            poseStack.mulPose(Axis.XP.rotationDegrees(-90f))

        Direction.DOWN ->
            poseStack.mulPose(Axis.XP.rotationDegrees(90f))
    }
}

private fun submitQuad(
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
        vertex(pose, buffer, -halfWidth,  halfHeight, 1f, 0f, light, color)
        vertex(pose, buffer,  halfWidth,  halfHeight, 0f, 0f, light, color)
        vertex(pose, buffer,  halfWidth, -halfHeight, 0f, 1f, light, color)
        vertex(pose, buffer, -halfWidth, -halfHeight, 1f, 1f, light, color)
    }
}

private fun submitCenterIndicator(
    poseStack: PoseStack,
    collector: SubmitNodeCollector,
    light: Int,
    color: Int,
) {
    val size = 0.12f
    collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(PREVIEW_CENTER_TEXTURE, false)) { pose, buffer ->
        vertex(pose, buffer, -size,  size, 1f, 0f, light, color)
        vertex(pose, buffer,  size,  size, 0f, 0f, light, color)
        vertex(pose, buffer,  size, -size, 0f, 1f, light, color)
        vertex(pose, buffer, -size, -size, 1f, 1f, light, color)
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
    buffer.addVertex(pose, x, y, 0.0f)
        .setColor(color)
        .setUv(u, v)
        .setOverlay(OverlayTexture.NO_OVERLAY)
        .setLight(light)
        .setNormal(pose, 0.0f, 0.0f, 1.0f)
}
