package cuitteacon26.thestreetism.client.render

import cuitteacon26.thestreetism.Thestreetism
import cuitteacon26.thestreetism.block.FlagClothBlock
import cuitteacon26.thestreetism.client.font.FontRegistry
import cuitteacon26.thestreetism.item.ModItems
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent

/**
 * Shows a highlight when hovering cloth blocks with the stitching tool.
 * Uses the same renderer pipeline as [FlagRenderer].
 */
@EventBusSubscriber(modid = Thestreetism.ID, value = [Dist.CLIENT])
object FlagPreviewRenderer {

    fun renderEditorPreview(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        flagWidth: Int,
        flagHeight: Int,
        richTextJson: String,
        fontId: String,
        styleJson: String,
    ) {
        graphics.fill(RenderPipelines.GUI, x, y, x + width, y + height, 0x80110F0C.toInt())
        graphics.outline(x, y, width, height, 0x55F4E1C1)
        val atlas = FontRegistry.getAtlas(fontId, richTextJson, styleJson, flagWidth, flagHeight) ?: return
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            atlas.textureId,
            x + 8,
            y + 8,
            0f,
            0f,
            width - 16,
            height - 16,
            atlas.width,
            atlas.height,
        )
    }

    @SubscribeEvent
    fun onSubmitCustomGeometry(event: SubmitCustomGeometryEvent) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (player.mainHandItem.item != ModItems.STITCHING_TOOL &&
            player.offhandItem.item != ModItems.STITCHING_TOOL) return

        val hit = mc.hitResult as? BlockHitResult ?: return
        if (hit.type != HitResult.Type.BLOCK) return

        val level = mc.level ?: return
        val state = level.getBlockState(hit.blockPos)
        if (state.block !is FlagClothBlock) return

        val camera = event.levelRenderState.cameraRenderState.pos
        val poseStack = event.poseStack
        poseStack.pushPose()
        poseStack.translate(
            hit.blockPos.x - camera.x,
            hit.blockPos.y - camera.y,
            hit.blockPos.z - camera.z,
        )
        cuitteacon26.thestreetism.client.SurfaceRenderUtil.submitCenterIndicator(
            poseStack, event.submitNodeCollector, 15728880, 0xCC55FF55.toInt()
        )
        poseStack.popPose()
    }
}
