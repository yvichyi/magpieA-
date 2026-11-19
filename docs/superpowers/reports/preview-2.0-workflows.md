# Magpie Android 2.0 preview 5 workflows

The upstream gateway-mode starting page, provider setup, gateway client actions, plugin tabs, and settings categories informed this change. The shipped core is pinned to 593517bf (v0.1.4342). The latest v0.1.4503 source was checked for the same structure.

`MainActivity`, `ConnectScreen`, `ScreenHost`, `HostActions`, and `ProviderSetupActivity` handle initial routing, direct workspace selection, gateway status, and continuation after provider setup. `CatalogScreen` and `CatalogViews` handle the model, provider, and group directory. `PluginsScreen` renders context actions, an official public index, and a labeled snapshot bundled as `android/assets/plugin-market.json`. `InsightsScreen` and `ToolsActivity` organize usage and settings around task entry points.

The signed x86_64 package ran on an Android 13 emulator. `native_preview.py --mode workflow` exercised the workspace chooser on three pages, local gateway startup, provider setup, a real HTTP request to a loopback provider, and the first-launch routing condition. `--mode plugins` used a loopback npm registry to install, disable, enable, and update a package. `--mode themes` switched both directions and verified the selected provider tab. The 320dp double-font device probe verified the five page routes and row and metric measurements. Both APKs passed signature, native library integrity, alignment, and full public download checks. The app runtime was exercised on x86_64, and the ARM64 build received packaging checks. The emulator lacked enough disk space for an in-place upgrade with both packages present.

Screenshots and the user-facing workflow table are in [the preview notes](../../../android/docs/preview-2.0-workflows/README.md).
