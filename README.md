# 学不通

**软件完全免费，禁止商用，仅供非商业用途。** 项目公开源码；适用范围和教育、公益机构等例外以 [PolyForm Noncommercial License 1.0.0](LICENSE) 原文为准。第三方组件及素材遵循各自的授权条款。

学不通是一款 Android 学习辅助工具，支持无障碍读取页面文字、整屏本地文字识别及截图搜题。可配置自己的 AI 供应商和模型。

- [项目介绍页](https://1xuda.github.io/xuebutong/)
- [前往介绍页下载 6.1.7](https://1xuda.github.io/xuebutong/#download)
- 遇到 bug 请联系作者企鹅：**2899145437**，或通过本仓库 Issues 反馈。

## 界面预览

首页与题库截图来自 6.1.6，模型和更新页面截图来自 6.1.5；题库中的题目为示例。

| 首页 | 模型与 API | 软件更新 |
| :---: | :---: | :---: |
| <img src="docs/assets/screen-home-616.png" alt="学不通首页" width="240"> | <img src="docs/assets/screen-model.png" alt="模型供应商配置" width="240"> | <img src="docs/assets/screen-update.png" alt="软件更新" width="240"> |

答案显示设置（6.1.7）：

<img src="docs/assets/screen-answer-settings.png" alt="答案显示自定义设置" width="240">

题库界面：

<img src="docs/assets/screen-bank.png" alt="本地题库界面与示例题目" width="240">

## 使用须知

### 答案显示设置

在“应用设置 → 答案显示设置”或答案大窗的设置按钮中，可调整窗口大小、颜色、内容、模板、显示时长与复制方式。格式支持 `{题目}`、`{答案}`、`{解析}`、`{模型名称}` 和 `{答案来源}`。模型未按格式返回时会完整显示原文，此时不能可靠地拆分答案与解析。截图模式中的题目可能来自视觉模型提取，应自行核对。

### 本地题库

在首页“题库”中可查看或导出内置 XLSX 模板，按“题库”工作表填写后导入；“填写示例”工作表不会导入。导入会先检查必填字段和重复题目。可在题库页面增删改查，并用“优先使用题库”开关决定是否在作答时参考本地题库。选择题仅在题干和选项完整匹配时参考，并按选项内容换算答案；无法可靠匹配时继续使用 AI 作答。截图模式启用题库后会先在本机 OCR 并请求 AI 整理题目，未匹配则仍使用原截图请求视觉模型。模型可能产生错误，题库答案也需要用户核对。

软件本身免费。AI 模型请求需要联网，第三方模型服务商可能收取费用，费用及模型权限以供应商为准。屏幕文字模式在本机识字；截图搜题会向所选视觉模型供应商发送整屏截图。API Key 和历史记录保存在本机应用数据中，请勿在 Issues、截图或提交内容中公开密钥。悬浮窗、无障碍和屏幕共享权限按所选功能在设备上授权。

应用内更新目前仍使用 `https://xudaco.online/xuebutong-site/releases/latest.json`，由原项目网站提供；GitHub Pages 是独立的介绍和下载入口。下载的新 APK 安装需用户确认，并要求与已安装版本签名匹配。

## 构建

需要 JDK 17、Android SDK Platform 34 和 Android Build Tools。首次构建需要网络下载 Gradle、Android Gradle Plugin 和 ONNX Runtime。

```powershell
.\gradlew.bat :android-app:assembleDebug
```

Debug 包位于 `android-app/build/outputs/apk/debug/`。项目不包含发布签名密钥；自行构建、使用不同签名的 APK 无法直接覆盖安装原签名版本。当前直接分发版本最低 Android 7.0（API 24），targetSdk 31。

## 页面与许可证

`docs/` 是 GitHub Pages 的静态介绍页面，使用 `main` 分支的 `/docs` 目录发布。项目代码按 [PolyForm Noncommercial 1.0.0](LICENSE) 提供源码，禁止商业用途；该许可证不属于 OSI 定义的开源许可证。

项目所含第三方组件、OCR 模型及供应商图标不因项目许可证而改变各自授权，见 [第三方说明](THIRD_PARTY.md) 和 `third-party-licenses/`。原启动页素材由项目维护者确认有公开及再发布授权。
