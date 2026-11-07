package com.sportage.obd.map

object TrackerMapPolicy {
    const val MAP_ASSET = "maps/qatar-2026-10-22.map"
    const val MAP_BYTES = 25_476_984L
    fun valid(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -85.0..85.0 && longitude in -180.0..180.0

    /** Prefer real English names; do not invent translations for missing names. */
    fun englishLabel(value: String?): String {
        val label = value.orEmpty().substringAfter('\u0008', value.orEmpty())
            .filterNot { it.isISOControl() }.trim()
        return if (label.any { it.isLetter() && Character.UnicodeScript.of(it.code) != Character.UnicodeScript.LATIN })
            "" else label
    }
}
