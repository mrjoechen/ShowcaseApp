---
layout: product
title: Showcase Privacy Policy
description: How Showcase handles your information
nav: privacy
hero_title: Privacy Policy
legal: true
---

**Effective date:** 2026-08-23<br>
**Last updated:** 2026-10-14

Showcase is a multi-platform media display application provided by Joe Chen ("Showcase", "we", "us", or "our"). This Privacy Policy explains how Showcase handles information when you use the mobile, desktop, or web versions of the application.

## 1. Privacy at a glance

- On first launch, Showcase asks whether to share usage and crash data. Optional usage analytics, device registration, feedback upload, and crash reporting start only if you allow it, and you can change your choice at any time with “Share Usage & Crash Data” in Settings. A necessary Supabase client and pseudonymous authentication session may still be initialized to retrieve protected service configuration.
- You may turn optional collection off at any time. The app then stops new optional analytics, device-detail, feedback, and crash-report transmissions while keeping the necessary Supabase configuration session available.
- On Android and iOS, weather uses only location supplied by the operating system after you grant permission and does **not** fall back to IP geolocation. A location is eligible only when its operating-system capture time (or the current time for a provider result without a timestamp) is less than 24 hours old, and it is never returned after permission is revoked. On desktop, Showcase does not use an external IP-geolocation provider; it may reuse an eligible timestamped local cache, otherwise location-based weather is unavailable.
- Media-source settings, credentials, and caches are primarily stored locally on your device. Connecting a source necessarily sends requests to that source or service provider.
- AI image summaries are optional. Photos are sent only to the AI service you configure, never to Showcase servers. On iOS and tvOS, an explicit consent dialog naming the service's domain appears before the first upload, and photos that may contain faces are skipped by default.
- Showcase does not sell personal information and does not use collected data for cross-app advertising tracking.

## 2. Information stored on your device

Showcase may store the following information locally:

- application preferences, theme, display, sorting, and playback settings;
- media-source configuration, including server addresses, collection or playlist identifiers, and credentials supplied by you;
- media URLs, metadata, thumbnails, and image caches needed for playback and performance;
- your anonymous-usage consent choice;
- AI service configuration (with the API token encrypted), generated AI summaries, and the AI service domains you agreed to send photos to;
- a necessary pseudonymous Supabase authentication session used to retrieve protected service configuration; and
- a locally generated persistent device UUID; when anonymous usage is enabled, its association with the authenticated Supabase user identifier and a random analytics session identifier. The device UUID is uploaded only with optional collection enabled and is not an authentication credential.

Sensitive source configuration is encrypted before it is written to the application settings store where supported by the application architecture. Local caches remain until they expire, you clear them in the application, or the application/platform removes them. Some platform-protected values, such as an iOS Keychain identifier, may survive reinstallation unless they are removed by the application or operating system.

The necessary Supabase authentication process assigns a pseudonymous user identifier and may provide Supabase with ordinary connection metadata such as IP address, request time, and request headers. While optional collection is off, Showcase uses this session only to authorize protected service-configuration requests and does not upload analytics events or device details.

## 3. Location and weather

