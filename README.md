# 网络攻防（NetSec）

DeepDeep 的外部插件：把安全作业口径、红队 runbook 与交付纪律注入会话。

- **提示词**：覆盖 Agent 系统提示词的 preamble / rules，注入正文随插件资产分发；可在插件页编辑口径段。
- **输入路由**：用户消息命中安全或破解类意图时，本轮消息附带对应 runbook 原文（最多两份）；`purge_playbook` 工具可随时按 id 取原文。
- **拒答兜底**：整轮只有拒答文本时，向会话补一条可直接使用的 runbook / 找片 / 逆向模板。
- **插件页**：状态、口径编辑、强化段与兜底开关、runbook 浏览、注入探针、最近动作。

## 安装

插件只从 `.ddp` 安装，不随 App 编译。把 `build/plugin/purge.ddp` 传到手机，用文件管理器以 DeepDeep 打开、分享给 DeepDeep，或在插件页选择「导入插件」。首次导入需要确认发布者签名与 keyId；之后升级必须使用同一发布者密钥签名。

插件与 App 同进程、同 UID、没有沙箱，安装即信任。只安装自己信任的插件。

## 构建

前置条件：JDK 17+、Android SDK（`ANDROID_HOME`/`ANDROID_SDK_ROOT` 或 `local.properties` 的 `sdk.dir`）。依赖从公开仓库 `https://sadami3066.github.io/deepdeep-maven` 解析，`plugin-api` 与 `design` 的版本以该仓库最新发布为准。

```bash
./gradlew testDebugUnitTest   # 单测
./gradlew assemblePlugin      # build/plugin/purge.unsigned.ddp
./gradlew signPlugin          # build/plugin/purge.ddp
./gradlew verifyPlugin        # 验签 + canonical digest 自检
```

签名密钥默认读 `keys/plugin-signing.pem` / `.pub`；密钥不随仓库分发，用 `-PpluginSigningKey=<绝对路径前缀>` 指向仓库外的密钥，或执行 `./gradlew generateSigningKey` 首次生成。`.pem` 私钥不要提交、不要外传：持有者可以以本发布者身份发布更新。

## 发布

1. `./gradlew verifyPlugin` 通过。
2. 记录 `build/plugin/purge.ddp` 的 sha256 与发布者 keyId。
3. 创建 GitHub Release，附上 `.ddp`，在说明里写明 sha256 与 keyId。
4. 页面文案、插件清单与资产一起随版本更新。

## 资产来源

`src/main/assets/purge/**` 中的提示词与 runbook 内容源自 dsh-purge-plus（MIT，作者 小杨 / YuJunZhiXue），经 DeepDeep PR #12 引入后按原样随插件分发；见 `NOTICE`。插件代码同样以 MIT 发布。
