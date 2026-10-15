---
layout: product
title: Showcase
description: Turn your devices into digital photo frames
nav: overview
hero: large
ctas:
  - label: Get it on Google Play
    url: https://play.google.com/store/apps/details?id=com.alpha.showcase
  - label: Download the APK
    url: https://github.com/mrjoechen/ShowcaseApp/releases/latest
    secondary: true
  - label: Open in your browser
    url: /web/
    secondary: true
---

Showcase turns your devices into digital photo frames. It brings local photos and network image sources together in customizable layouts, from slideshows and photo walls to Waterfall, Focus Photo Wall and Duo Fold transitions. Weather, photo details and optional AI image summaries add context to your photos as they play.
{: .lead}

<div class="gallery">
  <img src="{{ '/images/screenshot_home_en.png' | relative_url }}" alt="Photo sources on the home screen" loading="lazy">
  <img src="{{ '/images/screenshot_add_source_en.png' | relative_url }}" alt="Choosing a new photo source" loading="lazy">
  <img src="{{ '/images/screenshot_settings_1_en.png' | relative_url }}" alt="Display style settings" loading="lazy">
  <img src="{{ '/images/screenshot_settings_2_en.png' | relative_url }}" alt="Playback and general settings" loading="lazy">
</div>

## Available on

<div class="cards">
  <div class="card"><h4>Android phones and tablets</h4><p>Get Showcase on <a href="https://play.google.com/store/apps/details?id=com.alpha.showcase">Google Play</a>, or download the APK from <a href="https://github.com/mrjoechen/ShowcaseApp/releases/latest">GitHub Releases</a>.</p></div>
  <div class="card"><h4>Web</h4><p><a href="{{ '/web/' | relative_url }}">Open Showcase in your browser</a> with no installation. AI summaries and Duo Fold are not available in the browser.</p></div>
  <div class="card"><h4>iPhone, iPad and Apple TV</h4><p>Available on the App Store as Showcase Pix. <a href="{{ '/showcase-pix/' | relative_url }}">Learn about Showcase Pix</a>.</p></div>
  <div class="card"><h4>Open source</h4><p>The <a href="https://github.com/mrjoechen/ShowcaseApp">source code</a> is on GitHub under a noncommercial license.</p></div>
</div>

## See it in action

<video src="https://github.com/user-attachments/assets/5643c4ab-a5ba-4608-aadf-0e7913dafafc" controls playsinline preload="metadata" style="width:100%;border-radius:18px"></video>

## Your photos, from more places

<div class="cards">
  <div class="card"><h4>Device storage</h4><p>Folders on your device and the photo library on phones and tablets.</p></div>
  <div class="card"><h4>FTP, SFTP, SMB and WebDAV</h4><p>File servers and network shares, including a NAS on your home network.</p></div>
  <div class="card"><h4>S3-compatible storage</h4><p>Buckets on any S3-compatible storage service.</p></div>
  <div class="card"><h4>Immich and MT Photos</h4><p>Albums on your self-hosted photo server.</p></div>
  <div class="card"><h4>RSS feeds</h4><p>The images in the feeds you follow.</p></div>
  <div class="card"><h4>GitHub and Gitee</h4><p>Image and video files in repositories you choose.</p></div>
  <div class="card"><h4>TMDB</h4><p>Movie posters from Now Playing, Upcoming, Top Rated and Popular.</p></div>
  <div class="card"><h4>Unsplash and Pexels</h4><p>Your liked photos and collections.</p></div>
</div>

The sources you can add vary by platform.

## Display styles

<div class="cards">
  <div class="card"><h4>Slideshow</h4><p>Cube, Reveal, Carousel and Flip effects, with configurable playback intervals and image fitting.</p></div>
  <div class="card"><h4>Duo Fold</h4><p>A slideshow effect with a folding transition, perspective and gradient blur. Full view pulls the image back during the turn.</p></div>
  <div class="card"><h4>Fade</h4><p>Gentle fade transitions between photos.</p></div>
  <div class="card"><h4>Picture Wall</h4><p>Many photos in a grid with configurable rows, columns and frames.</p></div>
  <div class="card"><h4>Calendar</h4><p>Photos combined with a calendar and optional automatic playback.</p></div>
  <div class="card"><h4>Bento</h4><p>A collage of photos in tiles of different sizes.</p></div>
  <div class="card"><h4>Waterfall</h4><p>A staggered layout that scrolls vertically or horizontally. Adjust columns or rows, speed and spacing.</p></div>
  <div class="card"><h4>Focus Photo Wall</h4><p>Moves across a photo wall and enlarges the photo in focus. Adjust duration, size, emphasis, spacing and fitting.</p></div>
