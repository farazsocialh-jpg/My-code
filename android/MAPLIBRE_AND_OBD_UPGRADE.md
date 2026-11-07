# Sportage Android map, dock and OBD upgrade

Build `2026-11-06.j`, version `1.1.0`, version code `7`. Changes are restricted to `android/`; the web app is untouched. Install the supplied debug APK over the supplied e–i builds without uninstalling, using the same package and signing key.

## On-screen maps

- **Renderer:** MapLibre Native Android `12.0.1`, pinned to the existing Kotlin 2.0.21 toolchain. The attempted 13.7.1 dependency required incompatible Kotlin 2.2 metadata and was not forced through compiler checks. The published [12.0.1 metadata](https://repo.maven.apache.org/maven2/org/maplibre/gl/android-sdk/12.0.1/android-sdk-12.0.1.pom) specifies Kotlin 2.0.20.
- **Provider/cost:** OpenFreeMap public vector maps need no account or API key and are free, including commercial use, but have no SLA. Carrier data charges are separate ([OpenFreeMap](https://openfreemap.org/)).
- **Map themes:** Clean = Positron; Streets = Liberty; Detailed = Bright; Night = Dark; Qatar offline = the bundled Mapsforge map. Clean is default, selection persists, and the layers icon opens the picker. These are map themes, not a new cockpit-wide theme system.
- **Verified endpoints:** `https://tiles.openfreemap.org/styles/positron`, `https://tiles.openfreemap.org/styles/liberty`, `https://tiles.openfreemap.org/styles/bright`, and `https://tiles.openfreemap.org/styles/dark` returned version-8 style documents. Mobile clients use the same styles with MapLibre Native ([quick start](https://openfreemap.org/quick_start/)).
- **English labels:** Prefer `name:en`, then `name_en`, then Latin transliteration; remove local-name concatenation without replacing road references/house numbers. Missing English tags are not invented translations.
- **Tracking:** Marker and camera follow foreground location callbacks. The next position change recenters after manual panning. Header distinguishes waiting, last-known location, GPS accuracy, network location and missing permission. This is not an always-on background route recorder.
- **External navigation:** Keep one map-header icon and the unrestricted Android geo intent, allowing compatible apps and Just once/Always where Android supports them, while honoring existing defaults. No vendor is forced.
- **Attribution:** Preserve MapLibre logo/attribution controls and source attribution; preserve offline OpenStreetMap attribution. OpenFreeMap describes its required credit on its [website](https://openfreemap.org/).

## Offline and failure behavior

The bundled Qatar map remains real vector street geometry, not a grid or WebView. It stays visible until MapLibre reports a complete rendered frame. Missing internet, unavailable OpenGL ES 2 graphics, initialization/style failure or an 18-second incomplete-render timeout returns to fallback; restored connectivity triggers recovery. A retry action is available after online render failure.

Qatar offline disables online rendering and worldwide raster downloads. Outside the actual bundled bounding box it explicitly states the coverage limitation. With internet but incompatible graphics, the original native worldwide raster fallback can still work outside Qatar.

The unchanged asset is `qatar-2026-10-22.map`, 25,476,984 bytes, SHA-256 `f2bf3f4c005bc07ca7e8fa8ca9545b8558c7abfb703320b055e7c107dd9c6f18`. Existing dataset and Mapsforge notices remain.

No bulk provider download feature is added. The HTTP client has an app-specific User-Agent and four per-host requests; OpenFreeMap documents offline-download limits of 3,000 tiles or 50 MB and four parallel requests ([quick start](https://openfreemap.org/quick_start/)).

## Quick-action dock

Music opens an installed compatible app; play/pause and previous/next send real Android media keys without invented track metadata or playback state. Previous/next are direct controls on wider screens and menu actions on phones. Phone opens a dialer without placing a call, Apps opens the existing drawer, and Quick actions contains OBD Bluetooth and CSV recording. Targets are 48 dp with accessible descriptions, while the sole external-navigation icon stays in its separate map-header slot.

## OBD expansion

- **81 read-only Mode 01 PID definitions:** Core speed/RPM/load, both-bank fuel trims, fuel and rail pressures, manifold/barometric pressure, MAF, throttle/pedals, oxygen voltage/trim, wideband lambda/voltage/current, catalyst/oil/ambient temperatures, ECU voltage, EGR/EVAP, ethanol, hybrid remaining-life parameter, fuel rate, injection timing, torque, run-time/distance counters and ECU odometer where supported. Formulas follow the publicly documented [standard OBD-II PIDs](https://en.wikipedia.org/wiki/OBD-II_PIDs); catalogue coverage is not a guarantee that this car supports every reading.
- **Capability/polling:** Walk advertised support pages from 00 through E0, stopping without a continuation bit, and merge support bitmaps across ECUs. Preserve speed/RPM priority and the old auxiliary rotation; add no more than one extra request per 750 ms, only for advertised PIDs. Three undecodable replies suppress an extra PID until reconnect. Use the first complete matching response, not fabricated per-module data.
- **Sensor workspace:** Sensors is in the landscape rail and phone More menu. Search by title/category/PID, filter Supported/Favorites/All, persist favorites, and inspect per-reading age, with stale status after 90 seconds. Expanded rows show first-value min/max/count accumulated while that screen is open, bounded to 120 samples.
- **Export/logging:** Export long-form timestamped CSV snapshots. Continuous CSV adds escaped extended-sample JSON with individual timestamps and units; stopped logs remain shareable and sessions receive unique filenames.
- **Vehicle/tests:** Show VIN and capability summary, adapter identity/protocol, and explicitly raw Mode 09 calibration/CVN/ECU-name plus Mode 06 monitor responses. Manufacturer-specific IDs/scaling/pass-fail are not guessed.
- **Readiness/freeze frame:** Respect monitor support bits and spark/diesel ignition type. Request individual Mode 02 PIDs with frame number 00 and the triggering DTC rather than bare 02.
- **History/safety:** Use a true 60-second core history, avoid duplicates from sensor-only updates, reset history on empty telemetry, and reject standard code clearing when OBD reports movement.

Existing stored/pending/permanent DTCs, VIN, readiness, alerts, trip, logging and the manual terminal remain. ABS, airbags, TPMS, service resets, DPF regeneration, transmission coding, actuator tests and Kia-specific modules are not universally accessible through generic OBD and are not falsely claimed; enhanced diagnostics need the correct vehicle-specific definitions, protocol and hardware. Standard versus manufacturer-specific services are described in the [OBD-II overview](https://en.wikipedia.org/wiki/OBD-II_PIDs).

## Verification and acceptance

Build checks cover compilation, unit tests, native offline map/data/language regressions, Compose layouts/interactions, lint and APK signing. Public style JSON/TileJSON are independently inspected. Robolectric previews show actual Compose/native offline rendering with illustrative central-Doha/vehicle fixtures, not the user's vehicle or physical-device captures.

Verified checkpoint: 82 tests, zero failures/errors; debug APK assembly and lint pass, with zero lint errors and 51 warnings (dependency versions, existing deprecated APIs/resources and style recommendations). Signing certificate SHA-256 remains `09d72d7fefd83820ec5e220fe4fbb2de9869799ad75384d945a450d44045eef8`. A final storage-error safeguard stops CSV recording when writing fails instead of falsely retaining the recording flag.

Robolectric does not verify MapLibre's Android GPU renderer. There is no stable accelerated emulator here; the previous unaccelerated attempt suffered system-server restarts and install timeouts. No physical head-unit/GPS/Bluetooth/ECU acceptance is claimed.

Device acceptance should check every online theme, foreground GPS following, airplane-mode Qatar fallback, network recovery, Bluetooth reconnection, installed-navigation choice, media response and actual supported sensors. The basemap integration does not silently promise traffic, satellite imagery, address search or turn-by-turn routing.
