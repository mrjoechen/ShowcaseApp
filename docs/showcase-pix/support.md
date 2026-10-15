---
layout: product
title: Showcase Pix Support
description: Help and contact for Showcase Pix on iPhone, iPad and Apple TV
nav: support
hero_title: Support
---

## Contact us

Email **[mrjctech@gmail.com](mailto:mrjctech@gmail.com)**. To help us look into a problem, please include your device model, your iOS, iPadOS or tvOS version, and the app version shown in **Settings > About**.

You can also send feedback from **Settings > About > Feedback** in the app. The in-app form is available while **Share Usage & Crash Data** is on; email works either way.

Please do not send passwords, API keys, or private server addresses.

## Getting started

1. Open the app. On first launch it asks whether to share usage and crash data. Either choice works, and you can change it later in **Settings**.
2. Tap **+** and choose **Gallery** to pick photos from your library, or add one of your own network sources. On Apple TV, select **+** with the Siri Remote and add a network source, or set up sources on iPhone or iPad and sync them with iCloud.
3. Tap a source to start playback. Open **Settings** to choose a display style and adjust its options.

## Frequently asked questions

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

## Privacy and terms

- [Privacy Policy]({{ '/showcase-pix/privacypolicy' | relative_url }})
- [Terms of Use]({{ '/showcase-pix/termsconditions' | relative_url }})
