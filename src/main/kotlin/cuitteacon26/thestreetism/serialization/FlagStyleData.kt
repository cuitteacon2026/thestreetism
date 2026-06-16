package cuitteacon26.thestreetism.serialization

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

enum class FlagTextAlignment(val serializedName: String) {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right");

    companion object {
        fun byName(name: String?): FlagTextAlignment =
            entries.firstOrNull { it.serializedName.equals(name, ignoreCase = true) } ?: CENTER
    }
}

data class FlagStyleData(
    val textColor: Int = 0xFFF7F1E3.toInt(),
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val alignment: FlagTextAlignment = FlagTextAlignment.CENTER,
    val lineSpacing: Float = 1.1f,
    val fontScale: Float = 1.0f,
) {
    fun toJson(): String {
        val json = JsonObject()
        json.addProperty("textColor", textColor)
        json.addProperty("bold", bold)
        json.addProperty("italic", italic)
        json.addProperty("underline", underline)
        json.addProperty("strikethrough", strikethrough)
        json.addProperty("alignment", alignment.serializedName)
        json.addProperty("lineSpacing", lineSpacing)
        json.addProperty("fontScale", fontScale)
        return json.toString()
    }

    fun toStyle(): Style {
        var style = Style.EMPTY.withColor(TextColor.fromRgb(textColor and 0xFFFFFF))
        style = style.withBold(bold)
        style = style.withItalic(italic)
        style = style.withUnderlined(underline)
        style = style.withStrikethrough(strikethrough)
        return style
    }

    companion object {
        val DEFAULT = FlagStyleData()

        fun fromJson(raw: String?): FlagStyleData {
            if (raw.isNullOrBlank()) return DEFAULT
            return runCatching {
                val json = JsonParser.parseString(raw).asJsonObject
                FlagStyleData(
                    textColor = json.get("textColor")?.asInt ?: DEFAULT.textColor,
                    bold = json.get("bold")?.asBoolean ?: DEFAULT.bold,
                    italic = json.get("italic")?.asBoolean ?: DEFAULT.italic,
                    underline = json.get("underline")?.asBoolean ?: DEFAULT.underline,
                    strikethrough = json.get("strikethrough")?.asBoolean ?: DEFAULT.strikethrough,
                    alignment = FlagTextAlignment.byName(json.get("alignment")?.asString),
                    lineSpacing = (json.get("lineSpacing")?.asFloat ?: DEFAULT.lineSpacing).coerceIn(0.8f, 2.5f),
                    fontScale = (json.get("fontScale")?.asFloat ?: DEFAULT.fontScale).coerceIn(0.5f, 4.0f),
                )
            }.getOrDefault(DEFAULT)
        }
    }
}
