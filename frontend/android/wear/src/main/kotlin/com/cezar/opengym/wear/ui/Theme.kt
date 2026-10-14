package com.cezar.opengym.wear.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

/** The watch's look, from the approved mockups (specs/fork-watch, design §8): black, one green. */
object Ui {
    val accent = Color(0xFF30D158)
    val onAccent = Color(0xFF04150A)
    val surface = Color(0xFF22262B)
    val row = Color(0xFF1B1F23)
    val outline = Color(0xFF33383E)
    val dim = Color(0xFFB4B9BF)
    val dot = Color(0xFF5B6168)
    val rowOn = Color(0xFF102017)
}

/** The page dots at the top: the current one long and white. */
@Composable
fun PageDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(
                Modifier.width(if (i == current) 7.dp else 3.dp).height(3.dp)
                    .background(if (i == current) Color.White else Ui.dot, RoundedCornerShape(2.dp)),
            )
        }
    }
}

/**
 * The watch speaks the app's language, not its own system's (settings.lang from the phone, as
 * upstream's Apple Watch does): "pt" is Brazilian Portuguese here; anything else falls back to
 * the watch's own language when the app has no strings for it.
 */
@Composable
fun InAppLanguage(lang: String?, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val locale = remember(lang) {
        when {
            lang.isNullOrBlank() -> null
            lang.startsWith("pt") -> Locale.forLanguageTag("pt-BR")
            else -> Locale.forLanguageTag(lang)
        }
    }
    if (locale == null) { content(); return }
    val ctx: Context = remember(base, locale) {
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(locale)
        base.createConfigurationContext(cfg)
    }
    CompositionLocalProvider(LocalContext provides ctx, LocalConfiguration provides ctx.resources.configuration) { content() }
}
