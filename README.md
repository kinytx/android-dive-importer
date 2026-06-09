# DivePlan 潜水日志导入助手 · Android

一个 Android app，用于把潜水电脑表上的潜水日志通过蓝牙读出来，自动上传到 DivePlan
账号，跟小程序 / web 端看到的是同一份数据。

## 当前进度

- [x] **P0 项目骨架** —— Kotlin + Compose + Material3，主题对齐小程序 dark/light，Hilt + Room + WorkManager 依赖就位
- [ ] P1 账号绑定（二维码 + 6 位码 → ApiKey）
- [ ] P2 BLE 扫描 + vendor 识别
- [ ] P3 libdc 路径 dump 抓取（Shearwater 优先）
- [ ] P4 离线上传队列 + WorkManager
- [ ] P5 Garmin 路径 WSS
- [ ] P6 完成态 / 历史 / 设置
- [ ] P7 后端 `/api/me/dives/parse` 接受 X-Api-Key
- [ ] 真机校验 + 第一台 Shearwater 抓 dump 上传成功

## 双路径架构

```
扫描发现 BLE 设备
  ├─ vendor 是 Shearwater/Suunto/Mares/... → libdc 路径
  │     BLE GATT → 抓 dump 字节流 → filesDir 落盘（离线 OK）
  │     → 上传队列 POST /api/me/dives/parse (X-Api-Key)
  │     → 网络断：队列保留，联网补传（WorkManager）
  │
  └─ vendor 是 Garmin → garmin-sidecar 路径
        BLE → WSS 实时 → server DeviceSessionRouter (driver=garmin-sidecar)
        协议复用 gas-dive-plan/shared/utils/garmin-ble-wss-bridge.ts
        离线时显示「需要网络」（v1.0 不支持续传）
```

## 鉴权

不走 Bearer JWT，走 **API Key**（独立 token 域）：

- 用户在小程序 / web 端 `POST /api/me/ble-probe-bind-codes` 生成 6 位码 + 二维码 URL
- Android 端扫码 / 输入码 → `POST /api/ble-probe/bind-codes/consume` → 返回 `ApiKey`
- 所有后续请求带 `X-Api-Key: dpk_xxx` header
- 本地 ApiKey 存 `EncryptedSharedPreferences`

## 构建

```powershell
cd S:\GMP\android-dive-importer
.\gradlew.bat assembleDebug
```

APK 输出：`app\build\outputs\apk\debug\app-debug.apk`

## 相关仓库

- `gas-dive-server` —— ECS 后端，含 `BleProbeBindController` / `BleProbeCapturesController`
- `gas-dive-server/tools/device-import/android-ble-probe` —— 早期 BLE 嗅探调试工具（保留，不要混用）
- `garmin-sidecar` —— Garmin 协议 C 核心 + WSS bridge（clean-room 实现，严禁 copy Gadgetbridge）
- `gas-dive-plan/shared/utils/garmin-ble-wss-bridge.ts` —— 小程序版 Garmin BLE WSS 客户端，本 app P5 port 到 Kotlin

## IP / 法务边界

- ❌ **严禁** import `GMP/Gadgetbridge/` 任何源码到本工程（AGPL-3.0）
- ✅ 复用 `gas-dive-plan/shared/utils/garmin-ble-wss-bridge.ts` 的协议消息格式（DivePlan 自研）
- ✅ 通过观察行为重写实现（参见 `garmin-sidecar/CLEAN_ROOM.md`）
