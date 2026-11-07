package com.sportage.obd.ui.boards

import android.content.Context
import android.content.SharedPreferences

/**
 * Widget types a driver can place on a custom board. Values map 1:1 to the
 * renderers in [BoardWidgets]; adding a type requires a renderer entry and a
 * picker entry, nothing else.
 */
enum class BoardWidgetType(val id: String, val title: String) {
    SPEED("speed", "Speed"),
    RPM("rpm", "Engine speed"),
    COOLANT("coolant", "Coolant temp"),
    INTAKE_TEMP("intake", "Intake air temp"),
    FUEL("fuel", "Fuel level"),
    THROTTLE("throttle", "Throttle"),
    LOAD("load", "Engine load"),
    VOLTAGE("voltage", "Battery"),
    GEAR("gear", "Gear estimate"),
    MAF("maf", "Air flow"),
    TIMING("timing", "Timing advance"),
    MAP("map", "Live map"),
    CLOCK("clock", "Clock"),
    GPS("gps", "GPS position"),
    TRIP_DISTANCE("trip_distance", "Trip distance"),
    TRIP_DURATION("trip_duration", "Trip duration"),
    CONNECTION("connection", "Connection"),
    SPEED_GRAPH("speed_graph", "Speed graph"),
    RPM_GRAPH("rpm_graph", "RPM graph");

    companion object {
        fun byId(id: String): BoardWidgetType? = entries.firstOrNull { it.id == id }
    }
}

/** Hard limits keep persisted layouts renderable on any screen size. */
object BoardLimits {
    const val MAX_BOARDS = 6
    const val MAX_WIDGETS_PER_BOARD = 12
}

/**
 * A board layout: one or more swipeable pages, each holding an ordered list of
 * widgets. Persisted as a plain token string ("speed|rpm;;map|clock") so the
 * codec needs no JSON dependency and stays testable on the JVM.
 */
data class BoardLayout(val boards: List<List<BoardWidgetType>>) {
    companion object {
        private val BOARD_SEPARATOR = ";;"
        private val WIDGET_SEPARATOR = "|"

        val DEFAULT = BoardLayout(
            listOf(
                listOf(
                    BoardWidgetType.SPEED, BoardWidgetType.RPM,
                    BoardWidgetType.COOLANT, BoardWidgetType.VOLTAGE,
                    BoardWidgetType.MAP, BoardWidgetType.CLOCK
                )
            )
        )

        fun fromJson(raw: String?): BoardLayout {
            if (raw.isNullOrBlank()) return DEFAULT
            val boards = raw.split(BOARD_SEPARATOR)
                .map { board ->
                    board.split(WIDGET_SEPARATOR)
                        .filter { it.isNotBlank() }
                        .mapNotNull(BoardWidgetType::byId)
                }
                .filter { it.isNotEmpty() }
                .map { it.take(BoardLimits.MAX_WIDGETS_PER_BOARD) }
                .take(BoardLimits.MAX_BOARDS)
            return if (boards.isEmpty()) DEFAULT else BoardLayout(boards)
        }

        fun toJson(layout: BoardLayout): String =
            layout.boards.joinToString(BOARD_SEPARATOR) { board ->
                board.joinToString(WIDGET_SEPARATOR) { it.id }
            }

        fun load(prefs: SharedPreferences): BoardLayout = fromJson(prefs.getString("board_layout", null))

        fun save(prefs: SharedPreferences, layout: BoardLayout) {
            prefs.edit().putString("board_layout", toJson(layout)).apply()
        }
    }
}

/** Live values the board widgets read. Bundled once so widgets stay simple. */
data class BoardData(
    val speed: Int? = null,
    val speedUnit: String = "km/h",
    val rpm: Int? = null,
    val coolant: Int? = null,
    val intakeTemp: Int? = null,
    val fuelLevel: Int? = null,
    val throttle: Float? = null,
    val load: Float? = null,
    val voltage: Float? = null,
    val gear: Int = 0,
    val mafGs: Float? = null,
    val timingAdvanceDeg: Float? = null,
    val currentLat: Double = 25.2854,
    val currentLon: Double = 51.5310,
    val gpsStatus: String? = null,
    val tripDistanceKm: Double = 0.0,
    val tripDurationSeconds: Long = 0L,
    val connectionLabel: String = "Disconnected",
    val connectionOk: Boolean = false,
    val speedHistory: List<Pair<Long, Int>> = emptyList(),
    val rpmHistory: List<Pair<Long, Int>> = emptyList()
)

/** Persists board layouts; kept behind an interface so tests run without Android. */
interface BoardStore {
    fun load(): BoardLayout
    fun save(layout: BoardLayout)
}

class PrefsBoardStore(context: Context) : BoardStore {
    private val prefs = context.getSharedPreferences("sportage_obd_boards", Context.MODE_PRIVATE)
    override fun load(): BoardLayout = BoardLayout.load(prefs)
    override fun save(layout: BoardLayout) = BoardLayout.save(prefs, layout)
}
