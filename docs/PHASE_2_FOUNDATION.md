# Phase 2 — Executable Foundation

## Included in this checkpoint

- Android application shell with a real MapLibre map view.
- OpenStreetMap raster base layer with attribution.
- Kraków pilot camera position and interactive pan/zoom.
- Working camera recenter and pitch toggle controls.
- Rust domain-core workspace with a canonical road-event model and freshness tests.
- JNI bridge proving that the Android app can call the Rust native library.
- Stable Android application ID and CI-derived increasing version code.
- Release workflow that requires a persistent signing key, verifies the APK signature and uploads the APK artifact.

## Not implemented in this phase

No commercial traffic connector, municipal feed, driver-report backend, route engine, live vehicle location, earnings mirror or production 3D city data is claimed. Those belong to later phases. The OSM tile endpoint is a pilot base layer and must be reviewed for production usage policy and scale.

## Signed updates

See ANDROID_SIGNING.md. The same release keystore must be used for every update; otherwise Android will reject an in-place install. Debug builds deliberately use a different application ID suffix so they cannot overwrite release installs.
