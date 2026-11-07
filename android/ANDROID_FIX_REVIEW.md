# Android Bluetooth and map repair

Latest build: `2026-11-06.j`, version `1.1.0`, version code `7`. The change set is scoped to `android/`; the React/Vite web application, its package files, and root APKs are unchanged.

Build j integrates MapLibre/OpenFreeMap with selectable themes, retained Qatar fallback, a music/actions dock and 81 standard read-only OBD PID definitions. See [upgrade behavior and limitations](MAPLIBRE_AND_OBD_UPGRADE.md) for the current implementation and acceptance checks. The sections below preserve the earlier native-map and visual-refinement repair history.

## Latest map presentation revision

The user subsequently requested a less cartoonish map and one icon to choose among installed navigation apps, with Android's Just once / Always behavior. This supersedes the interim request for a Waze-only button. This revision keeps the working embedded offline map but uses a restrained light street style: pale gray buildings, thin gray road casings, muted green parks, and reduced visual clutter. There is no dark map filter or theme toggle. A new cache namespace prevents old heavy-outline tiles from surviving the update.

Both Follow GPS buttons are removed. A single 48 dp icon-only navigation button occupies a dedicated header slot, outside the map, source label, coordinates, and driving information, in portrait and landscape. It has the accessibility label "Open navigation app", but no visible text label. Its callback sends a standard implicit `ACTION_VIEW` intent with `geo:<latitude>,<longitude>?z=16`, without a package, component, or forced chooser. Android can therefore show compatible installed handlers with Just once / Always, honor an existing default, or launch the only compatible app directly. A forced `Intent.createChooser` is deliberately not used because it would defeat persistent defaults. If no compatible application can launch, a visible message appears; it does not silently open a browser/store or navigate to a fabricated destination. Location changes still automatically move the embedded map without a follow button. The picker is controlled by the Android version/device; it includes apps that register a compatible geo intent handler, not every app with a navigation-related name.

## Bluetooth findings and corrections

- **Stable connection state:** The service previously exposed the current transport's flow through a getter, then replaced the transport. Existing repository collectors could remain subscribed to the obsolete flow. A lifetime-stable relay now follows the active transport and rejects stale emissions.
- **One selected device:** Paired and BLE rows share address-scoped presentation logic. A test with twelve devices requires exactly one “Connecting...” label and disables all additional connection attempts.
- **Single-flight and cancellation:** Repository and service guards replace the activity's arbitrary 1.2-second unlock. Rejected duplicate requests restore actual service state. Cancel clears pending binds, cancels connection/reconnection jobs, closes the pending socket/GATT, and prevents late callbacks from reviving an obsolete connection.
- **Classic Bluetooth:** Socket attempts retain bounded deadlines, are cancellation-aware, close failed sockets, and handle permission revocation. Commands are serialized so polling, diagnostics, and terminal traffic cannot consume one another's replies. Missing prompts time out as link failures rather than appearing to be successful empty responses.
- **BLE interoperability:** Standard short UUIDs are normalized correctly. Both legacy Android and Android 13+ notification callbacks are implemented. Notification/indication setup waits for the descriptor acknowledgement before initialization; writes are serialized, checked, and chunked for the default MTU. Response timeouts trigger link-error handling instead of silently cancelling polling.
- **Pairing and scanning:** Bond broadcasts are filtered to the selected MAC. The receiver is registered before bonding begins, pairing has a timeout, scans stop before connecting, and BLE scans expire. Receiver flags account for Bluetooth broadcasts from the platform.
- **Recovery and cleanup:** A failed manual selection stops with an error instead of looping forever against a laptop/car stereo. Recovery from a dropped established link is capped at five attempts with bounded backoff. Telemetry and adapter-specific capability data reset on a new selection; log/tone resources close appropriately.
- **Small screens:** Device actions wrap instead of squeezing labels across several lines, and long paired-device names no longer crowd out the connection button.

## Map findings and corrections

The user's physical-device screenshot still showed only the old position-marker fallback after the browser-map changes. Browser-only rendering tests were therefore insufficient evidence that the embedded map would work on the target Android device. This build removes that architecture instead of claiming another browser setting will fix it.

- **Native renderer:** Mapsforge renders the embedded map directly through Android graphics. The HTML asset, JavaScript engine, WebKit dependency, embedded browser, and fake grid/marker fallback are deleted. No local dark filter or theme toggle is used.
- **Bundled Qatar streets:** The APK includes a 25,476,984-byte vector street map of Qatar, dated 22 October 2026. It is copied to private app storage on the IO dispatcher, then rendered locally. Viewing Qatar streets does not require internet, browser support, a tile-server response, an account, or an API key.
- **World coverage:** Outside the bundled map's bounding box, the native renderer switches to standard OpenStreetMap tiles over HTTPS. This worldwide mode requires internet. Its cache is separate from the offline vector cache and uses an identifying user agent.
- **GPS:** The marker and map center follow valid native location fixes without reloading the map. No Follow GPS controls are needed or shown. Last-known location uses the newest permitted provider; absent/rejected providers do not block the other provider.
- **English:** The Qatar dataset supports English and the reader now explicitly selects it. Language-marker prefixes are removed. Features without an English/Latin-script name are left unlabeled rather than displaying Arabic or fabricating a translation. The English cache is separate from any previously rendered Arabic tiles. Worldwide online raster labels remain controlled by their provider.
- **Lifecycle:** A native MapView occupies an explicit match-parent container. Render/cache/download resources are released when disposed. The asset is copied atomically, and a real initialization error is shown instead of a fake map.
- **External navigation:** One icon-only header button delegates installed navigation-app selection and default handling to Android. No vendor-specific package routing or visibility query remains. Obsolete website-search routing and duplicate floating/follow controls are removed.
- **Attribution:** Visible OpenStreetMap credit links to the copyright/license information. The bundled dataset and renderer notices are in the APK assets.

