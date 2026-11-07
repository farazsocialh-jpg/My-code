package com.sportage.obd

import android.app.Application
import android.util.Log
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Application-level crash capture + Firebase initialization.
 *
 * - Firebase Crashlytics is initialized automatically by the Firebase library when
 *   google-services.json is present. Crashlytics reports uncaught exceptions.
 * - Firebase Analytics is initialized the same way and tracks anonymous app-usage
 *   events (onboarding completion, OBD connection, scan completions, launcher usage).
 *
 * Every uncaught exception is ALSO written to files/last_crash.txt BEFORE the process
 * dies, so the next launch shows the crash reason on the head unit itself even when
 * Crashlytics upload fails (no network, missing google-services.json, etc.).
 */
class SportageObdApp : Application() {

    lateinit var firebaseAnalytics: FirebaseAnalytics
        private set

    override fun onCreate() {
        super.onCreate()
        org.mapsforge.map.android.graphics.AndroidGraphicFactory.createInstance(this)

        // Initialize Firebase Analytics (always-on, anonymous usage tracking).
        // If google-services.json is missing, Firebase is inactive but the app still runs.
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Write local crash log first (always works, no network needed).
                val log = File(filesDir, "last_crash.txt")
                log.writeText(
                    buildString {
                        append("Time: ")
                            .append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                            .append('\n')
                        append("Thread: ").append(thread.name).append('\n')
                        append("Exception: ").append(throwable.javaClass.name).append('\n')
                        append("Message: ").append(throwable.localizedMessage ?: "").append('\n')
                        append('\n')
                        append(throwable.stackTraceToString())
                    }
                )

                // Also send to Crashlytics (best-effort; fails silently if offline / not configured).
                try {
                    FirebaseCrashlytics.getInstance().log("Uncaught exception in ${thread.name}")
                    FirebaseCrashlytics.getInstance().recordException(throwable)
                } catch (_: Exception) {
                    // Crashlytics must never mask the original crash.
                }

                // Log key context for analytics (best-effort, no user-identifying data).
                try {
                    firebaseAnalytics.logEvent("app_crash", bundleOf(
                        "exception_class" to (throwable.javaClass.name.take(100)),
                        "has_previous_crash_file" to File(filesDir, "last_crash.txt").exists()
                    ))
                } catch (_: Exception) {}

            } catch (_: Exception) {
                // Writing the log must never mask the original crash.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
