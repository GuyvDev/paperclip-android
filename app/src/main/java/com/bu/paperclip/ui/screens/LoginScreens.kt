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
fun SetupScreen(initial: String, initialToken: String = "", onSave: (url: String, token: String) -> Unit) {
    var url by remember { mutableStateOf(initial.ifEmpty { "https://" }) }
    var token by remember { mutableStateOf(initialToken) }
    var showToken by remember { mutableStateOf(initialToken.isNotEmpty()) }
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
        Spacer(Modifier.height(8.dp))
        if (showToken) {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Board API key (optional)") },
                placeholder = { Text("pcp_board_…") },
                supportingText = { Text("Skips browser sign-in. Live updates fall back to polling with a key.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TextButton(onClick = { showToken = true }) { Text("Use an API key instead") }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onSave(url.trim().trimEnd('/'), token.trim()) }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
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

private sealed class Gate {
    data object Checking : Gate()
    data object Open : Gate()
    data class Required(val host: String) : Gate()
    data class Error(val message: String) : Gate()
}

/**
 * Native sign-in. If an auth proxy (Cloudflare Access) sits in front of the API, its login runs in a
 * browser pointed at a JSON endpoint, so the web UI's JavaScript never needs to load. Paperclip's own
 * sign-in is a native form; the full web page remains as a fallback for SSO-only setups.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(baseUrl: String, onSignedIn: () -> Unit, onChangeServer: () -> Unit) {
    val scope = rememberCoroutineScope()
    var gate by remember { mutableStateOf<Gate>(Gate.Checking) }
    var gateBrowser by remember { mutableStateOf(false) }
    var webFallback by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var probeKey by remember { mutableStateOf(0) }
    val host = remember(baseUrl) { baseUrl.toHttpUrlOrNull()?.host ?: baseUrl }

    androidx.compose.runtime.LaunchedEffect(probeKey) {
        gate = Gate.Checking
        gate = try {
            val g = Api.gateHost()
            if (g == null) {
                if (Api.checkSession()) { onSignedIn(); return@LaunchedEffect }
                Gate.Open
            } else Gate.Required(g)
        } catch (t: Throwable) {
            Gate.Error(com.bu.paperclip.ui.components.errorMessage(t))
        }
    }

    if (webFallback) {
        LoginScreen(baseUrl, onSignedIn, onChangeServer = { webFallback = false })
        BackHandler { webFallback = false }
        return
    }
    if (gateBrowser) {
        GateBrowser(baseUrl, onPassed = { gateBrowser = false; probeKey++ }, onClose = { gateBrowser = false })
        return
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp).imePadding(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Sign in", style = MaterialTheme.typography.headlineMedium)
        Text(host, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        when (val g = gate) {
            Gate.Checking -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is Gate.Error -> {
                Text(g.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { probeKey++ }) { Text("Retry") }
            }
            is Gate.Required -> {
                Text(
                    "This server is protected by ${g.host}. Sign in there first; the app keeps that session.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { gateBrowser = true }, modifier = Modifier.fillMaxWidth()) { Text("Continue to ${g.host}") }
            }
            Gate.Open -> {
                OutlinedTextField(
                    value = email, onValueChange = { email = it; error = null },
                    label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password, onValueChange = { password = it; error = null },
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                Api.signIn(email, password)
                                if (Api.checkSession()) onSignedIn() else error = "Signed in, but the session wasn't accepted."
                            } catch (t: Throwable) {
                                error = com.bu.paperclip.ui.components.errorMessage(t)
                            } finally { busy = false }
                        }
                    },
                    enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Sign in") }
            }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { webFallback = true }) { Text("Sign in with the web page instead") }
        TextButton(onClick = onChangeServer) { Text("Change server") }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GateBrowser(baseUrl: String, onPassed: () -> Unit, onClose: () -> Unit) {
    var progress by remember { mutableStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    BackHandler { if (webView?.canGoBack() == true) webView?.goBack() else onClose() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { BackIcon(onClose) },
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
                        settings.userAgentString = settings.userAgentString.replace("; wv", "").replace("Version/4.0 ", "")
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { progress = 10 }
                            override fun onPageFinished(view: WebView, url: String?) {
                                progress = 100
                                CookieManager.getInstance().flush()
                                if (url != null && url.startsWith("$baseUrl/api/")) onPassed()
                            }
                        }
                        loadUrl("$baseUrl/api/health")
                        webView = this
                    }
                },
            )
            if (progress in 1..99) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}
