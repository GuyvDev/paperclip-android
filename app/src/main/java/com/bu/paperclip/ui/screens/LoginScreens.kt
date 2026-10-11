package com.bu.paperclip.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bu.paperclip.net.Api
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
fun SetupScreen(initial: String, onSave: (String) -> Unit) {
    var url by remember { mutableStateOf(initial.ifEmpty { "https://" }) }
    val valid = url.trim().toHttpUrlOrNull()?.host?.isNotBlank() == true
    Column(
        Modifier.fillMaxSize().padding(24.dp).imePadding(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Paperclip", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter the address you open Paperclip at in your browser. You'll sign in on the next screen; " +
                "the app keeps that session (including Cloudflare Access) for all its requests.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Server URL") },
            placeholder = { Text("https://paperclip.example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onSave(url.trim().trimEnd('/')) }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
    }
}

/**
 * Signs in through the real web UI so any front door (Cloudflare Access, SSO, email code) works.
 * Cookies land in the shared WebView store that the API client reads.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(baseUrl: String, onSignedIn: () -> Unit, onChangeServer: () -> Unit) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(0) }
    var checking by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var probe by remember { mutableStateOf<Job?>(null) }
    val host = remember(baseUrl) { baseUrl.toHttpUrlOrNull()?.host ?: baseUrl }

    fun check() {
        probe?.cancel()
        probe = scope.launch {
            delay(400)
            checking = true
            val ok = Api.checkSession()
            checking = false
            if (ok) onSignedIn()
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true) { webView?.goBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Sign in", style = MaterialTheme.typography.titleMedium)
                        Text(host, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, "Reload") }
                    TextButton(onClick = onChangeServer) { Text("Server") }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Some identity providers refuse embedded browsers; present as regular Chrome.
                        settings.userAgentString = settings.userAgentString.replace("; wv", "").replace("Version/4.0 ", "")
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { progress = 10 }
                            override fun onPageFinished(view: WebView, url: String?) {
                                progress = 100
                                CookieManager.getInstance().flush()
                                if (url != null && url.startsWith(baseUrl)) check()
                            }
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
                        }
                        loadUrl("$baseUrl/")
                        webView = this
                    }
                },
            )
            if (progress in 1..99) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
fun SplashScreen(message: String = "Connecting…", onRetry: (() -> Unit)? = null, onSignIn: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (onRetry == null) CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
        if (onSignIn != null) TextButton(onClick = onSignIn) { Text("Sign in again") }
    }
}

@Composable
fun BackIcon(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
}
