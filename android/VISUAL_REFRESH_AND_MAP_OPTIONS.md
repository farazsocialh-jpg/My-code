# Sportage Android visual refresh and free map options

Build-i research/history note: the later build `2026-11-06.j` now implements MapLibre/OpenFreeMap, selectable map themes, offline Qatar fallback, a quick dock and expanded OBD support. Current behavior and verification limits are documented in [MapLibre and OBD upgrade](MAPLIBRE_AND_OBD_UPGRADE.md); the research and earlier design review below remain historical context.

Build `2026-11-05.i`, version `1.0.5` (6), refines the working Android app. The React/Vite web application is untouched, and this build keeps the functioning native offline map rather than silently introducing another map engine during a visual pass.

## Visual changes

- **Dashboard:** A simpler Sportage header, one Bluetooth connection action, calmer charcoal surfaces, softer accent colors, and native sans-serif typography replace the dense instrument-terminal look.
- **Vehicle data:** Speed is easier to scan, RPM/coolant/battery are grouped together, and engine load/throttle remain available below. A compact layout and scrolling preserve access on small displays and with larger text.
- **Map:** One icon-only external-navigation action remains in a dedicated header. Coordinates and the configurable speed alert have their own footer; the duplicate speed overlay and decorative GPS status are removed. The map remains light and English, with no dark recoloring.
- **Navigation:** A labeled Material rail serves landscape. Phone portrait uses Home, Gauges, Trip, Media, and More; the native More menu exposes Diagnostics, Apps, and Setup. All original page IDs and callbacks are preserved.
- **Secondary screens:** Gauges and trip use responsive grids instead of squeezed four-column phone layouts. Diagnostics has separate text/action rows and scrollable tabs; media presets use a compact adaptive grid. Settings text and controls have independent measured space. App selection and the Bluetooth interface share calmer fonts/colors; Bluetooth transport and pairing logic are unchanged.

## Best free map choice for this app

My recommendation for a future, more visually capable map is **MapLibre Native plus OpenFreeMap**, starting with a restrained light style such as Positron or Liberty. MapLibre provides GPU-accelerated native rendering, while OpenFreeMap supplies the styles and map hosting; this is a recommendation for the user's free-only requirement, not a universal ranking of map quality ([MapLibre Native](https://maplibre.org/projects/native/), [OpenFreeMap](https://openfreemap.org/), [mobile setup and styles](https://openfreemap.org/quick_start/)).

| Option | Visual and functional fit | What “free” means | Recommendation |
|---|---|---|---|
| MapLibre Native + OpenFreeMap | Customizable vector-map styles; camera rotation/tilt, markers, polygons and route-line drawing; an offline-region API is available. Drawing a route line is not calculating a route ([Native capabilities](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-libre-map/index.html), [offline API](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.offline/index.html), [styles](https://openfreemap.org/quick_start/)). | OpenFreeMap advertises free public hosting, commercial use, no account/API key, and no map-view/request limits. It has no SLA guarantee ([OpenFreeMap](https://openfreemap.org/)). | Best fit for a customizable, zero-billing-account map experience. Default to a clean light street map rather than a visually busy 3D view. |
| Google Maps SDK for Android | A native Android map option; the standard SDK and maps loaded with a map ID are distinct billing cases ([SKU definitions](https://developers.google.com/maps/billing-and-pricing/sku-details/maps)). | The Maps SDK SKU has unlimited free usage, but a billing-enabled Google Cloud project and restricted API key are still required. Android maps using a map ID invoke the separate Dynamic Maps SKU; do not assume every Google map configuration is no-cost ([pricing](https://developers.google.com/maps/billing-and-pricing/pricing), [setup](https://developers.google.com/maps/documentation/android-sdk/get-api-key), [map ID billing](https://developers.google.com/maps/billing-and-pricing/sku-details/maps)). | Viable if the user explicitly accepts Cloud billing setup and provides a correctly restricted key. No billing setup or Google integration is performed in this visual build. |
| Mapbox Maps SDK | Dynamic mobile rendering and map controls are offered; navigation and other location products have their own pricing sections ([Mapbox pricing](https://www.mapbox.com/pricing)). | The mobile Maps SDK has a free tier up to 25,000 monthly active users, followed by paid tiers. This is a free allowance, not an unlimited no-cost guarantee ([Mapbox pricing](https://www.mapbox.com/pricing)). | Not the first recommendation for a strict no-payment-risk requirement, despite its capable visuals. |

### What the recommended stack can and cannot promise

We can build GPS following, camera bearing, tilt, markers, and overlays on MapLibre's camera and map APIs. Offline-region support exists, but permitted data downloading is a separate provider constraint ([MapLibre map API](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-libre-map/index.html), [offline API](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.offline/index.html)).

OpenFreeMap currently permits offline downloads of at most 3,000 tiles or 50 MB per download, at most four parallel requests, and an identifying User-Agent; larger downloads require contacting the project. These conditions must be respected rather than assuming unlimited offline country downloads ([OpenFreeMap quick start](https://openfreemap.org/quick_start/)).

Do not assume that free base-map hosting includes a routing engine, search service, live traffic feed, or satellite imagery. Those features require their own service, data, license, availability, and cost evaluation; MapLibre's renderer and offline APIs alone do not establish such an entitlement ([MapLibre Native](https://maplibre.org/projects/native/), [OpenFreeMap services and styles](https://openfreemap.org/), [example of separately priced navigation and traffic products](https://www.mapbox.com/pricing)).

For a migration, keep the working offline Qatar renderer available until MapLibre's style loading, English labels, GPS following, recovery from missing connectivity, and hardware rendering have passed acceptance on the actual head unit. This research does not count as that implementation or device verification.

## Verification and installation

The visual tests exercise the actual Compose screens, not a web mockup. Their readings, connection state, and central-Doha position are illustrative test fixtures, not a recording of the user's car or live location. The native map geometry is rendered from the real bundled Qatar dataset.

Coverage includes phone portrait, a 1024×600 head-unit layout, an 800×360 landscape layout, and a 360 dp phone at 1.3× font scale. Five Compose tests exercise Trip, Setup, Gauges, Diagnostics, and Media; the portrait More menu; the single external-navigation icon; settings label/control separation; scrolling to lower telemetry; scan callbacks; and cancelling the code-clear confirmation without clearing anything. The enlarged-text case explicitly checks that a three-digit speed does not overflow its text layout. The complete suite contains 67 tests, alongside APK assembly and Android lint.

Robolectric native graphics provide simulated Android renders, not physical-phone or emulator screenshots. The static Impeccable detector reports no findings, but its regex-based source pass does not certify native accessibility or layout; rendered inspection and interaction tests provide separate evidence. Physical head-unit touch behavior, Bluetooth hardware, external-app handoff, and driving-time readability still need device confirmation.

Install build i over the supplied h build without uninstalling; the Android package and signing key are preserved. Map-provider migration remains a separate decision, and no Cloud account, payment method, API key, or paid service has been created.
