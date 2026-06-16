package cuitteacon26.thestreetism.client.gui

import cuitteacon26.thestreetism.client.font.FontRegistry
import cuitteacon26.thestreetism.client.render.FlagPreviewRenderer
import cuitteacon26.thestreetism.network.FlagUpdatePayload
import cuitteacon26.thestreetism.serialization.FlagStyleData
import cuitteacon26.thestreetism.serialization.FlagTextAlignment
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.client.network.ClientPacketDistributor

class FlagEditorScreen(
    private val controllerPos: BlockPos,
    private val initialRichTextJson: String,
    private val initialFont: String,
    private val initialStyleJson: String,
    private val initialName: String,
    private val flagWidth: Int,
    private val flagHeight: Int,
) : Screen(Component.translatable("thestreetism.gui.flag_editor")) {

    private lateinit var editor: RichTextEditorWidget
    private lateinit var nameBox: EditBox
    private lateinit var fontSelector: CycleButton<String>
    private lateinit var alignmentSelector: CycleButton<FlagTextAlignment>
    private lateinit var colorSelector: CycleButton<PaletteColor>
    private lateinit var modeButton: Button
    private lateinit var boldButton: Button
    private lateinit var italicButton: Button
    private lateinit var underlineButton: Button
    private lateinit var strikeButton: Button

    private var selectedFont: String = initialFont.ifBlank { FontRegistry.DEFAULT_FONT_ID }
    private var styleData: FlagStyleData = FlagStyleData.fromJson(initialStyleJson)

    private var leftX = 0
    private var topY = 0
    private var leftWidth = 0
    private var rightX = 0
    private var rightWidth = 0
    private var editorY = 0
    private var editorHeight = 0

    override fun init() {
        if (!FontRegistry.fontIds.contains(selectedFont)) {
            selectedFont = FontRegistry.DEFAULT_FONT_ID
        }

        leftX = 16
        topY = 16
        val contentBottom = height - 16
        val gap = 16
        val minLeftWidth = 320
        val totalInnerWidth = (width - leftX - 16).coerceAtLeast(640)
        val preferredLeftWidth = (totalInnerWidth * 0.48f).toInt()
        val maxLeftWidth = (totalInnerWidth - gap - 260).coerceAtLeast(minLeftWidth)
        leftWidth = preferredLeftWidth.coerceIn(minLeftWidth, maxLeftWidth)
        rightX = leftX + leftWidth + gap
        rightWidth = (width - rightX - 16).coerceAtLeast(260)

        val toolbarY = topY + 56
        editorY = toolbarY + 30
        editorHeight = (contentBottom - editorY - 96).coerceAtLeast(160)

        nameBox = EditBox(font, leftX, topY + 18, leftWidth.coerceAtMost(260), 20, Component.translatable("thestreetism.gui.flag_editor.name"))
        nameBox.setMaxLength(128)
        nameBox.value = initialName
        addRenderableWidget(nameBox)

        editor = RichTextEditorWidget(font, leftX, editorY, leftWidth, editorHeight, initialRichTextJson, styleData)
        addRenderableWidget(editor.widget())

        boldButton = smallToggleButton(leftX, toolbarY, 30, "B") { styleData = styleData.copy(bold = !styleData.bold); refreshToolbar() }
        italicButton = smallToggleButton(leftX + 36, toolbarY, 30, "I") { styleData = styleData.copy(italic = !styleData.italic); refreshToolbar() }
        underlineButton = smallToggleButton(leftX + 72, toolbarY, 30, "U") { styleData = styleData.copy(underline = !styleData.underline); refreshToolbar() }
        strikeButton = smallToggleButton(leftX + 108, toolbarY, 30, "S") { styleData = styleData.copy(strikethrough = !styleData.strikethrough); refreshToolbar() }
        modeButton = Button.builder(modeLabel()) {
            editor.toggleMode(styleData)
            it.message = modeLabel()
        }.bounds(leftX + 146, toolbarY, 92, 20).build()

        addRenderableWidget(boldButton)
        addRenderableWidget(italicButton)
        addRenderableWidget(underlineButton)
        addRenderableWidget(strikeButton)
        addRenderableWidget(modeButton)

        val colorX = leftX + 246
        colorSelector = CycleButton.builder<PaletteColor>({ Component.literal(it.label) }, currentPaletteColor())
            .withValues(PALETTE)
            .displayOnlyValue()
            .create(colorX, toolbarY, (leftWidth - (colorX - leftX)).coerceAtLeast(110), 20, Component.translatable("thestreetism.gui.flag_editor.color")) { _, value ->
                styleData = styleData.copy(textColor = value.argb)
            }
        addRenderableWidget(colorSelector)

        val bottomRowY = contentBottom - 48
        val fontWidth = (leftWidth * 0.44f).toInt().coerceAtLeast(140)
        val alignWidth = (leftWidth - fontWidth - 180).coerceIn(90, 130)
        val metricButtonWidth = 34
        val buttonGap = 4
        val metricStartX = leftX + fontWidth + 10 + alignWidth + 10
        val reloadWidth = ((rightWidth - 10) / 2).coerceAtLeast(110)
        val saveWidth = (rightWidth - reloadWidth - 10).coerceAtLeast(110)
        fontSelector = CycleButton.builder<String>({ Component.literal(it) }, selectedFont)
            .withValues(FontRegistry.fontIds)
            .displayOnlyValue()
            .create(leftX, bottomRowY, fontWidth, 20, Component.translatable("thestreetism.gui.flag_editor.font")) { _, value ->
                selectedFont = value
            }
        addRenderableWidget(fontSelector)

        alignmentSelector = CycleButton.builder<FlagTextAlignment>(
            { alignmentLabel(it) },
            styleData.alignment,
        )
            .withValues(FlagTextAlignment.entries)
            .displayOnlyValue()
            .create(leftX + fontWidth + 10, bottomRowY, alignWidth, 20, Component.translatable("thestreetism.gui.flag_editor.align")) { _, value ->
                styleData = styleData.copy(alignment = value)
            }
        addRenderableWidget(alignmentSelector)

        addRenderableWidget(Button.builder(Component.literal("-A")) {
            styleData = styleData.copy(fontScale = (styleData.fontScale - 0.1f).coerceAtLeast(0.5f))
        }.bounds(metricStartX, bottomRowY, metricButtonWidth, 20).build())
        addRenderableWidget(Button.builder(Component.literal("+A")) {
            styleData = styleData.copy(fontScale = (styleData.fontScale + 0.1f).coerceAtMost(4.0f))
        }.bounds(metricStartX + metricButtonWidth + buttonGap, bottomRowY, metricButtonWidth, 20).build())
        addRenderableWidget(Button.builder(Component.literal("-L")) {
            styleData = styleData.copy(lineSpacing = (styleData.lineSpacing - 0.1f).coerceAtLeast(0.8f))
        }.bounds(metricStartX + (metricButtonWidth + buttonGap) * 2 + 6, bottomRowY, metricButtonWidth, 20).build())
        addRenderableWidget(Button.builder(Component.literal("+L")) {
            styleData = styleData.copy(lineSpacing = (styleData.lineSpacing + 0.1f).coerceAtMost(2.5f))
        }.bounds(metricStartX + (metricButtonWidth + buttonGap) * 3 + 6, bottomRowY, metricButtonWidth, 20).build())

        addRenderableWidget(Button.builder(Component.translatable("thestreetism.gui.flag_editor.reload_fonts")) {
            FontRegistry.reload()
            minecraft.setScreen(
                FlagEditorScreen(
                    controllerPos = controllerPos,
                    initialRichTextJson = editor.serializedJson(styleData),
                    initialFont = selectedFont,
                    initialStyleJson = styleData.toJson(),
                    initialName = nameBox.value,
                    flagWidth = flagWidth,
                    flagHeight = flagHeight,
                )
            )
        }.bounds(rightX, bottomRowY, reloadWidth, 20).build())

        addRenderableWidget(Button.builder(Component.translatable("thestreetism.gui.flag_editor.save")) {
            ClientPacketDistributor.sendToServer(
                FlagUpdatePayload(
                    controllerPos = controllerPos,
                    richTextJson = editor.serializedJson(styleData),
                    fontId = selectedFont,
                    styleJson = styleData.toJson(),
                    customName = nameBox.value,
                )
            )
            onClose()
        }.bounds(rightX + reloadWidth + 10, bottomRowY, saveWidth, 20).build())

        refreshToolbar()
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(RenderPipelines.GUI, 0, 0, width, height, 0xD113100D.toInt())
        graphics.fillGradient(0, 0, width, 48, 0xCC2E2314.toInt(), 0x002E2314)
        graphics.fill(RenderPipelines.GUI, leftX - 8, topY + 44, leftX + leftWidth + 8, topY + 84, 0x441F1A12)
        graphics.fill(RenderPipelines.GUI, leftX - 8, editorY - 8, leftX + leftWidth + 8, editorY + editorHeight + 8, 0x3015120E)
        graphics.fill(RenderPipelines.GUI, rightX - 8, topY + 44, rightX + rightWidth + 8, height - 64, 0x2A15120E)
        graphics.text(font, title, leftX, topY, 0xFFF4E7CE.toInt())
        graphics.text(font, Component.translatable("thestreetism.gui.flag_editor.preview"), rightX, topY, 0xFFD8C7A8.toInt())
        graphics.text(font, Component.translatable("thestreetism.gui.flag_editor.name"), leftX, topY + 6, 0xFFD8C7A8.toInt())
        graphics.text(font, Component.literal("A ${"%.1f".format(styleData.fontScale)}"), leftX + leftWidth - 124, height - 68, 0xFFEAD9BA.toInt())
        graphics.text(font, Component.literal("L ${"%.1f".format(styleData.lineSpacing)}"), leftX + leftWidth - 50, height - 68, 0xFFEAD9BA.toInt())

        val previewX = rightX
        val previewY = topY + 56
        val previewWidth = rightWidth
        val previewHeight = (height - previewY - 72).coerceAtLeast(140)
        FlagPreviewRenderer.renderEditorPreview(
            graphics = graphics,
            x = previewX,
            y = previewY,
            width = previewWidth,
            height = previewHeight,
            flagWidth = flagWidth,
            flagHeight = flagHeight,
            richTextJson = editor.serializedJson(styleData),
            fontId = selectedFont,
            styleJson = styleData.toJson(),
        )

        if (editor.hasParseError()) {
            graphics.text(font, Component.translatable("thestreetism.gui.flag_editor.json_error"), leftX + 246, topY + 50, 0xFFFF7A7A.toInt())
        }
    }

    override fun isPauseScreen(): Boolean = false

    private fun smallToggleButton(x: Int, y: Int, width: Int, label: String, onPress: () -> Unit): Button {
        return Button.builder(Component.literal(label)) {
            onPress()
        }.bounds(x, y, width, 20).build()
    }

    private fun refreshToolbar() {
        boldButton.message = toggleLabel("B", styleData.bold)
        italicButton.message = toggleLabel("I", styleData.italic)
        underlineButton.message = toggleLabel("U", styleData.underline)
        strikeButton.message = toggleLabel("S", styleData.strikethrough)
        modeButton.message = modeLabel()
        if (::alignmentSelector.isInitialized) {
            alignmentSelector.setValue(styleData.alignment)
        }
        if (::colorSelector.isInitialized) {
            colorSelector.setValue(currentPaletteColor())
        }
    }

    private fun toggleLabel(base: String, enabled: Boolean): Component =
        Component.literal(if (enabled) "[$base]" else base)

    private fun modeLabel(): Component =
        Component.translatable(if (editor.isJsonMode()) "thestreetism.gui.flag_editor.mode_json" else "thestreetism.gui.flag_editor.mode_text")

    private fun alignmentLabel(alignment: FlagTextAlignment): Component = when (alignment) {
        FlagTextAlignment.LEFT -> Component.translatable("thestreetism.gui.flag_editor.align_left")
        FlagTextAlignment.CENTER -> Component.translatable("thestreetism.gui.flag_editor.align_center")
        FlagTextAlignment.RIGHT -> Component.translatable("thestreetism.gui.flag_editor.align_right")
    }

    private fun currentPaletteColor(): PaletteColor =
        PALETTE.firstOrNull { it.argb == styleData.textColor } ?: PALETTE.first()

    private data class PaletteColor(val label: String, val argb: Int)

    companion object {
        private val PALETTE = listOf(
            PaletteColor("Ivory", 0xFFF7F1E3.toInt()),
            PaletteColor("Ink", 0xFF1E1A18.toInt()),
            PaletteColor("Red", 0xFFB73A2B.toInt()),
            PaletteColor("Blue", 0xFF2F5D8A.toInt()),
            PaletteColor("Gold", 0xFFC89A2B.toInt()),
            PaletteColor("Green", 0xFF3E7B52.toInt()),
        )
    }
}
