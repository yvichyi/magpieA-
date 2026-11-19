# Magpie Android 2.0 预览 5

原版的网关模式以服务商为入口，调用配置由网关页负责。安卓版据此将首次启动落在「模型 → 服务商」，把本机和远程工作区的入口放到需要连接的页面。[原版网关模式](https://github.com/yetone/magpie/blob/v0.1.4342/internal/gui/assets/app.js#L23394-L23409) · [原版网关页](https://github.com/yetone/magpie/blob/v0.1.4342/internal/gui/assets/plugins/gateway/client.js#L1-L22)

| 要做的事 | 安卓入口 |
| --- | --- |
| 添加来源 | 模型 → 服务商 → 添加服务商 |
| 管理和使用模型 | 模型 → 选择模型 → 使用此目标接入 |
| 整理路由分组 | 模型 → 分组 → 新建分组 |
| 复制配置或测试本机模型 | 接入 → 选择目标和协议 |
| 找插件和管理已安装插件 | 插件 → 内置 / 发现 / 已安装 |
| 查看统计与请求明细 | 用量 → 概览 / 分析 / 日历 / 请求 |
| 更改偏好、备份及核心设置 | 右上设置 → 对应功能分组 |

从接入页添加服务商时，「保存并接入」回到原页并选中该服务商的可用模型。模型目录将搜索与筛选收进同一组控件。接入、目录、插件和用量都能直接打开工作区选择。

插件发现页使用上游公开目录。公开目录暂不可达时，页面标记并显示随 APK 打包的上游目录快照。安装和启用插件需要已连接的工作区。[原版插件中心](https://github.com/yetone/magpie/blob/v0.1.4503/internal/gui/assets/plugins.js#L934-L957) · [目录快照来源](https://github.com/yetone/magpie/blob/v0.1.4342/internal/plugin/market.json)

内置核心为 v0.1.4342，Bun 为 1.4.2。两份安装包沿用预览 4 的签名。

## 安卓实拍

Android 13 模拟器运行签名发布包时截取。

- [接入页的本机和远程操作](screens/01-glass-connection.png)
- [服务商页的连接入口](screens/02-swiss-models.png)
- [无网关时浏览并标明快照的插件目录](screens/03-swiss-plugins.png)
- [用量页未连接时的入口](screens/04-swiss-usage.png)
- [位置统一的核心设置、用量和网络入口](screens/05-swiss-settings.png)
- [测试插件更新至 v1.0.2](screens/06-plugin-updated.png)
- [保存服务商后发出测试请求并收到响应](screens/07-model-call.png)

- 320dp 双倍字号 [模型](screens/08-compact-models.png) · [用量](screens/09-compact-usage.png)

## 验收

- Android 13 模拟器安装 x86_64 最终签名包。设备中的 APK 与公开安装包一致。切换液态玻璃和瑞士风格后保留服务商页的选中状态。
- 验证首次启动的服务商落点，以及模型、插件、用量留在当前页打开工作区选择。应用从接入页保存测试服务商后，自动选中该来源的模型，内置网关将请求发到本地测试服务商并处理响应。
- 实际启动内置网关和 Bun，通过本地 npm 测试源完成插件安装、停用、启用和升级。测试源记录两版安装包各下载一次。
- 320dp 双倍字号下通过五页导航及模型行、用量数字的布局断言。
- JVM 测试和 API 28 链接检查通过。ARM64 与 x86_64 核对了签名、16 KB 对齐及随包核心和 Bun。两份公开安装包均已完整下载校验。

覆盖安装验证受到模拟器的 1 GB 分区限制，运行验收使用释放旧包安装空间后装入的新版。ARM64 包完成签名和文件校验。
