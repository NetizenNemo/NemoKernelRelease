# 备份功能完善计划

## 现状

- 备份位置：`/data/local/tmp/ninc/backups/`
- 备份时机：每次刷写前自动执行 `dd` 备份
- 命名格式：`boot_backup_yyyyMMdd_HHmmss.img`
- 当前能力：生成路径、列表、大小统计、删除
- 缺陷：无元数据、无恢复界面、设置页只占位

---

## 1. 备份元数据系统

### 压缩存储
备份使用 `gzip -9`（最大压缩），典型 boot 分区从 128MB → ~25MB，大幅节省空间。

```
dd if=/dev/block/boot of=$TMP.img bs=1048576
gzip -9 $TMP.img                          → $TMP.img.gz (最大压缩)
sha256sum $TMP.img.gz                     → .meta 记录
```

恢复时直接解压管道到 dd：
```
gunzip -c backup.img.gz | dd of=/dev/block/boot bs=1048576
```

### 元数据 JSON

每个备份产生时，同时生成同名 `.meta` 文件（JSON），记录：

```json
{
  "id": "boot_backup_20260621_143022",
  "timestamp": 1768894222000,
  "fileName": "boot_backup_20260621_143022.img.gz",
  "originalSize": 134217728,
  "compressedSize": 25165824,
  "kernelVersion": "5.10.198-android12-9-00001-gxxx",
  "device": "marble",
  "androidVersion": "14",
  "bootPartition": "/dev/block/by-name/boot_a",
  "sha256": "a1b2c3d4...",
  "note": "before flashing Nanally v1.0"
}
```

来源：
- `kernelVersion` → `uname -r`
- `device` → `getprop ro.product.device`
- `androidVersion` → `getprop ro.build.version.release`
- `sha256` → `sha256sum` 备份文件

## 2. BackupManager 新增/改造

| 方法 | 说明 |
|------|------|
| `createBackup(bootPartition, note?) → BackupMeta?` | 封装备份全流程：dd + 收集元数据 + 写 .meta |
| `listBackups(): List<BackupMeta>` | 返回带元数据的备份列表 |
| `getBackup(id): BackupMeta?` | 单个查询 |
| `deleteBackup(id): Boolean` | 删除 .img + .meta |
| `restoreBackup(id, bootPartition, onLog): FlashResult` | 恢复并记录日志 |
| `getTotalSize(): Long` | 汇总大小 |

## 3. 界面

### 备份列表页（新屏幕 `BackupScreen`）

```
┌──────────────────────────────┐
│  ← 备份管理                    │  TopAppBar
├──────────────────────────────┤
│  □ 自动刷写前备份 [开关]        │  SwitchPreference
│                              │
│  ┌─── 2026-06-21 14:30 ───┐  │
│  │ 内核: 5.10.198-android… │  │  Card
│  │ 大小: 128 MB            │  │  每项一个
│  │ 设备: marble            │  │
│  │ [恢复] [删除]            │  │
│  └────────────────────────┘  │
│                              │
│  ┌─── 2026-06-20 09:15 ───┐  │
│  │ 内核: 5.10.192-android… │  │
│  │ 大小: 128 MB            │  │
│  │ 设备: marble            │  │
│  │ [恢复] [删除]            │  │
│  └────────────────────────┘  │
│                              │
│  共 2 个备份 | 256 MB         │  底部统计
└──────────────────────────────┘
```

### 恢复确认弹窗

```
┌─ 恢复备份 ──────────────────┐
│                              │
│  将使用此备份恢复 boot 分区：  │
│  boot_backup_20260621_143022 │
│  内核版本: 5.10.198          │
│  备份时间: 2026-06-21 14:30  │
│                              │
│  ⚠️ 恢复后建议立即重启        │
│                              │
│      [取消]    [确认恢复]      │
└──────────────────────────────┘
```

### 设置页改造

当前 `ArrowPreference("Backup 管理")` 改为可点击跳转到 `BackupScreen`：

```kotlin
ArrowPreference(
    title = "备份管理",
    summary = "${listBackups().size} 个备份 · ${formatSize(totalSize)}",
    onClick = { navigateToBackupScreen() }
)
```

## 4. 数据模型

```kotlin
@Serializable
data class BackupMeta(
    val id: String,
    val timestamp: Long,
    val fileName: String,          // xxx.img.gz
    val originalSize: Long,        // boot 分区原始大小
    val compressedSize: Long,      // gz 压缩后大小
    val kernelVersion: String,
    val device: String,
    val androidVersion: String,
    val bootPartition: String,
    val sha256: String?,
    val note: String,
) {
    val imgPath: String get() = "$BACKUP_DIR/$fileName"
    val metaPath: String get() = "$BACKUP_DIR/$id.meta"
    val formattedTime: String get() = SimpleDateFormat ... format(Date(timestamp))
    val formattedSize: String get() = formatBytes(fileSize)
}
```

## 5. 实现步骤

1. 创建 `BackupMeta` 数据类 + JSON 序列化（kotlinx.serialization）
2. 重写 `BackupManager`：createBackup、listBackups、restore、delete
3. 创建 `BackupScreen.kt`：LazyColumn + Card 列表 + 长按/按钮操作
4. 实现恢复确认弹窗（OverlayDialog）
5. 设置页 "备份管理" 跳转到 BackupScreen
6. KernelScreen 刷写前使用 `createBackup` 替代手动 dd

---

要开始实现吗？
