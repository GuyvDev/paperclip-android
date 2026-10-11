package com.bu.paperclip.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun parseInstant(s: String?): Instant? = s?.let { runCatching { Instant.parse(it) }.getOrNull() }

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
