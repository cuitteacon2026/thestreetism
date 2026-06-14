package cuitteacon26.thestreetism.client

import cuitteacon26.thestreetism.Thestreetism
import cuitteacon26.thestreetism.banner.BannerTextAlignment
import cuitteacon26.thestreetism.entity.BannerEntity
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.awt.image.BufferedImage
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.text.AttributedCharacterIterator
import java.text.AttributedString
import java.util.Base64
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

object GraffitiTextures {
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private val loaded = ConcurrentHashMap<String, Identifier>()

    private val FALLBACK_TEXTURE =
        Identifier.fromNamespaceAndPath(
            Thestreetism.ID,
            "textures/graffiti/empty.png"
        )

    fun hasPreferredSystemFont(): Boolean = preferredFontFamily() != null

    fun resolve(key: String): Identifier {
        return when {
            key.startsWith(BANNER_KEY_PREFIX) ->
                resolveBanner(key.removePrefix(BANNER_KEY_PREFIX))

            key.startsWith("remote:") ->
                resolveRemote(key, key.removePrefix("remote:"))

            key.startsWith("local:") ->
                resolveLocal(key.removePrefix("local:"))

            else ->
                resolveLocal(key)
        }
    }

    fun resolveBannerTexture(
        width: Float,
        height: Float,
        backgroundColor: Int,
        textColor: Int,
        text: String,
        fontScale: Float,
        textAlignment: BannerTextAlignment,
    ): String {
        val key = bannerTextureKey(width, height, backgroundColor, textColor, text, fontScale, textAlignment)
        resolveBanner(key)
        return "$BANNER_KEY_PREFIX$key"
    }

    private fun resolveLocal(name: String): Identifier {
        val parsed = Identifier.tryParse(name)

        if (parsed != null) {
            if (
                parsed.path.startsWith("textures/")
                || parsed.path.endsWith(".png")
            ) {
                return parsed
            }

            return Identifier.fromNamespaceAndPath(
                parsed.namespace,
                "textures/graffiti/${parsed.path}.png"
            )
        }

        return Identifier.fromNamespaceAndPath(
            Thestreetism.ID,
            "textures/graffiti/$name.png"
        )
    }

    private fun resolveBanner(key: String): Identifier {
        loaded[key]?.let {
            return it
        }

        val payload = BannerTexturePayload.decode(key) ?: return FALLBACK_TEXTURE
        val textureId =
            Identifier.fromNamespaceAndPath(
                Thestreetism.ID,
                "dynamic_banner/${sha256(key)}"
            )

        if (loading.add(key)) {
            CompletableFuture
                .supplyAsync {
                    buildBannerTexture(payload)
                }
                .thenAccept { image ->
                    Minecraft.getInstance().execute {
                        try {
                            Minecraft.getInstance()
                                .textureManager
                                .register(
                                    textureId,
                                    DynamicTexture(
                                        { "dynamic banner $key" },
                                        image
                                    )
                                )

                            loaded[key] = textureId
                        } catch (e: Exception) {
                            Thestreetism.LOGGER.error(
                                "Failed to register dynamic banner texture {}",
                                key,
                                e
                            )
                        } finally {
                            loading.remove(key)
                        }
                    }
                }
                .exceptionally { error ->
                    loading.remove(key)

                    Thestreetism.LOGGER.warn(
                        "Failed to build dynamic banner texture {}",
                        key,
                        error
                    )

                    null
                }
        }

        return FALLBACK_TEXTURE
    }

    private fun resolveRemote(cacheKey: String, url: String): Identifier {
        loaded[cacheKey]?.let {
            return it
        }

        val textureId =
            Identifier.fromNamespaceAndPath(
                Thestreetism.ID,
                "remote_graffiti/${sha256(url)}"
            )

        if (loading.add(cacheKey)) {
            CompletableFuture
                .supplyAsync {
                    download(url)
                }
                .thenAccept { image ->
                    Minecraft.getInstance().execute {
                        try {
                            Minecraft.getInstance()
                                .textureManager
                                .register(
                                    textureId,
                                    DynamicTexture(
                                        { "remote graffiti $url" },
                                        image
                                    )
                                )

                            loaded[cacheKey] = textureId

                            Thestreetism.LOGGER.info(
                                "Loaded remote graffiti texture {}",
                                url
                            )
                        } catch (e: Exception) {
                            Thestreetism.LOGGER.error(
                                "Failed to register remote graffiti texture {}",
                                url,
                                e
                            )
                        } finally {
                            loading.remove(cacheKey)
                        }
                    }
                }
                .exceptionally { error ->
                    loading.remove(cacheKey)

                    Thestreetism.LOGGER.warn(
                        "Failed to load remote graffiti texture {}",
                        url,
                        error
                    )

                    null
                }
        }

        return FALLBACK_TEXTURE
    }

