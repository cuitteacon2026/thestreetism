package cuitteacon26.thestreetism.client.gui

import cuitteacon26.thestreetism.client.font.FontRegistry
import cuitteacon26.thestreetism.color.RgbColor
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
) : Screen(Component.translatable("thestreetism.gui.flag_editor")) {

    private lateinit var editor: RichTextEditorWidget
    private lateinit var nameBox: EditBox
    private lateinit var fontSelector: CycleButton<String>
    private lateinit var alignmentSelector: CycleButton<FlagTextAlignment>
    private lateinit var colorBox: EditBox
    private lateinit var modeButton: Button
    private lateinit var boldButton: Button
    private lateinit var italicButton: Button
    private lateinit var underlineButton: Button
    private lateinit var strikeButton: Button

    private var selectedFont: String = initialFont.ifBlank { FontRegistry.DEFAULT_FONT_ID }
    private var styleData: FlagStyleData = FlagStyleData.fromJson(initialStyleJson).let {
        it.copy(textColor = RgbColor.opaqueArgb(it.textColor))
    }

    private var contentX = 0
    private var contentWidth = 0
    private var topY = 0
    private var toolbarY = 0
    private var metricsY = 0
    private var secondMetricX = 0
    private var editorY = 0
    private var editorHeight = 0
    private var optionsY = 0
    private var actionsY = 0

    override fun init() {
        if (!FontRegistry.fontIds.contains(selectedFont)) {
            selectedFont = FontRegistry.DEFAULT_FONT_ID
        }

        val horizontalMargin = if (width < 360) 12 else 16
        contentWidth = (width - horizontalMargin * 2).coerceAtLeast(1).coerceAtMost(720)
        contentX = (width - contentWidth) / 2
        topY = 12
        toolbarY = topY + 54
        metricsY = toolbarY + 26
        editorY = metricsY + 28
        actionsY = height - 28
        optionsY = actionsY - 28
        editorHeight = (optionsY - editorY - 8).coerceAtLeast(1)

        nameBox = EditBox(
            font,
            contentX,
            topY + 28,
            contentWidth.coerceAtMost(320),
            20,
            Component.translatable("thestreetism.gui.flag_editor.name"),
        )
        nameBox.setMaxLength(128)
        nameBox.value = initialName
        addRenderableWidget(nameBox)

        editor = RichTextEditorWidget(font, contentX, editorY, contentWidth, editorHeight, initialRichTextJson, styleData)
        addRenderableWidget(editor.widget())

        val toggleGap = 4
        val toolbarGap = 8
        val toggleWidth = ((contentWidth / 2 - toggleGap * 3) / 4).coerceIn(1, 30)
        val toggleGroupWidth = toggleWidth * 4 + toggleGap * 3
        boldButton = smallToggleButton(contentX, toolbarY, toggleWidth, "B") {
            styleData = styleData.copy(bold = !styleData.bold)
            refreshToolbar()
        }
        italicButton = smallToggleButton(contentX + toggleWidth + toggleGap, toolbarY, toggleWidth, "I") {
            styleData = styleData.copy(italic = !styleData.italic)
            refreshToolbar()
        }
        underlineButton = smallToggleButton(contentX + (toggleWidth + toggleGap) * 2, toolbarY, toggleWidth, "U") {
            styleData = styleData.copy(underline = !styleData.underline)
            refreshToolbar()
        }
        strikeButton = smallToggleButton(contentX + (toggleWidth + toggleGap) * 3, toolbarY, toggleWidth, "S") {
            styleData = styleData.copy(strikethrough = !styleData.strikethrough)
            refreshToolbar()
        }

        val selectorSpace = (contentWidth - toggleGroupWidth - toolbarGap * 2).coerceAtLeast(2)
        val modeWidth = (selectorSpace / 2).coerceAtMost(92)
        val colorWidth = (selectorSpace - modeWidth).coerceAtMost(140)
        val modeX = contentX + toggleGroupWidth + toolbarGap
        modeButton = Button.builder(modeLabel()) {
            editor.toggleMode(styleData)
            it.message = modeLabel()
        }.bounds(modeX, toolbarY, modeWidth, 20).build()

        val colorX = modeX + modeWidth + toolbarGap
        colorBox = EditBox(
            font,
            colorX,
            toolbarY,
            colorWidth,
            20,
            Component.translatable("thestreetism.gui.flag_editor.color"),
        )
        colorBox.setMaxLength(RgbColor.HEX_LENGTH)
        colorBox.value = RgbColor.formatHex(styleData.textColor)
        colorBox.setResponder {
            RgbColor.parseHex(it)?.let { color -> styleData = styleData.copy(textColor = color) }
        }

        addRenderableWidget(boldButton)
        addRenderableWidget(italicButton)
        addRenderableWidget(underlineButton)
        addRenderableWidget(strikeButton)
        addRenderableWidget(modeButton)
        addRenderableWidget(colorBox)

        val metricGroupGap = if (contentWidth >= 280) 12 else 6
        val metricGroupWidth = (contentWidth - metricGroupGap) / 2
        val metricButtonGap = 4
        val metricButtonWidth = ((metricGroupWidth - metricButtonGap * 2) / 3).coerceIn(1, 36)
        val metricLabelWidth = metricGroupWidth - metricButtonWidth * 2 - metricButtonGap * 2
        val firstMetricButtonX = contentX + metricLabelWidth + metricButtonGap
        secondMetricX = contentX + metricGroupWidth + metricGroupGap
        val secondMetricButtonX = secondMetricX + metricLabelWidth + metricButtonGap

        addRenderableWidget(Button.builder(Component.literal("-")) {
            styleData = styleData.copy(fontScale = (styleData.fontScale - 0.1f).coerceAtLeast(0.5f))
        }.bounds(firstMetricButtonX, metricsY, metricButtonWidth, 20).build())
        addRenderableWidget(Button.builder(Component.literal("+")) {
            styleData = styleData.copy(fontScale = (styleData.fontScale + 0.1f).coerceAtMost(4.0f))
        }.bounds(firstMetricButtonX + metricButtonWidth + metricButtonGap, metricsY, metricButtonWidth, 20).build())
        addRenderableWidget(Button.builder(Component.literal("-")) {
            styleData = styleData.copy(lineSpacing = (styleData.lineSpacing - 0.1f).coerceAtLeast(0.8f))
        }.bounds(secondMetricButtonX, metricsY, metricButtonWidth, 20).build())
        addRenderableWidget(Button.builder(Component.literal("+")) {
            styleData = styleData.copy(lineSpacing = (styleData.lineSpacing + 0.1f).coerceAtMost(2.5f))
        }.bounds(secondMetricButtonX + metricButtonWidth + metricButtonGap, metricsY, metricButtonWidth, 20).build())

        val footerGap = 8
        val fontWidth = ((contentWidth - footerGap) * 3) / 5
        val alignWidth = contentWidth - footerGap - fontWidth
        fontSelector = CycleButton.builder<String>({ Component.literal(it) }, selectedFont)
            .withValues(FontRegistry.fontIds)
            .displayOnlyValue()
            .create(contentX, optionsY, fontWidth, 20, Component.translatable("thestreetism.gui.flag_editor.font")) { _, value ->
                selectedFont = value
            }
        addRenderableWidget(fontSelector)

        alignmentSelector = CycleButton.builder<FlagTextAlignment>({ alignmentLabel(it) }, styleData.alignment)
            .withValues(FlagTextAlignment.entries)
            .displayOnlyValue()
            .create(contentX + fontWidth + footerGap, optionsY, alignWidth, 20, Component.translatable("thestreetism.gui.flag_editor.align")) { _, value ->
                styleData = styleData.copy(alignment = value)
            }
        addRenderableWidget(alignmentSelector)

        val reloadWidth = (contentWidth - footerGap) / 2
        val saveWidth = contentWidth - footerGap - reloadWidth
        addRenderableWidget(Button.builder(Component.translatable("thestreetism.gui.flag_editor.reload_fonts")) {
            FontRegistry.reload()
            minecraft.setScreen(
                FlagEditorScreen(
                    controllerPos = controllerPos,
                    initialRichTextJson = editor.serializedJson(styleData),
                    initialFont = selectedFont,
                    initialStyleJson = styleData.toJson(),
                    initialName = nameBox.value,
                )
            )
        }.bounds(contentX, actionsY, reloadWidth, 20).build())

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
        }.bounds(contentX + reloadWidth + footerGap, actionsY, saveWidth, 20).build())

        refreshToolbar()
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(RenderPipelines.GUI, 0, 0, width, height, 0xD113100D.toInt())
        graphics.fillGradient(0, 0, width, 48, 0xCC2E2314.toInt(), 0x002E2314)
        graphics.fill(RenderPipelines.GUI, contentX - 8, toolbarY - 6, contentX + contentWidth + 8, metricsY + 26, 0x441F1A12)
        graphics.fill(RenderPipelines.GUI, contentX - 8, editorY - 8, contentX + contentWidth + 8, editorY + editorHeight + 8, 0x3015120E)
        graphics.fill(RenderPipelines.GUI, contentX - 8, optionsY - 6, contentX + contentWidth + 8, actionsY + 26, 0x2A15120E)
        graphics.text(font, title, contentX, topY, 0xFFF4E7CE.toInt())
        graphics.text(font, Component.translatable("thestreetism.gui.flag_editor.name"), contentX, topY + 16, 0xFFD8C7A8.toInt())
        graphics.text(font, Component.literal("A ${"%.1f".format(styleData.fontScale)}"), contentX, metricsY + 6, 0xFFEAD9BA.toInt())
        graphics.text(font, Component.literal("L ${"%.1f".format(styleData.lineSpacing)}"), secondMetricX, metricsY + 6, 0xFFEAD9BA.toInt())

        editor.serializedJson(styleData)
        if (editor.hasParseError()) {
            val error = Component.translatable("thestreetism.gui.flag_editor.json_error")
            val errorX = (contentX + contentWidth - font.width(error)).coerceAtLeast(contentX)
            graphics.text(font, error, errorX, topY, 0xFFFF7A7A.toInt())
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

}
