# AgentScope 随行 · 独立 Android 客户端

代码仓库：`xieyinch/idea-lab`。目标是完整重写 AgentScope Platform / QwenPaw 在手机上的页面与交互，优先覆盖全部 Agent 控制能力，同时提供社区、技能和插件市场。

**当前 0.3.1：原生开发预览。没有 WebView，没有加载官方网页，没有 JS 注入。尚未完成全部功能复刻与真机验收。**

## 下载 APK

进入本仓库 **Releases（发行页）** 下载 APK，附版本说明及 SHA256 校验文件。每次 main 提交通过云端单元测试、Lint、编译和模拟器导航检查后自动发布一个预发行版，同时在 Actions 保留构建产物与报告。

最低 Android 8.0（API 26），通用 APK 无 CPU 架构限制。

测试包使用 debug 签名。若需要持续覆盖安装，在仓库 Actions Secret `ANDROID_DEBUG_KEYSTORE_BASE64` 中配置固定 debug keystore（Android 默认 debug 别名与密码）；未配置时各 runner 签名可能不同，需要卸载旧版本再安装。正式发行签名仍待配置。

## 原生页面

- **控制台**：平台部署列表、启动/停止/重启/升级/恢复/重置、日志与资源状态；连接 Agent 实例。
- **对话**：历史记录、新建会话、SSE 流式文字、停止任务、待审批列表及精确允许/拒绝。
- **配置**：智能体、运行配置、模型、工具、技能、MCP、ACP、频道、心跳、定时任务、环境变量、安全配置等。配置使用开关、文本、数值及嵌套原生表单，不让用户编辑原始 JSON。
- **文件**：工作区目录、文本文件编辑及 ETag 冲突保护、上传（20 MB 上限）、系统文件保存下载。
- **终端**：终端创建、输入、输出轮询、Ctrl+C / Tab / 方向键。当前为基础文本终端，尚未实现完整 VT 屏幕仿真。
- **社区**：列表、搜索、详情、点赞、收藏、发帖、提问与回复；技能/插件市场列表和详情。

这些是代码已实现的页面与接口路径，**不代表已完成真实服务端端到端验证**。具体缺口见 [功能清单](docs/FEATURES.md)。

## 登录与连接

1. 在 App 的「我的」或控制台使用平台邮箱密码登录。密码只用于官方登录请求，不保存。
2. 在部署列表选择已有部署，点击「连接 Agent 控制台」。客户端根据平台返回的访问入口获取实例信息。
3. 也可通过「连接实例」配置自己的 HTTPS QwenPaw 地址及实例访问令牌。

平台 Token 与实例 Token 分别存储，通过 Android Keystore 的 AES-GCM 加密，平台 Token 只发送到官方平台，禁止自动跨域重定向和明文 HTTP。接口错误会显示失败，不以占位数据假装成功。

## 构建

JDK 17、Gradle 8.9、Android Gradle Plugin 8.7.3、SDK 35 / Build Tools 35.0.0。

```
gradle testDebugUnitTest lintDebug assembleDebug
```

CI 会安装固定工具版本；当前暂未附带 Gradle Wrapper。测试覆盖流式文本合并、完整入口会话交换、Cookie 恢复与来源隔离、失效入口、跨域跳转拒绝和安卓模拟器的分区导航。完整真机和真实账号端到端测试仍需进行。

## 接口依据与边界

平台接口依据 platform.agentscope.io 公开前端 0.2.5 中的请求定义；实例接口依据 [QwenPaw 官方源码](https://github.com/agentscope-ai/QwenPaw/tree/c84ff8dc0800175de76954556deecef367c51d73/console/src/api)。平台 API 与实例 API 分开适配。未提交用户实例地址、登录信息或私密配置。

项目为非官方客户端，与 AgentScope 官方无隶属关系。首次版本尚不能承诺与平台全部功能等价。

## 0.3.0 界面与认证修复

采用 HIG 启发的语义色、清晰标题、分组内容、稳定底栏和分层操作；保持安卓返回习惯与原生控件。支持系统浅/深色，关闭系统强制反色以避免二次变色。每个分区保留独立返回栈和滚动位置。

参考：[Apple Layout](https://developer.apple.com/design/human-interface-guidelines/layout)、[Apple Tab bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars)。图标为自行绘制的线性图标。

平台托管实例的访问链接会先经由完整 HTTPS 入口建立会话，而不是截成域名后直接请求 API。Cookie 以实例同源为边界，与 Token 一起加密保存；入口失效与实例认证失败分别显示。没有重置、停用或弱化用户实例的认证配置。

云端 `visual-qa` 作业会保存原生页面截图及导航测试报告。真实账户的网关/部署版本仍需安装后核对，不能把模拟服务测试等同于已验证用户实例。

### 0.3.1 入口认证补充修复

平台获取入口接口返回的 Cookie 现在会单独保存，并按浏览器标准的域名/路径/Secure 规则用于官方托管实例的首次导航。host-only Cookie 不会扩散，Bearer Token 不会跨域转发；退出会清除内存及持久会话。新增 HTTPS 模拟服务回归用例覆盖从平台响应到实例入口的整段 Cookie 交接。
