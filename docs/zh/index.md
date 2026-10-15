---
title: Showcase
description: 把您的设备变成数字相框
nav: overview
hero: large
ctas:
  - label: 在 Google Play 下载
    url: https://play.google.com/store/apps/details?id=com.alpha.showcase
  - label: 下载 APK
    url: https://github.com/mrjoechen/ShowcaseApp/releases/latest
    secondary: true
  - label: 在浏览器中使用
    url: /web/
    secondary: true
---

Showcase 是一款将设备变成数字相框的应用。它将本地照片与网络图片源汇集在一起，支持幻灯片、照片墙、瀑布流、焦点照片墙和 Duo Fold 折叠切换等多种展示方式，还可在播放时显示天气、照片拍摄信息，以及按需开启的 AI 图片摘要，让照片展示更丰富。
{: .lead}

<div class="gallery">
  <img src="{{ '/images/screenshot_home_zh.png' | relative_url }}" alt="首页的图片源列表" loading="lazy">
  <img src="{{ '/images/screenshot_add_source_zh.png' | relative_url }}" alt="选择新的图片源" loading="lazy">
  <img src="{{ '/images/screenshot_settings_1_zh.png' | relative_url }}" alt="展示样式设置" loading="lazy">
  <img src="{{ '/images/screenshot_settings_2_zh.png' | relative_url }}" alt="播放与通用设置" loading="lazy">
</div>

## 支持的平台

<div class="cards">
  <div class="card"><h4>Android 手机和平板</h4><p>在 <a href="https://play.google.com/store/apps/details?id=com.alpha.showcase">Google Play</a> 下载，或从 <a href="https://github.com/mrjoechen/ShowcaseApp/releases/latest">GitHub Releases</a> 下载 APK。</p></div>
  <div class="card"><h4>网页版</h4><p>无需安装，<a href="{{ '/web/' | relative_url }}">直接在浏览器中使用 Showcase</a>。浏览器中不提供 AI 摘要和 Duo Fold。</p></div>
  <div class="card"><h4>iPhone、iPad 和 Apple TV</h4><p>在 App Store 上名为 Showcase Pix。<a href="{{ '/showcase-pix/zh/' | relative_url }}">了解 Showcase Pix</a>。</p></div>
  <div class="card"><h4>开源</h4><p><a href="https://github.com/mrjoechen/ShowcaseApp">源代码</a> 以非商业许可证发布在 GitHub 上。</p></div>
</div>

## 实际效果

<video src="https://github.com/user-attachments/assets/5643c4ab-a5ba-4608-aadf-0e7913dafafc" controls playsinline preload="metadata" style="width:100%;border-radius:18px"></video>

## 好照片，不止一个来源

<div class="cards">
  <div class="card"><h4>设备存储</h4><p>设备上的文件夹，以及手机和平板的照片图库。</p></div>
  <div class="card"><h4>FTP、SFTP、SMB 与 WebDAV</h4><p>文件服务器和网络共享，包括家庭网络中的 NAS。</p></div>
  <div class="card"><h4>S3 兼容存储</h4><p>任意 S3 兼容存储服务中的存储桶。</p></div>
  <div class="card"><h4>Immich 与 MT Photos</h4><p>自建相册服务器中的相册。</p></div>
  <div class="card"><h4>RSS 订阅</h4><p>您所关注订阅源中的图片。</p></div>
  <div class="card"><h4>GitHub 与 Gitee</h4><p>您指定仓库中的图片和视频文件。</p></div>
  <div class="card"><h4>TMDB</h4><p>正在上映、即将上映、高分榜和热门榜的电影海报。</p></div>
  <div class="card"><h4>Unsplash 与 Pexels</h4><p>您喜欢的照片和收藏。</p></div>
</div>

可添加的图片源因平台而异。

## 展示样式

<div class="cards">
  <div class="card"><h4>幻灯片</h4><p>立方体、揭示、轮播、翻转等切换效果，可设置播放间隔和图片适配方式。</p></div>
  <div class="card"><h4>Duo Fold</h4><p>带有透视和渐变模糊的折叠翻页效果；开启全局视角后，翻页时画面会先缩小再恢复。</p></div>
  <div class="card"><h4>淡入淡出</h4><p>以柔和的渐变过渡切换照片。</p></div>
  <div class="card"><h4>照片墙</h4><p>以网格同时展示多张照片，可调整行列数与相框样式。</p></div>
  <div class="card"><h4>日历</h4><p>将照片与日历组合展示，支持自动播放。</p></div>
  <div class="card"><h4>Bento</h4><p>用大小不同的区域拼贴展示照片。</p></div>
  <div class="card"><h4>瀑布流</h4><p>错落排列的照片沿垂直或水平方向自动滚动，可调整列数或行数、滚动速度和间距。</p></div>
  <div class="card"><h4>焦点照片墙</h4><p>在照片墙中移动焦点并放大当前照片，可调整停留时间、尺寸、焦点突出程度、间距和图片适配方式。</p></div>
