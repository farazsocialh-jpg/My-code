# Sportage OBD Intelligence - Native Android APK Build & Installation Guide

This project is a native Android application built with **Kotlin, Jetpack Compose, Material 3**, and the **Android Bluetooth SPP / BLE Stack** designed for the **Kia Sportage 2.0 MPI AWD** and **Samsung Galaxy S24 Ultra** / **Android Head Units**.

---

## 1. Quick APK Build Instructions

To build the debug APK on your computer:

```bash
# 1. Clone or open the repository
cd android

# 2. Build the Debug APK using Gradle
./gradlew assembleDebug

# 3. Locate your compiled APK:
# The generated APK will be at:
# android/app/build/outputs/apk/debug/app-debug.apk
```

---

## 2. Installing on Samsung Galaxy S24 Ultra or Android Head Unit

1. **Enable Developer Options on Device:**
   - Go to `Settings` -> `About phone` -> tap `Build number` 7 times.
   - Go to `Settings` -> `Developer options` -> Enable **USB Debugging** and **Install via USB**.

2. **Install via ADB:**
   ```bash
   adb install -r android/app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Or Transfer & Install Directly:**
   - Copy `app-debug.apk` to your phone via USB or Google Drive.
   - Open the file manager on your phone and tap `app-debug.apk` to install.

---

## 3. Setting as the Default Car Launcher

Because the `AndroidManifest.xml` includes:
```xml
<intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.HOME" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```
You can set this application as your **Default Home App / Car Launcher** in `Settings` -> `Apps` -> `Default apps` -> `Home app`. When you start your car or press the Home button, this dashboard will greet you immediately.

---

## 4. Pairing Your ELM327 Bluetooth Adapter

1. Plug your **ELM327 Bluetooth (or BLE)** adapter into the OBD2 diagnostic port under the steering column.
2. Turn the car ignition key to **ON** or start the engine.
3. Open **Android Bluetooth Settings** and pair with the adapter (PIN is typically `1234` or `0000`).
4. Launch the **Sportage Launcher** app and tap **Connect ELM327**.
5. Live vehicle velocity, engine RPM, automatic gear (`D1`–`D6`), coolant temperature, and battery alternator voltage will stream continuously!

---

## 5. Release Signing (Optional, Recommended)

Release builds are **unsigned** by default, which means the CI artifact cannot be installed directly on a device. To produce an installable, signed release APK:

1. Create a keystore once and keep it safe (losing it means you can never publish an update with the same identity):
   ```bash
   keytool -genkeypair -v \
     -keystore android/release-signing.jks \
     -alias sportageobd \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Copy `android/keystore.properties.example` to `android/keystore.properties` and fill in your passwords. Both files (`.jks` and `keystore.properties`) are gitignored - never commit them.
3. Build: `cd android && ./gradlew assembleRelease`. The APK in `app/build/outputs/apk/release/` is now signed and installable.

### Signing in CI

Add these repository secrets (`Settings -> Secrets and variables -> Actions`) and the GitHub workflow signs the release APK automatically:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 release-signing.jks` output |
| `ANDROID_KEYSTORE_PASSWORD` | keystore store password |
| `ANDROID_KEY_ALIAS` | key alias (e.g. `sportageobd`) |
| `ANDROID_KEY_PASSWORD` | key password |

Without these secrets the workflow still succeeds, but the release APK remains unsigned.
