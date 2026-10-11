package com.bu.paperclip.ui.components

import android.content.Context
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.bu.paperclip.net.Api
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolver
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.destination.ImageDestinationProcessor
import io.noties.markwon.image.network.OkHttpNetworkSchemeHandler
import io.noties.markwon.linkify.LinkifyPlugin

/** App-wide link router; set by the navigation host. Returns true when the link was handled in-app. */
object Links {
    var handler: (String) -> Boolean = { false }
}

private var cached: Triple<Int, String, Markwon>? = null

private fun markwon(context: Context, codeBg: Int): Markwon {
    cached?.let { (bg, base, m) -> if (bg == codeBg && base == Api.baseUrl) return m }
    val m = Markwon.builder(context)
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(LinkifyPlugin.create())
        .usePlugin(ImagesPlugin.create { plugin ->
            plugin.addSchemeHandler(OkHttpNetworkSchemeHandler.create(Api.mediaClient))
        })
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                builder.codeBackgroundColor(codeBg)
                    .codeBlockBackgroundColor(codeBg)
                    .codeTextSize(spToPx(context, 13f))
                    .headingBreakHeight(0)
                    .headingTextSizeMultipliers(floatArrayOf(1.3f, 1.18f, 1.08f, 1f, 1f, 1f))
            }

            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.imageDestinationProcessor(object : ImageDestinationProcessor() {
                    override fun process(destination: String): String = Api.absolute(destination)
                })
                builder.linkResolver(object : LinkResolver {
                    override fun resolve(view: View, link: String) {
                        if (Links.handler(link)) return
                        runCatching {
                            val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(Api.absolute(link)))
                            view.context.startActivity(i)
                        }
                    }
                })
            }
        })
        .build()
    cached = Triple(codeBg, Api.baseUrl, m)
    return m
}

private fun spToPx(context: Context, sp: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics).toInt()

/** Paperclip mentions are markdown links with custom schemes; show them as "@Name". */
private val mentionRe = Regex("""\[(@?[^\]]+)]\((agent|user|project|skill|routine|pipeline)://[^)\s]*\)""")

fun prepareMarkdown(text: String): String = mentionRe.replace(text) { m ->
    val label = m.groupValues[1]
    val scheme = m.groupValues[2]
    val href = m.value.substringAfter("](").dropLast(1)
    if (scheme == "agent" || scheme == "project") "[$label]($href)" else "**$label**"
}

/** Renders GitHub-flavoured markdown with selectable text, images and in-app links. */
@Composable
fun Markdown(text: String, modifier: Modifier = Modifier, fontSizeSp: Float = 15f, selectable: Boolean = true) {
    val context = LocalContext.current
    val color = LocalContentColor.current.toArgb()
    val link = MaterialTheme.colorScheme.tertiary.toArgb()
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest.toArgb()
    val mw = remember(codeBg, Api.baseUrl) { markwon(context, codeBg) }
    val prepared = remember(text) { prepareMarkdown(text) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextIsSelectable(selectable)
                movementMethod = LinkMovementMethod.getInstance()
                setLineSpacing(0f, 1.15f)
            }
        },
        update = { tv ->
            tv.setTextColor(color)
            tv.setLinkTextColor(link)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
            mw.setMarkdown(tv, prepared)
        },
    )
}
