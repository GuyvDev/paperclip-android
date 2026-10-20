package com.bu.paperclip.util

/**
 * Routine schedules as the web UI's presets (every hour/day/weekday/week/month, or custom cron).
 * Weekdays use cron numbering: 0 = Sunday … 6 = Saturday.
 */
data class Schedule(
    val preset: Preset,
    val minute: Int = 0,
    val hour: Int = 9,
    val weekday: Int = 1,
    val dayOfMonth: Int = 1,
    val custom: String = "",
) {
    enum class Preset(val label: String) { Hourly("Every hour"), Daily("Every day"), Weekdays("Weekdays"), Weekly("Weekly"), Monthly("Monthly"), Custom("Custom (cron)") }

    fun toCron(): String = when (preset) {
        Preset.Hourly -> "$minute * * * *"
        Preset.Daily -> "$minute $hour * * *"
        Preset.Weekdays -> "$minute $hour * * 1-5"
        Preset.Weekly -> "$minute $hour * * $weekday"
        Preset.Monthly -> "$minute $hour $dayOfMonth * *"
        Preset.Custom -> custom.trim()
    }

    companion object {
        val WEEKDAYS = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

        private fun num(s: String, range: IntRange): Int? = s.toIntOrNull()?.takeIf { it in range }

        /** Reads a cron string back into a preset; anything else is Custom. */
        fun parse(cron: String?): Schedule {
            val c = cron?.trim().orEmpty()
            val f = c.split(Regex("\\s+"))
            if (f.size != 5) return Schedule(Preset.Custom, custom = c)
            val (mi, h, dom, mon, dow) = f
            val minute = num(mi, 0..59) ?: return Schedule(Preset.Custom, custom = c)
            if (mon != "*") return Schedule(Preset.Custom, custom = c)
            if (h == "*" && dom == "*" && dow == "*") return Schedule(Preset.Hourly, minute = minute)
            val hour = num(h, 0..23) ?: return Schedule(Preset.Custom, custom = c)
            return when {
                dom == "*" && dow == "*" -> Schedule(Preset.Daily, minute, hour)
                dom == "*" && dow == "1-5" -> Schedule(Preset.Weekdays, minute, hour)
                dom == "*" && num(dow, 0..7) != null -> Schedule(Preset.Weekly, minute, hour, weekday = num(dow, 0..7)!! % 7)
                dow == "*" && num(dom, 1..31) != null -> Schedule(Preset.Monthly, minute, hour, dayOfMonth = num(dom, 1..31)!!)
                else -> Schedule(Preset.Custom, custom = c)
            }
        }

        fun clock(hour: Int, minute: Int): String {
            val h12 = if (hour % 12 == 0) 12 else hour % 12
            return "%d:%02d %s".format(h12, minute, if (hour < 12) "AM" else "PM")
        }

        /** "Every day at 9:00 AM", "Weekdays at 6:30 PM", "Every Monday at …", or the raw cron. */
        fun describe(cron: String?): String {
            val s = parse(cron)
            return when (s.preset) {
                Preset.Hourly -> if (s.minute == 0) "Every hour" else "Every hour at :%02d".format(s.minute)
                Preset.Daily -> "Every day at ${clock(s.hour, s.minute)}"
                Preset.Weekdays -> "Weekdays at ${clock(s.hour, s.minute)}"
                Preset.Weekly -> "Every ${WEEKDAYS[s.weekday]} at ${clock(s.hour, s.minute)}"
                Preset.Monthly -> "Monthly on day ${s.dayOfMonth} at ${clock(s.hour, s.minute)}"
                Preset.Custom -> if (s.custom == "* * * * *") "Every minute" else "Cron: ${s.custom}"
            }
        }

        private val cronField = Regex("""^(\*|\d+(-\d+)?)(/\d+)?(,(\*|\d+(-\d+)?)(/\d+)?)*$""")

        /** The web UI's checks; null when valid. The server still has the final word. */
        fun validate(cron: String): String? {
            val f = cron.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                f.isEmpty() -> "Enter a 5-field cron expression."
                f.size != 5 -> "Use exactly 5 fields; this has ${f.size}."
                f.any { !cronField.matches(it) } -> "Cron fields must use valid numbers, ranges, lists, wildcards, or steps."
                else -> null
            }
        }
    }
}
