---
title: Showcase Pix Privacy Policy
description: How Showcase Pix handles your information on iPhone, iPad and Apple TV
nav: privacy
hero_title: Privacy Policy
legal: true
---

**Effective date:** 2026-10-15<br>
**Last updated:** 2026-10-17

Showcase Pix is a digital photo frame app for iPhone, iPad and Apple TV provided by Joe Chen ("Showcase Pix", "we", "us", or "our"). On your Home Screen the app appears as "Showcase". This Privacy Policy explains how Showcase Pix handles information when you use it on iPhone, iPad and Apple TV.

## 1. Privacy at a glance

- No account is required, and Showcase Pix does not show ads.
- On first launch, the app asks whether to share usage and crash data. Optional usage analytics, device registration, feedback upload and crash reporting start only if you allow it, and you can change your choice at any time with “Share Usage & Crash Data” in Settings.
- Whatever you choose, the app keeps a necessary pseudonymous Supabase session to retrieve protected service configuration.
- Photos from your photo library and your own servers are read and displayed on your device. They are not uploaded to our servers.
- Weather uses your location only after you grant permission, and only coordinates rounded to about 1 km are sent to the weather service.
- AI image summaries are optional. Before the first photo is sent, the app asks for your explicit consent and shows the domain of the AI service you configured. Photos that may contain faces are skipped by default.
- We do not sell personal information and do not track you across other companies’ apps or websites.

## 2. Information stored on your device

Showcase Pix may store the following information on your device:

- app preferences, theme, display, sorting and playback settings;
- photo source configuration, including server addresses and the credentials you enter;
- references to the photos you pick from your photo library;
- media addresses, metadata, thumbnails and image caches needed for playback;
- your choice about sharing usage and crash data;
- AI service configuration (with the API token encrypted), generated AI summaries, and the AI service domains you agreed to send photos to;
- a necessary pseudonymous Supabase authentication session used to retrieve protected service configuration; and
- a locally generated persistent device UUID. While usage data sharing is on, it is associated with the authenticated Supabase user identifier and a random analytics session identifier. The device UUID is uploaded only while optional collection is on and is not a sign-in credential.

Sensitive source configuration is encrypted before it is stored. Local caches remain until they expire, you clear them in the app, or the system removes them. Some values protected by the Keychain may survive reinstallation unless the app or the system removes them.

The necessary Supabase authentication process assigns a pseudonymous user identifier and may give Supabase ordinary connection metadata such as your IP address, request time and request headers. While optional collection is off, this session is used only to authorize protected service-configuration requests, and no analytics events or device details are uploaded.

## 3. Photo library

On iPhone and iPad, Showcase Pix asks for access to your photo library so it can display the photos you select. If you allow access to selected photos only, the app can read just those photos. Photos are read and displayed on your device. A photo leaves the device only if you turn on AI image summaries and agree to send it, as described in section 9.

On iPhone and iPad, the app also asks for permission to add images to your photo library. It uses this permission only when you choose to save an image. Apple TV has no photo library, so on Apple TV the app plays only photos from the sources you configure and cannot save images.

When a photo contains location metadata, its coordinates may be sent to Apple’s geocoding service to show the place where it was taken. Apple handles this request under its own privacy policy.

## 4. Location and weather

