package com.bu.paperclip.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Accepts `Z` and `+hh:mm` ISO timestamps (older Android rejects offsets in Instant.parse) and epoch numbers. */
fun parseInstant(s: String?): Instant? {
    if (s.isNullOrBlank()) return null
    runCatching { return Instant.parse(s) }
    runCatching { return java.time.OffsetDateTime.parse(s).toInstant() }
    s.toLongOrNull()?.let { n -> return if (n > 100_000_000_000L) Instant.ofEpochMilli(n) else Instant.ofEpochSecond(n) }
    return null
}

fun providerLabel(p: String): String = when (p.lowercase()) {
    "anthropic" -> "Anthropic"
    "openai" -> "OpenAI"
    "google", "gemini", "google_antigravity" -> "Google"
    "zai", "z.ai" -> "Z.ai"
    else -> p.replaceFirstChar { it.uppercase() }
}

fun relativeTime(iso: String?, now: Instant = Instant.now()): String {
    val t = parseInstant(iso) ?: return ""
    val d = Duration.between(t, now)
    val s = d.seconds
    return when {
        s < 0 -> "in " + short(-s)
        s < 45 -> "now"
        else -> short(s) + " ago"
    }
}

private fun short(s: Long): String = when {
    s < 3600 -> "${(s / 60).coerceAtLeast(1)}m"
    s < 86400 -> "${s / 3600}h"
    s < 86400 * 30 -> "${s / 86400}d"
    s < 86400 * 365 -> "${s / (86400 * 30)}mo"
    else -> "${s / (86400 * 365)}y"
}

private val absFmt = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault())

fun absoluteTime(iso: String?): String = parseInstant(iso)?.let { absFmt.format(it) } ?: ""

fun durationLabel(startIso: String?, endIso: String?): String {
    val a = parseInstant(startIso) ?: return ""
    val b = parseInstant(endIso) ?: Instant.now()
    val s = Duration.between(a, b).seconds.coerceAtLeast(0)
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m ${s % 60}s"
        else -> "${s / 3600}h ${(s % 3600) / 60}m"
    }
}

fun formatCents(cents: Long?): String {
    val c = cents ?: 0
    return "$" + String.format("%,.2f", c / 100.0)
}
