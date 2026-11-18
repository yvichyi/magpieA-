# Visible glass and motion implementation

This corrects the visual and interaction acceptance criteria in [the approved Android 2.0 plan](2026-11-17-liquid-glass.md).

- `GlassPolicy`, `GlassFrame`, `GlassBlur31`, `GlassPanel` and `GlassShader33` keep real refraction on API 33 and above when motion is reduced. The pinned header and floating dock sample the same live scene through independently sized lenses. The rim bends a visible band and the center remains softened for control legibility.
- `MainActivity` places scroll content beneath the header and preserves the dock while switching roots. `GlassNavigation` moves the selected capsule. `UiMotion` adds press recovery, short in-app transitions and matched Activity transitions. Motion reduction remains available independently of transparency reduction.
- Check the shader on a contrasting stripe and test the rendering capability matrix. Build signed ARM64 and x86_64 packages with the current core and try the installed interactions and evidence capture. Publish the verified artifacts with an exact test record.
