package cuitteacon26.thestreetism.client.render

import cuitteacon26.thestreetism.blockentity.FlagControllerBlockEntity
import cuitteacon26.thestreetism.client.font.FontRegistry
import cuitteacon26.thestreetism.multiblock.FlagStructureValidator.Plane
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.phys.Vec3

/**
 * Renders a stitched flag as one continuous animated cloth mesh.
 *
 * ONE mesh per flag — no per-block quads. Client-side vertex displacement animation.
 * World Y+ is always vertical (never inferred from block orientation).
 */
class FlagRenderState : BlockEntityRenderState() {
    var flagWidth = 1
    var flagHeight = 1
    var plane = Plane.ZY
    var seed = 0L
    var uuid: java.util.UUID = java.util.UUID.randomUUID()
    var fontId = ""
    var richTextJson = ""
    var styleJson = ""
    var gameTime = 0L
    var partialTicks = 0f
}

class FlagRenderer(ctx: BlockEntityRendererProvider.Context) : BlockEntityRenderer<FlagControllerBlockEntity, FlagRenderState> {

    override fun shouldRenderOffScreen(): Boolean = true

    override fun getViewDistance(): Int = 128

    override fun createRenderState(): FlagRenderState = FlagRenderState()

    override fun extractRenderState(
        be: FlagControllerBlockEntity,
        state: FlagRenderState,
        partialTicks: Float,
        cameraPosition: Vec3,
        breakProgress: ModelFeatureRenderer.CrumblingOverlay?,
    ) {
        BlockEntityRenderState.extractBase(be, state, breakProgress)
        state.flagWidth = be.flagWidth
        state.flagHeight = be.flagHeight
        state.plane = be.plane
        state.seed = be.seed
        state.uuid = be.uuid
        state.fontId = be.fontId
        state.richTextJson = be.richTextJson
        state.styleJson = be.styleJson
        state.gameTime = be.level?.gameTime ?: 0L
        state.partialTicks = partialTicks
    }

    override fun submit(state: FlagRenderState, poseStack: PoseStack, collector: SubmitNodeCollector, camera: CameraRenderState) {
        val normal = if (state.plane == Plane.XY) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 0.0, 1.0)

        val mesh = meshCache.getOrPut(MeshCacheKey(state.flagWidth, state.flagHeight, state.plane)) {
            FlagMeshBuilder.build(state.flagWidth, state.flagHeight, state.plane)
        }

        val time = (state.gameTime + state.partialTicks) / 20f
        val animated = animCache.getOrPut(state.uuid) { Array(mesh.vertexCount) { Vec3.ZERO } }
        if (animated.size != mesh.vertexCount) {
            animCache[state.uuid] = Array(mesh.vertexCount) { Vec3.ZERO }
        }
        val animationTarget = animCache[state.uuid] ?: Array(mesh.vertexCount) { Vec3.ZERO }

        FlagAnimation.animate(mesh.basePositions, animationTarget, mesh.cols, mesh.rows, time, state.seed, normal)

        val atlas = FontRegistry.getAtlas(state.fontId, state.richTextJson, state.styleJson, state.flagWidth, state.flagHeight)
            ?: return

        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(atlas.textureId, false)) { pose, buf ->
            val flipU = state.plane == Plane.XY
            for (row in 0 until mesh.rows) {
                for (col in 0 until mesh.cols) {
                    val tl = animationTarget[mesh.index(col, row)]
                    val tr = animationTarget[mesh.index(col + 1, row)]
                    val br = animationTarget[mesh.index(col + 1, row + 1)]
                    val bl = animationTarget[mesh.index(col, row + 1)]
                    val rawU0 = col.toFloat() / mesh.cols
                    val rawU1 = (col + 1).toFloat() / mesh.cols
                    val u0 = if (flipU) 1.0f - rawU0 else rawU0
                    val u1 = if (flipU) 1.0f - rawU1 else rawU1
                    val v0 = row.toFloat() / mesh.rows
                    val v1 = (row + 1).toFloat() / mesh.rows
                    addQuad(buf, pose, tl, tr, br, bl, u0, u1, v0, v1, state.lightCoords, normal)
                    addQuad(buf, pose, tr, tl, bl, br, u1, u0, v0, v1, state.lightCoords, normal.scale(-1.0))
                }
            }
        }
    }

    companion object {
        private val meshCache = HashMap<MeshCacheKey, FlagMeshBuilder.Mesh>()
        private val animCache = HashMap<java.util.UUID, Array<Vec3>>()
    }

    private data class MeshCacheKey(val w: Int, val h: Int, val plane: Plane)

    private fun addQuad(
        buffer: com.mojang.blaze3d.vertex.VertexConsumer,
        pose: PoseStack.Pose,
        tl: Vec3,
        tr: Vec3,
        br: Vec3,
        bl: Vec3,
        u0: Float,
        u1: Float,
        v0: Float,
        v1: Float,
        light: Int,
        normal: Vec3,
    ) {
        val nx = normal.x.toFloat()
        val ny = normal.y.toFloat()
        val nz = normal.z.toFloat()
        buffer.addVertex(pose, tl.x.toFloat(), tl.y.toFloat(), tl.z.toFloat()).setColor(-1).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz)
        buffer.addVertex(pose, tr.x.toFloat(), tr.y.toFloat(), tr.z.toFloat()).setColor(-1).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz)
        buffer.addVertex(pose, br.x.toFloat(), br.y.toFloat(), br.z.toFloat()).setColor(-1).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz)
        buffer.addVertex(pose, bl.x.toFloat(), bl.y.toFloat(), bl.z.toFloat()).setColor(-1).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz)
    }
}
