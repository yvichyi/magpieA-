# Android 折射和动效修订记录

`GlassFrame` 和 `GlassBlur31` 共享页面的显示列表，把顶部栏和底栏的取样区域分别缓存。`GlassShader33` 加宽折射带，`GlassPanel` 提供双层边缘高光和大字体遮罩。`InsetScrollView` 在保持取样区域的同时给焦点留出顶部空间。

`GlassNavigation` 保留底栏实例，通过位移和宽度变化移动选中块。`UiMotion` 管理可打断的页面运动和触摸反馈，设置页与高级页使用同方向的窗口过渡。左右布局切换时选中块跟随实际标签方向。

运行 `android/tests/run.sh --unit-only` 和 `android/tests/glass_shader_test.py` 验证能力选择与像素结果。`NativePreviewAcceptance` 的 `glass` 模式验证保留的导航、首帧位移、最终就位以及减少动效时两处折射仍取到变化的背景。

[版本与验收说明](../../../android/docs/preview-2.0-motion/README.md)