</div>

To use Duo Fold, choose **Slide → Slide effect → Duo Fold** in the display settings. It needs Android 12 or later with hardware acceleration, or an Apple or desktop device with blur support. Where it is not supported, the option is hidden and saved settings fall back to a regular slide.

## Time, date and weather

Turn on **Show time and date** to see the clock, date, weekday, current temperature in Celsius and weather conditions during playback. Weather icons and animated backgrounds follow the conditions and refresh automatically. Weather needs a network connection and your location; if it cannot load, the clock and date stay visible.

## Every photo has more to tell

In Slideshow, Fade and Calendar, interact with a photo to show its details: capture time, camera and lens, aperture, shutter speed, ISO, focal length, image size, file size, and where it was taken when the photo includes a location. The overlay hides again after a moment. Photos without EXIF data still play and show their basic file information.

## AI image summaries

Connect your own AI image understanding service to generate a description, a short caption and tags for the photo on screen in Slideshow, Fade and Calendar. The output follows the app's language. Summaries are saved for reuse, and a double-tap creates a new one or retries a failed request.

1. In Settings, open the AI configuration and add an **AI image understanding** service with your own base URL, API token and a model that supports image understanding.
2. Select the configuration, then turn on **Show AI image summaries** in the Slideshow, Fade or Calendar settings.
3. Start playback. Summaries appear as photos are shown.

AI image summaries are available in the Android, Apple and desktop apps, but not in the browser. Photos go only to the service you configure, never to Showcase servers. Each photo is re-encoded without its original metadata and reduced to at most 768 px on the long edge and 512 KB before upload. Face privacy is on by default, so photos that may contain a face, or that cannot be checked, are skipped. On Apple devices, the app asks for your consent and shows the service's domain before the first photo is sent. Review your AI provider's privacy policy before use.

Developers can find the Kotlin Multiplatform AI module in the repository: [AI module and build instructions](https://github.com/mrjoechen/ShowcaseApp/blob/main/ai-model-capabilities/README.md#multiplatform-integration).

## Make it yours

Manage your sources and set up each display style with its own playback interval, image fitting and layout. Sort photos, include subfolders, refresh sources automatically, and open playback in full screen to suit your photo frame.

## Where the idea came from

Showcase grew out of two ideas: the Picture Wall screensaver on macOS, which turns any folder of images into a moving display, and the wish to give idle Android phones and tablets a second life as digital photo frames. As an Android engineer, I wanted to bring both together in one polished app that makes it a pleasure to see your photos every day.

## Support the project

If Showcase is useful to you, a review or a donation helps keep it maintained and growing. See the ways to [donate]({{ '/donate' | relative_url }}), and meet the [sponsors]({{ '/sponsors' | relative_url }}) who already support it. Thank you!

## Community

- [Telegram channel](https://t.me/showcase_app_release) for release news
- [Telegram group](https://t.me/showcase_app_group) to chat with other users

<div class="qr">
  <figure><img src="{{ '/images/showcase_telegram_group.png' | relative_url }}" alt="QR code for the Showcase Telegram group" loading="lazy"><figcaption>Telegram group</figcaption></figure>
  <figure><img src="https://raw.githubusercontent.com/mrjoechen/ShowcaseApp/main/resource/showcase_wechat_group.png" alt="QR code for the Showcase WeChat group" loading="lazy"><figcaption>WeChat group</figcaption></figure>
</div>

## License

Showcase uses a dual-license model:

- Noncommercial use: [PolyForm Noncommercial 1.0.0](https://github.com/mrjoechen/ShowcaseApp/blob/main/licenses/PolyForm-Noncommercial-1.0.0.txt)
- Commercial use: requires a separate written agreement, see [COMMERCIAL_LICENSE.md](https://github.com/mrjoechen/ShowcaseApp/blob/main/COMMERCIAL_LICENSE.md)

See [LICENSE](https://github.com/mrjoechen/ShowcaseApp/blob/main/LICENSE) for the license selector and scope. The web build uses the MiSans Normal font by Xiaomi Inc., which is governed by its own [MiSans Font Intellectual Property License Agreement](https://github.com/mrjoechen/ShowcaseApp/blob/main/composeApp/src/webMain/composeResources/files/licenses/MiSans-Font-License-Agreement.pdf).
