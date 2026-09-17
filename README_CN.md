<p align="center">
  <img src="./docs/Showcase.png" alt="Showcase" width="100" height="100"/>
</p>
<h1 align="center">Showcase App</h1>
<p align="center">
  <a href="https://github.com/mrjoechen/ShowcaseApp/releases/latest"><img src="https://img.shields.io/github/downloads/mrjoechen/ShowcaseApp/total?style=flat" alt="Downloads"></a>
  <a href="https://t.me/showcase_app_release"><img src="https://img.shields.io/badge/showcase-telegram-blue?style=flat&logo=telegram" alt="Telegram"></a>
  <a href="https://github.com/mrjoechen/ShowcaseApp/stargazers"><img src="https://img.shields.io/github/stars/mrjoechen/ShowcaseApp" alt="stars"></a>
  <a href="https://ko-fi.com/joechen"><img src="https://img.shields.io/badge/ko--fi-Buy_me_a_coffee-ff5f5f?logo=ko-fi&style=for-the-badgeKo-fi" alt="ko-fi"></a>
</p>

<p align="center">
  <a href="README.md">English</a> | <b>中文</b>
</p>

> 本代码仓库正在进行多平台迁移，代码尚未完成，敬请期待。

## 概述

ShowcaseApp 是一款将设备变成数字相框的应用。它将本地照片与网络图片源汇集在一起，支持幻灯片、照片墙、瀑布流、焦点图片墙和 Duo Fold 折叠切换等多种展示方式，还可在播放时显示天气、照片 EXIF 信息，以及按需开启的 AI 图片摘要，让照片展示更丰富。

