package com.example.ui.components

import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Display formatting in Indian conventions. */
object Format {
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val india = Locale.forLanguageTag("en-IN")
    private val dateTime = DateTimeFormatter.ofPattern("d MMM, h:mm a", india)
    private val time = DateTimeFormatter.ofPattern("h:mm a", india)
    private val date = DateTimeFormatter.ofPattern("d MMM yyyy", india)
    private val shortDay = DateTimeFormatter.ofPattern("EEE", india)

    fun money(amount: Double?): String {
        if (amount == null) return "—"
        val format = NumberFormat.getCurrencyInstance(india)
        format.maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
        return format.format(amount)
    }

    fun km(distance: Double?): String = distance?.let { String.format(india, "%.1f km", it) } ?: "—"

    fun dateTime(instant: Instant?): String = instant?.let { dateTime.format(it.atZone(zone)) } ?: "—"

    fun time(instant: Instant?): String = instant?.let { time.format(it.atZone(zone)) } ?: "—"

    fun date(day: LocalDate?): String = day?.let { date.format(it) } ?: "—"

    fun date(instant: Instant?): String = instant?.let { date.format(it.atZone(zone)) } ?: "—"

    fun weekday(day: LocalDate): String = shortDay.format(day)

    fun ago(instant: Instant?, now: Instant = Instant.now()): String {
        if (instant == null) return ""
        val minutes = Duration.between(instant, now).toMinutes()
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 24 * 60 -> "${minutes / 60} h ago"
            else -> dateTime(instant)
        }
    }

    fun title(value: String): String =
        value.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase(india) else it.toString() }
}
