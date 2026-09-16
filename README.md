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
  <b>English</b> | <a href="README_CN.md">中文</a>
</p>

> This code repository is undergoing multi-platform migration. The code is not yet complete. Please stay tuned.

## Overview

ShowcaseApp transforms your devices into digital photo frames. It brings local photos and network image sources together in customizable layouts, from slideshows and photo walls to Waterfall, Focus Photo Wall, and Duo Fold transitions. Weather, photo EXIF information, and optional AI image summaries add context to your photos as they play.

[<img src="/docs/images/google-play-badge.png" width="323" height="125" />](https://play.google.com/store/apps/details?id=com.alpha.showcase)
[<img src="/docs/images//github-badge.png" width="323" height="125" />](https://github.com/mrjoechen/ShowcaseApp/releases/latest)

> The iOS version is still on TestFlight.

[<img src="/docs/images/apple_app_store_badge.png" width="323" height="125" />](https://testflight.apple.com/join/D8va19RR)

## Preview

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

## Features

### 🖼 Multiple Image Sources

- Local device storage
- FTP/SFTP servers
- SMB network shares
- WebDAV repositories
- TMDB movie posters (Now playing, Upcoming, Top rated and Popular)
- Unsplash image source (Your liked photos or collections)
- Pexels image source (Your collections)
- GitHub repositories (with Image/Video files)

### 🎨 Customizable Display Styles

| Style | Highlights |
| --- | --- |
| Slideshow | Browse photos with Cube, Reveal, Carousel, and Flip effects, with configurable playback intervals and image fitting. |
| Fade | Gentle fade transitions between photos. |
| Picture Wall | Display multiple photos in a grid with configurable rows, columns, and frames. |
| Calendar | Combine photos with a calendar and optional automatic playback. |
| Bento | Arrange photos in a collage of differently sized tiles. |
| Waterfall | Automatically scroll through a staggered photo layout vertically or horizontally; adjust the column or row count, scroll speed, and spacing. |
| Focus Photo Wall | Move across a photo wall while enlarging the photo in focus; adjust its display duration, photo size, focus emphasis, spacing, and image fitting. |
| Duo Fold (Slideshow effect) | Turn photos with a folding transition, perspective, and gradient blur. The optional full-view effect pulls the image back during the turn and restores it afterward. |

To use Duo Fold, select **Slide → Slide effect → Duo Fold** in the display settings. **Full view during page turn** controls the pullback effect. Duo Fold requires Android 12 or later with hardware acceleration, or a desktop/iOS environment with blur support. It is currently unavailable in browsers; unsupported devices hide the option and fall back to a regular slide transition for saved Duo Fold settings.

### 🌤️ Weather, Time, and Date

- Enable **Show time and date** to display the clock, date, weekday, current temperature in Celsius, and weather conditions during playback.
- Weather icons and animated backgrounds reflect the current conditions, with weather data refreshed automatically.
- Weather requires network access and an available location. Allow location access when prompted; if weather cannot be loaded, the clock and date remain visible.

### 📷 Photo EXIF Information

- In Slideshow, Fade, and Calendar modes, interact with a photo to reveal its information overlay, which automatically hides after a short time.
- View available details such as capture time, camera and lens, aperture, shutter speed, ISO, focal length, image dimensions, and file size. Photos with location metadata can also show where they were taken.
- The displayed fields depend on the photo and its source. Photos without EXIF data still play normally and show any available basic file information.

### ✨ AI Image Summaries and Creation

- **Image summaries:** Generate a description, a short caption, and tags for the displayed photo in Slideshow, Fade, and Calendar modes. The requested output language follows the app's current language.
- **Reuse and retry:** Generated summaries are cached for reuse. Double-click or double-tap the summary area to regenerate a result or retry a failed request.
- **Image creation:** Use a photo as the starting point for AI image-to-image generation, then view results, compare the original and generated images, and save them from **AI Creations**.

To enable image summaries:

1. Open the AI configuration in settings and add an **AI Image Understanding** service using your own Base URL, API Token, and a model that supports image understanding.
2. Select the configuration, then enable **Show AI image summaries** in the Slideshow, Fade, or Calendar settings.
3. Start playback. Summaries appear automatically as photos are displayed.

AI image generation uses a separate **AI Image-to-Image** configuration. AI features are available in Android, iOS, and desktop clients; browser versions hide these controls. Images are sent to the model service you configure for processing.

For developers, the Kotlin Multiplatform AI capability module is included in this repository. See [AI module and build instructions](ai-model-capabilities/README.md#multiplatform-integration).

### 🛠️ Flexible Configuration

Manage image sources and customize each display style, including playback intervals, image fitting, and layout options. Configure sorting, include subfolders, refresh sources automatically, or open playback in full screen to suit your photo frame setup.

### 💡 Source of inspiration
The idea for ShowcaseApp came from two main inspirations:

- The elegant Picture Wall screensaver on macOS that turns any folder of images into a beautiful visual display
- A desire to repurpose idle Android devices photo or pad as digital photo frames for displaying cherished memories

As an Android engineer, I wanted to combine these concepts into a single, polished application that would breathe new life into unused devices while creating an aesthetically pleasing way to enjoy photos.

## 💖 [Sponsors](https://github.com/mrjoechen/ShowcaseApp/blob/main/docs/sponsors.md)

A huge thank you to all the individuals and organizations that support this project. 
Your contributions help keep the project alive and allow me to dedicate more time to its development. Checkout this [List](https://mrjoechen.github.io/ShowcaseApp/sponsors).

## Supporting the Project
If you find ShowcaseApp useful and would like to support its continued development, your donations are greatly appreciated. Contributions of any size or leave a good review can help maintain the app, add new features, and improve performance.
Ways to contribute or support:

- [Buy Me a Coffee](https://ko-fi.com/joechen)
- [WeChat Donate](https://raw.githubusercontent.com/mrjoechen/ShowcaseApp/refs/heads/main/docs/images/wechat_donate.png)
- [GitHub Sponsors](https://github.com/sponsors/mrjoechen)
- [PayPal](https://www.paypal.me/chenqiao1104)
- [Google Play](https://play.google.com/store/apps/details?id=com.alpha.showcase)

All supporters will be acknowledged in the app's credits (unless you prefer to remain anonymous). For substantial contributions, you'll gain early access to beta features and a direct line to provide feature suggestions.


## 👋 Welcome !
### Telegram Group
[<img src="/docs/images/showcase_telegram_group.png" width="170" height="252" />](https://t.me/showcase_app_group)

### WeChat Group
[<img src="/resource/showcase_wechat_group.jpg" width="170" height="252" />](https://raw.githubusercontent.com/mrjoechen/ShowcaseApp/main/resource/showcase_wechat_group.jpg)

- [Donate](https://mrjoechen.github.io/ShowcaseApp/donate)
- [Privacy policy](https://mrjoechen.github.io/ShowcaseApp/privacypolicy)
- [Terms and conditions](https://mrjoechen.github.io/ShowcaseApp/termsconditions)
- [Telegram Channel](https://t.me/showcase_app_release)

## License

This project uses a dual-license model:

- Noncommercial use: [PolyForm Noncommercial 1.0.0](licenses/PolyForm-Noncommercial-1.0.0.txt)
- Commercial use: requires a separate written agreement, see [COMMERCIAL_LICENSE.md](COMMERCIAL_LICENSE.md)

See [LICENSE](LICENSE) for the license selector and scope.

### Third-party font

The Web build of ShowcaseApp uses [MiSans Normal](composeApp/src/webMain/composeResources/font/MiSansNormal.ttf), provided by Xiaomi Inc. MiSans is governed by the separate [MiSans Font Intellectual Property License Agreement](composeApp/src/webMain/composeResources/files/licenses/MiSans-Font-License-Agreement.pdf) and is not covered by ShowcaseApp's dual-license terms. See the [official MiSans website](https://hyperos.mi.com/font/en/) for more information.

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=mrjoechen/ShowcaseApp&type=Date)](https://star-history.com/#mrjoechen/ShowcaseApp&Date)
