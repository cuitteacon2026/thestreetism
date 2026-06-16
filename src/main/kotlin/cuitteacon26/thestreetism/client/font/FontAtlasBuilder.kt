package cuitteacon26.thestreetism.client.font

import cuitteacon26.thestreetism.Thestreetism
import cuitteacon26.thestreetism.serialization.FlagStyleData
import cuitteacon26.thestreetism.serialization.FlagTextAlignment
import cuitteacon26.thestreetism.serialization.FlagTextSerialization
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.max
import kotlin.math.roundToInt

object FontAtlasBuilder {

    private const val ATLAS_WIDTH = 1024
    private const val MIN_ATLAS_HEIGHT = 384
    private const val MAX_ATLAS_HEIGHT = 1024
    private const val CONTENT_PADDING = 48

    data class RenderedAtlas(
        val textureId: Identifier,
        val width: Int,
        val height: Int,
    )

    fun buildAndUpload(
        fontId: String,
        richTextJson: String,
        styleJson: String,
        flagWidth: Int,
        flagHeight: Int,
    ): RenderedAtlas? {
        return runCatching {
            val styleData = FlagStyleData.fromJson(styleJson)
            val component = FlagTextSerialization.componentFromJson(richTextJson)
            val image = renderFlagSurface(fontId, component, styleData, flagWidth, flagHeight)
            upload(fontId, richTextJson.hashCode(), styleJson.hashCode(), image)
        }.onFailure {
            Thestreetism.LOGGER.warn("FontAtlasBuilder: failed to build atlas for $fontId: ${it.message}")
        }.getOrNull()
    }

