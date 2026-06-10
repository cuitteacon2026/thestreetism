package cuitteacon26.thestreetism.graffiti

import cuitteacon26.thestreetism.Thestreetism
import net.minecraft.resources.Identifier

object GraffitiRegistry {
    data class GraffitiDefinition(
        val id: Identifier,
        val texture: Identifier,
        val width: Float,
        val height: Float,
    )

    val BUILT_INS: List<GraffitiDefinition> = List(50) { index ->
        val number = (index + 1).toString().padStart(2, '0')
        val id = Identifier.fromNamespaceAndPath(Thestreetism.ID, "graffiti_$number")
        GraffitiDefinition(id, id.withPrefix("textures/graffiti/").withSuffix(".png"), 1.0f, 1.0f)
    }

    val DEFAULT = BUILT_INS.first()

    fun get(id: Identifier): GraffitiDefinition = BUILT_INS.firstOrNull { it.id == id } ?: DEFAULT
}
