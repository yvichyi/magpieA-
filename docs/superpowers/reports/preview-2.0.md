# Android 2.0 预览交付记录

原生宿主使用 `MainActivity`、`ScreenHost` 与 `NavigationModel` 管理四个入口和详情返回。`ConnectScreen`、`CatalogScreen`、`PluginsScreen`、`InsightsScreen` 对接内置网关。`GlassFrame`、`GlassPanel` 与 `GlassShader33` 负责底栏材质。`ManagementActivity`、`ToolsActivity` 和移动端资源提供高级设置入口及同工作空间内的返回行为。

签名构建使用 `android/build.sh`。`android/tests/run.sh --unit-only` 验证模型、插件、账本、CSV、网络与材料策略。`android/tests/native_preview.py` 与 `NativePreviewAcceptance.java` 在隔离模拟器运行覆盖安装、三种协议、插件传输、统计导出、折射和 320 × 568 大字体验收。测试场景按次使用独立 npm 包名，以覆盖 Bun 下载行为。

核心和 Bun 使用与 1.12 相同的已签发二进制。运行验收使用 x86_64 Android 15 SDK 模拟器，ARM64 完成签名、ABI、对齐和下载校验。实体机、旧系统运行和外部供应商 OAuth 待验证。

[页面实拍](../../../android/docs/preview-2.0/README.md) · [验收记录](../../../android/docs/preview-2.0/VERIFICATION.md)
