package com.example.notifytv.phone

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

private enum class Conn { NONE, CHECKING, OK, FAIL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(resumeTick: Int, style: NotifStyle, appCount: Int, onOpenStyle: () -> Unit, onOpenApps: () -> Unit) {
    val ctx = LocalContext.current
    var host by remember { mutableStateOf(Prefs.host(ctx)) }
    var conn by remember { mutableStateOf(Conn.NONE) }
    var forwarding by remember { mutableStateOf(Prefs.forwarding(ctx)) }
    val hasAccess = remember(resumeTick) {
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    }

    LaunchedEffect(resumeTick, host) {
        if (host == null) {
            conn = Conn.NONE
        } else {
            conn = Conn.CHECKING
            TvSender.ping(ctx) { ok -> conn = if (ok) Conn.OK else Conn.FAIL }
        }
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { r ->
        val contents = r.contents ?: return@rememberLauncherForActivityResult
        val uri = Uri.parse(contents)
        val h = uri.host
        if (uri.scheme == "notifytv" && h != null) {
            Prefs.setTarget(ctx, h, if (uri.port > 0) uri.port else 47321, uri.getQueryParameter("t").orEmpty())
            host = h
            conn = Conn.CHECKING
            TvSender.sendTest(ctx) { ok -> conn = if (ok) Conn.OK else Conn.FAIL }
        } else {
            Toast.makeText(ctx, "That isn't a NotifyTV QR code", Toast.LENGTH_SHORT).show()
        }
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeTopAppBar(title = { Text("NotifyTV") }, scrollBehavior = scroll) }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectionCard(
                host = host,
                conn = conn,
                onScan = {
                    scanner.launch(
                        ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Point at the QR code on your TV")
                            .setBeepEnabled(false)
                            .setOrientationLocked(false)
                    )
                },
                onTest = {
                    conn = Conn.CHECKING
                    TvSender.sendTest(ctx) { ok -> conn = if (ok) Conn.OK else Conn.FAIL }
                }
            )

            if (!hasAccess) {
                AccessCard(
                    onGrant = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    onAppInfo = {
                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                    }
                )
            }

            Text(
                "Settings",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )
            ElevatedCard(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Forward notifications") },
                    supportingContent = { Text(if (forwarding) "On" else "Paused") },
                    leadingContent = { IconBadge(rememberVectorPainter(Icons.Filled.Notifications)) },
                    trailingContent = {
                        Switch(checked = forwarding, onCheckedChange = { forwarding = it; Prefs.setForwarding(ctx, it) })
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                NavItem(
                    painterResource(R.drawable.ic_palette),
                    "Notification style",
                    "${style.corner.label} · ${style.widthDp} dp · ${style.durationSec} s · ${style.anim.label}",
                    onOpenStyle
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                NavItem(
                    painterResource(R.drawable.ic_apps),
                    "Apps",
                    if (appCount == 0) "None selected yet, tap to choose" else "$appCount app${if (appCount == 1) "" else "s"} forwarded",
                    onOpenApps
                )
            }

            Text(
                "Keep the phone and the TV on the same WiFi network. Open NotifyTV on the TV once so it starts listening.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConnectionCard(host: String?, conn: Conn, onScan: () -> Unit, onTest: () -> Unit) {
    val title: String
    val dot: Color
    when (conn) {
        Conn.NONE -> { title = "No TV paired"; dot = MaterialTheme.colorScheme.outline }
        Conn.CHECKING -> { title = "Checking…"; dot = MaterialTheme.colorScheme.tertiary }
        Conn.OK -> { title = "Connected"; dot = Color(0xFF43A047) }
        Conn.FAIL -> { title = "Can't reach TV"; dot = MaterialTheme.colorScheme.error }
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painterResource(R.drawable.ic_tv), null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
                        Spacer(Modifier.width(8.dp))
                        Text(title, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        host ?: "Open NotifyTV on your TV and scan its QR code",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onScan, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_qr), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (host == null) "Scan QR" else "Re-pair")
                }
                FilledTonalButton(onClick = onTest, enabled = host != null, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Test")
                }
            }
        }
    }
}

@Composable
private fun AccessCard(onGrant: () -> Unit, onAppInfo: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(12.dp))
                Text("Notification access needed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "NotifyTV needs to read your notifications to send them to the TV. If the switch is greyed out, open App info, tap ⋮ and choose Allow restricted settings first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onGrant,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                ) { Text("Grant access") }
                TextButton(onClick = onAppInfo) { Text("App info", color = MaterialTheme.colorScheme.onErrorContainer) }
            }
        }
    }
}

@Composable
private fun NavItem(icon: Painter, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { IconBadge(icon) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
