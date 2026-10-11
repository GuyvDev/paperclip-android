package com.bu.paperclip.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bu.paperclip.BuildConfig
import com.bu.paperclip.data.AppSettings
import com.bu.paperclip.data.AppState
import com.bu.paperclip.net.Api
import com.bu.paperclip.net.LiveEvents
import com.bu.paperclip.notify.NotifyWorker
import com.bu.paperclip.ui.Nav
import com.bu.paperclip.ui.components.Avatar
import com.bu.paperclip.ui.components.Divider
import com.bu.paperclip.ui.components.SectionHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: Nav, onSignedOut: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by AppState.settings.flow.collectAsState(AppSettings())
    val me by AppState.me.collectAsState()
    val company by AppState.company.collectAsState()
    val companies by AppState.companies.collectAsState()
    val connected by LiveEvents.connected.collectAsState()
    var confirmSignOut by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch { AppState.settings.setNotifications(granted); NotifyWorker.schedule(ctx, granted) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { BackIcon { nav.back() } }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {
            SectionHeader("Account")
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(me?.name ?: me?.email ?: "?", me?.image, 40.dp, isAgent = false)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(me?.name ?: "Signed in", style = MaterialTheme.typography.titleMedium)
                    Text(me?.email ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    "Server: ${Api.baseUrl}" + if (connected) "  ·  live" else "  ·  reconnecting",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (companies.size > 1) {
                SectionHeader("Company")
                companies.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { AppState.selectCompany(c) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name)
                            Text(c.issuePrefix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (c.id == company?.id) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            SectionHeader("Appearance")
            listOf("system" to "Follow system", "dark" to "Dark", "light" to "Light").forEach { (k, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable { scope.launch { AppState.settings.setTheme(k) } }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, Modifier.weight(1f))
                    if (settings.theme == k) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            SectionHeader("Notifications")
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Agent requests")
                    Text(
                        "Checks every ~15 minutes for new questions, confirmations, approvals and failed runs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.notifications,
                    onCheckedChange = { on ->
                        if (on && Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            scope.launch { AppState.settings.setNotifications(on); NotifyWorker.schedule(ctx, on) }
                        }
                    },
                )
            }
            TextButton(onClick = { NotifyWorker.runNow(ctx) }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Check now") }

            SectionHeader("Server")
            Text(
                if (Api.bearer != null) "Connected with an API key" else "Connected with a browser session",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Divider()
            Text(
                "Sign out",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().clickable { confirmSignOut = true }.padding(16.dp),
            )
            Text(
                "Paperclip for Android ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("This clears the app's Paperclip and Cloudflare sessions on this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    scope.launch { LiveEvents.stop(); Api.signOut(); AppState.settings.setApiToken(""); Api.configure(Api.baseUrl, null); onSignedOut() }
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}
