# Preview 6 check-in and motion

The native check-in directory uses the pinned upstream settings, disabled-account and account check-in endpoints. `CheckInScreen` and `CheckInSnapshot` distinguish displayed account identity, an uncertain response, provider-directed verification and day rollover. The action is visible in the usage screen and in the usage section of settings. `CoreApi` allows only the explicit routes. The activity restores the screen after a workspace change, and the directory reads when gateway readiness changes.

`MaterialBackdrop` replaces the bitmap with a single cached gradient. `GlassFrame`, `GlassBlur31` and `GlassShader33` reuse lens display lists and sample a smaller area. API 33 combines refraction and softening. `MainActivity`, `UiMotion` and `GlassNavigation` retain the header, start the slide at first pre-draw, and keep the root opaque while translating. `CatalogRepository`, `CatalogScreen` and `PluginsScreen` avoid repeated work on warm tab re-entry.

JVM and API 28 linkage checks, focused API and snapshot fixture tests, cadence probes and shipped AGSL pixel checks passed. Both builds retain the previous certificate and native binaries. Benchmarks and interaction tests are driven by the signed x86_64 artifact and local fixtures in `android/tests/native_preview.py`.
