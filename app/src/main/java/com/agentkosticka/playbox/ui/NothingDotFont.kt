package com.agentkosticka.playbox.ui

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import java.io.File

/** Uses Nothing OS NDot57 only when Android actually resolves that family. */
object NothingDotFont {
    private val candidate: Typeface? by lazy {
        // Unknown family names resolve to DEFAULT. toString() is not a font-name API.
        val named = Typeface.create("NDot57", Typeface.NORMAL)
        if (named != Typeface.DEFAULT) named else {
            // Some Nothing OS versions ship the font without exposing a named family.
            sequenceOf("NDot57Caps.otf", "Ndot-57.otf", "Ndot-57-Aligned.otf")
                .map { File("/system/fonts", it) }
                .filter { it.isFile && it.canRead() }
                .mapNotNull { runCatching { Typeface.createFromFile(it) }.getOrNull() }
                .firstOrNull()
        }
    }

    val available: Boolean get() = candidate != null
    val typeface: Typeface get() = candidate ?: Typeface.MONOSPACE
    val family: FontFamily by lazy { FontFamily(typeface) }
}
