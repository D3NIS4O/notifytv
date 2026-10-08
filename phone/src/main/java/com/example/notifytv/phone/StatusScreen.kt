package com.example.notifytv.phone

import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatusScreen(settings: StatusSettings, onChange: (StatusSettings) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s = settings
    val latest by rememberUpdatedState(settings)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var cityInput by remember { mutableStateOf(settings.city) }
    var searching by remember { mutableStateOf(false) }
    var weather by remember { mutableStateOf<Weather.Now?>(null) }

    LaunchedEffect(s.lat, s.lon, s.fahrenheit) {
        weather = if (s.hasPlace) withContext(Dispatchers.IO) { Weather.current(s.lat, s.lon, s.fahrenheit) } else null
    }

    fun findCity() {
        val q = cityInput.trim()
        if (q.isEmpty() || searching) return
        searching = true
        scope.launch {
            val place = withContext(Dispatchers.IO) { Weather.geocode(q) }
            searching = false
            if (place == null) {
                snackbar.showSnackbar("Couldn't find \"$q\"")
            } else {
                cityInput = place.name
                onChange(latest.copy(city = place.name, lat = place.lat, lon = place.lon))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clock & status") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        StatusReporter.sync(ctx) { ok ->
                            scope.launch { snackbar.showSnackbar(if (ok) "Sent to your TV" else "Can't reach the TV") }
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.Send, "Send to TV now") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            StatusPreview(
                s = s,
                weather = weather,
                onCorner = { onChange(s.copy(corner = it)) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                "Tap a corner of the TV to move it. The clock uses the TV's own time; battery and weather come from this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Section("General") {
                    SwitchRow("Show on TV", s.enabled) { onChange(s.copy(enabled = it)) }
                    Text(
                        "Stays on screen on top of whatever is playing and hides while a notification popup is showing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text("Corner", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Corner.entries.forEach { c ->
                            FilterChip(selected = s.corner == c, onClick = { onChange(s.copy(corner = c)) }, label = { Text(c.label) })
                        }
                    }
                }

                Section("Show") {
                    SwitchRow("Clock", s.showClock) { onChange(s.copy(showClock = it)) }
                    AnimatedVisibility(s.showClock) {
                        SwitchRow("24-hour time", s.clock24h) { onChange(s.copy(clock24h = it)) }
                    }
                    SwitchRow("Phone battery", s.showBattery) { onChange(s.copy(showBattery = it)) }
                    SwitchRow("Weather", s.showWeather) { onChange(s.copy(showWeather = it)) }
                }

                if (s.showWeather) {
                    Section("Weather") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = cityInput,
                                onValueChange = { cityInput = it },
                                label = { Text("City") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { findCity() }),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            if (searching) {
                                CircularProgressIndicator(Modifier.size(24.dp))
                            } else {
                                IconButton(onClick = { findCity() }) { Icon(Icons.Filled.Search, "Find city") }
                            }
                        }
                        val w = weather
                        Text(
                            when {
                                !s.hasPlace -> "Type your city and tap search."
                                w != null -> "${s.city}: ${w.icon} ${w.rounded}\u00b0${if (s.fahrenheit) "F" else "C"}"
                                else -> s.city
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !s.fahrenheit, onClick = { onChange(s.copy(fahrenheit = false)) }, label = { Text("\u00b0C") })
                            FilterChip(selected = s.fahrenheit, onClick = { onChange(s.copy(fahrenheit = true)) }, label = { Text("\u00b0F") })
                        }
                    }
                }

                Section("Layout") {
                    SliderRow("Distance from edge", s.marginDp, 0..120, step = 2, unit = " dp") { onChange(s.copy(marginDp = it)) }
                    SliderRow("Space between items", s.gapDp, 0..40, unit = " dp") { onChange(s.copy(gapDp = it)) }
                }

                if (s.showClock) {
                    Section("Clock") {
                        SliderRow("Font size", s.clockSp, 8..60, unit = " sp") { onChange(s.copy(clockSp = it)) }
                        SwitchRow("Bold", s.clockBold) { onChange(s.copy(clockBold = it)) }
                    }
                }

                if (s.showWeather) {
                    BadgeStyleSection("Weather badge", s.weatherStyle, BadgeStyle.WEATHER) { onChange(latest.copy(weatherStyle = it)) }
                }
                if (s.showBattery) {
                    BadgeStyleSection("Battery badge", s.batteryStyle, BadgeStyle.BATTERY) { onChange(latest.copy(batteryStyle = it)) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StatusPreview(s: StatusSettings, weather: Weather.Now?, onCorner: (Corner) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val bm = remember { ctx.getSystemService(BatteryManager::class.java) }
    val tick = now / 30_000
    val level = remember(tick) { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100) }
    val charging = remember(tick) { bm.isCharging }
    val time = SimpleDateFormat(if (s.clock24h) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now))

    val shape = RoundedCornerShape(10.dp)
    BoxWithConstraints(
        modifier.fillMaxWidth().aspectRatio(16f / 9f)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF0D47A1), Color(0xFF4A148C), Color(0xFF880E4F))))
            .border(3.dp, Color(0xFF101010), shape)
    ) {
        val scale = maxWidth.value / 960f
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.weight(1f).fillMaxHeight().clickable { onCorner(Corner.TOP_LEFT) })
                Box(Modifier.weight(1f).fillMaxHeight().clickable { onCorner(Corner.TOP_RIGHT) })
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.weight(1f).fillMaxHeight().clickable { onCorner(Corner.BOTTOM_LEFT) })
                Box(Modifier.weight(1f).fillMaxHeight().clickable { onCorner(Corner.BOTTOM_RIGHT) })
            }
        }
        val align = when (s.corner) {
            Corner.TOP_LEFT -> Alignment.TopStart
            Corner.TOP_RIGHT -> Alignment.TopEnd
            Corner.BOTTOM_LEFT -> Alignment.BottomStart
            Corner.BOTTOM_RIGHT -> Alignment.BottomEnd
        }
        if (s.enabled && (s.showClock || s.showBattery || s.showWeather)) {
            StatusPill(s, scale, time, level, charging, weather, Modifier.align(align).padding((s.marginDp * scale).dp))
        } else {
            Text("Off", Modifier.align(Alignment.Center), color = Color.White.copy(alpha = 0.7f))
        }
    }
}

