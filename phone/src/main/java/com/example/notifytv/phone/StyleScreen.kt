package com.example.notifytv.phone

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SWATCHES = listOf(
    0x202020, 0x000000, 0x37474F, 0x1565C0, 0x00838F, 0x2E7D32,
    0x6A1B9A, 0xAD1457, 0xC62828, 0xEF6C00, 0xF9A825, 0xFFFFFF
)

private const val SHORT_TEXT = "See you at 8!"
private const val LONG_TEXT = "Hey! Are we still on for tonight? I'll bring snacks and the new board game everyone keeps talking about."

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StyleScreen(style: NotifStyle, onChange: (NotifStyle) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var replay by remember { mutableIntStateOf(0) }
    var longSample by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification style") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { replay++ }) { Icon(Icons.Filled.PlayArrow, "Replay animation") }
                    IconButton(onClick = {
                        TvSender.sendTest(ctx) { ok ->
                            scope.launch { snackbar.showSnackbar(if (ok) "Sent to your TV" else "Can't reach the TV") }
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.Send, "Send test to TV") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TvPreview(
                style = style,
                replay = replay,
                longSample = longSample,
                onCorner = { onChange(style.copy(corner = it)) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Preview:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilterChip(selected = !longSample, onClick = { longSample = false }, label = { Text("Short message") })
                FilterChip(selected = longSample, onClick = { longSample = true }, label = { Text("Long message") })
            }
            Text(
                "Tap a corner of the TV to move the notification. ▶ replays the animation, ➤ sends a test to the TV.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Section("Timing & animation") {
                    SliderRow("Show for", style.durationSec, 2..30, unit = " s") { onChange(style.copy(durationSec = it)) }
                    Text("Animation", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Anim.entries.forEach { a ->
                            FilterChip(selected = style.anim == a, onClick = { onChange(style.copy(anim = a)) }, label = { Text(a.label) })
                        }
                    }
                    SliderRow("Animation speed", style.animMs, 150..1000, step = 50, unit = " ms") { onChange(style.copy(animMs = it)) }
                }

                Section("Size & position") {
                    SwitchRow("Automatic width", style.autoWidth) { onChange(style.copy(autoWidth = it)) }
                    AnimatedVisibility(style.autoWidth) {
                        Text(
                            "Fits the text, up to the maximum width. Longer text wraps onto new lines.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    SliderRow(if (style.autoWidth) "Maximum width" else "Width", style.widthDp, 200..800, step = 10, unit = " dp") {
                        onChange(style.copy(widthDp = it))
                    }
                    SwitchRow("Automatic height", style.heightDp == 0) { auto -> onChange(style.copy(heightDp = if (auto) 0 else 120)) }
                    AnimatedVisibility(style.heightDp > 0) {
                        SliderRow("Height", style.heightDp.coerceAtLeast(60), 60..400, step = 5, unit = " dp") { onChange(style.copy(heightDp = it)) }
                    }
                    SliderRow("Distance from edge", style.marginDp, 0..120, step = 2, unit = " dp") { onChange(style.copy(marginDp = it)) }
                    SliderRow("Corner roundness", style.radiusDp, 0..48, unit = " dp") { onChange(style.copy(radiusDp = it)) }
                }

                Section("Text") {
                    SwitchRow("Show app name", style.showAppName) { onChange(style.copy(showAppName = it)) }
                    AnimatedVisibility(style.showAppName) {
                        SliderRow("App name size", style.appNameSp, 8..28, unit = " sp") { onChange(style.copy(appNameSp = it)) }
                    }
                    SliderRow("Title size", style.titleSp, 10..40, unit = " sp") { onChange(style.copy(titleSp = it)) }
                    SliderRow("Message size", style.textSp, 8..36, unit = " sp") { onChange(style.copy(textSp = it)) }
                    SliderRow("Max message lines", style.bodyLines, 1..10) { onChange(style.copy(bodyLines = it)) }
                }

                Section("Icon") {
                    SwitchRow("Show app icon", style.showIcon) { onChange(style.copy(showIcon = it)) }
                    AnimatedVisibility(style.showIcon) {
                        SliderRow("Icon size", style.iconDp, 16..120, step = 2, unit = " dp") { onChange(style.copy(iconDp = it)) }
                    }
                }

                Section("Background") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        SWATCHES.forEach { rgb -> Swatch(rgb, style.color == rgb) { onChange(style.copy(color = rgb)) } }
                    }
                    SliderRow("Opacity", style.opacity, 20..100, step = 5, unit = " %") { onChange(style.copy(opacity = it)) }
                }

                TextButton(onClick = { onChange(NotifStyle()) }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp)) {
                    Icon(Icons.Filled.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reset to defaults")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun Swatch(rgb: Int, selected: Boolean, onClick: () -> Unit) {
    val c = Color(0xFF000000.toInt() or rgb)
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(c)
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = if (c.luminance() > 0.5f) Color.Black else Color.White)
    }
}

@Composable
fun TvPreview(style: NotifStyle, replay: Int, longSample: Boolean, onCorner: (Corner) -> Unit, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(replay, style.anim) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(style.animMs, easing = LinearOutSlowInEasing))
        if (replay > 0) {
            delay(1200)
            progress.animateTo(0f, tween(style.animMs, easing = FastOutLinearInEasing))
            delay(300)
            progress.animateTo(1f, tween(style.animMs, easing = LinearOutSlowInEasing))
        }
    }

    val shape = RoundedCornerShape(10.dp)
    BoxWithConstraints(
        modifier.fillMaxWidth().aspectRatio(16f / 9f)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF0D47A1), Color(0xFF4A148C), Color(0xFF880E4F))))
            .border(3.dp, Color(0xFF101010), shape)
    ) {
        val scale = maxWidth.value / 960f
        val density = LocalDensity.current

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

        val align = when (style.corner) {
            Corner.TOP_LEFT -> Alignment.TopStart
            Corner.TOP_RIGHT -> Alignment.TopEnd
            Corner.BOTTOM_LEFT -> Alignment.BottomStart
            Corner.BOTTOM_RIGHT -> Alignment.BottomEnd
        }
        val marginPx = with(density) { (style.marginDp * scale).dp.toPx() }
        val isTop = style.corner.isTop
        val isRight = style.corner.isRight

        PreviewCard(
            style, scale, if (longSample) LONG_TEXT else SHORT_TEXT,
            Modifier.align(align).padding((style.marginDp * scale).dp).graphicsLayer {
                val p = progress.value
                when (style.anim) {
                    Anim.SLIDE -> translationX = (1f - p) * (size.width + marginPx + 8f) * (if (isRight) 1f else -1f)
                    Anim.DROP -> translationY = (1f - p) * (size.height + marginPx + 8f) * (if (isTop) -1f else 1f)
                    Anim.FADE -> alpha = p
                    Anim.POP -> {
                        alpha = p
                        scaleX = 0.8f + 0.2f * p
                        scaleY = 0.8f + 0.2f * p
                    }
                }
            }
        )
    }
}

