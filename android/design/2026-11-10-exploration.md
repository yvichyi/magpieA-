# Integrated 1.5 design direction

The user rejects 1.4 typography, layout and motion. Their explicit preference remains light black/white/gray, restrained, professional; do not add purple or arbitrary accent colors. The integrated direction has been implemented in the native Android host.

Three Image Gen concepts were displayed independently in this exact order. They are concepts, not screenshots or shipped UI. The user asked what differed, then explicitly instructed “继续啊 / 继续吧”. These represent three functional areas in one visual system, not three distinct design options. Continue implementation directly; no further selection gate applies.

1. Model workspace: `/workspace/generated_images/exec-429c0f6e-a560-4b64-adc1-c61cc653320b.png`
2. Connection workspace: `/workspace/generated_images/exec-f4f29ad9-6ce7-4b80-8fc6-5fbafd13fc60.png`
3. Usage overview: `/workspace/generated_images/exec-485e2fbf-0b3d-41d7-825d-9f74b1211784.png`

All use white/black/gray, readable 14–16sp body text, three primary navigation destinations, existing Magpie identity, and no content cards. Model/provider names and traffic in the concepts are illustrative. Keep production data real, unknown prices explicit, full management/export available, original core unchanged, and all security/lifecycle safeguards intact.

## Evidence and motion gap

The old `android/design/*1.4.png` files were inspected as existing visual references and attached to the concept generations. They are not a fresh audit capture. `NativeAcceptance` explicitly disables window, transition and animator duration scales; earlier successful callbacks/static renders therefore do not establish motion quality. A new motion run must enable normal animation scales and distinguish actual native View/Canvas frame capture from physical GPU/touch certification.

Source inspection shows repeated home/provider/routing navigation and whole-list rebuilds on refresh. These are candidates for simplification and stable updates; do not claim their perceived motion has been measured yet.

Official research sources:

- https://m3.material.io/styles/motion/transitions/transition-patterns
- https://developer.android.com/codelabs/material-motion-android
- https://m3.material.io/components/navigation-bar/overview

Apply motion to the relationship: top-level fade-through, forward/back detail transition, clear sheet entry/exit, localized copy feedback. Respect disabled/reduced animation and test rapid repeated taps, cancellation, navigation state and refreshing without scroll loss. Do not add motion merely as decoration.

## Prior 1.4 delivery

1.4 is already in the user's private Google Drive, with readback showing `can_download=true` for all eight files. Folder: https://drive.google.com/drive/folders/1-0dVLtVmvx82RZxiuvL6tVry19GrhrCJ

APK: https://drive.google.com/file/d/1of0CtSpHcYR5GX7Nq1wDnZXoYMJFvvww/view?usp=drivesdk

Source: https://drive.google.com/file/d/1JkDbC3edXxQ0aDA0uNdKRVWGExqvg4Sl/view?usp=drivesdk

APK SHA-256 remains `0eab49144549ce01b1c0cf7c81d8889b7506ee82e007fd550c59524b46d46108`. Cloud source size 104440955, SHA-256 `d5923b5566f6fef740ea5c89bb15bfb9e61566d57c45d3da31475b8dc6ab572c`; all source/assets/tests/licenses unchanged, only fourteen historical 1.1–1.3 preview PNGs omitted to meet the 100MiB upload limit. Full original local source ZIP is retained.

Local redundant dist APK copies and the verified cloud source staging ZIP were removed to free space; the delivered APK/source files under `/workspace/shared/downloads/` and prebuilt core libraries remain. `gh auth status` also identified an authenticated user-owned account, but no GitHub writes were made.

The initial `magpie_design_qa` startup failed because the template wanted more storage than available shared memory. A dedicated sparse userdata image resolved that startup issue. The lab now uses the 394×852/API35 software emulator with SwiftShader and Vulkan disabled. Earlier failed captures are retained as historical evidence; final proof must come from the final APK installation.

## 1.5 implementation and review

Three primary destinations are models, routing and usage. Model connection is an actionable protocol/configuration sheet. Whitespace, baseline alignment, a restrained type scale and rules replace content cards. The upstream core and full advanced workflows remain the source of truth.

A normal-animation API35 run produced actual Android screenrecord video in addition to native View/Canvas captures. Reviewing that video distinguished a Canvas text-capture artifact from two real defects: left-aligned bottom-navigation labels and a 1-pixel underline on first layout. Both are corrected and have native geometry assertions. Fresh final installation verification is recorded in VERIFICATION.md; do not treat intermediate recording as final-version proof.

Code review also identified pending navigation winning over later taps, large-font summary/model-value compression and premature repeat-copy reset. The implementation now cancels superseded navigation, wraps the daily summary, bounds the numeric column, and versions feedback timers. Dirty-page native leave requests are correlated and consumed once; rapid duplicate requests share one confirmation and retain only the latest destination.
