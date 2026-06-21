# KernelFlasher — Android 内核刷写工具

一个轻量、开源的 Android 内核刷写 App。支持两种方式获取内核镜像：

- **在线模式** — 自动从 GitHub Releases 检查并下载最新内核
- **本地模式** — 手动选择设备中已有的内核镜像文件

无需复杂操作，点按即可刷写。

---

## 功能概览

| 功能 | 说明 |
|------|------|
| 🔄 在线安装 | 绑定 GitHub 仓库，自动检测最新 Release 并下载 |
| 📁 本地安装 | 从存储中选择 .img 或 .gz 内核文件直接刷写 |
| ℹ️ 内核信息展示 | 解析镜像头，展示内核版本、构建日期、平台等元数据 |
| 📋 刷写日志 | 实时输出刷写过程，成功/失败一目了然 |
| ⏮️ 备份恢复 | 刷写前自动备份当前内核，支持一键恢复 |
| 🔐 Root 检测 | 检查设备 Root 状态与分区可写性 |
| 📱 设备信息 | 显示当前内核版本、Android 版本、架构等 |

---

## 屏幕截图

> _待补充_

---

## 安装方式

KernelFlasher 的内核刷写能力本身有两种安装模式：

> 🔗 **在线模式默认源**：<https://github.com/NetizenNemo/NemoKernelRelease>
>
> App 启动后会自动检查本仓库的 GitHub Releases 获取最新内核。
> 你也可以在设置中修改为其他内核发布仓库。

### 在线模式（推荐）

打开 App，首页自动检查本仓库（NemoKernelRelease）的最新 Release：

1. App 自动拉取仓库最新 Release 列表
2. 预览 Release 中的内核镜像文件信息（版本、大小、构建日期等）
3. 点击「下载并刷写」— 下载、校验、刷写一气呵成
4. 你也可以在「设置」中更换为其他 GitHub 内核发布仓库

适用于内核作者持续发布更新、用户希望一键追新的场景。

### 本地模式

1. 将内核镜像文件保存到设备存储中
2. 在 App 中点击「选择本地文件」
3. 浏览并选中 .img / .gz / .lz4 等格式的内核文件
4. 确认镜像信息后点击「刷写」

适用于开发者本地编译内核、或从其他渠道获取镜像的场景。

---

## 系统要求

| 条件 | 要求 |
|------|------|
| Android 版本 | **11 (API 30) 及以上** |
| Root 权限 | **必须** — 刷写分区需要 root 或 Magisk/KernelSU 授权 |
| 空闲存储 | 至少 50MB（下载及备份用） |
| 网络（在线模式） | 可用互联网连接 |

---

## 📡 Release 通道（在线模式）

### 通道机制

Git Release **Tag 前缀**来区分不同设备/架构的内核通道。App 启动后自动检测设备型号，只拉取匹配的 Release。

### Tag 命名规范

```
<设备代号>-<语义版本号>
```

例如：

| Tag | 通道 | 说明 |
|-----|------|------|
| `nemo-a-v1.0` | Nemo-A 设备稳定版 | 适用于 Nemo-A 手机的内核 v1.0 |
| `nemo-a-v1.1` | Nemo-A 设备稳定版 | 适用于 Nemo-A 手机的内核 v1.1 |
| `nemo-b-v2.0` | Nemo-B 设备稳定版 | 适用于 Nemo-B 手机的内核 v2.0 |
| `nemo-b-v3.0-rc1` | Nemo-B 设备候选版 | rc/beta/alpha 后缀自动归为测试通道 |

> 💡 你可以在 Release Body 中附加设备代号标签（如 `device: nemo-a`），App 会优先解析 tag 前缀，同时支持 fallback 到 Body 元数据。

### 设备自动识别

App 读取以下 Android 系统属性来自动匹配通道：

| 属性 | 用途 |
|------|------|
| `ro.product.board` | 主板/平台名 |
| `ro.product.device` | 设备代号 |
| `ro.build.product` | 产品名 |

匹配优先级：`ro.product.device` > `ro.product.board` > `ro.build.product`。

### 发布新内核示例

```bash
# 为 Nemo-A 发布 v1.0 内核
git tag nemo-a-v1.0
git push origin nemo-a-v1.0

# 为 Nemo-B 发布 v2.0 候选版
git tag nemo-b-v2.0-rc1
git push origin nemo-b-v2.0-rc1
```

然后在 GitHub 上为 tag 创建 Release，上传对应内核镜像文件即可。

---

## 快速开始（开发者）

