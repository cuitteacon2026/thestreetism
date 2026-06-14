package cuitteacon26.thestreetism.client.gui

import cuitteacon26.thestreetism.banner.BannerTextAlignment
import cuitteacon26.thestreetism.entity.BannerEntity
import cuitteacon26.thestreetism.menu.BannerEditorMenu
import cuitteacon26.thestreetism.network.BannerUpdatePayload
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.neoforged.neoforge.client.network.ClientPacketDistributor

class BannerEditorScreen(
    private val editorMenu: BannerEditorMenu,
    playerInventory: Inventory,
    titleText: Component,
) : AbstractContainerScreen<BannerEditorMenu>(editorMenu, playerInventory, titleText, 360, 238) {
    private lateinit var textBox: MultiLineEditBox
    private lateinit var fontScaleBox: EditBox
    private lateinit var colorBox: EditBox
    private lateinit var textColorBox: EditBox
    private lateinit var applyButton: Button
    private lateinit var closeButton: Button
    private lateinit var minusScaleButton: Button
    private lateinit var plusScaleButton: Button
    private lateinit var alignLeftButton: Button
    private lateinit var alignCenterButton: Button
    private lateinit var alignRightButton: Button
    private lateinit var darkenButton: Button
    private lateinit var lightenButton: Button
    private var currentText = ""
    private var currentFontScale = 1.0f
    private var currentBackgroundColor = BannerEntity.DEFAULT_BACKGROUND_COLOR
    private var currentTextColor = BannerEntity.DEFAULT_TEXT_COLOR
    private var currentAlignment = BannerTextAlignment.CENTER

    override fun init() {
        super.init()
        currentText = editorMenu.initialText
        currentFontScale = editorMenu.initialFontScale
        currentBackgroundColor = editorMenu.initialBackgroundColor
        currentTextColor = editorMenu.initialTextColor
        currentAlignment = editorMenu.initialTextAlignment

        val panelX = leftPos + 16
        val panelY = topPos + 26
        val controlsX = leftPos + 208
        val controlsY = panelY

        textBox = MultiLineEditBox.builder()
            .setX(panelX)
            .setY(panelY + 14)
            .setPlaceholder(Component.literal("Banner text"))
            .setShowBackground(true)
            .build(font, 168, 88, Component.literal("Text"))
        textBox.setCharacterLimit(BannerEntity.MAX_TEXT_LENGTH)
        textBox.setLineLimit(8)
        textBox.setValue(currentText)
        textBox.setValueListener {
            currentText = it
        }
        addRenderableWidget(textBox)

        fontScaleBox = EditBox(font, controlsX, controlsY + 14, 58, 18, Component.literal("Scale"))
        fontScaleBox.value = formatScale(currentFontScale)
        fontScaleBox.setResponder {
            currentFontScale = parseScale(it)
        }
        addRenderableWidget(fontScaleBox)

        minusScaleButton = Button.builder(Component.literal("-")) {
            currentFontScale = (currentFontScale - 0.1f).coerceAtLeast(0.5f)
            syncControlFields()
        }.bounds(controlsX + 64, controlsY + 14, 20, 18).build()
        addRenderableWidget(minusScaleButton)

        plusScaleButton = Button.builder(Component.literal("+")) {
            currentFontScale = (currentFontScale + 0.1f).coerceAtMost(4.0f)
            syncControlFields()
        }.bounds(controlsX + 88, controlsY + 14, 20, 18).build()
        addRenderableWidget(plusScaleButton)

        colorBox = EditBox(font, controlsX, controlsY + 52, 94, 18, Component.literal("Color"))
        colorBox.value = formatColor(currentBackgroundColor)
        colorBox.setResponder {
            currentBackgroundColor = parseColor(it, currentBackgroundColor)
        }
        addRenderableWidget(colorBox)

        darkenButton = Button.builder(Component.literal("Darker")) {
            currentBackgroundColor = adjustColor(currentBackgroundColor, -18)
            syncControlFields()
        }.bounds(controlsX, controlsY + 76, 54, 18).build()
        addRenderableWidget(darkenButton)

        lightenButton = Button.builder(Component.literal("Lighter")) {
            currentBackgroundColor = adjustColor(currentBackgroundColor, 18)
            syncControlFields()
        }.bounds(controlsX + 58, controlsY + 76, 58, 18).build()
        addRenderableWidget(lightenButton)

        textColorBox = EditBox(font, controlsX, controlsY + 114, 94, 18, Component.literal("Font color"))
        textColorBox.value = formatColor(currentTextColor)
        textColorBox.setResponder {
            currentTextColor = parseColor(it, currentTextColor)
        }
        addRenderableWidget(textColorBox)

        alignLeftButton = Button.builder(Component.literal("Left")) {
            currentAlignment = BannerTextAlignment.LEFT
            syncAlignmentButtons()
        }.bounds(panelX, panelY + 108, 50, 18).build()
        addRenderableWidget(alignLeftButton)

        alignCenterButton = Button.builder(Component.literal("Center")) {
            currentAlignment = BannerTextAlignment.CENTER
            syncAlignmentButtons()
        }.bounds(panelX + 54, panelY + 108, 58, 18).build()
        addRenderableWidget(alignCenterButton)

        alignRightButton = Button.builder(Component.literal("Right")) {
            currentAlignment = BannerTextAlignment.RIGHT
            syncAlignmentButtons()
        }.bounds(panelX + 116, panelY + 108, 52, 18).build()
        addRenderableWidget(alignRightButton)

        applyButton = Button.builder(Component.literal("Apply")) {
            pushUpdate()
        }.bounds(controlsX, controlsY + 134, 54, 20).build()
        addRenderableWidget(applyButton)

        closeButton = Button.builder(Component.literal("Close")) {
            pushUpdate()
            onClose()
        }.bounds(controlsX + 60, controlsY + 134, 56, 20).build()
        addRenderableWidget(closeButton)

        syncAlignmentButtons()
    }

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        extractEditorBackground(graphics)
        extractPreview(graphics)
        graphics.text(font, Component.literal("Text"), leftPos + 16, topPos + 28, 0xFF404040.toInt())
        graphics.text(font, Component.literal("Font scale"), leftPos + 208, topPos + 28, 0xFF404040.toInt())
        graphics.text(font, Component.literal("Background"), leftPos + 208, topPos + 66, 0xFF404040.toInt())
        graphics.text(font, Component.literal("Font color"), leftPos + 208, topPos + 104, 0xFF404040.toInt())
        graphics.text(font, Component.literal("Preview"), leftPos + 16, topPos + 164, 0xFF404040.toInt())
        super.extractContents(graphics, mouseX, mouseY, partialTick)
    }

    private fun extractEditorBackground(graphics: GuiGraphicsExtractor) {
        graphics.fill(RenderPipelines.GUI, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFE9D3AA.toInt())
        graphics.outline(leftPos, topPos, imageWidth, imageHeight, 0xFF3F2D1A.toInt())
        graphics.fill(RenderPipelines.GUI, leftPos + 10, topPos + 18, leftPos + 190, topPos + 154, 0x66FFFFFF)
        graphics.fill(RenderPipelines.GUI, leftPos + 202, topPos + 18, leftPos + 334, topPos + 154, 0x66FFFFFF)
        graphics.fill(RenderPipelines.GUI, leftPos + 10, topPos + 154, leftPos + 350, topPos + 228, 0x66FFFFFF)
    }

    private fun extractPreview(graphics: GuiGraphicsExtractor) {
        val previewX = leftPos + 24
        val previewY = topPos + 180
        val previewWidth = 300
        val previewHeight = 36
        graphics.fill(RenderPipelines.GUI, previewX - 2, previewY - 2, previewX + previewWidth + 2, previewY + previewHeight + 2, 0xFF3F2D1A.toInt())
        graphics.fill(RenderPipelines.GUI, previewX, previewY, previewX + previewWidth, previewY + previewHeight, currentBackgroundColor or 0xFF000000.toInt())
        val lines = currentText.take(BannerEntity.MAX_TEXT_LENGTH).trimEnd().lines().take(4)
        if (lines.isEmpty() || lines.all { it.isBlank() }) return

        val textColor = currentTextColor or 0xFF000000.toInt()
        val scale = currentFontScale.coerceIn(0.5f, 4.0f).coerceAtMost(2.0f)
        val lineHeight = (font.lineHeight * scale).toInt().coerceAtLeast(1)
        val totalHeight = lineHeight * lines.size
        val originY = previewY + (previewHeight - totalHeight) / 2
        graphics.pose().pushMatrix()
        graphics.pose().scale(scale, scale)
        lines.forEachIndexed { index, line ->
            val lineWidth = font.width(line)
            val x = when (currentAlignment) {
                BannerTextAlignment.LEFT -> previewX + 8
                BannerTextAlignment.CENTER -> previewX + (previewWidth - (lineWidth * scale).toInt()) / 2
                BannerTextAlignment.RIGHT -> previewX + previewWidth - (lineWidth * scale).toInt() - 8
            }
            val y = originY + index * lineHeight
            graphics.text(font, line, (x / scale).toInt(), (y / scale).toInt(), textColor, false)
        }
        graphics.pose().popMatrix()
    }

    private fun pushUpdate() {
        if (editorMenu.bannerEntityId < 0) return
        ClientPacketDistributor.sendToServer(
            BannerUpdatePayload(
                entityId = editorMenu.bannerEntityId,
                backgroundColor = currentBackgroundColor,
                textColor = currentTextColor,
                text = currentText,
                fontScale = currentFontScale,
                textAlignment = currentAlignment,
            )
        )
    }

    private fun syncControlFields() {
        fontScaleBox.value = formatScale(currentFontScale)
        colorBox.value = formatColor(currentBackgroundColor)
        textColorBox.value = formatColor(currentTextColor)
    }

    private fun syncAlignmentButtons() {
        alignLeftButton.active = currentAlignment != BannerTextAlignment.LEFT
        alignCenterButton.active = currentAlignment != BannerTextAlignment.CENTER
        alignRightButton.active = currentAlignment != BannerTextAlignment.RIGHT
    }

    private fun parseScale(value: String): Float {
        return value.toFloatOrNull()?.coerceIn(0.5f, 4.0f) ?: currentFontScale
    }

    private fun parseColor(value: String, fallback: Int): Int {
        val normalized = value.removePrefix("#")
        val parsed = normalized.toUIntOrNull(16)?.toInt() ?: return fallback
        return if (normalized.length <= 6) parsed or 0xFF000000.toInt() else parsed
    }

    private fun formatScale(value: Float): String = String.format(java.util.Locale.ROOT, "%.1f", value)

    private fun formatColor(value: Int): String = String.format(java.util.Locale.ROOT, "%08X", value)

    private fun adjustColor(color: Int, delta: Int): Int {
        val a = color and -0x1000000
        val r = (((color shr 16) and 0xFF) + delta).coerceIn(0, 255)
        val g = (((color shr 8) and 0xFF) + delta).coerceIn(0, 255)
        val b = ((color and 0xFF) + delta).coerceIn(0, 255)
        return a or (r shl 16) or (g shl 8) or b
    }
}
