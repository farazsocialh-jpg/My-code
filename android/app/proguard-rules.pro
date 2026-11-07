# SportageOBD ProGuard / R8 rules
#
# Used when isMinifyEnabled=true (release builds).
# Keep rules are ordered: first app-specific reflection, then library rules.

# --- Keep annotations and signatures needed by Kotlin reflection & serialization ---
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable
-dontwarn kotlin.reflect.jvm.internal.**

# --- Keep the app's data classes (SharedPreferences JSON, CSV, trip/fuel models) ---
# These are read/written with org.json and string parsing; keep their fields.
-keep class com.sportage.obd.fuel.** { *; }
-keepclassmembers class com.sportage.obd.fuel.** { *; }
-keep class com.sportage.obd.obd.** { *; }
-keepclassmembers class com.sportage.obd.obd.** { *; }
-keep class com.sportage.obd.service.** { *; }
-keepclassmembers class com.sportage.obd.service.** { *; }
-keep class com.sportage.obd.bluetooth.** { *; }
-keepclassmembers class com.sportage.obd.bluetooth.** { *; }
-keep class com.sportage.obd.media.** { *; }
-keepclassmembers class com.sportage.obd.media.** { *; }
-keep class com.sportage.obd.map.** { *; }
-keepclassmembers class com.sportage.obd.map.** { *; }
-keep class com.sportage.obd.repository.** { *; }
-keep class com.sportage.obd.viewmodel.** { *; }

# --- Reflection on BluetoothDevice.createRfcommSocket(int) ---
# BluetoothTransport uses reflection to call the hidden API on some devices.
# The Android framework class is never stripped, but keep our call sites.
-keepclassmembers class com.sportage.obd.bluetooth.BluetoothTransport {
    public *;
    private *;
}

# --- OkHttp: keep its internal classes that use reflection ---
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# --- Mapsforge / MapLibre: keep model classes that may be accessed reflectively ---
-keep class org.mapsforge.** { *; }
-dontwarn org.mapsforge.**

# --- Compose / Material3: keep composable functions and preview classes ---
-keep class androidx.compose.** { *; }
-keep class com.sportage.obd.ui.** { *; }
-keepclassmembers class com.sportage.obd.ui.** {
    @androidx.compose.runtime.Composable <methods>;
}

# --- Keep MainActivity and ObdService for component resolution ---
-keep class com.sportage.obd.MainActivity { *; }
-keep class com.sportage.obd.SportageObdApp { *; }
-keep class com.sportage.obd.service.ObdService { *; }
-keep class com.sportage.obd.viewmodel.ObdViewModel { *; }

# --- Keep enum and data classes used in flows / saved state ---
-keep class com.sportage.obd.bluetooth.ConnectionState { *; }
-keep class com.sportage.obd.service.ObdTelemetry { *; }
-keep class com.sportage.obd.service.ActiveAlert { *; }
-keep class com.sportage.obd.service.AlertThresholds { *; }

# --- Remove debug log calls in release builds (optional, safe) ---
# -assumenosideeffects class android.util.Log {
#     public static *** d(...);
#     public static *** v(...);
# }

