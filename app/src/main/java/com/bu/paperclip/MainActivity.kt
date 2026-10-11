package com.bu.paperclip

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.bu.paperclip.data.AppSettings
import com.bu.paperclip.data.AppState
import com.bu.paperclip.ui.AppRoot
import com.bu.paperclip.ui.theme.PaperclipTheme

class MainActivity : ComponentActivity() {
    private val pendingLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingLink.value = intent?.getStringExtra(EXTRA_LINK)
        setContent {
            val settings by AppState.settings.flow.collectAsState(AppSettings(theme = "system"))
            val dark = when (settings.theme) { "dark" -> true; "light" -> false; else -> null }
            PaperclipTheme(darkOverride = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AppRoot(pendingLink.value) { pendingLink.value = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_LINK)?.let { pendingLink.value = it }
    }

    companion object {
        const val EXTRA_LINK = "link"
    }
}
