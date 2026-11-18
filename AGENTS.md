# Working agreement for the approved Android 2.0 redesign

- Persistent source is `/workspace/magpie-android-v2`, branch `feature/liquid-glass-v2`. Never put source or sole progress records in `/dev/shm`; only disposable builds/tests belong there.
- Approved spec/plan: `docs/superpowers/plans/2026-11-17-liquid-glass.md`. User explicitly requested implementation; continue without repeated approval questions.
- Existing wrapper/core baseline is GitHub main8de93cb, Go0.1.4342. Core reference sources are `/workspace/magpie` (read-only). Do not change the core submodule.
- Parent owns shared `Ui`, `CoreApi`, navigation, MainActivity, build, manifest, integration and publication. Workers have exclusive file ownership, no subagents, no shared-file edits, no git staging/commits, no emulator/full APK builds. Focused tests use worker-specific temp directories.
- Native Java17/manual Android SDK build, min28/target35, same ai.magpie.android/signature/data. Preserve credentials, dirty guards, origin authentication, asynchronous workspace scopes, real Go/Bun functionality.
- Main screen contract: AppScreen.view/onResume/onPause/onBack/dispose; ScreenHost.activity/ui/openRoot/openDetail(title,AppScreen)/advanced(view[,key,value])/connect(ref)/refresh.
- Four roots connect/home/plugins/usage = 接入/模型/插件/用量. Header/dock owned by main host; worker root views use scroll content with96dp bottom clearance. Global workspace/settings; detail back preserves context.
- Light neutral liquid glass on dock/controls/overlays, readable solid body/chart surfaces, no purple or repeated nested cards. Body15–16sp, heading24–28, gutters20dp,48dp targets.
- No mock production data or dead feature buttons; do not label configured models online. Stats use authoritative ledger totals including cache; unpriced/missing metrics are not zero/free. Actual API33 backdrop refraction, lower APIs degrade honestly.
- Save a report in `docs/superpowers/reports/<task>.md` with exact files, tests, gaps. Parent commits and backs up completed modules immediately.
