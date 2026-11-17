# 1.3 sourced visual identity

The user says 1.2 still lacks style and explicitly asks for online materials. Keep the already selected light professional direction, existing native app/code target, and genuine API data. Implement a coherent iris/soft-gray identity rather than replacing working functionality.

Sources actually inspected/downloaded:

- Linear official UI redesign article: https://linear.app/now/how-we-redesigned-the-linear-ui — alignment, hierarchy, deliberate accent and navigation treatment. Reference screenshots are research-only in the toolchain folder, not shipped in the APK.
- Radix Colors official scale guidance: https://www.radix-ui.com/colors/docs/palette-composition/understanding-the-scale . Palette/code licensed MIT; pinned revision and per-file SHA-256 in `android/assets/design/SOURCES.json`.
- Phosphor Icons official SVGs: https://github.com/phosphor-icons/core . MIT, downloaded from a pinned commit with original opacity and path geometry preserved in Android vectors. Exact sources/checksums in the same manifest.
- Magpie bird path from upstream `site/public/img/add-to-magpie-light.svg`, covered by the project's existing MIT license; retain the project's own identity in a violet brand tile.

Implementation: neutral iris-tinted canvas, white grouped surfaces, one dark violet gateway hero with a low-opacity server illustration, official duotone icons, a clear violet navigation indicator, consistent primary/secondary controls, and violet data bars drawn from unchanged upstream buckets. Preserve Noto Sans SC/Inter and typography accessibility. No screenshot, logo or marketing artwork from Linear is copied into the product.

Checks: vector alpha/source integrity and license presence; existing Android JVM/browser/core contracts; signed 1.3 APK install and native callback/view rendering at normal/large font. The emulator's known display-layer/gesture limits remain disclosed.

Execution: import assets and theme; restyle native home/statistics/management shell without changing accounting; review actual Android renders; package APK/source/asset provenance and verification.