Weather is optional. On Android and iOS, Showcase asks the operating system for location permission. When permission has been granted and a native location is available, latitude and longitude and their capture time are stored in a local persistent cache. To obtain current weather conditions, the coordinates are rounded to two decimal places (about 1 km) and sent to [Open-Meteo](https://open-meteo.com/); the full-precision value stays only in the local cache. While permission remains granted, Showcase may reuse that location if the native provider is temporarily unavailable and fewer than 24 hours have elapsed since capture. Permission is checked again after each suspended native or cache operation; if permission is denied, restricted, or revoked, location-based weather is unavailable and neither a newly obtained nor cached location is returned. The mobile applications do not fall back to IP geolocation.

An older cache record without a capture time is not used because its age cannot be established; Showcase attempts to delete that legacy record instead of making it appear newly captured. An expired record is not used for weather fallback, although the stored record may remain until it is overwritten, application data is cleared, the application is uninstalled, or the platform removes it.

On desktop, Showcase does not request a location from an IP-geolocation service or another external location provider. It may reuse an existing timestamped local location cache only while that cache is eligible under the same 24-hour rule; an older cache without a capture time is not used. Otherwise, weather that requires a location is unavailable. Any resulting coordinates are rounded to two decimal places (about 1 km) and sent only to Open-Meteo for weather data. Showcase does not send its Supabase anonymous user ID or analytics device ID to Open-Meteo and does not upload location to its Supabase analytics database.

## 4. Optional anonymous usage data

Showcase asks on first launch whether to share usage and crash data, and nothing described in this section is collected before you allow it. While “Share Usage & Crash Data” is on, Showcase may process the following pseudonymous information:

- the necessary Supabase anonymous user ID, a separately generated persistent device UUID bound to that user, and a random analytics session ID;
- low-entropy device model/manufacturer, operating-system name/version/API level, locale language, CPU architecture, application version/build/namespace, and build type;
- feature interactions, event names, event time, and event type;
- crash reports, stack traces, error logs, and performance diagnostics through Sentry.

Although this data does not require your name, phone number, or account email, persistent identifiers can distinguish one installation or anonymous account from another. We therefore describe the data as **pseudonymous**, not fully anonymous.

Purposes are limited to measuring feature use, diagnosing failures, maintaining service security, and improving reliability. We do not use this information for targeted advertising or tracking across other companies’ applications or websites.

### Turning collection off

Turning “Share Usage & Crash Data” off stops future optional analytics, device registration, feedback upload, and crash-report collection from Showcase. Showcase keeps a necessary pseudonymous Supabase authentication session and client connection so that features can retrieve protected service configuration, but it does not associate that session with analytics events or upload device details while optional collection is off. Disabling collection does not automatically erase records already received by a service provider. To request deletion of previously collected Showcase-controlled Supabase records, contact us at the address below and include the anonymous identifier if it is available to you.

## 5. Feedback

If you choose to submit feedback while anonymous usage data is enabled, Showcase sends the feedback text, the optional contact email you enter, and the registered persistent device UUID to Supabase, where the server associates the record with the authenticated Supabase user ID. This information is used only to respond to the feedback, investigate the reported issue, and improve Showcase. Do not include passwords, API keys, private media URLs, or other sensitive information in feedback.

## 6. Media sources and third-party services

Showcase can connect to services selected or configured by you, including local-network servers, cloud storage, RSS feeds, GitHub/Gitee, Immich, WebDAV, S3-compatible storage, Unsplash, Pexels, TMDB, and supported music services. The exact services contacted depend on the sources you enable.

When a request is made, the relevant provider may receive your IP address, request time, request headers, API credential or access token for that provider, search or collection identifiers, and requested media identifiers. These providers process information under their own terms and privacy policies. Showcase does not control their independent logging, security, or retention practices.

Notable providers include:

- [Supabase Privacy Policy](https://supabase.com/privacy)
- [Sentry Privacy Policy](https://sentry.io/privacy/)
- [Open-Meteo Privacy Policy](https://open-meteo.com/en/terms)
- [Unsplash Privacy Policy](https://unsplash.com/privacy)
- [Pexels Privacy Policy](https://www.pexels.com/privacy-policy/)
- [TMDB Privacy Policy](https://www.themoviedb.org/privacy-policy)

## 7. AI image summaries

AI image summaries are optional and stay inactive until you configure an AI image understanding service with your own endpoint, model, and API token, and turn on **Show AI image summaries**. Showcase does not provide a built-in AI service and does not upload photos to its own servers.

- **What is sent:** while the feature is on, the photo currently playing is sent, together with a fixed instruction and the requested output language, to the endpoint you configured, authenticated with your API token. Before upload, the photo is re-encoded without its original metadata (such as EXIF and location) and reduced to at most 768 pixels on its longest edge and 512 KB. The provider may also receive ordinary connection metadata such as your IP address and request time.
- **Consent:** on iOS and tvOS, before the first upload Showcase shows an explicit consent dialog that names the domain of the configured service. It appears when you turn on the feature, or when you save or select an AI image understanding service. Photos are uploaded only after you tap **Agree**; if you decline, nothing is sent. Your choice is stored locally on the device for that domain and is not synchronized, so another device or a different domain asks again.
- **Face privacy:** face privacy is on by default. Faces are detected locally on your device, and a photo that may contain a face, or that cannot be checked, is skipped and not uploaded. Local detection can miss faces. You can turn face privacy off in the AI configuration.
- **Results and credentials:** generated summaries, captions, and tags are stored locally for reuse and are not sent to Showcase servers. Your API token is encrypted before it is stored. If you turn on iCloud Sync, AI service settings, including the API token, sync between your own Apple devices through your iCloud account.
- **Your provider's terms:** the AI provider processes photos under its own terms, privacy policy, and retention practices, which Showcase does not control. Review them before use, and do not send photos you are not comfortable sharing with that provider.

## 8. Local network

Showcase may request local-network access to discover or connect to devices and media servers that you configure, such as SMB, FTP, SFTP, WebDAV, Immich, or compatible storage services. Local-network addresses and credentials are used to establish the requested connection. Showcase does not upload your local-network credentials to its analytics database.

## 9. Retention and deletion

- Local settings and caches remain until you clear them, delete a source, uninstall the application, or the platform removes them.
- Supabase anonymous-account, device, analytics, and feedback records are retained while needed to operate, secure, diagnose, and improve Showcase, or until a valid deletion request is completed.
- Sentry retains diagnostic records according to the retention configured for the Showcase project and Sentry’s applicable terms.
- Third-party media and weather providers determine retention for requests they receive under their own policies.
- AI summaries and AI upload consent choices remain on the device until you clear application data or uninstall the application. Photos sent to an AI provider are retained according to that provider's policy.

You may clear application caches in Settings, revoke location permission in system settings, disable anonymous usage data in Showcase, and contact us to request access to or deletion of Showcase-controlled server records. We may need the anonymous device or user identifier to locate pseudonymous records. We may retain limited records when required by law, security, fraud prevention, or dispute resolution.

## 10. Security

We use reasonable technical and organizational measures, including platform-protected storage where available, encrypted local source configuration, HTTPS for fixed public services, and limited-purpose credentials. No method of storage or transmission is completely secure. You are responsible for protecting credentials for media services and for using trusted local-network servers.

## 11. Children

Showcase is not directed to children under 13, or the higher minimum age required by applicable law. We do not knowingly request a child’s name, contact details, or account information. If you believe a child has provided personal information through feedback or diagnostics, contact us so that we can investigate and delete it where required.

## 12. International processing

Supabase, Sentry, Open-Meteo, media providers, and the AI service you configure may process requests in countries or regions different from yours. By enabling an optional service or configuring a remote source, information may be transferred according to that provider’s infrastructure and legal terms. Mandatory rights under your local data-protection and consumer laws continue to apply.

## 13. Changes

We may update this policy when features, providers, or legal requirements change. The current version and effective date will be published at the policy URL and made accessible from Showcase. Material changes will be highlighted in the application or release notes where appropriate.

## 14. Contact

For privacy questions, data-access requests, or deletion requests, contact:

**Joe Chen**

**Email:** [mrjctech@gmail.com](mailto:mrjctech@gmail.com)
