package com.example.notifytv.phone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppItem(val pkg: String, val label: String, val icon: ImageBitmap)

class MainActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { SettingsScreen(resumeTick.intValue) } } }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }
}

private val CORNERS = listOf("TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT")
private val COLORS = listOf(0xE6202020, 0xE61565C0, 0xE62E7D32, 0xE6C62828, 0xE66A1B9A, 0xF2FFFFFF).map { it.toInt() }

fun loadApps(c: Context): List<AppItem> {
    val pm = c.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != c.packageName }
        .map { AppItem(it.packageName, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it).toBitmap(64, 64).asImageBitmap()) }
        .sortedBy { it.label.lowercase() }
}

private fun prettyCorner(c: String) = c.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

@Composable
fun SettingsScreen(resumeTick: Int) {
    val ctx = LocalContext.current
    var host by remember { mutableStateOf(Prefs.host(ctx)) }
    var status by remember { mutableStateOf("") }
    var corner by remember { mutableStateOf(Prefs.corner(ctx)) }
    var duration by remember { mutableFloatStateOf(Prefs.durationSec(ctx).toFloat()) }
    var bg by remember { mutableIntStateOf(Prefs.bgColor(ctx)) }
    var enabled by remember { mutableStateOf(Prefs.enabledApps(ctx)) }
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(ctx) } }

    val hasAccess = remember(resumeTick) {
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    }

    fun test() {
        status = "Connecting..."
        TvSender.sendNotification(ctx, ctx.packageName, "Hello from your phone", "This is a test notification") { ok ->
            status = if (ok) "Connected to TV" else "Can't reach TV"
        }
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { r ->
        val contents = r.contents
        if (contents != null) {
            val uri = Uri.parse(contents)
            val h = uri.host
            if (uri.scheme == "notifytv" && h != null) {
                Prefs.setTarget(ctx, h, if (uri.port > 0) uri.port else 47321, uri.getQueryParameter("t").orEmpty())
                host = h
                test()
            } else {
                status = "Not a NotifyTV QR code"
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("NotifyTV", style = MaterialTheme.typography.headlineMedium)
            Text(if (host != null) "TV: $host   $status" else "No TV paired. $status")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scanner.launch(
                        ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Scan the QR code on your TV")
                            .setBeepEnabled(false)
                            .setOrientationLocked(false)
                    )
                }) { Text("Scan TV QR") }
                OutlinedButton(onClick = { test() }, enabled = host != null) { Text("Send test") }
            }
            if (!hasAccess) {
                Button(
                    onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Grant notification access") }
            }
        }

        item {
            Text("Corner", style = MaterialTheme.typography.titleMedium)
            CORNERS.chunked(2).forEach { row ->
                Row {
                    row.forEach { c ->
                        Row(
                            Modifier.weight(1f).clickable { corner = c; Prefs.setCorner(ctx, c) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = corner == c, onClick = { corner = c; Prefs.setCorner(ctx, c) })
                            Text(prettyCorner(c))
                        }
                    }
                }
            }
        }

        item {
            Text("Duration: ${duration.toInt()} s", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = duration,
                onValueChange = { duration = it },
                valueRange = 2f..30f,
                steps = 27,
                onValueChangeFinished = { Prefs.setDurationSec(ctx, duration.toInt()) }
            )
        }

        item {
            Text("Background", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                COLORS.forEach { col ->
                    val selected = bg == col
                    Box(
                        Modifier.size(40.dp)
                            .background(Color(col), CircleShape)
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
                            .clickable { bg = col; Prefs.setBgColor(ctx, col) }
                    )
                }
            }
        }

        item { Text("Apps to forward", style = MaterialTheme.typography.titleMedium) }

        items(apps, key = { it.pkg }) { app ->
            val checked = app.pkg in enabled
            val toggle = {
                enabled = if (checked) enabled - app.pkg else enabled + app.pkg
                Prefs.setEnabledApps(ctx, enabled)
            }
            Row(Modifier.fillMaxWidth().clickable { toggle() }, verticalAlignment = Alignment.CenterVertically) {
                Image(app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Text(app.label, Modifier.weight(1f))
                Checkbox(checked = checked, onCheckedChange = { toggle() })
            }
        }
    }
}
