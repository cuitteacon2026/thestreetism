package cuitteacon26.thestreetism.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import cuitteacon26.thestreetism.entity.SkateboardEntity
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth

class SkateboardRenderState : EntityRenderState() {
    var yRot = 0.0f
    var pitch = 0.0f
    var hurtTime = 0.0f
    var hurtDir = 1
    var damageTime = 0.0f
}

class SkateboardRenderer(context: EntityRendererProvider.Context) : EntityRenderer<SkateboardEntity, SkateboardRenderState>(context) {
    init {
        shadowRadius = 0.55f
        shadowStrength = 0.7f
    }

    override fun createRenderState(): SkateboardRenderState = SkateboardRenderState()

    override fun extractRenderState(entity: SkateboardEntity, state: SkateboardRenderState, partialTicks: Float) {
        super.extractRenderState(entity, state, partialTicks)
        state.yRot = entity.getYRot(partialTicks)
        state.pitch = if (entity.onGround()) {
            0.0f
        } else {
            Mth.clamp((-entity.deltaMovement.y * 35.0).toFloat(), -18.0f, 24.0f)
        }
        state.hurtTime = entity.hurtTime - partialTicks
        state.hurtDir = entity.hurtDir
        state.damageTime = (entity.damage - partialTicks).coerceAtLeast(0.0f)
    }

    override fun submit(
        state: SkateboardRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        camera: CameraRenderState,
    ) {
        poseStack.pushPose()
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot))
        poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch))
        if (state.hurtTime > 0.0f) {
            val hurtRoll = Mth.sin(state.hurtTime.toDouble()) * state.hurtTime * state.damageTime / 24.0f * state.hurtDir
            poseStack.mulPose(Axis.ZP.rotationDegrees(hurtRoll))
        }

        submitBox(poseStack, submitNodeCollector, DECK_TEXTURE, -0.25f, 0.14f, -0.68f, 0.25f, 0.22f, 0.68f, state.lightCoords)
        submitBox(poseStack, submitNodeCollector, GRIP_TEXTURE, -0.225f, 0.219f, -0.61f, 0.225f, 0.232f, 0.61f, state.lightCoords)
        submitBox(poseStack, submitNodeCollector, METAL_TEXTURE, -0.34f, 0.08f, -0.44f, 0.34f, 0.14f, -0.36f, state.lightCoords)
        submitBox(poseStack, submitNodeCollector, METAL_TEXTURE, -0.34f, 0.08f, 0.36f, 0.34f, 0.14f, 0.44f, state.lightCoords)

        submitWheel(poseStack, submitNodeCollector, -0.33f, -0.40f, state.lightCoords)
        submitWheel(poseStack, submitNodeCollector, 0.33f, -0.40f, state.lightCoords)
        submitWheel(poseStack, submitNodeCollector, -0.33f, 0.40f, state.lightCoords)
        submitWheel(poseStack, submitNodeCollector, 0.33f, 0.40f, state.lightCoords)

        poseStack.popPose()
        super.submit(state, poseStack, submitNodeCollector, camera)
    }

    private fun submitWheel(
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        x: Float,
        z: Float,
        light: Int,
    ) {
        submitBox(
            poseStack,
            collector,
            WHEEL_TEXTURE,
            x - 0.075f,
            0.015f,
            z - 0.085f,
            x + 0.075f,
            0.105f,
            z + 0.085f,
            light,
        )
    }

    private fun submitBox(
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        texture: Identifier,
        minX: Float,
        minY: Float,
        minZ: Float,
        maxX: Float,
        maxY: Float,
        maxZ: Float,
        light: Int,
    ) {
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture)) { pose, buffer ->
            face(pose, buffer, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ, 0.0f, 1.0f, 0.0f, light)
            face(pose, buffer, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, 0.0f, -1.0f, 0.0f, light)
            face(pose, buffer, minX, maxY, maxZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, 0.0f, 0.0f, 1.0f, light)
            face(pose, buffer, maxX, maxY, minZ, maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, 0.0f, 0.0f, -1.0f, light)
            face(pose, buffer, maxX, maxY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, 1.0f, 0.0f, 0.0f, light)
            face(pose, buffer, minX, maxY, minZ, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, -1.0f, 0.0f, 0.0f, light)
        }
    }

    private fun face(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        x0: Float,
        y0: Float,
        z0: Float,
        x1: Float,
        y1: Float,
        z1: Float,
        x2: Float,
        y2: Float,
        z2: Float,
        x3: Float,
        y3: Float,
        z3: Float,
        normalX: Float,
        normalY: Float,
        normalZ: Float,
        light: Int,
    ) {
        vertex(pose, buffer, x0, y0, z0, 0.0f, 0.0f, normalX, normalY, normalZ, light)
        vertex(pose, buffer, x1, y1, z1, 0.0f, 1.0f, normalX, normalY, normalZ, light)
        vertex(pose, buffer, x2, y2, z2, 1.0f, 1.0f, normalX, normalY, normalZ, light)
        vertex(pose, buffer, x3, y3, z3, 1.0f, 0.0f, normalX, normalY, normalZ, light)
    }

    private fun vertex(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        x: Float,
        y: Float,
        z: Float,
        u: Float,
        v: Float,
        normalX: Float,
        normalY: Float,
        normalZ: Float,
        light: Int,
    ) {
        buffer.addVertex(pose, x, y, z)
            .setColor(-1)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, normalX, normalY, normalZ)
    }

    companion object {
        private val DECK_TEXTURE = Identifier.withDefaultNamespace("textures/block/oak_planks.png")
        private val GRIP_TEXTURE = Identifier.withDefaultNamespace("textures/block/black_concrete.png")
        private val METAL_TEXTURE = Identifier.withDefaultNamespace("textures/block/iron_block.png")
        private val WHEEL_TEXTURE = Identifier.withDefaultNamespace("textures/block/gray_concrete.png")
    }
}