    private fun buildBannerTexture(payload: BannerTexturePayload): NativeImage {
        val pixelWidth = bannerTexturePixelSize(payload.width)
        val pixelHeight = bannerTexturePixelSize(payload.height)
        val buffered = BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics = buffered.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.color = Color(payload.backgroundColor or 0xFF000000.toInt(), true)
            graphics.fillRect(0, 0, pixelWidth, pixelHeight)
            drawBannerText(graphics, payload, pixelWidth, pixelHeight)
        } finally {
            graphics.dispose()
        }

        val image = NativeImage(NativeImage.Format.RGBA, pixelWidth, pixelHeight, false)
        for (y in 0 until pixelHeight) {
            for (x in 0 until pixelWidth) {
                image.setPixel(x, y, buffered.getRGB(x, y))
            }
        }
        return image
    }

    private fun bannerTextureKey(
        width: Float,
        height: Float,
        backgroundColor: Int,
        textColor: Int,
        text: String,
        fontScale: Float,
        textAlignment: BannerTextAlignment,
    ): String {
        return listOf(
            formatBannerNumber(width),
            formatBannerNumber(height),
            normalizeColor(backgroundColor).toUInt().toString(16),
            normalizeColor(textColor).toUInt().toString(16),
            formatBannerNumber(fontScale),
            textAlignment.serializedName,
            encodeKeyPart(text.take(BannerEntity.MAX_TEXT_LENGTH)),
        ).joinToString("|")
    }

    private fun drawBannerText(
        graphics: java.awt.Graphics2D,
        payload: BannerTexturePayload,
        pixelWidth: Int,
        pixelHeight: Int,
    ) {
        val safeText = payload.text.trimEnd()
        if (safeText.isBlank()) return

        val paddingX = pixelWidth * 0.08f
        val paddingY = pixelHeight * 0.12f
        val usableWidth = (pixelWidth - paddingX * 2.0f).coerceAtLeast(1.0f)
        val usableHeight = (pixelHeight - paddingY * 2.0f).coerceAtLeast(1.0f)
        val baseFont = preferredFont() ?: return
        val baseFontSize = (pixelHeight * 0.18f * payload.fontScale.coerceIn(0.5f, 4.0f)).coerceAtLeast(8.0f)
        val lines = layoutBannerText(safeText, baseFont.deriveFont(baseFontSize), usableWidth)
        if (lines.isEmpty()) return

        var font = baseFont.deriveFont(baseFontSize)
        var metrics = graphics.getFontMetrics(font)
        val unscaledHeight = lines.size * metrics.height
        if (unscaledHeight > usableHeight) {
            font = font.deriveFont((baseFontSize * usableHeight / unscaledHeight).coerceAtLeast(6.0f))
            metrics = graphics.getFontMetrics(font)
        }

        val resolvedLines = layoutBannerText(safeText, font, usableWidth)
        if (resolvedLines.isEmpty()) return
        val totalHeight = resolvedLines.size * metrics.height
        var y = paddingY + ((usableHeight - totalHeight) / 2.0f).coerceAtLeast(0.0f) + metrics.ascent
        graphics.font = font
        graphics.color = Color(payload.textColor or 0xFF000000.toInt(), true)
        resolvedLines.forEach { line ->
            val lineWidth = metrics.stringWidth(line)
            val x = paddingX + when (payload.textAlignment) {
                BannerTextAlignment.LEFT -> 0.0f
                BannerTextAlignment.CENTER -> (usableWidth - lineWidth) / 2.0f
                BannerTextAlignment.RIGHT -> usableWidth - lineWidth
            }.coerceAtLeast(0.0f)
            graphics.drawString(line, x, y)
            y += metrics.height
        }
    }

    private fun layoutBannerText(text: String, font: Font, width: Float): List<String> {
        val attributes = mapOf<AttributedCharacterIterator.Attribute, Any>(TextAttribute.FONT to font)
        val context = FontRenderContext(null, true, true)
        val lines = mutableListOf<String>()
        text.lines().forEach { paragraph ->
            if (paragraph.isBlank()) {
                lines += ""
            } else {
                val attributed = AttributedString(paragraph, attributes)
                val iterator = attributed.iterator
                val measurer = LineBreakMeasurer(iterator, context)
                while (measurer.position < iterator.endIndex) {
                    val start = measurer.position
                    measurer.nextLayout(width)
                    lines += paragraph.substring(start, measurer.position)
                }
            }
        }
        return lines
    }

    private fun preferredFont(): Font? = preferredFontFamily()?.let { Font(it, Font.PLAIN, 24) }

    private fun preferredFontFamily(): String? {
        val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        return SYSTEM_FONT_PREFERENCE.firstOrNull { it in available }
    }

    private fun formatBannerNumber(value: Float): String {
        return String.format(Locale.ROOT, "%.3f", value)
    }

    private fun bannerTexturePixelSize(value: Float): Int {
        return (value.coerceAtLeast(0.25f) * BANNER_TEXTURE_PIXELS_PER_BLOCK)
            .toInt()
            .coerceIn(BANNER_TEXTURE_MIN_SIZE, BANNER_TEXTURE_MAX_SIZE)
    }

    private fun download(url: String): NativeImage {
        val uri = URI(url)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(scheme == "http" || scheme == "https") {
            "Only HTTP/HTTPS URLs are supported"
        }

        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.requestMethod = "GET"
        connection.doInput = true

        return try {
            connection.inputStream.buffered().use { stream ->
                NativeImage.read(LimitedInputStream(stream, MAX_DOWNLOAD_BYTES))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(value: String): String {
        val digest =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))

        return digest.joinToString("") {
            "%02x".format(Locale.ROOT, it)
        }
    }

    private fun normalizeColor(color: Int): Int = color or 0xFF000000.toInt()

    private fun encodeKeyPart(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodeKeyPart(value: String): String? {
        return try {
            String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private data class BannerTexturePayload(
        val width: Float,
        val height: Float,
        val backgroundColor: Int,
        val textColor: Int,
        val fontScale: Float,
        val textAlignment: BannerTextAlignment,
        val text: String,
    ) {
        companion object {
            fun decode(key: String): BannerTexturePayload? {
                val parts = key.split('|')
                if (parts.size != 7) return null
                val width = parts[0].toFloatOrNull() ?: return null
                val height = parts[1].toFloatOrNull() ?: return null
                val backgroundColor = parts[2].toUIntOrNull(16)?.toInt() ?: return null
                val textColor = parts[3].toUIntOrNull(16)?.toInt() ?: return null
                val fontScale = parts[4].toFloatOrNull() ?: return null
                val textAlignment = BannerTextAlignment.bySerializedName(parts[5])
                val text = decodeKeyPart(parts[6]) ?: return null
                return BannerTexturePayload(width, height, backgroundColor, textColor, fontScale, textAlignment, text)
            }
        }
    }

    private class LimitedInputStream(
        private val delegate: java.io.InputStream,
        private val maxBytes: Long,
    ) : java.io.InputStream() {
        private var bytesRead = 0L

        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0) {
                incrementBytesRead(1)
            }
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val value = delegate.read(b, off, len)
            if (value > 0) {
                incrementBytesRead(value.toLong())
            }
            return value
        }

        override fun close() = delegate.close()

        private fun incrementBytesRead(count: Long) {
            bytesRead += count
            check(bytesRead <= maxBytes) {
                "Remote graffiti texture exceeds $maxBytes bytes"
            }
        }
    }

    private const val CONNECT_TIMEOUT_MILLIS = 5_000
    private const val READ_TIMEOUT_MILLIS = 10_000
    private const val MAX_DOWNLOAD_BYTES = 8L * 1024L * 1024L
    private val SYSTEM_FONT_PREFERENCE = listOf("DengXian", "SimSun", "Microsoft YaHei", "等线", "宋体", "微软雅黑")
    private const val USER_AGENT = "thestreetism-graffiti-loader"
    private const val BANNER_KEY_PREFIX = "banner:"
    private const val BANNER_TEXTURE_PIXELS_PER_BLOCK = 64.0f
    private const val BANNER_TEXTURE_MIN_SIZE = 16
    private const val BANNER_TEXTURE_MAX_SIZE = 512
}
