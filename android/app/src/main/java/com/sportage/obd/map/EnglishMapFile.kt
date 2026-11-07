package com.sportage.obd.map

import org.mapsforge.map.reader.MapFile
import java.io.File

/** The bundled dataset includes English names; avoid its Arabic default labels. */
internal class EnglishMapFile(file: File) : MapFile(file, "en") {
    override fun extractLocalized(value: String?): String =
        TrackerMapPolicy.englishLabel(super.extractLocalized(value))
}
