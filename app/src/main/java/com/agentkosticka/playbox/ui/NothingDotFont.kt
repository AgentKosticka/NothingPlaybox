package com.agentkosticka.playbox.ui

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily

/** Uses Nothing OS NDot57 only when Android actually resolves that family. */
object NothingDotFont {
    private val candidate: Typeface by lazy { Typeface.create("NDot57", Typeface.NORMAL) }

    // Typeface.familyName is hidden from the SDK stubs. Vendor builds include the
    // resolved family in toString(); unlike a brand check this does not mislabel fallback fonts.
    val available: Boolean by lazy {
        candidate.toString().contains("NDot", ignoreCase = true)
    }

    val typeface: Typeface get() = if (available) candidate else Typeface.MONOSPACE
    val family: FontFamily by lazy { FontFamily(typeface) }
}