### 环境要求

- **Android Studio** Hedgehog (2023.1.1) 或更新版本
- **JDK** 17+
- **Gradle** 8.x（随项目捆绑）
- **Android SDK** 34+

### 克隆 & 构建

```bash
git clone https://github.com/NetizenNemo/NemoKernelRelease.git
cd NemoKernelRelease

# 调试构建
./gradlew assembleDebug

# 生产构建（需配置签名）
./gradlew assembleRelease
```

生成的 APK 位于 `app/build/outputs/apk/` 目录下。

### 配置签名（可选）

将 `keystore.properties` 放至项目根目录：

```properties
storeFile=/path/to/your.keystore
storePassword=your_store_pass
keyAlias=your_key_alias
keyPassword=your_key_pass
```

---

## 项目结构

```
KernelFlasher/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/kernelflasher/
│   │   │   │   ├── ui/                # Jetpack Compose 界面
│   │   │   │   │   ├── screen/        # 主界面、刷写界面、设置界面
│   │   │   │   │   ├── component/     # 可复用 UI 组件
│   │   │   │   │   └── theme/         # Material3 主题
│   │   │   │   ├── data/
│   │   │   │   │   ├── github/        # GitHub API 客户端 (Retrofit)
│   │   │   │   │   ├── local/         # 本地文件与镜像解析
│   │   │   │   │   └── model/         # 数据模型
│   │   │   │   ├── flash/             # 内核刷写引擎（root shell 操作）
│   │   │   │   ├── backup/            # 备份与恢复逻辑
│   │   │   │   └── util/              # 工具类
│   │   │   ├── res/                   # 资源文件
│   │   │   └── AndroidManifest.xml
│   │   └── test/                      # 单元测试
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | **Kotlin** 100% |
| UI | **Jetpack Compose** + Material 3 |
| 异步 | **Kotlin Coroutines** + Flow |
| 依赖注入 | **Hilt** |
| 网络（在线模式） | **Retrofit** + OkHttp + Kotlinx Serialization |
| 本地存储 | **DataStore** (偏好设置) |
| 刷写引擎 | Root shell 命令 ( `dd` / `flash` / `abootimg` ) |
| 构建 | Gradle KTS + Version Catalog |

---

## 安全说明

> ⚠️ **刷写内核有风险，操作不当可能导致设备无法启动！**

- 刷写前 **务必完整备份** 当前内核，App 会自动执行此操作
- 仅支持已验证的内核镜像格式，防止刷入损坏文件
- 所有网络请求仅面向用户指定的 GitHub 仓库，App 不内置任何第三方仓库地址
- 源代码完全开源，可审计每一步操作

---

## 常见问题

**Q: 需要 Root 吗？**  
A: 是的。刷写 `/dev/block/` 下的分区需要 root 权限。推荐使用 Magisk 或 KernelSU。

**Q: 刷坏了怎么办？**  
A: App 会在刷写前自动备份当前内核到 `/sdcard/KernelFlasher/backups/`。你可以从 App 的「恢复」功能或通过 recovery 手动刷回备份。

**Q: 支持哪些分区？**  
A: 默认刷写 `boot` 分区（即内核所在分区）。部分设备可通过设置选择 `boot_a` / `boot_b`（A/B 分区设备）。

**Q: 在线模式一定要 GitHub 吗？**  
A: 目前仅支持 GitHub Releases API。默认连接到 `NetizenNemo/NemoKernelRelease`，可在设置中修改为任意 GitHub 仓库。未来可能支持 Gitee 或自定义下载 URL。

**Q: 内核文件需要什么格式？**  
A: 支持标准的 Android boot image（.img）以及常见的压缩格式（.gz、.lz4、.xz）。

---

## 贡献指南

欢迎任何形式的贡献！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feat/amazing-feature`)
3. 提交修改 (`git commit -m 'feat: add amazing feature'`)
4. 推送分支 (`git push origin feat/amazing-feature`)
5. 发起 Pull Request

请确保代码遵循 [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) 并包含对应的测试。

---

## License

```
MIT License

Copyright (c) 2025 KernelFlasher

Permission is hereby granted...
```

> _查看 [LICENSE](LICENSE) 文件获取完整内容。_

---

## 致谢

- [Magisk](https://github.com/topjohnwu/Musical) — Root 方案与刷写思路参考
- [KernelSU](https://github.com/tiann/KernelSU) — 内核级 Root 方案
- [Android Boot Image](https://source.android.com/docs/core/architecture/bootloader) — 官方文档
