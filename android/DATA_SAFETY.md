# Data Safety Form — Google Play Console

**App:** Sportage OBD-II Intelligence  
**Package:** `com.sportage.obd`  
**Date prepared:** 6 November 2026

This document mirrors the information needed for the Google Play Console **Data safety** section. It is written to match the questions in the Play Console, so you can copy answers directly.

---

## 1. Data collection and security

### 1.1 Does your app collect or share any user data?

**Yes.**

### 1.2 Data types collected

| Data type | Collected? | Shared? | Why collected | How used | Is it required for core features? | Can users request deletion? |
|-----------|-----------|---------|---------------|----------|---------------------------------|---------------------------|
| **App activity** (in-app events: pairing, scan, launcher usage, page navigation) | Yes | No | To measure whether key flows work and to fix failures | Analytics (anonymous app-installation-ID tied events) | No (can be disabled) | N/A — no personal identifier |
| **Crash logs / Diagnostics** | Yes (only if user opts in) | Yes (to crash-reporting provider) | To diagnose crashes | Crash reporting (Firebase Crashlytics) | No (voluntary opt-in) | User can stop sharing; provider retains per its policy |
| **Location** | Yes (coarse/fine) | No | Bluetooth device scanning (Android requirement) + embedded map + weather | On-device only: map rendering, weather fetch, Bluetooth scan | No — OBD diagnostics work without it | N/A — not sent off-device |
| **Bluetooth** (device discovery / connection) | Yes | No | To connect to ELM327 OBD adapters | On-device connection to the user's own adapter | Yes — core OBD feature | N/A — not stored off-device |
| **Vehicle data (OBD-II readings, DTCs, fuel, trips)** | Yes | No (by default) | To show live telemetry, graphs, scans, fuel analytics, trips | On-device display, local JSON/CSV storage | Yes — core diagnostic/launcher features | Local data deleted with app uninstall; user can clear in-app trips/fuel history |

### 1.3 Data sharing

- **No data is sold.**
- Crash logs may be shared with the crash-reporting provider (Firebase Crashlytics / Google) **only if the user opts in** via the crash dialog.
- Analytics events are sent to Firebase Analytics; they are tied to an anonymous app-installation ID, not a personal identity.
- Vehicle/OBD data is **not shared** with any third party by default.
- When the App opens other apps (navigation, dialer, music player, CSV share), those apps handle their own data; the App does not receive data back.

### 1.4 Data security

- OBD, trip, fuel, and preference data is stored **locally on the device** (SharedPreferences, local JSON files, CSV in app storage). No OBD data is transmitted unless the user explicitly enables a future cloud-sync feature.
- Network traffic (weather fetch, map tile downloads) uses HTTPS.
- No advertising or profiling SDKs are used.
- Crash logs are transmitted over HTTPS to the crash-reporting provider.
- The App processes location on-device only; it does not upload location.

### 1.5 Data deletion

- **Local data:** OBD readings, trips, fuel history, and preferences are stored locally. Uninstalling the App removes app-private local data. In-app screens allow clearing trip and fuel history where applicable.
- **Crash logs:** Users control whether to share a crash report. Once shared, retention is governed by the crash-reporting provider.
- **Analytics:** Tied to an anonymous app-installation ID; no personal deletion flow is applicable because no personal identifier is collected.

---

## 2. Play Console question-by-question mapping

### Data types the App processes

- **App activity:** Yes — anonymous usage events for analytics.
- **Crash logs / Diagnostics:** Yes — opt-in crash reporting.
- **Location:** Yes — coarse/fine for Bluetooth scan + map/weather; on-device only.
- **Bluetooth:** Yes — to connect to the user's own ELM327 adapter.

### Data types the App does NOT collect

- Contact info
- Financial info
- Health & fitness
- Personal info (name, email, etc.) — unless the user voluntarily shares a crash report that includes device info
- Messages & email
- Photos & videos
- Audio, file, and video data (other than CSV logs the user explicitly starts)
- Device identifiers used for advertising (none used)

### Data shared with third parties

- Crash logs: shared with Firebase Crashlytics/Google **only if user opts in**.
- Analytics: Firebase Analytics (anonymous events).
- No other third-party sharing.

### Is data collected from children under 13?

No. The App is not directed to children under 13 and does not knowingly collect personal information from children.

### Does your app use data for a purpose other than service provision?

No. All data is used to provide the diagnostic/launcher features, analytics, and crash reporting described above.

---

## 3. Permission justifications (for Play Console permission declarations)

| Permission | Declared reason | Is it core? |
|-----------|----------------|------------|
| `BLUETOOTH` / `BLUETOOTH_ADMIN` (pre-Android 12) | Connect to ELM327 OBD adapters | Yes |
| `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` (Android 12+) | Find and connect to ELM327 OBD adapters | Yes |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Bluetooth device scanning (Android requirement) + map/weather | No — OBD works without it |
| `INTERNET` / `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | Weather API, map tile downloads | No — OBD works offline |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Keep OBD streaming active when switching apps | Yes |
| `POST_NOTIFICATIONS` | Show OBD alerts and service status | No — OBD works without notification |
| `QUERY_ALL_PACKAGES` | Launcher app drawer lists installed apps | Yes — launcher feature |

---

## 4. Notes for the developer

- When filling the Play Console Data safety form, select **"No"** for any data type that is not collected.
- For **Location**, indicate it is used only for Bluetooth device scanning and app functionality (map/weather), not for advertising or tracking.
- For **App activity**, indicate it is anonymous usage analytics that can be disabled.
- For **Crash logs**, indicate opt-in sharing with Firebase Crashlytics/Google.
- Keep PRIVACY.md in the repo root (or a publicly accessible URL) and link it in the store listing.

---

**Source files:**
- `PRIVACY.md` (user-facing privacy policy)
- `android/app/src/main/AndroidManifest.xml` (permissions)
- `android/app/src/main/java/com/sportage/obd/ui/PrivacyPolicyScreen.kt` (in-app privacy screen)
