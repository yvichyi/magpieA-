# Android 2.0 预览 4 排版修订

`Ui.Type` 统一原生页面标题、区块标题、行标题、说明、标识和数据值的处理。采用系统字号缩放，并根据字体度量留出行间空间。

`CatalogViews` 将名称、标识和状态分别排版。目录保留完整无障碍文本，详情提供完整的模型、分组和服务商标识。`ConnectScreen` 使用同样的标签和标识规则，复制值保持原样。

`InsightsScreen` 将标题放到用量筛选之前。大数字使用等宽数字与独立说明。`AdaptivePair` 在两列放不下时垂直排列。`PluginsScreen` 与 `ToolsActivity` 使用同样的标题和说明字阶。`mobile.css` 在内嵌管理页统一输入内边距和文字行距，保留滑块的刻度对齐。

回归检查覆盖 320px 的内嵌设置输入与滑块。原生验收脚本使用 `typography` 和 `typography-small` 检查普通和 320dp、200% 字体下的长名称与大数字。验收记录包含实际获得的设备结果。

[排版截图与验收](../../../android/docs/preview-2.0-type/README.md)
