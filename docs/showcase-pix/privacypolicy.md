---
title: Showcase Pix Privacy Policy
description: How Showcase Pix handles your information on iPhone, iPad and Apple TV / Showcase Pix 如何在 iPhone、iPad 和 Apple TV 上处理您的信息
nav: privacy
hero_title: Privacy Policy
legal: true
---

[English](#english) · [中文](#中文)

## English

**Effective date:** 2026-10-15<br>
**Last updated:** 2026-10-15

Showcase Pix is a digital photo frame app for iPhone, iPad and Apple TV provided by Joe Chen ("Showcase Pix", "we", "us", or "our"). On your Home Screen the app appears as "Showcase". This Privacy Policy explains how Showcase Pix handles information when you use it on iPhone, iPad and Apple TV.

### 1. Privacy at a glance

- No account is required, and Showcase Pix does not show ads.
- On first launch, the app asks whether to share usage and crash data. Optional usage analytics, device registration, feedback upload and crash reporting start only if you allow it, and you can change your choice at any time with “Share Usage & Crash Data” in Settings.
- Whatever you choose, the app keeps a necessary pseudonymous Supabase session to retrieve protected service configuration.
- Photos from your photo library and your own servers are read and displayed on your device. They are not uploaded to our servers.
- Weather uses your location only after you grant permission, and only coordinates rounded to about 1 km are sent to the weather service.
- AI image summaries are optional. Before the first photo is sent, the app asks for your explicit consent and shows the domain of the AI service you configured. Photos that may contain faces are skipped by default.
- We do not sell personal information and do not track you across other companies’ apps or websites.

### 2. Information stored on your device

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

### 3. Photo library

On iPhone and iPad, Showcase Pix asks for access to your photo library so it can display the photos you select. If you allow access to selected photos only, the app can read just those photos. Photos are read and displayed on your device. A photo leaves the device only if you turn on AI image summaries and agree to send it, as described in section 9.

On iPhone and iPad, the app also asks for permission to add images to your photo library. It uses this permission only when you choose to save an image. Apple TV has no photo library, so on Apple TV the app plays only photos from the sources you configure and cannot save images.

When a photo contains location metadata, its coordinates may be sent to Apple’s geocoding service to show the place where it was taken. Apple handles this request under its own privacy policy.

### 4. Location and weather

Weather is optional. When you use the clock and weather overlay, Showcase Pix asks for permission to use your location while the app is in use. With permission granted, the latitude, longitude and capture time are stored in a local cache. To fetch current weather, the coordinates are rounded to two decimal places (about 1 km) and sent to [Open-Meteo](https://open-meteo.com/); the full-precision value stays only in the local cache.

While permission remains granted, the app may reuse a cached location less than 24 hours old if a new location is temporarily unavailable. Cache records without a capture time and expired records are not used. If permission is denied, restricted or revoked, location-based weather is unavailable and no location is returned. The app does not fall back to IP-based location, does not send its Supabase identifiers to Open-Meteo, and does not upload your location to its analytics database.

### 5. iCloud Sync

If you turn on iCloud Sync, your settings and photo source configurations, including the passwords, tokens and AI API keys they contain, are stored in iCloud key-value storage in your own iCloud account so that your Apple devices can share them. Apple stores this data under its iCloud terms, and we cannot access it. You can turn sync off at any time, and **Turn Off and Delete iCloud Data** in the iCloud Sync settings removes the synced copy from iCloud. Your choices about sharing usage data and sending photos to an AI service are kept on each device and are not synced.

### 6. Optional usage and crash data

Showcase Pix asks on first launch whether to share usage and crash data, and nothing described in this section is collected before you allow it. While “Share Usage & Crash Data” is on, the app may process the following pseudonymous information:

- the necessary Supabase anonymous user ID, a separately generated persistent device UUID bound to that user, and a random analytics session ID;
- device model and model identifier, iOS or tvOS version, language, CPU architecture, and app version and build;
- feature interactions, event names, event time and event type;
- crash reports, stack traces, error logs and performance diagnostics through Sentry.

Although this data does not require your name, phone number or account email, persistent identifiers can distinguish one installation or anonymous account from another. We therefore describe the data as **pseudonymous**, not fully anonymous.

Purposes are limited to measuring feature use, diagnosing failures, maintaining service security and improving reliability. We do not use this information for targeted advertising or tracking across other companies’ apps or websites.

#### Turning collection off

Turning “Share Usage & Crash Data” off stops future optional analytics, device registration, feedback upload and crash-report collection. The app keeps the necessary pseudonymous Supabase session so that features can retrieve protected service configuration, but while optional collection is off that session is not associated with analytics events and no device details are uploaded. Turning collection off does not automatically erase records already received. To request deletion of previously collected records, contact us at the address below and include the anonymous identifier if it is available to you.

### 7. Feedback

If you submit feedback in the app while usage data sharing is on, Showcase Pix sends the feedback text, the optional contact email you enter and the registered device UUID to Supabase, where the record is associated with the authenticated Supabase user ID. This information is used only to respond to your feedback, investigate the reported issue and improve the app. Do not include passwords, API keys, private server addresses or other sensitive information in feedback.

### 8. Photo sources and third-party services

Besides your photo library, Showcase Pix can connect to sources you configure: SMB shares, WebDAV servers, S3-compatible storage, Immich, MT Photos, RSS feeds, and GitHub or Gitee repositories. The app contacts only the sources you add. To improve availability, image files from GitHub may be downloaded through a third-party mirror service, which receives the address of the requested file and ordinary connection metadata.

When a request is made, the relevant provider may receive your IP address, request time, request headers, the credential or access token you configured for it, and identifiers of the requested items. These providers handle information under their own terms and privacy policies, and we do not control their logging, security or retention practices.

Service providers that Showcase Pix itself uses include:

- [Supabase Privacy Policy](https://supabase.com/privacy)
- [Sentry Privacy Policy](https://sentry.io/privacy/)
- [Open-Meteo Terms and Privacy](https://open-meteo.com/en/terms)
- [Apple Privacy Policy](https://www.apple.com/legal/privacy/)

### 9. AI image summaries

AI image summaries are optional and stay inactive until you configure an AI image understanding service with your own endpoint, model and API token, and turn on **Show AI image summaries**. Showcase Pix does not provide a built-in AI service and does not upload photos to its own servers.

- **What is sent:** while the feature is on, the photo currently playing is sent, together with a fixed instruction and the requested output language, to the endpoint you configured, authenticated with your API token. Before upload, the photo is re-encoded without its original metadata (such as EXIF and location) and reduced to at most 768 pixels on its longest edge and 512 KB. The provider may also receive ordinary connection metadata such as your IP address and request time.
- **Consent:** before the first upload, the app shows an explicit consent dialog that names the domain of the configured service. It appears when you turn on the feature, or when you save or select an AI image understanding service. Photos are uploaded only after you tap **Agree**; if you decline, nothing is sent. Your choice is stored on the device for that domain and is not synced, so another device or a different domain asks again.
- **Face privacy:** face privacy is on by default. Faces are detected on your device, and a photo that may contain a face, or that cannot be checked, is skipped and not uploaded. Local detection can miss faces. Apple TV cannot detect faces on the device, so while face privacy is on, no photos are sent from Apple TV; if you turn it off there, photos are sent without a face check. You can turn face privacy off in the AI image services settings.
- **Results and credentials:** generated descriptions, captions and tags are stored on the device for reuse and are not sent to our servers. Your API token is encrypted before it is stored, and syncs to your other Apple devices only if you turn on iCloud Sync.
- **Your provider’s terms:** the AI provider processes photos under its own terms, privacy policy and retention practices, which we do not control. Review them before use, and do not send photos you are not comfortable sharing with that provider.

### 10. Local network

Showcase Pix asks for local network access so it can connect to the NAS, SMB, WebDAV or self-hosted photo servers you add on your home network. Local-network addresses and credentials are used only to make the connections you request and are not uploaded to our analytics database. You can change this permission in iOS **Settings > Privacy & Security > Local Network**, or in the Privacy settings of your Apple TV.

### 11. Retention and deletion

- Data on your device remains until you clear it, delete a source, delete the app, or the system removes it. On Apple TV, tvOS may remove cached photos and saved AI summaries when storage runs low.
- Supabase anonymous-account, device, analytics and feedback records are kept while needed to operate, secure, diagnose and improve the app, or until a valid deletion request is completed.
- Sentry keeps diagnostic records according to the retention configured for the project and Sentry’s terms.
- Weather, media and AI providers keep the requests they receive according to their own policies.
- Data synced with iCloud Sync stays in your iCloud account until you delete it from the iCloud Sync settings or through your iCloud account.

You can clear caches in Settings, revoke photo, location and local network permissions in the Settings app of your device, turn off usage data sharing, and contact us to request access to or deletion of records we control. We may need the anonymous device or user identifier to find pseudonymous records. We may keep limited records when required by law, security, fraud prevention or dispute resolution.

### 12. Security

We use reasonable technical and organizational measures, including the Keychain, encrypted local source configuration, HTTPS for fixed public services, and limited-purpose credentials. No method of storage or transmission is completely secure. You are responsible for protecting the credentials for your own services and for connecting only to servers you trust.

### 13. Children

Showcase Pix is not directed to children under 13, or the higher minimum age required by applicable law. We do not knowingly request a child’s name, contact details or account information. If you believe a child has provided personal information through feedback or diagnostics, contact us so that we can investigate and delete it where required.

### 14. International processing

Supabase, Sentry, Open-Meteo, Apple, the photo sources you configure and the AI service you configure may process requests in countries or regions other than yours. Mandatory rights under your local data-protection and consumer laws continue to apply.

### 15. Changes

We may update this policy when features, providers or legal requirements change. The current version and effective date are published at this address and linked from the app. Material changes will be highlighted in the app or in the App Store release notes where appropriate.

### 16. Contact

For privacy questions, data-access requests or deletion requests, contact:

**Joe Chen**

**Email:** [mrjctech@gmail.com](mailto:mrjctech@gmail.com)

## 中文

**生效日期：** 2026-10-15<br>
**最后更新：** 2026-10-15

Showcase Pix 是由 Joe Chen 提供的 iPhone、iPad 和 Apple TV 数字相框应用（以下简称“Showcase Pix”或“本应用”），在主屏幕上显示为“Showcase”。本隐私政策说明您在 iPhone、iPad 和 Apple TV 上使用 Showcase Pix 时，本应用如何处理相关信息。

### 1. 隐私要点

- 无需注册账号，本应用不展示广告。
- 首次启动时，本应用会询问您是否共享使用与崩溃数据。可选使用统计、设备注册、反馈上传及崩溃报告仅在您允许后才会开始，您可以随时在设置中通过“共享使用与崩溃数据”更改选择。
- 无论您如何选择，本应用都会保留读取受保护服务配置所需的假名化 Supabase 会话。
- 照片图库和您自己服务器中的照片在设备上读取和展示，不会上传到我们的服务器。
- 天气功能仅在您授权后使用位置，且只会把取整到约 1 公里的经纬度发送给天气服务。
- AI 图片摘要为可选功能。发送第一张照片之前，本应用会征求您的明确同意，并显示您所配置 AI 服务的域名。可能包含人脸的照片默认会被跳过。
- 我们不出售个人信息，也不会跨其他公司的应用或网站追踪您。

### 2. 保存在设备上的信息

Showcase Pix 可能在您的设备上保存：

- 应用偏好、主题、展示、排序和播放设置；
- 您配置的图片源信息，包括服务器地址和您填写的凭据；
- 您从照片图库中挑选的照片的引用；
- 播放所需的媒体地址、元数据、缩略图与图片缓存；
- 您对是否共享使用与崩溃数据的选择；
- AI 服务配置（API Token 加密保存）、已生成的 AI 摘要，以及您同意发送照片的 AI 服务域名；
- 用于读取受保护服务配置的必要假名化 Supabase 认证会话；
- 本地生成的持久设备 UUID。开启使用数据共享期间，它会与已认证的 Supabase 用户标识符及随机统计会话标识符关联。设备 UUID 仅在开启可选收集时上传，不是登录凭据。

敏感的图片源配置会先加密再保存。本地缓存会保留至到期、由您在应用内清除，或由系统清理。部分受钥匙串保护的信息可能在重新安装后仍然存在，直至被本应用或系统删除。

必要的 Supabase 认证过程会分配假名化用户标识符，并可能使 Supabase 接收到 IP 地址、请求时间及请求头等常规连接信息。在可选收集关闭期间，该会话仅用于授权受保护的服务配置请求，不会上传统计事件或设备详情。

### 3. 照片图库

在 iPhone 和 iPad 上，Showcase Pix 会申请访问您的照片图库，以展示您选择的照片。如果您只允许访问部分照片，本应用只能读取这些照片。照片在设备上读取和展示；只有在您开启 AI 图片摘要并同意发送时，照片才会离开设备，详见第 9 节。

在 iPhone 和 iPad 上，本应用还会申请向照片图库添加图片的权限，仅在您选择保存图片时使用。Apple TV 没有照片图库，因此在 Apple TV 上本应用只播放您配置的图片源中的照片，也无法保存图片。

如果照片带有位置元数据，其经纬度可能会发送给 Apple 的地理编码服务，用于显示拍摄地点。Apple 依据其自身隐私政策处理该请求。

### 4. 定位与天气

天气功能为可选功能。使用时钟与天气时，Showcase Pix 会申请在使用期间访问您的位置。获得授权后，经纬度及采集时间会保存在本地缓存中。获取当前天气时，经纬度会取整至小数点后两位（约 1 公里）再发送给 [Open-Meteo](https://open-meteo.com/)；完整精度的位置仅保存在本地缓存中。

在权限仍有效、暂时无法取得新位置时，本应用可能使用距采集时间不足 24 小时的缓存位置。没有采集时间的缓存记录和已过期的记录不会被使用。如果权限被拒绝、受限或撤回，依赖位置的天气功能将不可用，也不会返回任何位置。本应用不会回退到基于 IP 的定位，不会向 Open-Meteo 发送 Supabase 标识符，也不会把您的位置上传到统计数据库。

### 5. iCloud 同步

如果您开启 iCloud 同步，您的设置和图片源配置（包括其中的密码、Token 和 AI API 密钥）会保存在您自己 iCloud 账户的键值存储中，以便在您的 Apple 设备之间共享。这些数据由 Apple 依据 iCloud 条款保存，我们无法访问。您可以随时关闭同步；在 iCloud 同步设置中选择 **关闭并删除 iCloud 数据**，即可从 iCloud 中删除同步副本。您对共享使用数据及向 AI 服务发送照片的选择保存在各台设备上，不会同步。

### 6. 可选使用与崩溃数据

Showcase Pix 会在首次启动时询问您是否共享使用与崩溃数据；在您允许之前，不会收集本节所述的任何信息。在“共享使用与崩溃数据”开启期间，本应用可能处理以下假名化信息：

- 必要的 Supabase 匿名用户 ID、独立生成并与该用户绑定的持久设备 UUID，以及随机统计会话 ID；
- 设备型号及型号标识符、iOS 或 tvOS 版本、语言、CPU 架构，以及应用版本和构建号；
- 功能交互、事件名称、事件时间和事件类型；
- 通过 Sentry 收集的崩溃报告、调用栈、错误日志及性能诊断信息。

这些信息不要求您提供姓名、电话号码或账户邮箱，但持久标识符仍可以区分不同的安装实例或匿名账户。因此，我们将其称为**假名化数据**，而不是完全匿名数据。

处理目的仅限于衡量功能使用情况、诊断故障、维护服务安全及提升可靠性。我们不会将这些信息用于定向广告，也不会跨其他公司的应用或网站进行追踪。

#### 关闭数据收集

关闭“共享使用与崩溃数据”后，本应用会停止后续的可选统计、设备注册、反馈上传及崩溃报告收集。为使相关功能能够读取受保护的服务配置，本应用仍会保留必要的假名化 Supabase 会话；在可选收集关闭期间，该会话不会与统计事件关联，也不会上传设备详情。关闭收集不会自动删除已经收到的记录。如需删除此前收集的记录，请通过文末邮箱联系我们；如您能够取得匿名标识符，请一并提供。

### 7. 反馈

如果您在开启使用数据共享期间通过应用提交反馈，Showcase Pix 会将反馈内容、您自愿填写的联系邮箱以及已注册的设备 UUID 发送到 Supabase，并将记录关联到已认证的 Supabase 用户 ID。这些信息仅用于回复反馈、调查问题及改进本应用。请勿在反馈中填写密码、API 密钥、私有服务器地址或其他敏感信息。

### 8. 图片源与第三方服务

除照片图库外，Showcase Pix 还可以连接您配置的图片源：SMB 共享、WebDAV 服务器、S3 兼容存储、Immich、MT Photos、RSS 订阅，以及 GitHub 或 Gitee 仓库。本应用只会访问您添加的图片源。为提高可用性，来自 GitHub 的图片文件可能会通过第三方镜像服务下载，该服务会收到所请求文件的地址及常规连接信息。

发送请求时，相应提供商可能接收到您的 IP 地址、请求时间、请求头、您为其配置的凭据或访问令牌，以及所请求项目的标识符。各提供商依据其自身条款和隐私政策处理信息，我们无法控制其日志、安全及保留策略。

Showcase Pix 自身使用的服务商包括：

- [Supabase 隐私政策](https://supabase.com/privacy)
- [Sentry 隐私政策](https://sentry.io/privacy/)
- [Open-Meteo 条款与隐私](https://open-meteo.com/en/terms)
- [Apple 隐私政策](https://www.apple.com/legal/privacy/)

### 9. AI 图片摘要

AI 图片摘要为可选功能。只有在您使用自己的服务地址、模型和 API Token 配置 AI 图片理解服务，并开启 **显示 AI 图片摘要** 后才会生效。Showcase Pix 不提供内置 AI 服务，也不会将照片上传到自己的服务器。

- **发送的内容：** 功能开启期间，本应用会将当前播放的照片连同固定的指令和请求的输出语言，发送至您配置的服务地址，并使用您的 API Token 进行认证。上传前，照片会重新编码并去除原始元数据（如 EXIF 和位置），长边不超过 768 像素，大小不超过 512 KB。服务商还可能接收到您的 IP 地址、请求时间等常规连接信息。
- **同意：** 首次上传前，本应用会弹出明确的同意对话框，并显示所配置服务的域名。开启该功能，或保存、选择 AI 图片理解服务时会出现该对话框。只有点击 **同意** 后才会上传照片；选择不同意则不会发送任何照片。您的选择会按域名保存在本设备上且不会同步，因此在其他设备上或更换为其他域名时会再次询问。
- **人脸隐私保护：** 人脸隐私保护默认开启。本应用在设备上检测人脸，可能包含人脸或无法完成检测的照片会被跳过，不会上传。本地检测可能存在漏检。Apple TV 无法在设备上检测人脸，因此在开启人脸隐私保护时，不会从 Apple TV 发送任何照片；如果在 Apple TV 上关闭该功能，照片将在不经人脸检查的情况下发送。您可以在 AI 图片服务设置中关闭人脸隐私保护。
- **结果与凭据：** 生成的描述、文案和标签保存在设备上以便复用，不会发送到我们的服务器。API Token 会先加密再保存，只有在您开启 iCloud 同步时才会同步到您的其他 Apple 设备。
- **服务商条款：** AI 服务商依据其自身条款、隐私政策及保留规则处理照片，我们无法控制。请在使用前查阅相关规则，并避免向该服务商发送您不愿分享的照片。

### 10. 本地网络

Showcase Pix 会申请本地网络权限，以连接您在家庭网络中添加的 NAS、SMB、WebDAV 或自建相册服务器。本地网络地址和凭据仅用于建立您要求的连接，不会上传到统计数据库。您可以在 iOS **设置 > 隐私与安全性 > 本地网络** 中修改此权限，或在 Apple TV 设置的隐私选项中修改。

### 11. 保留与删除

- 设备上的数据会保留至您清除数据、删除图片源、删除本应用，或系统将其移除。在 Apple TV 上，存储空间不足时 tvOS 可能清理缓存的照片和已保存的 AI 摘要。
- Supabase 中的匿名账户、设备、统计和反馈记录，会在运营、保障安全、诊断及改进本应用所需期间保留，或保留至有效删除请求处理完成。
- Sentry 诊断记录依据项目的保留配置及 Sentry 条款保存。
- 天气、媒体及 AI 服务商依据各自政策保留其收到的请求。
- 通过 iCloud 同步的数据会保留在您的 iCloud 账户中，直至您在 iCloud 同步设置中或通过 iCloud 账户删除。

您可以在设置中清除缓存，在设备的设置中撤回照片、定位和本地网络权限，关闭使用数据共享，并联系我们申请访问或删除由我们控制的记录。为定位假名化记录，我们可能需要匿名设备或用户标识符。在法律、安全、防欺诈或争议处理要求下，我们可能保留有限记录。

### 12. 安全

我们采取合理的技术与组织措施，包括使用钥匙串、加密本地图片源配置、固定公共服务使用 HTTPS，以及限制凭据用途。但任何存储或传输方式都无法保证绝对安全。您有责任保护自己服务的凭据，并仅连接可信的服务器。

### 13. 儿童隐私

Showcase Pix 并非面向 13 岁以下儿童，或适用法律规定的更高最低年龄人群。我们不会主动要求儿童提供姓名、联系方式或账户信息。如您认为儿童通过反馈或诊断功能提交了个人信息，请联系我们，以便在需要时调查并删除。

### 14. 跨境处理

Supabase、Sentry、Open-Meteo、Apple、您配置的图片源及 AI 服务可能在您所在国家或地区以外处理请求。您依据当地数据保护及消费者法律享有的强制性权利不受影响。

### 15. 政策变更

当功能、服务商或法律要求发生变化时，我们可能更新本政策。当前版本及生效日期会发布于本页面，并可从应用内访问；重大变化将在适当情况下通过应用或 App Store 版本说明提示。

### 16. 联系我们

如有隐私问题，或需要申请访问、删除数据，请联系：

**Joe Chen**

**邮箱：** [mrjctech@gmail.com](mailto:mrjctech@gmail.com)
