# Preview 6 check-in and motion

The native check-in directory uses the pinned upstream settings, disabled-account and account check-in endpoints. `CheckInScreen` and `CheckInSnapshot` distinguish displayed account identity, an uncertain response, provider-directed verification and day rollover. The action is visible in the usage screen and in the usage section of settings. `CoreApi` allows only the explicit routes. The activity restores the screen after a workspace change, and the directory reads when gateway readiness changes.

`MaterialBackdrop` replaces the bitmap with a single cached gradient. `GlassFrame`, `GlassBlur31` and `GlassShader33` reuse lens display lists and sample a smaller area. API 33 combines refraction and softening. `MainActivity`, `UiMotion` and `GlassNavigation` retain the header, start the slide at first pre-draw, and keep the root opaque while translating. `CatalogRepository`, `CatalogScreen` and `PluginsScreen` avoid repeated work on warm tab re-entry.

JVM and API 28 linkage checks, focused API and snapshot fixture tests, cadence probes and shipped AGSL pixel checks passed. Both builds retain the previous certificate and native binaries. Benchmarks and interaction tests are driven by the signed x86_64 artifact and local fixtures in `android/tests/native_preview.py`.

Android 13 x86_64 installed preview 5, passed the baseline frame probe, then upgraded in place to the exact published preview 6 APK. Preview 6 passed the same frame probe, the authenticated check-in fixture and both real glass lens checks. P90 total frame duration went from 496 ms to 245 ms in this software emulator. See [the device evidence](../../../android/docs/preview-2.0-material/DEVICE-EVIDENCE.json).
