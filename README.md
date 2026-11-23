# 纯净中国象棋（ChineseChess）

一款离线可玩的中国象棋应用，内置本地 AI 引擎，支持人机对战、同屏双人、房间码远程对战与对局复盘。基于 Capacitor 7 打包为 Android 原生应用，前端为无构建的原生 HTML/CSS/JS，无需联网即可完整对弈。

## 功能特性

- **人机对战**：内置 Alpha-Beta + 静态搜索引擎，四档难度（入门 4 层 / 进阶 6 层 / 高级 10 层 / 顶级 20 层），可选执红/执黑。
- **双人对弈**：同屏热座，轮流落子。
- **远程对战**：房间码联机，可自定义服务器地址，支持悔棋 / 认输 / 复制邀请链接。
- **对局辅助**：棋谱记录与导出、历史对局存档、一键复盘、悔棋 / 认输。
- **系统适配**：边到边布局 + 真实安全区适配，适配华为 / 小米全面屏手势与三大金刚键，最低 Android 7.0（API 24）。

## 技术栈

| 模块 | 说明 |
| --- | --- |
| 前端 | 原生 HTML / CSS / JS（无构建、无框架，CSP 禁用内联脚本） |
| 棋盘渲染 | SVG（`board.js`） |
| 象棋引擎 | 走法生成 / 将军检测 / 胜负判定 + Negamax + Alpha-Beta（`engine.js`） |
| 打包 | Capacitor 7（Android） |
| 应用 | 应用名「纯净中国象棋」，包名 `com.purexiangqi.app` |

## 目录结构

```
ChineseChess/
├── frontend/               # 前端源码（原生 JS，无构建）
│   ├── index.html          # 入口页
│   ├── css/app.css         # 样式
│   └── js/
│       ├── app.js          # 页面 / 交互逻辑
│       ├── engine.js       # 象棋引擎（window.XiangqiEngine）
│       ├── board.js        # SVG 棋盘渲染（window.XiangqiBoard）
│       ├── api.js          # 联网对战 API
│       └── sound.js        # 落子音效（Web Audio 合成）
├── android/                # Capacitor Android 工程（Gradle）
├── capacitor.config.json   # Capacitor 配置
├── xiangqi.apk             # 已签名发布包（英文文件名，便于分发）
└── 纯净中国象棋.apk        # 已签名发布包
```

## 环境要求

- Node.js 18+（仅用于 `npx cap` 同步，前端本身无构建）
- JDK 21
- Android SDK（platform 35 + build-tools）
- Android Gradle Plugin（工程内置 Gradle Wrapper，无需手动安装 Gradle）

## 构建步骤

### 1. 同步前端资源

修改 `frontend/` 后，将 Web 资源同步到 Android 工程：

```powershell
npx cap copy android
```

### 2. 构建 APK

先配置环境变量（路径按本机实际填写）：

```powershell
$env:ANDROID_HOME="<你的 Android SDK 路径>"
$env:ANDROID_SDK_ROOT="<你的 Android SDK 路径>"
$env:JAVA_HOME="<你的 JDK 21 路径>"
```

Debug 包（无需签名）：

```powershell
cd android
.\gradlew.bat assembleDebug --no-daemon
```

Release 包（正式签名，用于分发）：

```powershell
.\gradlew.bat assembleRelease --no-daemon
```

输出路径：

- Release：`android/app/build/outputs/apk/release/app-release.apk`
- Debug：`android/app/build/outputs/apk/debug/app-debug.apk`

### 3. 签名说明（重要）

- 正式签名密钥位于 `android/release.keystore`（已加入 `.gitignore`，**不会随仓库分发**）。
- 别名与密码见 `android/app/build.gradle` 的 `signingConfigs`。
- **请自行备份 `release.keystore`**：丢失后无法对新版本签名，只能卸载重装。
- 换机构建时，需先把备份的 `release.keystore` 放回 `android/` 目录。

## 安装

1. 将 `xiangqi.apk` 存到手机「文件管理 → 下载」目录（建议用英文文件名，避免部分机型安装报错）。
2. 从系统文件管理器点击安装（不要从微信等临时目录直接安装）。
3. 首次安装需允许「安装未知来源应用」。

## 版本

- 当前版本：**2.0**（`versionCode 2`）
- 最低系统：Android 7.0（API 24）
