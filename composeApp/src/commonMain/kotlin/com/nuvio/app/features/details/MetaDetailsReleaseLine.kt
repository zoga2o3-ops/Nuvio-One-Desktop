package com.nuvio.app.features.details

import com.nuvio.app.core.format.extractReleaseYearForDisplay

private fun isTvSeriesType(type: String): Boolean =
    when (type.trim().lowercase()) {
        "series", "tv", "show", "tvshow" -> true
        else -> false
    }

private fun isEndedSeriesStatus(status: String?): Boolean {
    if (status.isNullOrBlank()) return false
    val s = status.trim().lowercase()
    if ("returning" in s || "in production" in s) return false
    return "ended" in s || "canceled" in s || "cancelled" in s
}

/**
 * Compact release line under the details hero: movies → year only; TV → "2025 -" or "2021 - 2028".
 */
fun formatMetaReleaseLineForDetails(meta: MetaDetails, fullDate: Boolean = false): String? {
    // Settings → Full release date: "March 3, 2026" when the addon has the day.
    val fullDateText = if (fullDate) {
        listOfNotNull(meta.released, meta.releaseInfo)
            .map { it.trim() }
            .firstOrNull { Regex("""^\d{4}-\d{2}-\d{2}""").containsMatchIn(it) }
            ?.let { date ->
                // A movie's release is a calendar date ("2026-03-03T00:00:00Z" must stay the 3rd
                // everywhere). A series' first air date is an air time, converted to the viewer's
                // day like episode dates are (a US 8 pm premiere is 00:00 UTC the next day).
                val value = if (isTvSeriesType(meta.type)) date else date.take(10)
                com.nuvio.app.core.format.formatReleaseDateForDisplay(value)
            }
    } else {
        null
    }
    val raw = meta.releaseInfo?.trim()?.takeIf { it.isNotEmpty() } ?: return fullDateText
    if (!isTvSeriesType(meta.type)) {
        return fullDateText ?: extractReleaseYearForDisplay(raw)?.toString()
    }
    if (fullDateText != null) {
        val endYear = meta.lastAirDate?.let { extractReleaseYearForDisplay(it) }
        return if (isEndedSeriesStatus(meta.status) && endYear != null) "$fullDateText - $endYear" else fullDateText
    }
    val startYear = extractReleaseYearForDisplay(raw) ?: return raw
    val endYear = meta.lastAirDate?.let { extractReleaseYearForDisplay(it) }
    return when {
        isEndedSeriesStatus(meta.status) && endYear != null ->
            if (endYear == startYear) startYear.toString()
            else "$startYear - $endYear"
        isEndedSeriesStatus(meta.status) -> startYear.toString()
        else -> "$startYear -"
    }
}
