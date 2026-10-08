package com.example.notifytv.phone

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [system] = no launcher icon (System UI, Phone services, etc.); hidden in the app list unless "System apps" is on. */
data class AppItem(val pkg: String, val label: String, val icon: ImageBitmap, val system: Boolean = false)

enum class Screen { HOME, STYLE, STATUS, APPS }

class MainActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotifyTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(resumeTick.intValue)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }
}

/**
 * Every installed app: normal (launchable) apps plus system apps without a launcher icon, such as System UI,
 * which posts e.g. the "Screenshot saved" notification on many phones.
 */
fun loadApps(c: Context): List<AppItem> {
    val pm = c.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val launchable = pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
    @Suppress("DEPRECATION")
    val all = runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
    val launcherInfos = pm.queryIntentActivities(intent, 0).map { it.activityInfo.applicationInfo }
    return (all + launcherInfos)
        .distinctBy { it.packageName }
        .filter { it.packageName != c.packageName }
        .mapNotNull { info ->
            runCatching {
                AppItem(
                    pkg = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = pm.getApplicationIcon(info).toBitmap(96, 96).asImageBitmap(),
                    system = info.packageName !in launchable,
                )
            }.getOrNull()
        }
        .sortedBy { it.label.lowercase() }
}

@Composable
fun App(resumeTick: Int) {
    val ctx = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var style by remember { mutableStateOf(Prefs.style(ctx)) }
    var enabledApps by remember { mutableStateOf(Prefs.enabledApps(ctx)) }
    var status by remember { mutableStateOf(Prefs.status(ctx)) }
    var apps by remember { mutableStateOf<List<AppItem>?>(null) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(ctx) } }
    LaunchedEffect(Unit) { StatusReporter.sync(ctx) }

    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            if (targetState != Screen.HOME) {
                (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
            }
        },
        label = "nav"
    ) { target ->
        when (target) {
            Screen.HOME -> HomeScreen(
                resumeTick = resumeTick,
                style = style,
                appCount = enabledApps.size,
                status = status,
                onOpenStyle = { screen = Screen.STYLE },
                onOpenStatus = { screen = Screen.STATUS },
                onOpenApps = { screen = Screen.APPS }
            )
            Screen.STYLE -> StyleScreen(
                style = style,
                onChange = { style = it; Prefs.setStyle(ctx, it) },
                onBack = { screen = Screen.HOME }
            )
            Screen.STATUS -> StatusScreen(
                settings = status,
                onChange = { status = it; Prefs.setStatus(ctx, it); StatusReporter.sync(ctx) },
                onBack = { screen = Screen.HOME }
            )
            Screen.APPS -> AppsScreen(
                apps = apps,
                enabled = enabledApps,
                onToggle = { pkg, on ->
                    enabledApps = if (on) enabledApps + pkg else enabledApps - pkg
                    Prefs.setEnabledApps(ctx, enabledApps)
                },
                onSetAll = { pkgs, on ->
                    enabledApps = if (on) enabledApps + pkgs else enabledApps - pkgs.toSet()
                    Prefs.setEnabledApps(ctx, enabledApps)
                },
                onBack = { screen = Screen.HOME }
            )
        }
    }
}
