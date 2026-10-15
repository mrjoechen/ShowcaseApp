---
layout: product
title: Showcase Privacy Policy
description: How Showcase handles your information / Showcase 如何处理您的信息
nav: privacy
hero_title: Privacy Policy
legal: true
---

[English](#english) · [中文](#中文)

## English

**Effective date:** 2026-08-23<br>
**Last updated:** 2026-10-14

Showcase is a multi-platform media display application provided by Joe Chen ("Showcase", "we", "us", or "our"). This Privacy Policy explains how Showcase handles information when you use the mobile, desktop, or web versions of the application.

### 1. Privacy at a glance

- On first launch, Showcase asks whether to share usage and crash data. Optional usage analytics, device registration, feedback upload, and crash reporting start only if you allow it, and you can change your choice at any time with “Share Usage & Crash Data” in Settings. A necessary Supabase client and pseudonymous authentication session may still be initialized to retrieve protected service configuration.
- You may turn optional collection off at any time. The app then stops new optional analytics, device-detail, feedback, and crash-report transmissions while keeping the necessary Supabase configuration session available.
- On Android and iOS, weather uses only location supplied by the operating system after you grant permission and does **not** fall back to IP geolocation. A location is eligible only when its operating-system capture time (or the current time for a provider result without a timestamp) is less than 24 hours old, and it is never returned after permission is revoked. On desktop, Showcase does not use an external IP-geolocation provider; it may reuse an eligible timestamped local cache, otherwise location-based weather is unavailable.
- Media-source settings, credentials, and caches are primarily stored locally on your device. Connecting a source necessarily sends requests to that source or service provider.
- AI image summaries are optional. Photos are sent only to the AI service you configure, never to Showcase servers. On iOS and tvOS, an explicit consent dialog naming the service's domain appears before the first upload, and photos that may contain faces are skipped by default.
- Showcase does not sell personal information and does not use collected data for cross-app advertising tracking.

### 2. Information stored on your device

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

### 3. Location and weather

Weather is optional. On Android and iOS, Showcase asks the operating system for location permission. When permission has been granted and a native location is available, latitude and longitude and their capture time are stored in a local persistent cache. To obtain current weather conditions, the coordinates are rounded to two decimal places (about 1 km) and sent to [Open-Meteo](https://open-meteo.com/); the full-precision value stays only in the local cache. While permission remains granted, Showcase may reuse that location if the native provider is temporarily unavailable and fewer than 24 hours have elapsed since capture. Permission is checked again after each suspended native or cache operation; if permission is denied, restricted, or revoked, location-based weather is unavailable and neither a newly obtained nor cached location is returned. The mobile applications do not fall back to IP geolocation.

An older cache record without a capture time is not used because its age cannot be established; Showcase attempts to delete that legacy record instead of making it appear newly captured. An expired record is not used for weather fallback, although the stored record may remain until it is overwritten, application data is cleared, the application is uninstalled, or the platform removes it.

On desktop, Showcase does not request a location from an IP-geolocation service or another external location provider. It may reuse an existing timestamped local location cache only while that cache is eligible under the same 24-hour rule; an older cache without a capture time is not used. Otherwise, weather that requires a location is unavailable. Any resulting coordinates are rounded to two decimal places (about 1 km) and sent only to Open-Meteo for weather data. Showcase does not send its Supabase anonymous user ID or analytics device ID to Open-Meteo and does not upload location to its Supabase analytics database.

### 4. Optional anonymous usage data

Showcase asks on first launch whether to share usage and crash data, and nothing described in this section is collected before you allow it. While “Share Usage & Crash Data” is on, Showcase may process the following pseudonymous information:

- the necessary Supabase anonymous user ID, a separately generated persistent device UUID bound to that user, and a random analytics session ID;
- low-entropy device model/manufacturer, operating-system name/version/API level, locale language, CPU architecture, application version/build/namespace, and build type;
- feature interactions, event names, event time, and event type;
- crash reports, stack traces, error logs, and performance diagnostics through Sentry.

Although this data does not require your name, phone number, or account email, persistent identifiers can distinguish one installation or anonymous account from another. We therefore describe the data as **pseudonymous**, not fully anonymous.

Purposes are limited to measuring feature use, diagnosing failures, maintaining service security, and improving reliability. We do not use this information for targeted advertising or tracking across other companies’ applications or websites.

#### Turning collection off

Turning “Share Usage & Crash Data” off stops future optional analytics, device registration, feedback upload, and crash-report collection from Showcase. Showcase keeps a necessary pseudonymous Supabase authentication session and client connection so that features can retrieve protected service configuration, but it does not associate that session with analytics events or upload device details while optional collection is off. Disabling collection does not automatically erase records already received by a service provider. To request deletion of previously collected Showcase-controlled Supabase records, contact us at the address below and include the anonymous identifier if it is available to you.

### 5. Feedback

If you choose to submit feedback while anonymous usage data is enabled, Showcase sends the feedback text, the optional contact email you enter, and the registered persistent device UUID to Supabase, where the server associates the record with the authenticated Supabase user ID. This information is used only to respond to the feedback, investigate the reported issue, and improve Showcase. Do not include passwords, API keys, private media URLs, or other sensitive information in feedback.

### 6. Media sources and third-party services

Showcase can connect to services selected or configured by you, including local-network servers, cloud storage, RSS feeds, GitHub/Gitee, Immich, WebDAV, S3-compatible storage, Unsplash, Pexels, TMDB, and supported music services. The exact services contacted depend on the sources you enable.

When a request is made, the relevant provider may receive your IP address, request time, request headers, API credential or access token for that provider, search or collection identifiers, and requested media identifiers. These providers process information under their own terms and privacy policies. Showcase does not control their independent logging, security, or retention practices.

Notable providers include:

- [Supabase Privacy Policy](https://supabase.com/privacy)
- [Sentry Privacy Policy](https://sentry.io/privacy/)
- [Open-Meteo Privacy Policy](https://open-meteo.com/en/terms)
- [Unsplash Privacy Policy](https://unsplash.com/privacy)
- [Pexels Privacy Policy](https://www.pexels.com/privacy-policy/)
- [TMDB Privacy Policy](https://www.themoviedb.org/privacy-policy)

### 7. AI image summaries

AI image summaries are optional and stay inactive until you configure an AI image understanding service with your own endpoint, model, and API token, and turn on **Show AI image summaries**. Showcase does not provide a built-in AI service and does not upload photos to its own servers.

- **What is sent:** while the feature is on, the photo currently playing is sent, together with a fixed instruction and the requested output language, to the endpoint you configured, authenticated with your API token. Before upload, the photo is re-encoded without its original metadata (such as EXIF and location) and reduced to at most 768 pixels on its longest edge and 512 KB. The provider may also receive ordinary connection metadata such as your IP address and request time.
- **Consent:** on iOS and tvOS, before the first upload Showcase shows an explicit consent dialog that names the domain of the configured service. It appears when you turn on the feature, or when you save or select an AI image understanding service. Photos are uploaded only after you tap **Agree**; if you decline, nothing is sent. Your choice is stored locally on the device for that domain and is not synchronized, so another device or a different domain asks again.
- **Face privacy:** face privacy is on by default. Faces are detected locally on your device, and a photo that may contain a face, or that cannot be checked, is skipped and not uploaded. Local detection can miss faces. You can turn face privacy off in the AI configuration.
- **Results and credentials:** generated summaries, captions, and tags are stored locally for reuse and are not sent to Showcase servers. Your API token is encrypted before it is stored. If you turn on iCloud Sync, AI service settings, including the API token, sync between your own Apple devices through your iCloud account.
- **Your provider's terms:** the AI provider processes photos under its own terms, privacy policy, and retention practices, which Showcase does not control. Review them before use, and do not send photos you are not comfortable sharing with that provider.

### 8. Local network

Showcase may request local-network access to discover or connect to devices and media servers that you configure, such as SMB, FTP, SFTP, WebDAV, Immich, or compatible storage services. Local-network addresses and credentials are used to establish the requested connection. Showcase does not upload your local-network credentials to its analytics database.

### 9. Retention and deletion

- Local settings and caches remain until you clear them, delete a source, uninstall the application, or the platform removes them.
- Supabase anonymous-account, device, analytics, and feedback records are retained while needed to operate, secure, diagnose, and improve Showcase, or until a valid deletion request is completed.
- Sentry retains diagnostic records according to the retention configured for the Showcase project and Sentry’s applicable terms.
- Third-party media and weather providers determine retention for requests they receive under their own policies.
- AI summaries and AI upload consent choices remain on the device until you clear application data or uninstall the application. Photos sent to an AI provider are retained according to that provider's policy.

You may clear application caches in Settings, revoke location permission in system settings, disable anonymous usage data in Showcase, and contact us to request access to or deletion of Showcase-controlled server records. We may need the anonymous device or user identifier to locate pseudonymous records. We may retain limited records when required by law, security, fraud prevention, or dispute resolution.

### 10. Security

We use reasonable technical and organizational measures, including platform-protected storage where available, encrypted local source configuration, HTTPS for fixed public services, and limited-purpose credentials. No method of storage or transmission is completely secure. You are responsible for protecting credentials for media services and for using trusted local-network servers.

### 11. Children

Showcase is not directed to children under 13, or the higher minimum age required by applicable law. We do not knowingly request a child’s name, contact details, or account information. If you believe a child has provided personal information through feedback or diagnostics, contact us so that we can investigate and delete it where required.

### 12. International processing

Supabase, Sentry, Open-Meteo, media providers, and the AI service you configure may process requests in countries or regions different from yours. By enabling an optional service or configuring a remote source, information may be transferred according to that provider’s infrastructure and legal terms. Mandatory rights under your local data-protection and consumer laws continue to apply.

### 13. Changes

We may update this policy when features, providers, or legal requirements change. The current version and effective date will be published at the policy URL and made accessible from Showcase. Material changes will be highlighted in the application or release notes where appropriate.

### 14. Contact

For privacy questions, data-access requests, or deletion requests, contact:

**Joe Chen**

**Email:** [mrjctech@gmail.com](mailto:mrjctech@gmail.com)

## 中文

**生效日期：** 2026-08-23<br>
**最后更新：** 2026-10-14

Showcase 是由 Joe Chen 提供的多平台媒体展示应用（以下简称“Showcase”或“本应用”）。本隐私政策说明您使用移动端、桌面端或网页端应用时，Showcase 如何处理相关信息。

### 1. 隐私要点

- 首次启动时，Showcase 会询问您是否共享使用与崩溃数据。可选使用统计、设备注册、反馈上传及崩溃报告仅在您允许后才会开始，您可以随时在设置中通过“共享使用与崩溃数据”更改选择。为读取受保护的服务配置，应用仍可能初始化必要的 Supabase 客户端和假名化认证会话。
- 您可以随时关闭可选收集。关闭后，应用会停止新的可选统计、设备详情、反馈及崩溃数据传输，同时保留读取服务配置所需的 Supabase 会话。
- Android 和 iOS 的天气功能仅在您授权后使用操作系统提供的位置，且**不会**回退到 IP 定位；仅当系统采集时间（或无时间戳的新提供者结果的当前时间）距今不足 24 小时时才可使用，撤回权限后不会返回该位置。桌面端不会使用外部 IP 定位服务，仅可能复用仍在有效期且带时间戳的本地位置缓存，否则依赖位置的天气功能不可用。
- 媒体源设置、凭据及缓存主要保存在您的设备本地。连接某一内容源时，应用必然会向该来源或服务提供商发送请求。
- AI 图片摘要为可选功能。照片只会发送至您配置的 AI 服务，不会上传到 Showcase 服务器。在 iOS 和 tvOS 上，首次上传前会弹出显示该服务域名的明确同意对话框；可能包含人脸的照片默认会被跳过。
- Showcase 不出售个人信息，也不会将所收集的数据用于跨应用广告追踪。

### 2. 保存在设备上的信息

Showcase 可能在设备本地保存：

- 应用偏好、主题、展示、排序和播放设置；
- 您配置的媒体源信息，包括服务器地址、集合或歌单标识符以及凭据；
- 播放和性能所需的媒体 URL、元数据、缩略图与图片缓存；
- 您对匿名使用数据的同意选择；
- AI 服务配置（API Token 加密保存）、已生成的 AI 摘要，以及您同意发送照片的 AI 服务域名；
- 用于读取受保护服务配置的必要假名化 Supabase 认证会话；
- 本地随机生成的持久设备 UUID；开启匿名使用数据后，保存其与已认证 Supabase 用户标识符的关联，并生成随机统计会话标识符。设备 UUID 仅在开启可选收集时上传，不是登录凭据。

对于应用架构支持的敏感媒体源配置，Showcase 会先加密再写入设置存储。本地缓存会保留至到期、由您在应用内清除，或由应用/操作系统清理。部分受平台保护的信息（例如 iOS 钥匙串中的标识符）可能在重新安装后仍然存在，直至被应用或操作系统删除。

必要的 Supabase 认证过程会分配假名化用户标识符，并可能使 Supabase 接收到 IP 地址、请求时间及请求头等常规连接信息。在可选收集关闭期间，Showcase 仅使用该会话授权受保护的服务配置请求，不会上传统计事件或设备详情。

### 3. 定位与天气

天气功能为可选功能。在 Android 和 iOS 上，Showcase 会向操作系统申请定位权限。在您已授权且系统可以取得原生位置时，应用会将经纬度及采集时间保存在本地持久缓存中；为获取当前天气，发送给 [Open-Meteo](https://open-meteo.com/) 前会将经纬度取整至小数点后两位（约 1 公里），完整精度的位置仅保存在本地缓存中。在权限仍有效、原生位置暂时不可用且距离采集时间不足 24 小时时，Showcase 可能继续使用该缓存。每次可能挂起的原生定位或缓存操作结束后，应用都会再次检查权限；如果权限被拒绝、受限或撤回，依赖位置的天气功能将不可用，应用不会返回刚取得的位置或缓存位置。移动端不会回退到 IP 定位。

对于没有采集时间的旧版缓存，由于无法确定其年龄，应用不会使用，并会尝试删除，而不会把读取时间当作新的采集时间。过期缓存不会再用于天气回退，但其存储记录可能保留至被新结果覆盖、应用数据被清除、应用被卸载，或平台将其移除。

在桌面端，Showcase 不会向 IP 定位服务或其他外部定位服务商请求位置；仅当带时间戳的本地位置缓存仍符合上述 24 小时有效期时，应用才可能继续使用该缓存，没有采集时间的旧缓存不会被使用。否则，依赖位置的天气功能不可用。所得经纬度会取整至小数点后两位（约 1 公里），且仅发送给 Open-Meteo 获取天气。Showcase 不会向 Open-Meteo 发送 Supabase 匿名用户 ID 或统计设备 ID，也不会将位置上传至 Supabase 统计数据库。

### 4. 可选匿名使用数据

Showcase 会在首次启动时询问您是否共享使用与崩溃数据；在您允许之前，不会收集本节所述的任何信息。在“共享使用与崩溃数据”开启期间，Showcase 可能处理以下假名化信息：

- 必要的 Supabase 匿名用户 ID、独立生成并与该用户绑定的持久设备 UUID，以及随机统计会话 ID；
- 设备型号/制造商、操作系统名称/版本/API 级别、语言、CPU 架构、应用版本/构建号/命名空间和构建类型；
- 功能交互、事件名称、事件时间和事件类型；
- 通过 Sentry 收集的崩溃报告、调用栈、错误日志及性能诊断信息。

这些信息不要求您提供姓名、电话号码或账户邮箱，但持久标识符仍可以区分不同的安装实例或匿名账户。因此，我们将其称为**假名化数据**，而不是完全匿名数据。

处理目的仅限于衡量功能使用情况、诊断故障、维护服务安全及提升可靠性。我们不会将这些信息用于定向广告或跨其他公司的应用、网站进行追踪。

#### 关闭数据收集

关闭“共享使用与崩溃数据”后，Showcase 会停止后续可选统计、设备注册、反馈上传及崩溃报告收集。为使相关功能能够读取受保护的服务配置，应用仍会保留必要的假名化 Supabase 认证会话和客户端连接；在可选收集关闭期间，该会话不会与统计事件关联，也不会用于上传设备详情。关闭收集不会自动删除服务提供商此前已经收到的记录。如需删除此前由 Showcase 控制的 Supabase 记录，请通过文末邮箱联系我们；如您能够取得匿名标识符，请一并提供。

### 5. 反馈

如果您在已开启匿名使用数据的情况下主动提交反馈，Showcase 会将反馈内容、您自愿填写的联系邮箱，以及已注册的持久设备 UUID 发送到 Supabase，并由服务端将记录关联到已认证的 Supabase 用户 ID。相关信息仅用于回复反馈、调查问题及改进应用。请勿在反馈中填写密码、API Key、私人媒体 URL 或其他敏感信息。

### 6. 媒体源与第三方服务

Showcase 可以连接由您选择或配置的服务，包括局域网服务器、云存储、RSS、GitHub/Gitee、Immich、WebDAV、兼容 S3 的存储、Unsplash、Pexels、TMDB 以及受支持的音乐服务。实际访问哪些服务取决于您启用的媒体源。

发送请求时，相应提供商可能接收到您的 IP 地址、请求时间、请求头、该提供商的 API 凭据或访问令牌、搜索/集合标识符以及所请求的媒体标识符。各提供商依据其自身条款和隐私政策处理信息；Showcase 无法控制其独立的日志、安全及保留策略。

主要提供商包括：

- [Supabase 隐私政策](https://supabase.com/privacy)
- [Sentry 隐私政策](https://sentry.io/privacy/)
- [Open-Meteo 隐私政策](https://open-meteo.com/en/terms)
- [Unsplash 隐私政策](https://unsplash.com/privacy)
- [Pexels 隐私政策](https://www.pexels.com/privacy-policy/)
- [TMDB 隐私政策](https://www.themoviedb.org/privacy-policy)

### 7. AI 图片摘要

AI 图片摘要为可选功能。只有在您使用自己的服务地址、模型和 API Token 配置 AI 图片理解服务，并开启 **显示 AI 图片摘要** 后才会生效。Showcase 不提供内置 AI 服务，也不会将照片上传到自己的服务器。

- **发送的内容：** 功能开启期间，应用会将当前播放的照片连同固定的指令和请求的输出语言，发送至您配置的服务地址，并使用您的 API Token 进行认证。上传前，照片会重新编码并去除原始元数据（如 EXIF 和位置），长边不超过 768 像素，大小不超过 512 KB。服务商还可能接收到您的 IP 地址、请求时间等常规连接信息。
- **同意：** 在 iOS 和 tvOS 上，首次上传前 Showcase 会弹出明确的同意对话框，并显示所配置服务的域名。开启该功能，或保存、选择 AI 图片理解服务时会出现该对话框。只有点击 **同意** 后才会上传照片；选择不同意则不会发送任何照片。您的选择会按域名保存在本设备上且不会同步，因此在其他设备上或更换为其他域名时会再次询问。
- **人脸隐私保护：** 人脸隐私保护默认开启。应用在设备本地检测人脸，可能包含人脸或无法完成检测的照片会被跳过，不会上传。本地检测可能存在漏检。您可以在 AI 配置中关闭人脸隐私保护。
- **结果与凭据：** 生成的摘要、文案和标签保存在本地以便复用，不会发送到 Showcase 服务器。API Token 会先加密再保存。如您开启 iCloud 同步，AI 服务设置（包括 API Token）会通过您的 iCloud 账户在您自己的 Apple 设备之间同步。
- **服务商条款：** AI 服务商依据其自身条款、隐私政策及保留规则处理照片，Showcase 无法控制。请在使用前查阅相关规则，并避免向该服务商发送您不愿分享的照片。

### 8. 局域网

Showcase 可能申请局域网权限，以发现或连接您配置的设备和媒体服务器，例如 SMB、FTP、SFTP、WebDAV、Immich 或兼容存储服务。局域网地址及凭据仅用于建立您要求的连接，不会上传到 Showcase 的统计数据库。

### 9. 保留与删除

- 本地设置和缓存会保留至您清除缓存、删除媒体源、卸载应用，或平台将其移除。
- Supabase 中的匿名账户、设备、统计和反馈记录，会在运营、保障安全、诊断及改进 Showcase 所需期间保留，或保留至有效删除请求处理完成。
- Sentry 诊断记录依据 Showcase 项目的保留配置及 Sentry 适用条款保存。
- 第三方媒体及天气提供商依据各自政策决定其收到请求的保留期限。
- AI 摘要及 AI 上传同意记录会保留在设备上，直至您清除应用数据或卸载应用。发送至 AI 服务商的照片依据该服务商的政策保留。

您可以在设置中清除缓存、在系统设置中撤回定位权限、在 Showcase 中关闭匿名使用数据，并联系我们申请访问或删除由 Showcase 控制的服务端记录。为定位假名化记录，我们可能需要匿名设备或用户标识符。在法律、安全、防欺诈或争议处理要求下，我们可能保留有限记录。

### 10. 安全

我们采取合理的技术与组织措施，包括在可用时使用平台保护存储、加密本地媒体源配置、固定公共服务使用 HTTPS，以及限制凭据用途。但任何存储或传输方式都无法保证绝对安全。您有责任保护媒体服务凭据，并仅连接可信的局域网服务器。

### 11. 儿童隐私

Showcase 并非面向 13 岁以下儿童，或适用法律规定的更高最低年龄人群。我们不会主动要求儿童提供姓名、联系方式或账户信息。如您认为儿童通过反馈或诊断功能提交了个人信息，请联系我们处理。

### 12. 跨境处理

Supabase、Sentry、Open-Meteo、媒体提供商及您配置的 AI 服务可能在您所在国家或地区以外处理请求。启用可选服务或配置远程媒体源后，信息可能依据该提供商的基础设施与法律条款进行跨境传输。您依据当地数据保护及消费者法律享有的强制性权利不受影响。

### 13. 政策变更

当功能、提供商或法律要求发生变化时，我们可能更新本政策。当前版本及生效日期会发布于隐私政策页面，并可从 Showcase 内访问；重大变化将在适当情况下通过应用或发行说明提示。

### 14. 联系我们

如有隐私问题，或需要申请访问、删除数据，请联系：

**Joe Chen**

**邮箱：** [mrjctech@gmail.com](mailto:mrjctech@gmail.com)