</div>

使用 Duo Fold 时，在展示设置中选择 **幻灯片 → 滑动效果 → Duo Fold**。Duo Fold 需要开启硬件加速的 Android 12 及以上设备，或支持模糊效果的 Apple 设备和桌面端。不支持的设备会隐藏该选项，并将已保存的 Duo Fold 配置回退为普通幻灯片。

## 时间、日期与天气

开启 **显示时间和日期** 后，播放时即可查看时钟、日期、星期、当前摄氏温度和天气状况。天气图标与动态背景随天气变化，并会自动刷新。天气功能需要网络连接及位置信息；天气暂时无法获取时，时钟和日期仍会正常显示。

## 照片里的故事，不止于画面

在幻灯片、淡入淡出和日历模式中，与照片交互即可查看它保留的信息：拍摄时间、相机与镜头、光圈、快门速度、ISO、焦距、图片尺寸、文件大小；带有位置信息的照片还会显示拍摄地点。信息面板会在片刻后自动隐藏。没有 EXIF 的照片仍可正常播放，并展示可获取的基础文件信息。

## AI 图片摘要

连接您自己的 AI 图片理解服务后，可以在幻灯片、淡入淡出和日历模式中，为当前照片生成内容描述、简短文案和标签，语言跟随应用当前语言。摘要会保存以便复用，双击即可重新生成或重试失败的请求。

1. 在设置中打开 AI 配置，添加 **AI 图片理解** 服务，填写您自己的 Base URL、API Token，以及支持图片理解的模型。
2. 选择该配置，并在幻灯片、淡入淡出或日历的设置中开启 **显示 AI 图片摘要**。
3. 开始播放，摘要会随照片展示自动出现。

AI 图片摘要适用于 Android、Apple 设备和桌面客户端，浏览器中不提供。照片只会发送至您配置的服务，不会上传到 Showcase 服务器。上传前，每张照片都会重新编码并去除原始元数据，长边不超过 768 像素、大小不超过 512 KB。人脸隐私保护默认开启，可能包含人脸或无法完成检测的照片会被跳过。在 Apple 设备上，发送第一张照片之前，应用会征求您的同意并显示服务的域名。使用前请查阅 AI 服务商的隐私政策。

面向开发者的 Kotlin Multiplatform AI 能力模块已包含在仓库中，详见 [AI 模块与构建说明](https://github.com/mrjoechen/ShowcaseApp/blob/main/ai-model-capabilities/README.md#multiplatform-integration)。

## 按您的方式设置

管理图片来源，并按展示样式分别调整播放间隔、图片适配与布局。还可以设置排序方式、包含子文件夹、自动刷新图片源，以及全屏播放，打造适合自己的数字相框。

## 灵感来源

Showcase 的创意来自两个灵感：macOS 上优雅的照片墙屏保，可以把任何图片文件夹变成流动的画面；以及希望让闲置的 Android 手机和平板重新成为展示珍贵回忆的数字相框。作为一名 Android 工程师，我想把两者结合成一个精致的应用，让每天看到自己的照片成为一种享受。

## 支持项目

如果 Showcase 对您有帮助，一条好评或一份捐赠都能帮助它持续维护和改进。请查看 [捐赠方式]({{ '/zh/donate' | relative_url }})，以及已经支持本项目的 [赞助者]({{ '/zh/sponsors' | relative_url }})。谢谢您！

## 社区

- [Telegram 频道](https://t.me/showcase_app_release)：获取版本更新
- [Telegram 群组](https://t.me/showcase_app_group)：与其他用户交流

<div class="qr">
  <figure><img src="{{ '/images/showcase_telegram_group.png' | relative_url }}" alt="Showcase Telegram 群组二维码" loading="lazy"><figcaption>Telegram 群组</figcaption></figure>
  <figure><img src="https://raw.githubusercontent.com/mrjoechen/ShowcaseApp/main/resource/showcase_wechat_group.png" alt="Showcase 微信群二维码" loading="lazy"><figcaption>微信群</figcaption></figure>
</div>

## 许可证

本项目采用双许可证模型：

- 非商业用途：[PolyForm Noncommercial 1.0.0](https://github.com/mrjoechen/ShowcaseApp/blob/main/licenses/PolyForm-Noncommercial-1.0.0.txt)
- 商业用途：需要另行签订书面协议，详见 [COMMERCIAL_LICENSE.md](https://github.com/mrjoechen/ShowcaseApp/blob/main/COMMERCIAL_LICENSE.md)

许可证的选择方式与适用范围详见 [LICENSE](https://github.com/mrjoechen/ShowcaseApp/blob/main/LICENSE)。网页版使用小米公司提供的 MiSans Normal 字体，该字体受 [MiSans 字体知识产权许可协议](https://github.com/mrjoechen/ShowcaseApp/blob/main/composeApp/src/webMain/composeResources/files/licenses/MiSans-Font-License-Agreement.pdf) 约束。
