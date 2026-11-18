# 建筑素材与瑞士主题验收记录

`Appearance` 保存稳定的主题 ID 和深浅色，`Ui` 提供字体、描边、控件与内容配色。`GlassFrame`、`GlassPanel` 和 `GlassNavigation` 切换玻璃与平直布局。`ArchitectureBackdrop` 将许可摄影裁切放在内容两端，并沿用实时背景折射。原始出处和许可写入 APK 的第三方声明。

`ToolsActivity` 提供两种主题选择。`MainActivity` 按工作空间保留正在使用的入口和界面状态。`CatalogScreen`、`ConnectScreen`、`InsightsScreen` 和 `PluginsScreen` 保存相应筛选及位置。插件写操作保持原结果回传，避免主题切换重发请求。处于详情中时延后应用外观，返回入口后刷新样式。

`ManagementActivity`、`mobile.js` 与 `mobile.css` 将主题和系统夜间配色传给同源管理页。测试验证主题选择默认值、文本和描边对比度、WebView 端深浅色配色以及发布包内图片与署名。

[体验与验收](../../../android/docs/preview-2.0-themes/README.md)
