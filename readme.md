# Syncle (Spatial Workstream) - Android 客户端

![Kotlin](https://img.shields.io/badge/Kotlin-1.8.10-7F52FF?style=for-the-badge&logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202023.03-4285F4?style=for-the-badge&logo=android)
![LiveKit SDK](https://img.shields.io/badge/LiveKit-v2.25.2-18181B?style=for-the-badge&logo=livekit)
![AGP](https://img.shields.io/badge/AGP-8.3.0-3DDC84?style=for-the-badge&logo=android)
![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge)

**Syncle** 是一款具备 RPG 级游戏化沉浸感与现代化端云协同架构的元宇宙空间协作与蓝图漫步平台 Android 客户端。项目遵循严格的**单向数据流 (UDF)** 架构设计原则，无需引入繁重的 3D 渲染引擎，通过原生 Jetpack Compose Canvas 实现超流畅的大世界探索、精准空间计算与超低延迟实时音视频协作。

---

## 业务痛点与核心价值 (Why Syncle?)

在当前混合办公与远程协作常态化的背景下，传统工具的局限性日益凸显。Syncle 致力于直击以下三大核心痛点，重塑团队协作体验：

1. **直击“高摩擦力与网格疲劳”：** 传统视频会议（Zoom/Teams）需提前发邀约、输链接，且全程处于僵硬的网格视图（Grid View），极易引发视觉与心理疲劳。Syncle 引入“空间漫步即沟通”理念，化身靠近同事即自动建立 WebRTC 音视频连接，离开即自动断开，将沟通摩擦力降至零。
2. **重塑“饮水机效应与空间感知”：** 远程办公让团队成员退化为孤立的在线图标，丧失了线下办公室中走廊偶遇、茶水间闲聊等非正式社交。Syncle 通过 2D 蓝图复刻实体办公室布局，让用户直观感知团队成员的空间位置与工作状态，无缝激发团队创造力与凝聚力。
3. **聚合“割裂的生产力工具”：** 彻底改变音视频通讯与白板、文档相互割裂的现状。将协作白板与在线文档深度绑定至地图坐标物件，化身靠近即可一键呼出沉浸式画中画协同编辑，实现语境与工具的空间级聚合。

---

## 核心功能特性

- **RPG 级沉浸式空间视口引擎 (Spatial Canvas)**
  - **等比缩放铺满 (Scale to Fill)**：引入基于 `baseScale * 1.35f` 的动态适配系统，确保背景贴图在任何屏幕比例下均能完全填满画布，彻底告别黑边。
  - **动态相机与边缘钳制 (Edge Clamping Camera)**：实现“平时化身固定在屏幕正中，背景反向平移”的沉浸式追踪视角；通过精准的 `coerceIn` 算法实现边缘钳制机制——当玩家接近地图边缘时，背景智能锁死，化身平滑过渡为在屏幕内独立漫步。
  - **GPU 矩阵加速**：结合 `withTransform` 矩阵闭包，实现背景平移、缩放与世界坐标系的无缝 GPU 计算，手势触控坐标通过反向矩阵映射实现高精度世界坐标还原。

- **端云协同与高精度空间计算 (Client-Server Synergy & Spatial Math)**
  - **动态资产与 Bounding Box 下发**：支持自业务服务端拉取标准化 JSON 地图元数据，动态加载底图、墙体与桌椅的高精度 AABB 碰撞体矩阵、私密会议室坐标及交互式物件。
  - **高频位置同步与平滑插值**：内置 20Hz (50ms) 的高频位置同步与平滑插值引擎 (`Lerp`)，结合本地 AABB 碰撞预测拦截，保障多端 Peer 漫游毫无卡顿且杜绝“穿墙”。
  - **安全鉴权与动态 Token 管理**：具备完善的业务服务端登录鉴权、动态换取 LiveKit JWT Access Token、鉴权超时重试及动态权限申请机制 (`CAMERA`, `RECORD_AUDIO`)。

- **极致解耦的单向数据流架构 (UDF & MVI)**
  - 完全隔离 UI 表现层与领域业务层，所有状态变动与后台协程通过 `GatherViewModel` 集中管控，杜绝多源状态同步冲突。
  - 纯无状态 UI 组件设计 (`SyncleScreen`, `SpatialCanvas`, `PeerVideoOverlay`)，实现极速响应与零副作用渲染。

- **超低延迟 RTC 与画中画微服务**
  - 基于 `LiveKit Android SDK (v2.25.2)` 打造专职微服务 `LiveKitService`，负责底层房间连接、事件分发与音视频轨道订阅。
  - 支持浮窗式画中画视频覆盖层 (`PeerVideoOverlay`)，提供身临其境的空间视听交互体验。

---

## 系统架构设计 (Triangular Architecture)

项目严格遵循清晰的端云协同三角架构分层模型，明确划分 Android 客户端、业务服务端与 LiveKit RTC 服务端的职责边界，数据流向单向循环，结构如下所示：

```
+-------------------------------------------------------------------+
|                        UI 表现层 (Compose)                         |
|  - SyncleScreen (UDF 顶层容器，统一管理状态下发与交互意图上抛)           |
|  - SpatialCanvas (纯无状态渲染画布，接收手势与 onMove 回调)           |
|  - PeerVideoOverlay (无状态画中画视频覆盖层)                         |
+-------------------------------------------------------------------+
                                 | (User Intents / onMove)
                                 v
+-------------------------------------------------------------------+
|                        领域层 (ViewModel)                          |
|  - GatherViewModel (集中管控 AvatarState, 维护 50ms/16ms 协程循环)  |
|  - AvatarState / RemotePeer (核心空间计算与状态插值实体)              |
+-------------------------------------------------------------------+
        | (1. Auth & Fetch Map)          | (3. UDP Position Broadcast)
        v                                v
+-------------------------------+   +-------------------------------+
|   Syncle 业务服务端 (Backend)   |   |     LiveKit RTC 服务端 (SFU)    |
|  - 鉴权与 JWT Token 安全签发     |   |  - 专职 WebRTC 音视频流转发    |
|  - 动态下发 Map & Bounding Box|   |  - 高频低延迟 UDP 数据通道广播   |
|  - 权威位置仲裁与全量快照维护    |   +-------------------------------+
+-------------------------------+
```

### 核心子系统职责说明：
1. **Android 客户端 (UI + ViewModel)：** 承载无状态的 Jetpack Compose 渲染树，处理用户触控意图，运行本地 AABB 碰撞预测与 50ms 平滑插值协程，负责音视频画面的呈递与采集。
2. **Syncle 业务服务端 (Backend API & Sync Server)：** 作为系统的业务大脑和权威数据源。负责用户登录鉴权、调用 LiveKit SDK 签发带有细粒度权限的 JWT Access Token；动态下发房间地图元数据（包含底图、Bounding Box 碰撞箱、会议室区域）；并通过 WebSocket/HTTP 维护房间权威状态树，处理快照下发、防作弊仲裁与离线检测。
3. **LiveKit RTC 服务端 (SFU)：** 专职负责底层 WebRTC 的音视频流转发与高频低延迟 UDP 数据通道 (`NetworkReliability.LOSSY`) 位置广播，实现端到端的极致低延迟通信。

---

## 技术栈与核心依赖

- **编程语言**: Kotlin 1.8.10
- **UI 框架**: Jetpack Compose (BOM 2023.03.00)
- **架构组件**: Lifecycle Runtime KTX 2.6.1, Activity Compose 1.7.2, ViewModel
- **异步与并发**: Kotlinx Coroutines Android 1.6.4, StateFlow / SharedFlow
- **网络与通信**: OkHttp 4.x, LiveKit Android SDK 2.25.2
- **数据解析**: Org JSON 20230227
- **构建系统**: Gradle 8.9, Android Gradle Plugin (AGP) 8.3.0（完美兼容 JDK 22 及 Android 15 16KB 内存页规范）

---

## 工程目录结构

```
Syncle/
├── app/
│   ├── build.gradle                 # 模块级构建配置 (定义 namespace 'com.example.syncle')
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml  # 应用配置及动态权限声明 (Theme.Syncle)
│       │   ├── assets/              # 本地地图资产缓存 (map_config.json)
│       │   ├── res/                 # 资源文件 (drawable/room1.png, values/themes.xml, strings.xml)
│       │   └── java/com/example/syncle/
│       │       ├── MainActivity.kt  # 主活动容器与权限申请流程
│       │       ├── model/           # 领域模型与微服务层 (GatherViewModel, LiveKitService, AuthRepository...)
│       │       └── ui/              # 纯 Compose 表现层组件 (SyncleScreen, SpatialCanvas, PeerVideoOverlay)
│       └── test/                    # 单元测试与核心业务逻辑验证套件
├── build.gradle                     # 根目录构建配置 (AGP 8.3.0)
├── settings.gradle                  # 仓库与依赖解析管理
├── AGENTS.md                        # AI 协同任务追踪与架构约束基线
├── prD.md                           # 详细产品需求文档 (涵盖业务痛点与端云架构设计)
├── handoff.md                       # 模块断点续传与状态交接记录
└── last-session-diff.md             # 增量变更跟踪与审计日志
```

---

## 快速开始与构建指南

打开 android studio, 同步一下gradle文件，然后运行即可。