    private fun renderFlagSurface(
        fontId: String,
        component: Component,
        styleData: FlagStyleData,
        flagWidth: Int,
        flagHeight: Int,
    ): BufferedImage {
        val atlasHeight = (ATLAS_WIDTH * (flagHeight.toFloat() / flagWidth.coerceAtLeast(1))).roundToInt()
            .coerceIn(MIN_ATLAS_HEIGHT, MAX_ATLAS_HEIGHT)
        val image = BufferedImage(ATLAS_WIDTH, atlasHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        paintBackground(graphics, ATLAS_WIDTH, atlasHeight)
        paintText(graphics, fontId, component, styleData, ATLAS_WIDTH, atlasHeight)
        graphics.dispose()
        return image
    }

    private fun paintBackground(graphics: Graphics2D, width: Int, height: Int) {
        val base = Color(0xE7, 0xDD, 0xCB, 0xFF)
        graphics.color = base
        graphics.fillRect(0, 0, width, height)
        for (y in 0 until height step 12) {
            val band = if ((y / 12) % 2 == 0) Color(0xEF, 0xE5, 0xD4, 0xFF) else Color(0xDD, 0xD3, 0xC0, 0xFF)
            graphics.color = band
            graphics.fillRect(0, y, width, 6)
        }
        graphics.color = Color(255, 255, 255, 42)
        graphics.stroke = BasicStroke(2f)
        graphics.drawRect(12, 12, width - 24, height - 24)
    }

    private fun paintText(
        graphics: Graphics2D,
        fontId: String,
        component: Component,
        styleData: FlagStyleData,
        atlasWidth: Int,
        atlasHeight: Int,
    ) {
        val contentWidth = (atlasWidth - CONTENT_PADDING * 2).coerceAtLeast(32)
        val contentHeight = (atlasHeight - CONTENT_PADDING * 2).coerceAtLeast(32)
        val runs = layoutRuns(graphics, fontId, component, styleData, contentWidth)
        if (runs.isEmpty()) return

        val totalHeight = runs.sumOf { line ->
            max(1, (line.height * styleData.lineSpacing).roundToInt())
        }.coerceAtMost(contentHeight)
        var y = CONTENT_PADDING + ((contentHeight - totalHeight) / 2)

        runs.forEach { line ->
            val x = CONTENT_PADDING + when (styleData.alignment) {
                FlagTextAlignment.LEFT -> 0
                FlagTextAlignment.CENTER -> (contentWidth - line.width) / 2
                FlagTextAlignment.RIGHT -> contentWidth - line.width
            }.coerceAtLeast(0)

            var drawX = x
            val baseline = y + line.ascent
            line.runs.forEach { run ->
                graphics.font = run.font
                graphics.color = run.color
                graphics.drawString(run.text, drawX, baseline)
                if (run.underline) {
                    val underlineY = baseline + max(1, run.metrics.descent / 2)
                    graphics.fillRect(drawX, underlineY, run.width, 2)
                }
                if (run.strikethrough) {
                    val strikeY = baseline - run.metrics.ascent / 3
                    graphics.fillRect(drawX, strikeY, run.width, 2)
                }
                drawX += run.width
            }
            y += max(1, (line.height * styleData.lineSpacing).roundToInt())
        }
    }

    private fun layoutRuns(
        graphics: Graphics2D,
        fontId: String,
        component: Component,
        styleData: FlagStyleData,
        maxWidth: Int,
    ): List<LayoutLine> {
        val lines = mutableListOf(LayoutLine())
        var currentLine = lines.last()

        for (token in tokenize(component)) {
            if (token.lineBreak) {
                currentLine = LayoutLine()
                lines.add(currentLine)
                continue
            }
            if (token.text.isBlank() && currentLine.runs.isEmpty()) continue

            val run = createRun(graphics, fontId, token.text, token.style, styleData)
            val wouldOverflow = currentLine.runs.isNotEmpty() && currentLine.width + run.width > maxWidth && !token.text.isBlank()
            if (wouldOverflow) {
                currentLine = LayoutLine()
                lines.add(currentLine)
            }

            if (token.text.isBlank() && currentLine.runs.isEmpty()) continue
            currentLine.add(run)
        }

        return lines.filter { it.runs.isNotEmpty() }
    }

    private fun createRun(
        graphics: Graphics2D,
        fontId: String,
        text: String,
        style: Style,
        styleData: FlagStyleData,
    ): DrawRun {
        val awtFont = FontRegistry.resolveFont(
            fontId = fontId,
            size = (38f * styleData.fontScale).coerceIn(14f, 120f),
            bold = style.isBold,
            italic = style.isItalic,
        )
        graphics.font = awtFont
        val metrics = graphics.getFontMetrics(awtFont)
        val rgb = style.color?.value ?: (styleData.textColor and 0xFFFFFF)
        val alpha = (styleData.textColor ushr 24).takeIf { it > 0 } ?: 255
        return DrawRun(
            text = text,
            font = awtFont,
            color = Color((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF, alpha),
            underline = style.isUnderlined,
            strikethrough = style.isStrikethrough,
            width = metrics.stringWidth(text),
            metrics = metrics,
        )
    }

    private fun tokenize(component: Component): List<Token> {
        val tokens = mutableListOf<Token>()
        component.toFlatList().forEach { part ->
            val style = part.style
            val builder = StringBuilder()
            fun flush() {
                if (builder.isNotEmpty()) {
                    tokens.add(Token(builder.toString(), style, false))
                    builder.setLength(0)
                }
            }

            part.string.forEach { ch ->
                when {
                    ch == '\n' -> {
                        flush()
                        tokens.add(Token("", style, true))
                    }

                    ch.isWhitespace() -> {
                        flush()
                        tokens.add(Token(ch.toString(), style, false))
                    }

                    else -> builder.append(ch)
                }
            }
            flush()
        }
        return if (tokens.isEmpty()) listOf(Token("", Style.EMPTY, false)) else tokens
    }

    private fun upload(fontId: String, richTextHash: Int, styleHash: Int, image: BufferedImage): RenderedAtlas {
        val id = Identifier.fromNamespaceAndPath(
            Thestreetism.ID,
            "dynamic/flag/${fontId}_${Integer.toHexString(richTextHash)}_${Integer.toHexString(styleHash)}"
        )
        val nativeImage = com.mojang.blaze3d.platform.NativeImage(
            com.mojang.blaze3d.platform.NativeImage.Format.RGBA,
            image.width,
            image.height,
            false,
        )
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                nativeImage.setPixel(x, y, (a shl 24) or (b shl 16) or (g shl 8) or r)
            }
        }

        Minecraft.getInstance().textureManager.register(id, DynamicTexture({ "streetism_flag_atlas" }, nativeImage))
        return RenderedAtlas(id, image.width, image.height)
    }

    private data class Token(val text: String, val style: Style, val lineBreak: Boolean)

    private data class DrawRun(
        val text: String,
        val font: Font,
        val color: Color,
        val underline: Boolean,
        val strikethrough: Boolean,
        val width: Int,
        val metrics: FontMetrics,
    )

    private data class LayoutLine(
        val runs: MutableList<DrawRun> = mutableListOf(),
        var width: Int = 0,
        var ascent: Int = 0,
        var descent: Int = 0,
    ) {
        val height: Int
            get() = ascent + descent

        fun add(run: DrawRun) {
            runs.add(run)
            width += run.width
            ascent = max(ascent, run.metrics.ascent)
            descent = max(descent, run.metrics.descent)
        }
    }
}
