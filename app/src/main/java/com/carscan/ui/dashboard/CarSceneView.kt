package com.carscan.ui.dashboard

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.carscan.ui.DashboardState
import com.carscan.ui.SceneCommand
import com.carscan.ui.theme.Palettes
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/** Доли экрана, занятые панелями: 3D-модель вписывается в оставшуюся центральную зону. */
data class SafeArea(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Данные, которые нужны viewer.html: подписи узлов, выбранный узел и режим рентгена. */
fun DashboardState.toSceneJson(): String {
    val nodes = JSONArray()
    nodeStatuses.forEach {
        nodes.put(
            JSONObject()
                .put("id", it.node.id)
                .put("status", it.status)
                .put("value", it.value)
                .put("level", it.level.js)
                .put("heat", it.heat.toDouble()),
        )
    }
    val palette = Palettes.byId(settings.themeId)
    val theme = JSONObject()
        .put("id", palette.id)
        .put("accent", palette.accent.rgbTriplet())
        .put("active", palette.active.rgbTriplet())
        .put("glow", palette.sceneGlow.rgbTriplet())
        .put("xray", palette.xrayTint.rgbTriplet())
    return JSONObject()
        .put("theme", theme)
        .put("xray", xrayMode)
        .put("selected", selectedNode?.id ?: JSONObject.NULL)
        .put("nodes", nodes)
        .toString()
}

/** «25, 227, 255» — формат для CSS rgba(var(--accent-rgb), a). */
private fun Color.rgbTriplet(): String =
    listOf(red, green, blue).joinToString(", ") { (it * 255).roundToInt().toString() }

private fun SceneCommand.toJs(): String = when (this) {
    is SceneCommand.Focus -> "window.carscan&&window.carscan.focus(${JSONObject.quote(node.id)})"
    SceneCommand.ResetCamera -> "window.carscan&&window.carscan.resetCamera()"
}

private fun SafeArea.toJs(): String =
    String.format(Locale.US, "window.carscan&&window.carscan.setSafeArea(%.4f,%.4f,%.4f,%.4f)", left, top, right, bottom)

/** Методы, доступные из JavaScript как window.AndroidBridge. */
private class SceneBridge(private val view: WebView, private val onTap: (String) -> Unit) {
    // Вызывается на служебном потоке WebView — переходим в главный
    @JavascriptInterface
    fun onNodeTapped(id: String) {
        view.post { onTap(id) }
    }
}

/** Последние значения, которые нужно повторно отправить, когда страница (пере)загрузится. */
private class ScenePush {
    var json: String? = null
    var safeArea: SafeArea? = null
}

/**
 * 3D-сцена: прозрачный WebView с model-viewer. Ассеты отдаются локально через WebViewAssetLoader
 * (https://appassets.androidplatform.net), поэтому интернет для 3D не нужен.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CarSceneView(
    sceneJson: String,
    safeArea: SafeArea?,
    commands: Flow<SceneCommand>,
    onNodeTapped: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnNodeTapped by rememberUpdatedState(onNodeTapped)
    val push = remember { ScenePush() }
    var webView by remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Всё грузится через WebViewAssetLoader, прямой доступ к файлам не нужен
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE // кэш отключён на время разработки

                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        assetLoader.shouldInterceptRequest(request.url)

                    override fun onPageFinished(view: WebView, url: String?) {
                        push.safeArea?.let { view.evaluateJavascript(it.toJs(), null) }
                        push.json?.let { view.evaluateJavascript("window.carscan&&window.carscan.update($it)", null) }
                    }
                }
                webChromeClient = WebChromeClient()

                addJavascriptInterface(SceneBridge(this) { id -> currentOnNodeTapped(id) }, "AndroidBridge")

                loadUrl("https://appassets.androidplatform.net/assets/viewer.html")
            }.also { webView = it }
        },
        update = { view ->
            if (push.json != sceneJson) {
                push.json = sceneJson
                view.evaluateJavascript("window.carscan&&window.carscan.update($sceneJson)", null)
            }
        },
        onRelease = { view ->
            view.removeJavascriptInterface("AndroidBridge")
            view.destroy()
        },
    )

    LaunchedEffect(webView, safeArea) {
        val view = webView ?: return@LaunchedEffect
        val area = safeArea ?: return@LaunchedEffect
        push.safeArea = area
        view.evaluateJavascript(area.toJs(), null)
    }

    LaunchedEffect(webView) {
        val view = webView ?: return@LaunchedEffect
        commands.collect { view.evaluateJavascript(it.toJs(), null) }
    }
}