/** Sliders for one badge's look; the same controls are used for weather and battery. */
@Composable
private fun BadgeStyleSection(title: String, st: BadgeStyle, defaults: BadgeStyle, onChange: (BadgeStyle) -> Unit) {
    Section(title) {
        SliderRow("Font size", st.textSp, 6..48, unit = " sp") { onChange(st.copy(textSp = it)) }
        SwitchRow("Bold text", st.bold) { onChange(st.copy(bold = it)) }
        SliderRow("Icon size", st.iconDp, 8..60, unit = " dp") { onChange(st.copy(iconDp = it)) }
        SliderRow("Height", st.heightDp, 14..90, unit = " dp") { onChange(st.copy(heightDp = it)) }
        SliderRow("Side padding", st.paddingDp, 0..40, unit = " dp") { onChange(st.copy(paddingDp = it)) }
        SliderRow("Corner roundness", st.radiusPct, 0..100, step = 5, unit = " %") { onChange(st.copy(radiusPct = it)) }
        SliderRow(
            "Border weight", st.borderTenths, 0..60,
            format = { if (it == 0) "None" else "%.1f dp".format(it / 10f) }
        ) { onChange(st.copy(borderTenths = it)) }
        SliderRow("Background opacity", st.opacity, 0..100, step = 5, unit = " %") { onChange(st.copy(opacity = it)) }
        TextButton(onClick = { onChange(defaults) }, enabled = st != defaults, modifier = Modifier.align(Alignment.End)) {
            Text("Reset")
        }
    }
}

