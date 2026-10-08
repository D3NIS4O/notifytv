package com.example.notifytv.phone

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    apps: List<AppItem>?,
    enabled: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onSetAll: (Collection<String>, Boolean) -> Unit,
    onBack: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var onlySelected by rememberSaveable { mutableStateOf(false) }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    val shown = remember(apps, query, onlySelected, showSystem, enabled) {
        apps.orEmpty().filter { a ->
            (query.isBlank() || a.label.contains(query, ignoreCase = true) || a.pkg.contains(query, ignoreCase = true)) &&
                (!onlySelected || a.pkg in enabled) &&
                (showSystem || !a.system || a.pkg in enabled)
        }
    }
    // Acts on the apps currently listed (search + filters), so e.g. "System apps" + "Select all" picks every app.
    val allShownOn = shown.isNotEmpty() && shown.all { it.pkg in enabled }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Apps")
                        Text(
                            "${enabled.size} selected",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = { onSetAll(shown.map { it.pkg }, !allShownOn) }, enabled = shown.isNotEmpty()) {
                        Text(if (allShownOn) "Deselect all" else "Select all")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Clear") }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp)
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !onlySelected, onClick = { onlySelected = false }, label = { Text("All apps") })
                FilterChip(selected = onlySelected, onClick = { onlySelected = true }, label = { Text("Selected") })
                // System apps (e.g. System UI, which posts screenshot notifications) have no launcher icon.
                FilterChip(selected = showSystem, onClick = { showSystem = !showSystem }, label = { Text("System apps") })
            }

            if (apps == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                if (shown.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (onlySelected) "No apps selected yet" else "No apps found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(shown, key = { it.pkg }) { app ->
                            val on = app.pkg in enabled
                            ListItem(
                                modifier = Modifier.clickable { onToggle(app.pkg, !on) },
                                headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text(
                                        if (app.system) "System \u00b7 ${app.pkg}" else app.pkg,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                leadingContent = { Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp)) },
                                trailingContent = { Switch(checked = on, onCheckedChange = { onToggle(app.pkg, it) }) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}
