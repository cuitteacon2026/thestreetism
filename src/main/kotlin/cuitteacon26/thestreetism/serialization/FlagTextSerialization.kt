package cuitteacon26.thestreetism.serialization

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.chat.MutableComponent

object FlagTextSerialization {
    fun componentToJson(component: Component): String {
        val encoded = ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, component)
        return encoded.result().map { it.toString() }.orElse("\"\"")
    }

    fun tryComponentFromJson(raw: String?): Component? {
        if (raw.isNullOrBlank()) return CommonComponents.EMPTY
        return runCatching {
            val element = JsonParser.parseString(raw)
            ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, element).result().orElse(null)
        }.getOrNull()
    }

    fun componentFromJson(raw: String?): Component {
        return tryComponentFromJson(raw) ?: CommonComponents.EMPTY
    }

    fun componentFromPlainText(text: String, styleData: FlagStyleData): Component {
        val root: MutableComponent = Component.literal(text)
        root.withStyle(styleData.toStyle())
        return root
    }

    fun richTextJsonFromPlainText(text: String, styleData: FlagStyleData): String =
        componentToJson(componentFromPlainText(text, styleData))

    fun plainText(raw: String?): String = componentFromJson(raw).string
}