/** Mirrors the TV: weather badge, battery badge and the clock, with the clock closest to the chosen corner. */
@Composable
private fun StatusPill(
    s: StatusSettings, scale: Float, time: String, level: Int, charging: Boolean, weather: Weather.Now?, modifier: Modifier
) {
    val badges = buildList {
        if (s.showWeather) add("weather")
        if (s.showBattery) add("battery")
    }
    val parts = when {
        !s.showClock -> badges
        s.corner.isRight -> badges + "clock"
        else -> listOf("clock") + badges
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { i, p ->
            if (i > 0) {
                val nearClock = p == "clock" || parts[i - 1] == "clock"
                Spacer(Modifier.width((s.gapDp * scale * if (nearClock) 1.6f else 1f).dp))
            }
            when (p) {
                "weather" -> {
                    val st = s.weatherStyle
                    Badge(st, scale) {
                        val kind = weather?.let { StatusIcons.weatherFor(it.code, it.isDay) } ?: StatusIcons.Weather.CLOUDY
                        WeatherIcon(kind, Modifier.size((st.iconDp * scale).dp))
                        Spacer(Modifier.width((st.iconDp * 0.3f * scale).dp))
                        BadgeText(if (weather != null) "${weather.rounded}\u00b0" else "\u2014\u00b0", st.textSp * scale, st.bold)
                    }
                }
                "battery" -> {
                    val st = s.batteryStyle
                    Badge(st, scale) {
                        val ih = st.iconDp * scale
                        BatteryIcon(level, Modifier.size(width = (ih * 0.55f).dp, height = ih.dp))
                        if (charging) {
                            Spacer(Modifier.width((ih * 0.12f).dp))
                            BoltIcon(Modifier.size(width = (ih * 0.42f).dp, height = (ih * 0.72f).dp))
                        }
                        Spacer(Modifier.width((st.iconDp * 0.3f * scale).dp))
                        BadgeText("$level%", st.textSp * scale, st.bold)
                    }
                }
                else -> BadgeText(time, s.clockSp * scale, s.clockBold)
            }
        }
    }
}

@Composable
private fun Badge(st: BadgeStyle, scale: Float, content: @Composable RowScope.() -> Unit) {
    // RoundedCornerShape's percent is of the shorter side, so 50 % = full pill.
    val shape = RoundedCornerShape(percent = st.radiusPct.coerceIn(0, 100) / 2)
    var m = Modifier.height((st.heightDp * scale).dp)
        .clip(shape)
        .background(Color.Black.copy(alpha = st.opacity.coerceIn(0, 100) / 100f))
    if (st.borderTenths > 0) m = m.border((st.borderTenths / 10f * scale).coerceAtLeast(0.4f).dp, Color.White, shape)
    Row(
        m.padding(horizontal = (st.paddingDp * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun BadgeText(text: String, sizeDp: Float, bold: Boolean) {
    Text(
        text, color = Color.White, fontSize = sizeDp.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, maxLines = 1
    )
}

@Composable
private fun WeatherIcon(kind: StatusIcons.Weather, modifier: Modifier) {
    Canvas(modifier) {
        drawIntoCanvas {
            StatusIcons.drawWeather(it.nativeCanvas, kind, 0f, 0f, minOf(size.width, size.height), android.graphics.Color.WHITE)
        }
    }
}

@Composable
private fun BatteryIcon(level: Int, modifier: Modifier) {
    Canvas(modifier) {
        drawIntoCanvas {
            StatusIcons.drawBattery(it.nativeCanvas, 0f, 0f, size.width, size.height, level, android.graphics.Color.WHITE)
        }
    }
}

@Composable
private fun BoltIcon(modifier: Modifier) {
    Canvas(modifier) {
        drawIntoCanvas {
            StatusIcons.drawBolt(it.nativeCanvas, 0f, 0f, size.width, size.height, android.graphics.Color.WHITE)
        }
    }
}