Weather is optional. When you use the clock and weather overlay, Showcase Pix asks for permission to use your location while the app is in use. With permission granted, the latitude, longitude and capture time are stored in a local cache. To fetch current weather, the coordinates are rounded to two decimal places (about 1 km) and sent to [Open-Meteo](https://open-meteo.com/); the full-precision value stays only in the local cache.

While permission remains granted, the app may reuse a cached location less than 24 hours old if a new location is temporarily unavailable. Cache records without a capture time and expired records are not used. If permission is denied, restricted or revoked, location-based weather is unavailable and no location is returned. The app does not fall back to IP-based location, does not send its Supabase identifiers to Open-Meteo, and does not upload your location to its analytics database.

## 5. iCloud Sync

If you turn on iCloud Sync, your photo source configurations, including the passwords and tokens they contain, are stored in iCloud key-value storage in your own iCloud account so that your Apple devices can share them. Apple stores this data under its iCloud terms, and we cannot access it. You can turn sync off at any time, and **Turn Off and Delete iCloud Data** in the iCloud Sync settings removes the synced copy from iCloud. Your settings, AI service settings and API keys, and your choices about sharing usage data and sending photos to an AI service are kept on each device and are not synced.

## 6. Optional usage and crash data

Showcase Pix asks on first launch whether to share usage and crash data, and nothing described in this section is collected before you allow it. While “Share Usage & Crash Data” is on, the app may process the following pseudonymous information:

- the necessary Supabase anonymous user ID, a separately generated persistent device UUID bound to that user, and a random analytics session ID;
- device model and model identifier, iOS or tvOS version, language, CPU architecture, and app version and build;
- feature interactions, event names, event time and event type;
- crash reports, stack traces, error logs and performance diagnostics through Sentry.

Although this data does not require your name, phone number or account email, persistent identifiers can distinguish one installation or anonymous account from another. We therefore describe the data as **pseudonymous**, not fully anonymous.

Purposes are limited to measuring feature use, diagnosing failures, maintaining service security and improving reliability. We do not use this information for targeted advertising or tracking across other companies’ apps or websites.

### Turning collection off

Turning “Share Usage & Crash Data” off stops future optional analytics, device registration, feedback upload and crash-report collection. The app keeps the necessary pseudonymous Supabase session so that features can retrieve protected service configuration, but while optional collection is off that session is not associated with analytics events and no device details are uploaded. Turning collection off does not automatically erase records already received. To request deletion of previously collected records, contact us at the address below and include the anonymous identifier if it is available to you.

## 7. Feedback

If you submit feedback in the app while usage data sharing is on, Showcase Pix sends the feedback text, the optional contact email you enter and the registered device UUID to Supabase, where the record is associated with the authenticated Supabase user ID. This information is used only to respond to your feedback, investigate the reported issue and improve the app. Do not include passwords, API keys, private server addresses or other sensitive information in feedback.

## 8. Photo sources and third-party services

Besides your photo library, Showcase Pix can connect to sources you configure: SMB shares, WebDAV servers, S3-compatible storage, Immich, MT Photos, RSS feeds, and GitHub or Gitee repositories. The app contacts only the sources you add. To improve availability, image files from GitHub may be downloaded through a third-party mirror service, which receives the address of the requested file and ordinary connection metadata.

When a request is made, the relevant provider may receive your IP address, request time, request headers, the credential or access token you configured for it, and identifiers of the requested items. These providers handle information under their own terms and privacy policies, and we do not control their logging, security or retention practices.

Service providers that Showcase Pix itself uses include:

- [Supabase Privacy Policy](https://supabase.com/privacy)
- [Sentry Privacy Policy](https://sentry.io/privacy/)
- [Open-Meteo Terms and Privacy](https://open-meteo.com/en/terms)
- [Apple Privacy Policy](https://www.apple.com/legal/privacy/)

## 9. AI image summaries

AI image summaries are optional and stay inactive until you configure an AI image understanding service with your own endpoint, model and API token, and turn on **Show AI image summaries**. Showcase Pix does not provide a built-in AI service and does not upload photos to its own servers.

- **What is sent:** while the feature is on, the photo currently playing is sent, together with a fixed instruction and the requested output language, to the endpoint you configured, authenticated with your API token. Before upload, the photo is re-encoded without its original metadata (such as EXIF and location) and reduced to at most 768 pixels on its longest edge and 512 KB. The provider may also receive ordinary connection metadata such as your IP address and request time.
- **Consent:** before the first upload, the app shows an explicit consent dialog that names the domain of the configured service. It appears when you turn on the feature, or when you save or select an AI image understanding service. Photos are uploaded only after you tap **Agree**; if you decline, nothing is sent. Your choice is stored on the device for that domain and is not synced, so another device or a different domain asks again.
- **Face privacy:** face privacy is on by default. Faces are detected on your device, and a photo that may contain a face, or that cannot be checked, is skipped and not uploaded. Local detection can miss faces. Apple TV cannot detect faces on the device, so while face privacy is on, no photos are sent from Apple TV; if you turn it off there, photos are sent without a face check. You can turn face privacy off in the AI image services settings.
- **Results and credentials:** generated descriptions, captions and tags are stored on the device for reuse and are not sent to our servers. Your API token is encrypted before it is stored and stays on the device; it is not synced through iCloud.
- **Your provider’s terms:** the AI provider processes photos under its own terms, privacy policy and retention practices, which we do not control. Review them before use, and do not send photos you are not comfortable sharing with that provider.

## 10. Local network

Showcase Pix asks for local network access so it can connect to the NAS, SMB, WebDAV or self-hosted photo servers you add on your home network. Local-network addresses and credentials are used only to make the connections you request and are not uploaded to our analytics database. You can change this permission in iOS **Settings > Privacy & Security > Local Network**, or in the Privacy settings of your Apple TV.

## 11. Retention and deletion

- Data on your device remains until you clear it, delete a source, delete the app, or the system removes it. On Apple TV, tvOS may remove cached photos and saved AI summaries when storage runs low.
- Supabase anonymous-account, device, analytics and feedback records are kept while needed to operate, secure, diagnose and improve the app, or until a valid deletion request is completed.
- Sentry keeps diagnostic records according to the retention configured for the project and Sentry’s terms.
- Weather, media and AI providers keep the requests they receive according to their own policies.
- Data synced with iCloud Sync stays in your iCloud account until you delete it from the iCloud Sync settings or through your iCloud account.

You can clear caches in Settings, revoke photo, location and local network permissions in the Settings app of your device, turn off usage data sharing, and contact us to request access to or deletion of records we control. We may need the anonymous device or user identifier to find pseudonymous records. We may keep limited records when required by law, security, fraud prevention or dispute resolution.

## 12. Security

We use reasonable technical and organizational measures, including the Keychain, encrypted local source configuration, HTTPS for fixed public services, and limited-purpose credentials. No method of storage or transmission is completely secure. You are responsible for protecting the credentials for your own services and for connecting only to servers you trust.

## 13. Children

Showcase Pix is not directed to children under 13, or the higher minimum age required by applicable law. We do not knowingly request a child’s name, contact details or account information. If you believe a child has provided personal information through feedback or diagnostics, contact us so that we can investigate and delete it where required.

## 14. International processing

Supabase, Sentry, Open-Meteo, Apple, the photo sources you configure and the AI service you configure may process requests in countries or regions other than yours. Mandatory rights under your local data-protection and consumer laws continue to apply.

## 15. Changes

We may update this policy when features, providers or legal requirements change. The current version and effective date are published at this address and linked from the app. Material changes will be highlighted in the app or in the App Store release notes where appropriate.

## 16. Contact

For privacy questions, data-access requests or deletion requests, contact:

**Joe Chen**

**Email:** [mrjctech@gmail.com](mailto:mrjctech@gmail.com)
