# Magpie Android 2.0 预览 7

| 页面 | 阅读和操作顺序 |
| --- | --- |
| 接入 | 网关状态、接入目标、接口协议与配置值、复制和试跑 |
| 添加服务商 | 连接信息、读取与选择模型、保存，订阅登录在连接信息旁 |
| 模型目录 | 页名与当前任务、搜索筛选、模型名称、标识和状态 |
| 模型、分组和服务商详情 | 对象名称和状态、主要操作、完整标识和相关成员 |
| 插件 | 内置、发现、已安装，各自把目录或列表放在状态说明旁 |
| 用量和请求 | Token 与调用数摘要、趋势、构成和图表，其后是请求列表与明细 |
| 签到和设置 | 签到先看账号和结果，刷新为快捷操作。设置的搜索、分类和开关按同一左边距排列 |

长标识在详情可读、可复制。筛选、表单和操作组合在宽度不足时分行。图表按当前字号为刻度留空间，日历日期使用等宽数字。指标数字在说明上方，放大后的多行说明仍可保持数字齐头。底栏加深了表层，减少下方正文的干扰。两套主题的正文沿用同一内容布局。

Java 17、最小 Android API 28 依赖检查、内置网关接口及账本相关 JVM 测试通过。两份 APK 的签名、16 KB 包对齐和内置核心及 Bun 字节均已校验。

## 有数据的安卓页面

394dp 默认字号。模型目录、用量和请求来自内置网关与本地测试服务商。截图中的模型名、账号和插件均是验收数据。

- [接入与测试结果](screens/05-call-success.png) · [添加服务商表单](screens/01-provider-setup.png)
- [模型目录](screens/06-populated-models.png) · [模型详情](screens/07-model-detail.png)
- [用量概览](screens/08-populated-usage.png) · [Token 构成](screens/09-populated-analysis.png) · [请求列表](screens/10-populated-requests.png) · [请求详情](screens/11-request-detail.png)
- [设置](screens/12-settings.png)
- [签到结果](screens/13-checkin.png) · [已安装插件](screens/14-plugins.png) · [插件详情](screens/15-plugin-detail.png)

## 窄屏与大字号

320dp、系统双倍字号。用量摘要的两个数字保持在同一行，说明独立换行。窄屏内容可继续向下滚动，完整标识在详情页展示。

[模型目录](screens/20-compact-models.png) · [用量摘要](screens/21-compact-usage.png) · [设置](screens/22-compact-settings.png)

## 安装包测试

Android 13 x86_64 模拟器安装最终签名包并核对了安装文件的 SHA-256。添加服务商后用内置网关发出测试请求，并从用量打开对应明细。签到手动请求和自动开关保存、插件安装、停用、启用、升级以及玻璃底栏与动效测试通过。服务商、签到账号和 npm 源使用本地验收数据。ARM64 文件完成封装、签名和公开下载校验。[测试记录](DEVICE-EVIDENCE.json)
