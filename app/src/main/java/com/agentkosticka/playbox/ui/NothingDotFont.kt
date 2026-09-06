package com.agentkosticka.playbox.ui

import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.text.font.FontFamily

/** Nothing OS exposes NDot57 on Nothing devices. Other phones safely use the existing mono face. */
object NothingDotFont {
    private val candidate: Typeface by lazy { Typeface.create("NDot57", Typeface.NORMAL) }
    // Typeface.familyName is hidden from the Kotlin SDK stubs; Android includes the
    // resolved family in Typeface.toString(), which also works on vendor builds.
    val available: Boolean by lazy {
        candidate.toString().contains("NDot", ignoreCase = true) ||
            (Build.MANUFACTURER.equals("Nothing", true) || Build.BRAND.equals("Nothing", true))
    }
    val typeface: Typeface get() = if (available) candidate else Typeface.MONOSPACE
    val family: FontFamily by lazy { FontFamily(typeface) }
}
