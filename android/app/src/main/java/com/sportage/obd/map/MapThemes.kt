package com.sportage.obd.map

enum class MapTheme(val title: String, val style: String?) {
    POSITRON("Clean", "positron"),
    LIBERTY("Streets", "liberty"),
    BRIGHT("Detailed", "bright"),
    DARK("Night", "dark"),
    OFFLINE("Qatar offline", null);

    val url: String? get() = style?.let { "https://tiles.openfreemap.org/styles/$it" }
    companion object {
        fun saved(value: String?): MapTheme = entries.find { it.name == value } ?: POSITRON
    }
}

/** Pure policy used by the renderer and tests. A loading style is never a ready map. */
object MapAvailability {
    fun useOnline(theme: MapTheme, internet: Boolean, graphics: Boolean, failed: Boolean): Boolean =
        theme != MapTheme.OFFLINE && internet && graphics && !failed
}

object MapLabels {
    const val ENGLISH = """["case",["all",["has","name:en"],["!=",["get","name:en"],""]],["get","name:en"],["all",["has","name_en"],["!=",["get","name_en"],""]],["get","name_en"],["coalesce",["get","name:latin"],""]]"""
    fun shouldLocalize(textFieldValue: String): Boolean = textFieldValue.contains("name")
}
