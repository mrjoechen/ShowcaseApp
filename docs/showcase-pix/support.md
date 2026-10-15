---
layout: product
title: Showcase Pix Support
description: Help and contact for Showcase Pix on iPhone, iPad and Apple TV / 帮助与联系方式
nav: support
hero_title: Support
---

[English](#english) · [中文](#中文)

## English

### Contact us

Email **[mrjctech@gmail.com](mailto:mrjctech@gmail.com)**. To help us look into a problem, please include your device model, your iOS, iPadOS or tvOS version, and the app version shown in **Settings > About**.

You can also send feedback from **Settings > About > Feedback** in the app. The in-app form is available while **Share Usage & Crash Data** is on; email works either way.

Please do not send passwords, API keys, or private server addresses.

### Getting started

1. Open the app. On first launch it asks whether to share usage and crash data. Either choice works, and you can change it later in **Settings**.
2. Tap **+** and choose **Gallery** to pick photos from your library, or add one of your own network sources. On Apple TV, select **+** with the Siri Remote and add a network source, or set up sources on iPhone or iPad and sync them with iCloud.
3. Tap a source to start playback. Open **Settings** to choose a display style and adjust its options.

### Frequently asked questions

**Which photo sources can I use?**
Your photo library (on iPhone and iPad), SMB shares, WebDAV servers, S3-compatible storage, Immich, MT Photos, RSS feeds, and GitHub or Gitee repositories that you choose. Network sources connect directly from your device to the server you enter.

**Why does the app ask for local network access?**
To reach the NAS, SMB, WebDAV, or self-hosted photo servers you add on your home network. You can change this in iOS **Settings > Privacy & Security > Local Network**, or in the Privacy settings of your Apple TV.

**Why does the app ask for my location?**
Only to show the weather next to the clock. Coordinates are rounded to about 1 km before they are sent to the Open-Meteo weather service. If you do not use the clock and weather overlay, location access is not needed, and you can turn it off in iOS **Settings > Privacy & Security > Location Services**, or in the Privacy settings of your Apple TV.

**Some photos do not appear.**
On iPhone and iPad, if you allowed access to selected photos only, add more in iOS **Settings > Privacy & Security > Photos > Showcase**, or pick them again when adding the Gallery source.

**Does the screen stay on during playback?**
Yes. The screen stays on while photos are playing. When you leave playback, your device's Auto-Lock or screen saver setting applies again.

**How do AI image summaries work?**
They are optional and off by default. The app has no built-in AI service: you enter your own service address and API key in **Settings > AI image services**. Before any photo is sent, the app asks for your consent and shows the service's domain. By default, faces are detected on your device and photos that contain faces are not uploaded. Turn off **Show AI image summaries** in the display settings to stop sending photos. Apple TV cannot check photos for faces, so with face privacy on, no photos are sent from Apple TV.

**How do I change my usage and crash data choice?**
Go to **Settings > General > Share Usage & Crash Data**.

**Do my settings sync between devices?**
Showcase Pix can sync your settings and source configurations across your Apple devices that use the same iCloud account, which is also the easiest way to set up Apple TV. You can turn sync off in **Settings > iCloud Sync**.

**Can I use Showcase Pix on Apple TV?**
Yes, on Apple TV with tvOS 16.0 or later. Apple TV has no photo library, so it plays photos from your network sources. Saving and sharing images is not available on Apple TV, and tvOS may remove cached photos and saved AI summaries when storage runs low.

**How do I delete my data?**
Photos and settings are stored on your device and are removed when you delete the app. To ask us to delete usage, crash, or feedback records, email us from the address above.

### Privacy and terms

- [Privacy Policy]({{ '/showcase-pix/privacypolicy' | relative_url }})
- [Terms of Use]({{ '/showcase-pix/termsconditions' | relative_url }})

## 中文

### 联系我们

请发送邮件至 **[mrjctech@gmail.com](mailto:mrjctech@gmail.com)**。为便于排查问题，请附上设备型号、iOS、iPadOS 或 tvOS 版本，以及 App 内 **设置 > 关于** 中显示的版本号。

您也可以在 App 内通过 **设置 > 关于 > 反馈** 提交反馈。App 内反馈需要开启 **共享使用与崩溃数据**；发邮件则不受此限制。

请勿在反馈中发送密码、API 密钥或私有服务器地址。

### 快速上手

1. 打开 App。首次启动时会询问是否共享使用与崩溃数据，选择哪一项都可以正常使用，之后也可以在 **设置** 中修改。
2. 点击 **+**，选择 **相册** 从照片图库中挑选照片，或添加您自己的网络图片源。在 Apple TV 上，用 Siri Remote 选择 **+** 并添加网络图片源，或在 iPhone、iPad 上设置好图片源后通过 iCloud 同步过来。
3. 点击图片源开始播放。在 **设置** 中选择展示样式并调整选项。

### 常见问题

**支持哪些图片来源？**
照片图库（iPhone 和 iPad）、SMB 共享、WebDAV 服务器、S3 兼容存储、Immich、MT Photos、RSS 订阅，以及您指定的 GitHub 或 Gitee 仓库。网络图片源由您的设备直接连接您填写的服务器。

**为什么需要访问本地网络？**
用于连接您添加的家庭网络中的 NAS、SMB、WebDAV 或自建相册服务器。可以在 iOS **设置 > 隐私与安全性 > 本地网络** 中修改，或在 Apple TV 设置的隐私选项中修改。

**为什么需要获取位置？**
仅用于在时钟旁显示天气。发送给 Open-Meteo 天气服务前，经纬度会取整到约 1 公里精度。如果不使用时钟与天气，就不需要位置权限，可以在 iOS **设置 > 隐私与安全性 > 定位服务** 中关闭，或在 Apple TV 设置的隐私选项中关闭。

**部分照片没有显示。**
在 iPhone 和 iPad 上，如果您只允许访问部分照片，可以在 iOS **设置 > 隐私与安全性 > 照片 > Showcase** 中添加，或在添加相册图片源时重新选择。

**播放时屏幕会保持常亮吗？**
会。播放照片期间屏幕保持常亮；退出播放后，恢复设备原有的自动锁定或屏幕保护程序设置。

**AI 图片摘要如何工作？**
这是可选功能，默认关闭。App 不内置 AI 服务，需要您在 **设置 > AI 图片服务** 中填写自己的服务地址和 API 密钥。发送任何照片之前，App 都会征求您的同意，并显示该服务的域名。默认情况下，系统会在设备本地检测人脸，含人脸的照片不会上传。在展示设置中关闭 **显示 AI 图片摘要** 即可停止发送照片。Apple TV 无法检测照片中的人脸，因此在开启人脸隐私保护时，不会从 Apple TV 发送任何照片。

**如何修改使用与崩溃数据的共享选择？**
前往 **设置 > 常规 > 共享使用与崩溃数据**。

**设置会在设备之间同步吗？**
Showcase Pix 可以在使用同一 iCloud 账户的 Apple 设备之间同步设置和图片源配置，这也是设置 Apple TV 最方便的方式。您可以在 **设置 > iCloud 同步** 中关闭同步。

**可以在 Apple TV 上使用 Showcase Pix 吗？**
可以，需要运行 tvOS 16.0 及以上版本的 Apple TV。Apple TV 没有照片图库，因此会播放来自网络图片源的照片。Apple TV 上无法保存或分享图片；存储空间不足时，tvOS 可能清理缓存的照片和已保存的 AI 摘要。

**如何删除我的数据？**
照片和设置保存在您的设备上，删除 App 即会移除。如需删除使用数据、崩溃数据或反馈记录，请通过上方邮箱联系我们。

### 隐私与条款

- [隐私政策]({{ '/showcase-pix/privacypolicy' | relative_url }})
- [使用条款]({{ '/showcase-pix/termsconditions' | relative_url }})