@Composable
private fun PreviewCard(style: NotifStyle, scale: Float, body: String, modifier: Modifier) {
    val rgb = Color(0xFF000000.toInt() or style.color)
    val bg = rgb.copy(alpha = style.opacity / 100f)
    val fg = if (rgb.luminance() > 0.5f) Color.Black else Color.White
    val shape = RoundedCornerShape((style.radiusDp * scale).dp)
    val widthMod = if (style.autoWidth) Modifier.widthIn(max = (style.widthDp * scale).dp) else Modifier.width((style.widthDp * scale).dp)
    val heightMod = if (style.heightDp > 0) Modifier.height((style.heightDp * scale).dp) else Modifier

    Row(
        modifier.then(widthMod).then(heightMod)
            .shadow((8 * scale).dp, shape)
            .clip(shape)
            .background(bg)
            .padding(horizontal = (16 * scale).dp, vertical = (14 * scale).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (style.showIcon) {
            Image(
                painterResource(R.drawable.ic_app), null,
                Modifier.size((style.iconDp * scale).dp).clip(RoundedCornerShape((style.iconDp * scale / 4).dp))
            )
            Spacer(Modifier.width((14 * scale).dp))
        }
        Column {
            if (style.showAppName) PText("Messages", style.appNameSp, scale, fg.copy(alpha = 0.7f))
            PText("Alex", style.titleSp, scale, fg, bold = true, lines = 2)
            PText(body, style.textSp, scale, fg, lines = style.bodyLines)
        }
    }
}

@Composable
private fun PText(text: String, sizeSp: Int, scale: Float, color: Color, bold: Boolean = false, lines: Int = 1) {
    Text(
        text,
        color = color,
        fontSize = (sizeSp * scale).sp,
        lineHeight = (sizeSp * scale * 1.2f).sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis
    )
}
