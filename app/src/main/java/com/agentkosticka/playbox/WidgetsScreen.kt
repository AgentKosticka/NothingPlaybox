package com.agentkosticka.playbox

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.agentkosticka.playbox.ui.Muted
import com.agentkosticka.playbox.ui.NothingDotFont
import com.agentkosticka.playbox.widget.TimeBarsRenderer
import com.agentkosticka.playbox.widget.TimeBarsWidget
import com.agentkosticka.playbox.widget.TimeBarsSettings
import com.agentkosticka.playbox.widget.BarFill
import com.agentkosticka.playbox.widget.DashboardRenderer
import com.agentkosticka.playbox.widget.DayDialWidget
import com.agentkosticka.playbox.widget.BatteryDotsWidget
import com.agentkosticka.playbox.widget.batteryStatus
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

@Composable
fun WidgetsScreen() {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    var status by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(TimeBarsSettings.load(context)) }
    var weekMenu by remember { mutableStateOf(false) }
    var widgetType by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("Time Bars") }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(60_000)
        }
    }
    val preview = remember(now, settings) { TimeBarsRenderer.render(now, settings).asImageBitmap() }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Time Bars", "Day Dial", "Battery Dots").forEach { name ->
                FilterChip(widgetType == name, { widgetType = name }, label = { Text(name) })
            }
        }
        if (widgetType != "Time Bars") {
            val battery = remember(now) { batteryStatus(context) }
            val picture = remember(now, widgetType) {
                if (widgetType == "Day Dial") DashboardRenderer.dayDial(now) else DashboardRenderer.battery(battery.first, battery.second)
            }
            Image(picture.asImageBitmap(), "$widgetType preview", Modifier.size(200.dp).align(Alignment.CenterHorizontally))
            Text("${widgetType.uppercase(Locale.ROOT)} / 2 × 2", fontFamily = FontFamily.Monospace)
            Text(if (widgetType == "Day Dial") "Watch today fill a ring of 60 dots, with the weekday and date at its heart."
                else "One dot for each battery percent, with a clear charging indicator.", color = Muted)
            Text("Refreshes about every 15 minutes. Battery saving may delay updates.", color = Muted)
            Button(onClick = {
                val provider = if (widgetType == "Day Dial") DayDialWidget::class.java else BatteryDotsWidget::class.java
                val manager = AppWidgetManager.getInstance(context)
                status = runCatching {
                    if (manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(ComponentName(context, provider), null, null)) "Finish adding the widget in your launcher."
                    else "Long-press your home screen → Widgets → Nothing Playbox → $widgetType."
                }.getOrDefault("Long-press your home screen → Widgets → Nothing Playbox → $widgetType.")
            }, modifier = Modifier.fillMaxWidth()) { Text("ADD TO HOME SCREEN") }
            status?.let { Text(it, color = Muted) }
            return@Column
        }
        Image(preview, "Live preview of four dotted time progress bars", Modifier.fillMaxWidth().aspectRatio(2f))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("TIME BARS / 4 × 2", fontFamily = NothingDotFont.family)
                Text("WEEK STARTS ON", fontFamily = NothingDotFont.family)
                Box {
                    OutlinedButton(onClick = { weekMenu = true }) {
                        Text(settings.weekStart.getDisplayName(TextStyle.FULL, locale))
                    }
                    DropdownMenu(expanded = weekMenu, onDismissRequest = { weekMenu = false }) {
                        DayOfWeek.entries.forEach { day ->
                            DropdownMenuItem(text = { Text(day.getDisplayName(TextStyle.FULL, locale)) }, onClick = {
                                settings = settings.copy(weekStart = day)
                                settings.save(context)
                                weekMenu = false
                            })
                        }
                    }
                }
                Text("FILL STYLE", fontFamily = NothingDotFont.family)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BarFill.entries.forEach { fill ->
                        FilterChip(selected = settings.fill == fill, onClick = {
                            settings = settings.copy(fill = fill)
                            settings.save(context)
                        }, label = { Text(fill.label) })
                    }
                }
                Text(if (settings.fill == BarFill.DENSITY) "Scattered dots build up in a fixed pattern as each period passes." else "Dots fill ${settings.fill.label.lowercase(Locale.ROOT)} as each period passes.", color = Muted)
                Text("Applies to all Time Bars widgets immediately. Refreshes about every 15 minutes; battery saving may delay updates.", color = Muted)
                Button(onClick = {
                    val manager = AppWidgetManager.getInstance(context)
                    status = if (manager.isRequestPinAppWidgetSupported) {
                        runCatching {
                            if (manager.requestPinAppWidget(ComponentName(context, TimeBarsWidget::class.java), null, null))
                                "Finish adding the widget in your launcher."
                            else "Long-press your home screen, open Widgets, then choose Nothing Playbox → Time bars."
                        }.getOrDefault("Long-press your home screen, open Widgets, then choose Nothing Playbox → Time bars.")
                    } else "Long-press your home screen, open Widgets, then choose Nothing Playbox → Time bars."
                }, modifier = Modifier.fillMaxWidth()) { Text("ADD TO HOME SCREEN") }
                status?.let { Text(it, color = Muted) }
            }
        }
    }
}
