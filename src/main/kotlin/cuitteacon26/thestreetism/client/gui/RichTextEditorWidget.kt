package cuitteacon26.thestreetism.client.gui

import cuitteacon26.thestreetism.serialization.FlagStyleData
import cuitteacon26.thestreetism.serialization.FlagTextSerialization
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.components.MultiLineEditBox
import net.minecraft.network.chat.Component

class RichTextEditorWidget(
    font: Font,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    initialRichTextJson: String,
    styleData: FlagStyleData,
) {
    private val textBox: MultiLineEditBox = MultiLineEditBox.builder()
        .setX(x)
        .setY(y)
        .setPlaceholder(Component.translatable("thestreetism.gui.flag_editor.text"))
        .build(font, width, height, Component.translatable("thestreetism.gui.flag_editor.text"))
        .also {
            it.setCharacterLimit(32767)
            it.setLineLimit(128)
        }

    private var jsonMode = false
    private var lastValidJson = initialRichTextJson.ifBlank {
        FlagTextSerialization.richTextJsonFromPlainText("", styleData)
    }
    private var parseError = false

    init {
        setRichTextJson(initialRichTextJson, styleData)
    }

    fun widget(): MultiLineEditBox = textBox

    fun isJsonMode(): Boolean = jsonMode

    fun hasParseError(): Boolean = parseError

    fun toggleMode(styleData: FlagStyleData) {
        jsonMode = !jsonMode
        if (jsonMode) {
            textBox.setValue(serializedJson(styleData))
        } else {
            textBox.setValue(FlagTextSerialization.plainText(serializedJson(styleData)))
        }
        parseError = false
    }

    fun setRichTextJson(richTextJson: String, styleData: FlagStyleData) {
        lastValidJson = if (richTextJson.isBlank()) {
            FlagTextSerialization.richTextJsonFromPlainText("", styleData)
        } else {
            richTextJson
        }
        textBox.setValue(if (jsonMode) lastValidJson else FlagTextSerialization.plainText(lastValidJson))
        parseError = false
    }

    fun serializedJson(styleData: FlagStyleData): String {
        val currentValue = textBox.value
        if (!jsonMode) {
            lastValidJson = FlagTextSerialization.richTextJsonFromPlainText(currentValue, styleData)
            parseError = false
            return lastValidJson
        }

        val parsed = FlagTextSerialization.tryComponentFromJson(currentValue)
        return if (parsed != null) {
            lastValidJson = FlagTextSerialization.componentToJson(parsed)
            parseError = false
            lastValidJson
        } else {
            parseError = true
            lastValidJson
        }
    }

    fun previewComponent(styleData: FlagStyleData): Component =
        FlagTextSerialization.componentFromJson(serializedJson(styleData))
}
