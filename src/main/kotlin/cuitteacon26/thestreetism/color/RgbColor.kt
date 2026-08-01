package cuitteacon26.thestreetism.color

import java.util.Locale

internal object RgbColor {
    const val HEX_LENGTH = 6

    private const val RGB_MASK = 0xFFFFFF
    private const val OPAQUE_ALPHA = -0x1000000

    fun parseHex(value: String): Int? {
        if (value.length != HEX_LENGTH || value.any { !it.isHexDigit() }) return null
        return value.toIntOrNull(16)?.let(::opaqueArgb)
    }

    fun formatHex(color: Int): String =
        String.format(Locale.ROOT, "%06X", rgb(color))

    fun opaqueArgb(color: Int): Int = rgb(color) or OPAQUE_ALPHA

    fun rgb(color: Int): Int = color and RGB_MASK

    fun argbToAbgr(color: Int): Int {
        val alphaAndGreen = color and 0xFF00FF00.toInt()
        val red = (color ushr 16) and 0xFF
        val blue = (color and 0xFF) shl 16
        return alphaAndGreen or blue or red
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