[<img src="/docs/images/google-play-badge.png" width="323" height="125" />](https://play.google.com/store/apps/details?id=com.alpha.showcase)
[<img src="/docs/images//github-badge.png" width="323" height="125" />](https://github.com/mrjoechen/ShowcaseApp/releases/latest)

> iOS 目前在 TestFlight.

[<img src="/docs/images/apple_app_store_badge.png" width="345" height="125" />](https://testflight.apple.com/join/D8va19RR)

## 预览

<div style="width:100%; display:flex; justify-content:space-between;">
  <a href="https://raw.githubusercontent.com/mrjoechen/ShowcaseApp/main/docs/images/screenshot_home.png">
    <img src="/docs/images/screenshot_home.png" width="30%">
  </a>
  <a href="https://raw.githubusercontent.com/mrjoechen/ShowcaseApp/main/docs/images/screenshot_setting1.png">
    <img src="/docs/images/screenshot_setting1.png" width="30%">
  </a>
  <a href="https://raw.githubusercontent.com/mrjoechen/ShowcaseApp/main/docs/images/screenshot_setting2.png">
    <img src="/docs/images/screenshot_setting2.png" width="30%">
  </a>
</div>

<video src="https://github.com/user-attachments/assets/5643c4ab-a5ba-4608-aadf-0e7913dafafc" width="2335" height="600"></video>

## 功能特性

### 🖼 多种图片来源

- 本地设备存储
- FTP/SFTP 服务器
- SMB 网络共享
- WebDAV 存储库
- TMDB 电影海报（正在上映、即将上映、高分榜和热门榜）
- Unsplash 图片源（您喜欢的或收藏的照片）
- Pexels 图片源（您的收藏）
- GitHub 仓库（包含图片/视频文件）

### 🎨 可自定义的展示样式

| 样式 | 展示效果与设置 |
| --- | --- |
| 幻灯片 | 支持立方体、揭示、轮播、折叠等切换效果，可设置播放间隔和图片适配方式。 |
| 淡入淡出 | 以柔和的渐变过渡切换照片。 |
| 照片墙 | 以网格同时展示多张照片，可调整行列数与相框样式。 |
| 日历 | 将照片与日历组合展示，支持自动播放。 |
| Bento（便当布局） | 使用大小不同的区域拼贴展示照片。 |
| 瀑布流 | 以错落排列的照片布局自动滚动，支持垂直或水平方向，可调整列数或行数、滚动速度和间距。 |
| 焦点图片墙 | 在照片墙中移动焦点并放大当前照片，可调整照片停留时间、尺寸、焦点突出程度、间距和图片适配方式。 |
| Duo Fold（幻灯片效果） | 通过带有透视和渐变模糊的折叠动画切换照片；可开启翻页时缩小画面、结束后恢复的全局视角效果。 |

使用 Duo Fold 时，在展示设置中选择 **幻灯片 → 滑动效果 → Duo Fold**，通过 **翻页全局视角** 开关控制翻页时的缩小效果。Duo Fold 需要开启硬件加速的 Android 12 及以上设备，或支持模糊效果的桌面端、iOS 环境。浏览器端暂不提供该效果；不支持的设备会隐藏选项，并将已保存的 Duo Fold 配置回退为普通幻灯片播放。

### 🌤️ 天气、时间与日期

- 开启 **显示时间和日期**，即可在播放时查看时钟、日期、星期、当前摄氏温度和天气状况。
- 天气图标与动态背景随天气状况变化，天气数据会自动刷新。
- 天气功能需要网络连接及可用的位置信息，请在提示时允许位置访问；天气暂时无法获取时，时钟和日期仍会正常显示。

### 📷 照片 EXIF 信息

- 在幻灯片、淡入淡出和日历模式中，与照片交互即可唤出信息面板，面板会在短暂停留后自动隐藏。
- 可查看照片中已有的拍摄时间、相机与镜头、光圈、快门速度、ISO、焦距、图片尺寸和文件大小等信息；带有位置元数据的照片还可展示拍摄地点。
- 具体显示字段取决于照片及图片源保留的信息。没有 EXIF 的照片仍可正常播放，并展示可获取的基础文件信息。

### ✨ AI 图片摘要

- **图片摘要**：在幻灯片、淡入淡出和日历模式下，为当前照片生成内容描述、简短文案和标签，生成语言跟随应用当前语言。
- **缓存与重试**：已生成的摘要会缓存以便复用；双击摘要区域可重新生成，失败时也可双击重试。

开启图片摘要：

1. 在设置中的 AI 配置里添加 **AI 图片理解** 服务，填写您自己的 Base URL、API Token，以及支持图片理解的模型。
2. 选择该配置，并在幻灯片、淡入淡出或日历的设置中开启 **显示 AI 图片摘要**。
3. 开始播放，摘要会随照片展示自动生成并显示。

AI 图片摘要适用于 Android、iOS 和桌面客户端，浏览器端隐藏相关入口；使用时，图片会发送至您配置的模型服务进行处理。

面向开发者的 Kotlin Multiplatform AI 能力模块已包含在本仓库中，详见 [AI 模块与构建说明](ai-model-capabilities/README.md#multiplatform-integration)。

### 🛠️ 灵活的配置

管理图片来源，并按展示样式调整播放间隔、图片适配和布局选项。还可配置排序方式、包含子文件夹、自动刷新图片源及播放窗口全屏显示，按自己的使用场景打造数字相框。

### 💡 灵感来源

ShowcaseApp 的创意来自两个主要灵感：

- macOS 上优雅的照片墙屏保，可以将任何图片文件夹变成美丽的视觉展示
- 希望将闲置的 Android 设备（手机或平板）重新用作数字相框，展示珍贵的回忆

作为一名 Android 工程师，我想将这些概念结合成一个精致的应用程序，让闲置的设备焕发新生，同时创造一种美观的方式来欣赏照片。

## 💖 [赞助者](https://github.com/mrjoechen/ShowcaseApp/blob/main/docs/sponsors.md)

非常感谢所有支持这个项目的个人和组织。您的贡献有助于保持项目的活力，让我能够投入更多时间进行开发。查看 [赞助者名单](https://mrjoechen.github.io/ShowcaseApp/sponsors)。

## 支持项目

如果您觉得 ShowcaseApp 有用，并希望支持其持续开发，非常感谢您的捐赠。任何金额的捐赠或留下好评都可以帮助维护应用、添加新功能和提升性能。

支持或贡献的方式：

- [Buy Me a Coffee](https://ko-fi.com/joechen)
- [微信赞赏](https://raw.githubusercontent.com/mrjoechen/ShowcaseApp/refs/heads/main/docs/images/wechat_donate.png)
- [GitHub Sponsors](https://github.com/sponsors/mrjoechen)
- [PayPal](https://www.paypal.me/chenqiao1104)
- [Google Play](https://play.google.com/store/apps/details?id=com.alpha.showcase)

所有支持者都将在应用的致谢中被提及（除非您希望保持匿名）。对于大额捐赠，您将获得 Beta 功能的早期访问权限以及直接提供功能建议的渠道。

## 👋 欢迎加入！

### Telegram 群组
[<img src="/docs/images/showcase_telegram_group.png" width="170" height="252" />](https://t.me/showcase_app_group)

### 微信群
[<img src="/resource/showcase_wechat_group.jpg" width="170" height="252" />](https://raw.githubusercontent.com/mrjoechen/ShowcaseApp/main/resource/showcase_wechat_group.jpg)

- [捐赠](https://mrjoechen.github.io/ShowcaseApp/donate)
- [隐私政策](https://mrjoechen.github.io/ShowcaseApp/privacypolicy)
- [条款和条件](https://mrjoechen.github.io/ShowcaseApp/termsconditions)
- [Telegram 频道](https://t.me/showcase_app_release)

## 许可证

本项目采用双许可证模型：

- 非商用：遵循 [PolyForm Noncommercial 1.0.0](licenses/PolyForm-Noncommercial-1.0.0.txt)
- 商用：需要单独签署书面商业授权协议，见 [COMMERCIAL_LICENSE.md](COMMERCIAL_LICENSE.md)

适用范围和选择规则请见 [LICENSE](LICENSE)。

### 第三方字体

ShowcaseApp 的 Web 版本使用由小米科技有限责任公司提供的 [MiSans Normal](composeApp/src/webMain/composeResources/font/MiSansNormal.ttf)。MiSans 的使用单独受 [《MiSans 字体知识产权许可协议》](composeApp/src/webMain/composeResources/files/licenses/MiSans-Font-License-Agreement.pdf)约束，不适用 ShowcaseApp 的双许可证条款。更多信息请参阅 [MiSans 官方网站](https://hyperos.mi.com/font/zh/)。

## Star 历史

[![Star History Chart](https://api.star-history.com/svg?repos=mrjoechen/ShowcaseApp&type=Date)](https://star-history.com/#mrjoechen/ShowcaseApp&Date)
