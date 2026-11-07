package com.sportage.obd.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An active warning condition raised by the AlertEngine.
 */
data class ActiveAlert(
    val key: String,
    val message: String,
    val severity: AlertSeverity,
    val triggeredAtMs: Long
)

enum class AlertSeverity { WARNING, CRITICAL }

/**
 * User-tunable warning thresholds. Defaults suit a Kia Sportage 2.0 MPI.
 */
data class AlertThresholds(
    val coolantMaxC: Int = 110,
    val batteryMinV: Float = 11.5f,
    val rpmMax: Int = 6000,
    val speedMaxKmh: Int = 160,
    val audibleAlerts: Boolean = true
) {
    companion object {
        const val PREFS_NAME = "sportage_obd_alerts"

        fun load(context: Context): AlertThresholds {
            val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return AlertThresholds(
                coolantMaxC = p.getInt("coolantMaxC", 110),
                batteryMinV = p.getFloat("batteryMinV", 11.5f),
                rpmMax = p.getInt("rpmMax", 6000),
                speedMaxKmh = p.getInt("speedMaxKmh", 160),
                audibleAlerts = p.getBoolean("audibleAlerts", true)
            )
        }

        fun save(context: Context, t: AlertThresholds) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt("coolantMaxC", t.coolantMaxC)
                .putFloat("batteryMinV", t.batteryMinV)
                .putInt("rpmMax", t.rpmMax)
                .putInt("speedMaxKmh", t.speedMaxKmh)
                .putBoolean("audibleAlerts", t.audibleAlerts)
                .apply()
        }
    }
}

/**
 * Evaluates every telemetry sample against the user's thresholds, keeps the
 * set of currently-active alerts (with a re-trigger cooldown so the beep
 * does not fire on every loop iteration) and beeps via ToneGenerator.
 */
class AlertEngine(private val context: Context) {

    private var thresholds: AlertThresholds = AlertThresholds.load(context)
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)

    private val _activeAlerts = MutableStateFlow<List<ActiveAlert>>(emptyList())
    val activeAlerts: StateFlow<List<ActiveAlert>> = _activeAlerts.asStateFlow()

    private val lastTriggerAt = mutableMapOf<String, Long>()
    private val alertNotification: ((ActiveAlert) -> Unit)? = null

    fun setAlertNotifier(notifier: (ActiveAlert) -> Unit) {}

    fun updateThresholds(newThresholds: AlertThresholds) {
        thresholds = newThresholds
        AlertThresholds.save(context, newThresholds)
    }

    fun currentThresholds(): AlertThresholds = thresholds

    fun evaluate(t: ObdTelemetry) {
        val triggered = mutableListOf<Pair<String, ActiveAlert>>()

        t.coolantTempC?.let { c ->
            if (c >= thresholds.coolantMaxC) {
                triggered += "coolant" to ActiveAlert(
                    "coolant",
                    "Coolant $c°C exceeds ${thresholds.coolantMaxC}°C",
                    AlertSeverity.CRITICAL,
                    System.currentTimeMillis()
                )
            }
        }
        t.batteryVoltage?.let { v ->
            if (v <= thresholds.batteryMinV) {
                triggered += "battery" to ActiveAlert(
                    "battery",
                    "Battery ${"%.1f".format(v)}V below ${thresholds.batteryMinV}V",
                    AlertSeverity.WARNING,
                    System.currentTimeMillis()
                )
            }
        }
        t.rpm?.let { r ->
            if (r >= thresholds.rpmMax) {
                triggered += "rpm" to ActiveAlert(
                    "rpm",
                    "Engine $r RPM over ${thresholds.rpmMax} redline zone",
                    AlertSeverity.WARNING,
                    System.currentTimeMillis()
                )
            }
        }
        t.speedKmh?.let { s ->
            if (s >= thresholds.speedMaxKmh) {
                triggered += "speed" to ActiveAlert(
                    "speed",
                    "Speed $s km/h over ${thresholds.speedMaxKmh} limit",
                    AlertSeverity.WARNING,
                    System.currentTimeMillis()
                )
            }
        }

        val now = System.currentTimeMillis()
        val updated = _activeAlerts.value.toMutableList()

        for ((key, alert) in triggered) {
            val existing = updated.find { it.key == key }
            if (existing == null) {
                updated.add(alert)
                maybeBeep(key, now)
            } else if (now - existing.triggeredAtMs > RESUME_MS) {
                // re-announce a condition that has been active for a while
                maybeBeep(key, now, force = true)
                updated[updated.indexOf(existing)] = alert
            }
        }

        // Clear alerts whose condition no longer holds
        val triggeredKeys = triggered.map { it.first }.toSet()
        updated.removeAll { it.key !in triggeredKeys }

        _activeAlerts.value = updated
    }

    private fun maybeBeep(key: String, now: Long, force: Boolean = false) {
        if (!thresholds.audibleAlerts) return
        val last = lastTriggerAt[key] ?: 0L
        if (force || now - last > BEEP_COOLDOWN_MS) {
            lastTriggerAt[key] = now
            try {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
            } catch (_: Exception) {}
        }
    }

    fun clear() {
        _activeAlerts.value = emptyList()
    }

    fun close() {
        clear()
        toneGenerator.release()
    }

    companion object {
        private const val BEEP_COOLDOWN_MS = 15_000L
        private const val RESUME_MS = 120_000L
    }
}
