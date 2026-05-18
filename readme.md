# Syncle (Spatial Workstream) - Android 客户端

![Kotlin](https://img.shields.io/badge/Kotlin-1.8.10-7F52FF?style=for-the-badge&logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202023.03-4285F4?style=for-the-badge&logo=android)
![LiveKit SDK](https://img.shields.io/badge/LiveKit-v2.25.2-18181B?style=for-the-badge&logo=livekit)
![AGP](https://img.shields.io/badge/AGP-8.3.0-3DDC84?style=for-the-badge&logo=android)
![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge)

**Syncle** 是一款具备 RPG 级游戏化沉浸感与现代化架构的元宇宙空间协作与蓝图漫步平台 Android 客户端。项目遵循严格的**单向数据流 (UDF)** 架构设计原则，无需引入繁重的 3D 渲染引擎，通过原生 Jetpack Compose Canvas 实现超流畅的大世界探索、精准空间计算与超低延迟实时音视频协作。

---

## 核心功能特性

- **RPG 级沉浸式空间视口引擎 (Spatial Canvas)**
  - **等比缩放铺满 (Scale to Fill)**：引入基于 `baseScale * 1.35f` 的动态适配系统，确保背景贴图在任何屏幕比例下均能完全填满画布，彻底告别黑边。
  - **动态相机与边缘钳制 (Edge Clamping Camera)**：实现“平时化身固定在屏幕正中，背景反向平移”的沉浸式追踪视角；通过精准的 `coerceIn` 算法实现边缘钳制机制——当玩家接近地图边缘时，背景智能锁死，化身平滑过渡为在屏幕内独立漫步。
  - **GPU 矩阵加速**：结合 `withTransform` 矩阵闭包，实现背景平移、缩放与世界坐标系的无缝 GPU 计算，手势触控坐标通过反向矩阵映射实现高精度世界坐标还原。

- **极致解耦的单向数据流架构 (UDF & MVI)**
  - 完全隔离 UI 表现层与领域业务层，所有状态变动与后台协程通过 `GatherViewModel` 集中管控，杜绝多源状态同步冲突。
  - 纯无状态 UI 组件设计 (`SyncleScreen`, `SpatialCanvas`, `PeerVideoOverlay`)，实现极速响应与零副作用渲染。

- **超低延迟 RTC 与画中画微服务**
  - 基于 `LiveKit Android SDK (v2.25.2)` 打造专职微服务 `LiveKitService`，负责底层房间连接、事件分发与音视频轨道订阅。
  - 支持浮窗式画中画视频覆盖层 (`PeerVideoOverlay`)，提供身临其境的空间视听交互体验。

- **高精度空间计算与状态同步**
  - 内置 50ms (20Hz) 的高频位置同步与平滑插值引擎，保障多端 Peer 漫游无卡顿。
  - 具备高精度 AABB 矩形碰撞检测，结合本地 JSON 地图资产 (`map_config.json`) 实现墙体与边界的精准阻挡。
  - 拥有完善的动态 Token 自动获取、鉴权超时重试及动态权限申请机制 (`CAMERA`, `RECORD_AUDIO`)。

---

## 系统架构设计

项目严格遵循清晰的四层架构分层模型，数据流向单向循环，结构如下所示：

```
+-------------------------------------------------------------------+
|                        UI 表现层 (Compose)                         |
|  - SyncleScreen (UDF 顶层容器，统一管理状态下发与交互意图上抛)           |
|  - SpatialCanvas (纯无状态渲染画布，接收 onMove 回调)                |
|  - PeerVideoOverlay (无状态画中画视频覆盖层)                         |
+-------------------------------------------------------------------+
                                 | (User Intents / onMove)
                                 v
+-------------------------------------------------------------------+
|                        领域层 (ViewModel)                          |
|  - GatherViewModel (集中管控 AvatarState, 维护 50ms/16ms 协程循环)  |
|  - AvatarState / RemotePeer (核心空间计算与状态插值实体)              |
+-------------------------------------------------------------------+
        | (Connection / Publish)         | (Parse Map / Fetch Token)
        v                                v
+-----------------------+     +-------------------------------------+
|  通信层 (LiveKit SDK)  |     |           数据与资源层               |
|  - LiveKitService     |     |  - AuthRepository (OkHttp 现代化)   |
|  (专职封装 RTC 交互)    |     |  - MapRepository (动态加载 assets)  |
+-----------------------+     +-------------------------------------+
```

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
│       │   ├── assets/              # 本地地图资产 (map_config.json, map_config2.json)
│       │   ├── res/                 # 资源文件 (drawable/room1.png, values/themes.xml, strings.xml)
│       │   └── java/com/example/syncle/
│       │       ├── MainActivity.kt  # 主活动容器与权限申请流程
│       │       ├── model/           # 领域模型与微服务层 (GatherViewModel, LiveKitService, AuthRepository...)
│       │       └── ui/              # 纯 Compose 表现层组件 (SyncleScreen, SpatialCanvas, PeerVideoOverlay)
│       └── test/                    # 单元测试与核心业务逻辑验证套件
├── build.gradle                     # 根目录构建配置 (AGP 8.3.0)
├── settings.gradle                  # 仓库与依赖解析管理
├── AGENTS.md                        # AI 协同任务追踪与架构约束基线
├── prD.md                           # 详细产品需求文档
├── handoff.md                       # 模块断点续传与状态交接记录
└── last-session-diff.md             # 增量变更跟踪与审计日志
```

---

## 快速开始与构建指南

### 1. 环境准备
- 推荐使用 Android Studio (Jellyfish 或更高版本)。
- 确保已安装 JDK 17 / JDK 21 / JDK 22。
- 确保 Android SDK Build-Tools 34.0.0 已就绪。

### 2. 本地配置 (local.properties)
在项目根目录的 `local.properties` 文件中补充您的 LiveKit 沙盒密钥或测试服务器地址：
```properties
# LiveKit Sandbox Configuration
livekit.sandbox_id=your_livekit_sandbox_id_here
```

### 3. 构建与运行
在项目根目录执行以下命令进行编译或安装测试：
```bash
# 清理构建缓存
./gradlew clean

# 编译生成 Debug APK
./gradlew assembleDebug

# 直接安装到连接的 Android 设备或模拟器
./gradlew installDebug
```

---

## 测试覆盖与验证

项目包含极其严密的单元测试套件，全面覆盖空间逻辑、同步插值、地图资产解析及边缘碰撞场景。执行以下命令运行全套测试：

```bash
./gradlew testDebugUnitTest
```

**测试套件清单**:
- `SpatialLogicTest.kt`: 验证动态相机视口矩阵、缩放算法与世界坐标反解析的准确性。
- `SyncAndInterpolationTest.kt`: 验证 50ms 频率下的平滑插值引擎与位置同步循环。
- `MapRepositoryTest.kt`: 验证对本地 JSON 地图文件的正确加载与解析能力。
- `CollisionEdgeCaseTest.kt`: 验证 AABB 矩形碰撞检测及极端贴墙移动时的边缘钳制逻辑。
- `AdvancedAudioTest.kt`: 验证底层 RTC 服务流事件分发及音轨控制状态机。

---

## 架构约束与设计准则

1. **渲染限制**: 严禁引入 OpenGL/Filament 等 3D 渲染引擎，必须坚持使用纯原生 Jetpack Compose Canvas 进行 2D 空间计算与渲染。
2. **单一数据源 (SSOT)**: 所有 Peer 及主角的坐标状态必须由 `GatherViewModel` 统一收拢与下发，严禁在 UI 层直接维护具备业务含义的位置状态。
3. **物理引擎极简原则**: 仅允许使用高效的 AABB 矩形碰撞算法，禁止引入繁重复杂的第三方物理引擎。
4. **生命周期绑定**: 底层 `LiveKitService` 必须与 `GatherViewModel` 的协程作用域深度绑定，做到随房间销毁而自动释放所有 WebRTC 资源。
