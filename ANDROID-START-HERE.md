# Magpie Android

安卓应用源码位于 `android/`，完整上游 Go 核心与管理界面通过固定的 magpie-core 子模块提供。

- 安装与功能说明：[android/README.zh-CN.md](android/README.zh-CN.md)
- 已测范围：[2.0 预览 3 主题和素材](android/docs/preview-2.0-themes/README.md)
- 构建入口：`bash android/build.sh`
- 输出：`dist/Magpie-Android-2.0.0-preview3-arm64.apk`

最低 Android 9.0，目标 API 35。Android 核心通过 NDK/CGO 使用系统 DNS；ARM64 按 16 KB ELF 页面对齐。源码交付包不包含签名私钥；自行构建会生成新证书，不能覆盖已安装的交付包。持有原证书时可用构建环境变量指定。

完整源码可用 `git clone --branch v2.0.0-preview3 --depth 1 --recurse-submodules --shallow-submodules https://github.com/yvichyi/magpieA-.git` 取得。ZIP 源码可运行 `bash android/fetch-core.sh` 获取固定核心。
