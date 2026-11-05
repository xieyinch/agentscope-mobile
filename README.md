# AgentScope 随行 · Android

面向 AgentScope Platform / QwenPaw 的非官方安卓客户端，代码托管于 `xieyinch/idea-lab`。

**核心目标：完整覆盖 Agent 控制面板，并逐项实现适合手机的原生操作界面，同时接入论坛社区、技能与插件市场。**

## 当前进度

0.1.0 是工程起点：原生底栏、页面管理与官方网页控制台。**尚未完成原生功能复刻，也未完成真机端到端验收。** 不将网页入口等同于每项功能已验证。

- Agent / 社区 / 技能 / 插件 / 我的五个入口。
- 保留各栏页面，站内共享 Cookie；登录仍由官方网站处理，代码不包含任何账户、密码、Cookie 或 API key。
- 用户点击 Open QWENPAW 后可在应用内新窗口打开控制台，支持回退与关闭。
- 系统文件选择上传、页面加载进度、网络失败重试、手机/桌面视图切换。
- HTTPS、禁用混合内容与本地文件访问、不注入原生 JavaScript 桥、不忽略证书错误。

## 下载与云端构建

打开仓库 **Actions → Android APK → 成功的运行 → Artifacts**，下载 `agentscope-android-运行序号` 并解压安装 APK。

每次推送 main、提交 PR 或手动运行，云端执行 `lintDebug assembleDebug`，输出通用测试 APK 与 SHA256 校验值；推送 `v*` 标签时自动发布预发行版。

测试 APK 使用 Android debug 签名。若需跨构建覆盖安装，请在 GitHub Actions secret `ANDROID_DEBUG_KEYSTORE_BASE64` 保存你自己的固定 debug keystore（Android 默认 debug 密码与别名），否则不同 runner 的临时签名可能不同，需要卸载旧版再安装。正式发行前应配置独立 release 签名。

## 构建环境

- JDK 17，Gradle 8.9，Android Gradle Plugin 8.7.3。
- Android SDK 35 / Build Tools 35.0.0；最低 Android 8.0（API 26）。
- 本地安装上述工具后执行 `gradle lintDebug assembleDebug`。本仓库暂未包含 Gradle Wrapper；CI 会安装固定版本 Gradle。

## 仍需完成

见 [控制台功能清单](docs/FEATURES.md)。尤其是原生 API 适配、流式对话、终端、文件管理、模型与多智能体配置等，必须逐项实现并验收。

当前受限：录音/语音输入尚未适配；需登录的下载会交给系统浏览器且不会转交凭据；第三方 OAuth 可能拒绝内嵌 WebView；控制台响应式布局与终端键盘需真机测试。登录失效遵循服务端规则，不能承诺永久登录。

本项目与 AgentScope 官方无隶属关系。不复制官方商标、账号数据或私密配置；Agent 后端仍使用用户自己的官方服务。
