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

    /**
     * Bounded LRU of uploaded atlases. Every distinct flag text produces a
     * 1024xN GPU texture, so an unbounded map here leaks video memory for the
     * whole session as players edit their flags. Evicted atlases are released
     * from the texture manager rather than merely dropped.
     */
    private val atlasCache = object : LinkedHashMap<AtlasKey, FontAtlasBuilder.RenderedAtlas>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<AtlasKey, FontAtlasBuilder.RenderedAtlas>): Boolean {
            if (size <= MAX_CACHED_ATLASES) return false
            releaseAtlas(eldest.value)
            return true
        }
    }

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
        atlasCache[key]?.let { return it }
        val built = FontAtlasBuilder.buildAndUpload(fontId, richTextJson, styleJson, flagWidth, flagHeight) ?: return null
        atlasCache[key] = built
        return built
    }

    fun resolveFont(fontId: String, size: Float, bold: Boolean, italic: Boolean): Font {
        val base = loadedFonts[fontId] ?: Font("SansSerif", Font.PLAIN, size.toInt().coerceAtLeast(1))
        val style = (if (bold) Font.BOLD else Font.PLAIN) or (if (italic) Font.ITALIC else Font.PLAIN)
        return base.deriveFont(style, size)
    }

    private fun clearAtlasCache() {
        atlasCache.values.forEach(::releaseAtlas)
        atlasCache.clear()
    }

    private fun releaseAtlas(atlas: FontAtlasBuilder.RenderedAtlas) {
        runCatching { Minecraft.getInstance().textureManager }.getOrNull()?.release(atlas.textureId)
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

    /** Upper bound on live flag atlases; keeps GPU memory flat during editing. */
    private const val MAX_CACHED_ATLASES = 24
}
