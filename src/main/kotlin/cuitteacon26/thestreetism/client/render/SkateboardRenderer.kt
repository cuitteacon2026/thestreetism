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
    var lean = 0.0f
    var wheelRotation = 0.0f
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
        state.pitch = entity.getBoardPitch(partialTicks)
        state.lean = entity.getLean(partialTicks)
        state.wheelRotation = entity.getWheelRotation(partialTicks)
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
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.lean))
        if (state.hurtTime > 0.0f) {
            val hurtRoll = Mth.sin(state.hurtTime.toDouble()) * state.hurtTime * state.damageTime / 24.0f * state.hurtDir
            poseStack.mulPose(Axis.ZP.rotationDegrees(hurtRoll))
        }

        submitBox(poseStack, submitNodeCollector, DECK_TEXTURE, -0.25f, 0.14f, -0.68f, 0.25f, 0.22f, 0.68f, state.lightCoords)
        submitBox(poseStack, submitNodeCollector, GRIP_TEXTURE, -0.225f, 0.219f, -0.61f, 0.225f, 0.232f, 0.61f, state.lightCoords)
        submitBox(poseStack, submitNodeCollector, METAL_TEXTURE, -0.34f, 0.08f, -0.44f, 0.34f, 0.14f, -0.36f, state.lightCoords)
        submitBox(poseStack, submitNodeCollector, METAL_TEXTURE, -0.34f, 0.08f, 0.36f, 0.34f, 0.14f, 0.44f, state.lightCoords)

        submitWheel(poseStack, submitNodeCollector, -0.33f, -0.40f, state.wheelRotation, state.lightCoords)
        submitWheel(poseStack, submitNodeCollector, 0.33f, -0.40f, state.wheelRotation, state.lightCoords)
        submitWheel(poseStack, submitNodeCollector, -0.33f, 0.40f, state.wheelRotation, state.lightCoords)
        submitWheel(poseStack, submitNodeCollector, 0.33f, 0.40f, state.wheelRotation, state.lightCoords)

        poseStack.popPose()
        super.submit(state, poseStack, submitNodeCollector, camera)
    }

    private fun submitWheel(
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        x: Float,
        z: Float,
        rotation: Float,
        light: Int,
    ) {
        poseStack.pushPose()
        poseStack.translate(x, WHEEL_CENTER_Y, z)
        poseStack.mulPose(Axis.XP.rotationDegrees(rotation))
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WHEEL_TEXTURE)) { pose, buffer ->
            for (segment in 0 until WHEEL_SEGMENTS) {
                val angle0 = segment * WHEEL_SEGMENT_ANGLE
                val angle1 = (segment + 1) * WHEEL_SEGMENT_ANGLE
                val y0 = Mth.sin(angle0.toDouble()) * WHEEL_RADIUS
                val z0 = Mth.cos(angle0.toDouble()) * WHEEL_RADIUS
                val y1 = Mth.sin(angle1.toDouble()) * WHEEL_RADIUS
                val z1 = Mth.cos(angle1.toDouble()) * WHEEL_RADIUS
                val normalAngle = angle0 + WHEEL_SEGMENT_ANGLE / 2.0f

                face(
                    pose,
                    buffer,
                    -WHEEL_HALF_WIDTH,
                    y0,
                    z0,
                    WHEEL_HALF_WIDTH,
                    y0,
                    z0,
                    WHEEL_HALF_WIDTH,
                    y1,
                    z1,
                    -WHEEL_HALF_WIDTH,
                    y1,
                    z1,
                    0.0f,
                    Mth.sin(normalAngle.toDouble()),
                    Mth.cos(normalAngle.toDouble()),
                    light,
                )
                face(
                    pose,
                    buffer,
                    -WHEEL_HALF_WIDTH,
                    0.0f,
                    0.0f,
                    -WHEEL_HALF_WIDTH,
                    y0,
                    z0,
                    -WHEEL_HALF_WIDTH,
                    y1,
                    z1,
                    -WHEEL_HALF_WIDTH,
                    0.0f,
                    0.0f,
                    -1.0f,
                    0.0f,
                    0.0f,
                    light,
                )
                face(
                    pose,
                    buffer,
                    WHEEL_HALF_WIDTH,
                    0.0f,
                    0.0f,
                    WHEEL_HALF_WIDTH,
                    y1,
                    z1,
                    WHEEL_HALF_WIDTH,
                    y0,
                    z0,
                    WHEEL_HALF_WIDTH,
                    0.0f,
                    0.0f,
                    1.0f,
                    0.0f,
                    0.0f,
                    light,
                )
            }
        }
        poseStack.popPose()
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
        private const val WHEEL_SEGMENTS = 8
        private const val WHEEL_SEGMENT_ANGLE = (Math.PI * 2.0 / WHEEL_SEGMENTS).toFloat()
        private const val WHEEL_RADIUS = 0.07f
        private const val WHEEL_HALF_WIDTH = 0.075f
        private const val WHEEL_CENTER_Y = 0.07f
    }
}
