package cuitteacon26.thestreetism.client.font

import cuitteacon26.thestreetism.Thestreetism
import net.minecraft.client.Minecraft
import net.neoforged.fml.loading.FMLPaths
import java.awt.Font
import java.nio.file.Files
import java.nio.file.Path

object FontRegistry {
    const val DEFAULT_FONT_ID = "default"

    private val fontsDir: Path = FMLPaths.CONFIGDIR.get().resolve("streetism").resolve("fonts")
    private val loadedFonts = mutableMapOf<String, Font>()
    private val atlasCache = mutableMapOf<AtlasKey, FontAtlasBuilder.RenderedAtlas>()

    val fontIds: List<String>
        get() = buildList {
            add(DEFAULT_FONT_ID)
            addAll(loadedFonts.keys.sorted())
        }

    fun reload() {
        loadedFonts.clear()
        clearAtlasCache()
        Files.createDirectories(fontsDir)
        Files.list(fontsDir).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter {
                    val name = it.fileName.toString().lowercase()
                    name.endsWith(".ttf") || name.endsWith(".otf")
                }
                .forEach(::loadFont)
        }
        Thestreetism.LOGGER.info("FontRegistry: loaded ${loadedFonts.size} external font(s)")
    }

    fun getAtlas(fontId: String, richTextJson: String, styleJson: String, flagWidth: Int, flagHeight: Int): FontAtlasBuilder.RenderedAtlas? {
        val key = AtlasKey(fontId, richTextJson.hashCode(), styleJson.hashCode(), flagWidth, flagHeight)
        return atlasCache.getOrPut(key) {
            FontAtlasBuilder.buildAndUpload(fontId, richTextJson, styleJson, flagWidth, flagHeight) ?: return null
        }
    }

    fun resolveFont(fontId: String, size: Float, bold: Boolean, italic: Boolean): Font {
        val base = loadedFonts[fontId] ?: Font("SansSerif", Font.PLAIN, size.toInt().coerceAtLeast(1))
        val style = (if (bold) Font.BOLD else Font.PLAIN) or (if (italic) Font.ITALIC else Font.PLAIN)
        return base.deriveFont(style, size)
    }

    private fun clearAtlasCache() {
        val textureManager = runCatching { Minecraft.getInstance().textureManager }.getOrNull()
        atlasCache.values.forEach { atlas -> textureManager?.release(atlas.textureId) }
        atlasCache.clear()
    }

    private fun loadFont(path: Path) {
        runCatching {
            path.toFile().inputStream().use { input ->
                Font.createFont(Font.TRUETYPE_FONT, input)
            }
        }.onSuccess { loadedFonts[path.fileName.toString().substringBeforeLast('.')] = it }
            .onFailure { Thestreetism.LOGGER.warn("FontRegistry: failed to load font ${path.fileName}: ${it.message}") }
    }

    private data class AtlasKey(
        val fontId: String,
        val richTextHash: Int,
        val styleHash: Int,
        val width: Int,
        val height: Int,
    )
}
