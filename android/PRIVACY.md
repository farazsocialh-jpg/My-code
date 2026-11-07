# Sportage OBD-II Intelligence — Privacy Policy

**Effective date:** 6 November 2026  
**Developer:** Faraz Social  
**App package:** `com.sportage.obd`  
**Contact:** farazsocialh@gmail.com · https://github.com/farazsocialh-jpg/sportageobd

---

## 1. Introduction

Sportage OBD-II Intelligence ("we," "our," "the App") is a two-in-one Android application: an automotive OBD-II diagnostic tool that reads vehicle data from an ELM327 Bluetooth adapter, and a car-launcher/cockpit replacement with widgets, navigation shortcuts, media controls, and quick actions. This policy explains how the App handles information. By installing or using the App, you consent to the practices described here.

## 2. Information We Collect

### 2.1 Vehicle and diagnostic data (local only, by default)
- Real-time sensor readings from your vehicle's OBD-II port via an ELM327 Bluetooth adapter: engine speed (RPM), vehicle speed, coolant temperature, intake air temperature, fuel level, throttle position, engine load, battery voltage, mass airflow, fuel trim, timing advance, fuel economy, fuel used, trip distance, and gear estimate.
- Diagnostic Trouble Codes (DTCs): stored, pending, and permanent codes, plus freeze-frame snapshots and readiness monitor status.
- Adapter and ECU metadata: adapter type (Bluetooth Classic or BLE), protocol, VIN (if reported by the ECU), and raw calibration/identification responses.

### 2.2 Device and usage data (minimal)
- The App does not use advertising SDKs, device fingerprinting, or advertising identifiers.
- We collect only what is needed to keep the App working: crash logs you choose to share, and basic in-app usage events for analytics (see Section 3).
- No contact list, call log, SMS, audio, camera, or microphone data is collected by default.

### 2.3 Location data
- The App uses coarse or fine location only to support the embedded map and weather display. Location is processed on-device; it is not sent to us for advertising or profiling.
- Location permission can be denied without breaking core OBD diagnostics.

### 2.4 Future cloud sync (opt-in only)
- If a future cloud-sync feature is introduced, your diagnostic history and trip logs may be stored on our servers. When enabled, you will be shown what is synced and can disable it at any time. No such feature is active by default today.

## 3. How We Use Information

- OBD and diagnostic data is used solely to show live telemetry, graphs, DTC scan results, fuel analytics, alerts, and trip records inside the App.
- Device/usage analytics help us understand whether key flows (Bluetooth pairing, scan completion, launcher usage) work as expected, so we can fix failures and improve stability. Analytics are tied to an anonymous app installation ID, not to your personal identity.
- Crash logs help us diagnose crashes. You choose whether to share them.

## 4. Data Sharing and Third Parties

- We do not sell your data.
- **Crash reporting and analytics:** If you choose to share a crash report, the report is sent through our crash-reporting provider (Firebase Crashlytics / Google). Analytics events are sent to Firebase Analytics. Processing is governed by Google's terms and privacy policy. See Google's privacy policy for details on how Google processes such data.
- **No vehicle data, DTCs, fuel history, or trip logs are shared with third parties by default.**
- When you use features that open other apps (navigation, phone dialer, music players, or sharing a CSV), those apps and their own privacy policies handle the data; this App does not receive information back from them.

## 5. Bluetooth and OBD Adapter Permissions

- The App requests Bluetooth permissions (`BLUETOOTH` / `BLUETOOTH_ADMIN` on older Android, and `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` on Android 12+) so it can find and connect to ELM327 adapters.
- Location permission (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`) is requested because Android requires location permission to scan Bluetooth devices; the App uses it only for Bluetooth scanning and map/weather features, not for tracking.
- Foreground service and notification permissions allow the App to keep OBD streaming active when you switch to navigation or other apps, and to show alerts.
- `QUERY_ALL_PACKAGES` is requested only so the launcher can list installed apps for its app drawer; it is not used for advertising or unrelated profiling.
- You can deny any permission; the App will degrade gracefully (for example, no Bluetooth scanning without Bluetooth permission, no map/weather without location).

## 6. Data Storage and Retention

- Diagnostic, trip, fuel, and preference data is stored locally on your device by default (shared preferences and local JSON/CSV files). No OBD readings or DTCs leave your device unless you explicitly enable a future cloud-sync feature and consent to it.
- CSV telemetry logs you start manually are stored in your device's public storage so you can share them with other apps. You control when logging starts and stops.
- Crash logs are retained by our crash-reporting provider according to its retention settings.
- When you uninstall the App, local data stored only on the device is removed with it (subject to your device's uninstall behavior for shared storage).

## 7. Children and Legal Basis

- The App is not directed to children under 13. We do not knowingly collect personal information from children. If you believe a child has used the App in a way that involves personal data, contact us and we will investigate.
- Where we rely on consent for any processing, you may withdraw it through the App's settings or by disabling the relevant feature. Withdrawal does not affect the lawfulness of prior processing.

## 8. Your Rights and Controls

Depending on your jurisdiction, you may have rights such as access, correction, deletion, portability, restriction, and objection. To exercise them, contact us using the information in Section 10. For locally stored data, you can also clear in-app data through your device settings or by resetting the relevant App screens (for example, trip/fuel history screens offer clearing where applicable).

## 9. Changes to This Policy

We may update this policy to reflect new features or legal requirements. Material changes will be communicated in the App or via the store listing where practicable. The "Effective" date at the top indicates the current version.

## 10. Contact

If you have questions or requests about this policy or the App's data practices, contact:

Faraz Social  
Email: farazsocialh@gmail.com  
GitHub: https://github.com/farazsocialh-jpg/sportageobd

---

**Language note:** This policy may be translated into Arabic, English, Korean, and other languages as needed; in case of conflict, the English version governs.
