package cuitteacon26.thestreetism.client.gui

import cuitteacon26.thestreetism.banner.BannerTextAlignment
import cuitteacon26.thestreetism.color.RgbColor
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
) : AbstractContainerScreen<BannerEditorMenu>(editorMenu, playerInventory, titleText, 360, 190) {
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
        currentBackgroundColor = RgbColor.opaqueArgb(editorMenu.initialBackgroundColor)
        currentTextColor = RgbColor.opaqueArgb(editorMenu.initialTextColor)
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
        colorBox.setMaxLength(RgbColor.HEX_LENGTH)
        colorBox.value = RgbColor.formatHex(currentBackgroundColor)
        colorBox.setResponder {
            RgbColor.parseHex(it)?.let { color -> currentBackgroundColor = color }
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
        textColorBox.setMaxLength(RgbColor.HEX_LENGTH)
        textColorBox.value = RgbColor.formatHex(currentTextColor)
        textColorBox.setResponder {
            RgbColor.parseHex(it)?.let { color -> currentTextColor = color }
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
        graphics.text(font, Component.literal("Text"), leftPos + 16, topPos + 28, 0xFF404040.toInt())
        graphics.text(font, Component.literal("Font scale"), leftPos + 208, topPos + 28, 0xFF404040.toInt())
        graphics.text(font, Component.literal("Background"), leftPos + 208, topPos + 66, 0xFF404040.toInt())
        graphics.text(font, Component.literal("Font color"), leftPos + 208, topPos + 128, 0xFF404040.toInt())
        super.extractContents(graphics, mouseX, mouseY, partialTick)
    }

    private fun extractEditorBackground(graphics: GuiGraphicsExtractor) {
        graphics.fill(RenderPipelines.GUI, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFE9D3AA.toInt())
        graphics.outline(leftPos, topPos, imageWidth, imageHeight, 0xFF3F2D1A.toInt())
        graphics.fill(RenderPipelines.GUI, leftPos + 10, topPos + 18, leftPos + 190, topPos + 182, 0x66FFFFFF)
        graphics.fill(RenderPipelines.GUI, leftPos + 202, topPos + 18, leftPos + 334, topPos + 182, 0x66FFFFFF)
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
        colorBox.value = RgbColor.formatHex(currentBackgroundColor)
        textColorBox.value = RgbColor.formatHex(currentTextColor)
    }

    private fun syncAlignmentButtons() {
        alignLeftButton.active = currentAlignment != BannerTextAlignment.LEFT
        alignCenterButton.active = currentAlignment != BannerTextAlignment.CENTER
        alignRightButton.active = currentAlignment != BannerTextAlignment.RIGHT
    }

    private fun parseScale(value: String): Float {
        return value.toFloatOrNull()?.coerceIn(0.5f, 4.0f) ?: currentFontScale
    }

    private fun formatScale(value: Float): String = String.format(java.util.Locale.ROOT, "%.1f", value)

    private fun adjustColor(color: Int, delta: Int): Int {
        val a = color and -0x1000000
        val r = (((color shr 16) and 0xFF) + delta).coerceIn(0, 255)
        val g = (((color shr 8) and 0xFF) + delta).coerceIn(0, 255)
        val b = ((color and 0xFF) + delta).coerceIn(0, 255)
        return a or (r shl 16) or (g shl 8) or b
    }
}