Offline street coverage is Qatar, not the entire world. This is a location tracker, not turn-by-turn navigation or a paid map service.

The dataset comes from the [Qatar Mapsforge download](https://alternativaslibres.org/en/download_other.html?file=Mapsforge_Qatar.zip), listed on [Alternativas Libres](https://alternativaslibres.org/en/downloads-mf.php). OpenStreetMap data can be redistributed with attribution under the Open Database License, as explained in [OpenStreetMap copyright and license](https://www.openstreetmap.org/copyright). Native rendering uses [Mapsforge](https://github.com/mapsforge/mapsforge), version 0.25.0.

## Additional review fixes

- **VIN:** Continuation headers and ELM row prefixes are handled without injecting header characters into the VIN, including responses already flattened by the transport. Truncated VINs are rejected.
- **Readiness:** Incomplete flags are read from the proper high bits of byte B and byte D, rather than treating support bits as failures. Test fixtures were corrected to include the fourth data byte.
- **Freeze frame parser:** A final one-byte parameter is no longer discarded solely because a second data byte is absent.
- **Clock:** The displayed clock now updates rather than relying on a derived value with no observable time dependency.
- **Dependencies:** The unused Navigation Compose and WebKit dependencies are removed. Native Mapsforge renderer/reader dependencies replace the browser-map stack; the street style is bundled as XML rather than depending on the generic themes package.

## Verification

Run from the repository root:

```sh
cd android
bash ./gradlew testDebugUnitTest assembleDebug lintDebug
```

The test suite contains 67 tests, including six native-map/data/language checks, an Android native-graphics rendering test of the street style, three external-app launcher tests, and five Compose layout/interaction tests. The launcher tests cover the implicit geo intent without a vendor restriction or forced chooser, unavailable navigation handlers not opening a browser/store, and invalid fixes not launching. Compose checks cover portrait, head-unit landscape, short landscape, 1.3× font scale, the More menu, Trip/Setup/Gauges/Diagnostics/Media navigation, settings label/control separation, scan callbacks, and cancelling destructive-code-clear confirmation without clearing. The obsolete JavaScript map harness was removed along with the browser map. Lint completes with zero errors; non-blocking dependency/resource warnings remain.

The native-map tests validate the bundled file's length, binary header and SHA-256; parse it with the real map reader; confirm coverage at the user's screenshot coordinates and central Doha; confirm a London fix is outside the offline area; and read nonempty street geometry at the user's position. Invalid location fixes are rejected. Bluetooth regressions remain unchanged.

The Robolectric API 29 native-graphics test instantiates the real native MapView, uses the screenshot's GPS coordinates, and waits for street-detail pixels away from the center marker and controls. It checks that the actual map center changes to a second GPS fix, releases the renderer, and writes `app/build/reports/native-map-android-canvas.png`. The language tests also confirm actual English names at the user's position, including Al Mashaf Street and Al Hurriya Street.

The unaccelerated local emulator did not complete a stable application smoke test: its system server restarted and installation timed out. This limitation is not counted as a passing device test. Robolectric/native-graphics testing is simulated Android, not a physical head unit; GPS reception, Bluetooth radio behavior, and vehicle checks remain necessary.

## Installation and device acceptance checks

The supplied APK is a debug/test build using the same local debug signing key as builds e/f, so it can update those builds without uninstalling or deleting app data. It has a different certificate from the old repository d APK; preserve data before considering removal of that old installation. A maintainer with the original key can instead rebuild for an in-place update of d.

- **Version:** Confirm build `2026-11-05.i` after installation.
- **Twelve-device list:** Tap only the actual ELM/OBD adapter. Exactly that row should show “Connecting...”; other rows should show disabled “Connect” actions.
- **Cancellation:** Cancel mid-connect, then select another device. No old attempt should reconnect later.
- **Invalid selection:** A laptop/stereo or unavailable adapter should end in a bounded, visible failure and permit a new selection.
- **Real adapter:** Verify adapter initialization and changing RPM/speed values; repeat once with the intended classic or BLE transport.
- **Map offline:** With internet disabled, Qatar streets should still render from the bundled dataset in the new restrained light street style, with a native GPS marker and an OFFLINE label. No dark map filter or map-theme toggle should exist.
- **Map tracking:** Move the device or inject a new GPS fix. The native marker and map center should follow without any Follow GPS button.
- **Navigation icon:** Confirm there is only one navigation icon, in the header and clear of other information, in portrait and landscape. With multiple compatible apps and no existing default, tapping it should show Android's normal app selection. Test Just once, then Always and subsequent direct opening; clear any previous default first if necessary. With no compatible maps app, it must show a helpful message and leave the tracker in place. Physical app handoff, device-specific resolver UI, and full-screen layout still require device verification; intent tests do not constitute a real installed-app test.
- **Outside Qatar:** With internet enabled, a position outside the offline map bounds should use worldwide online streets. The label must indicate internet is required.
- **Lifecycle:** Rotate the display, leave/return to the app, and reopen Bluetooth. Confirm map rendering and connection status remain consistent.
