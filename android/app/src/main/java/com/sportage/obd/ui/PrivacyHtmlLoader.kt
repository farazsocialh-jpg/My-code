package com.sportage.obd.ui

import androidx.core.content.ContextCompat

private const val ASSETS_HTML = "privacy.html"
private const val RAW_HTML = "privacy_policy.html"

object PrivacyHtmlLoader {
    /**
     * Returns the local privacy HTML when available.
     *
     * Order:
     * 1. assets/privacy.html
     * 2. res/raw/privacy_policy.html
     *
     * Returns null only when neither local source is present, so the caller can
     * fall back to the published URL.
     */
    fun loadLocalHtml(context: android.content.Context): String? = try {
        loadFromAssets(context, ASSETS_HTML)
    } catch (_: Exception) {
        try {
            loadFromRaw(context, RAW_HTML)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadFromAssets(context: android.content.Context, name: String): String {
        return context.assets.open(name).bufferedReader().use { it.readText() }
    }

    private fun loadFromRaw(context: android.content.Context, resName: String): String {
        val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
            .takeIf { it != 0 }
            ?: throw IllegalStateException("Missing raw/$resName")
        return context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
    }
}
