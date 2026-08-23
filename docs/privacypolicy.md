# Showcase Privacy Policy / 隐私政策

**Effective date / 生效日期:** 2026-08-23
**Last updated / 最后更新:** 2026-08-23

Showcase is a multi-platform media display application provided by Joe Chen ("Showcase", "we", "us", or "our"). This Privacy Policy explains how Showcase handles information when you use the mobile, desktop, or web versions of the application.

Showcase 是由 Joe Chen 提供的多平台媒体展示应用（以下简称“Showcase”或“本应用”）。本隐私政策说明您使用移动端、桌面端或网页端应用时，Showcase 如何处理相关信息。

## 1. Privacy at a glance / 隐私要点

- Anonymous usage analytics and crash reporting are **off by default**. Supabase and Sentry collection services are initialized only after you explicitly enable “Share Usage & Crash Data” in Settings.
- You may turn that option off at any time. The app then stops new analytics and crash-report transmissions, signs out of the anonymous Supabase session, and closes the Supabase and Sentry clients.
- Weather uses only the location supplied by the operating system after you grant location permission. Showcase does **not** use IP-address geolocation as a fallback.
- Media-source settings, credentials, and caches are primarily stored locally on your device. Connecting a source necessarily sends requests to that source or service provider.
- Showcase does not sell personal information and does not use collected data for cross-app advertising tracking.

- 匿名使用统计和崩溃报告**默认关闭**。只有在您于设置中明确开启“共享使用与崩溃数据”后，应用才会初始化 Supabase 和 Sentry 数据收集服务。
- 您可以随时关闭该选项。关闭后，应用会停止新的统计及崩溃数据传输、退出匿名 Supabase 会话，并关闭 Supabase 与 Sentry 客户端。
- 天气功能仅在您授予系统定位权限后使用操作系统提供的位置；Showcase **不会**在定位失败或权限被拒绝时通过 IP 地址推断位置。
- 媒体源设置、凭据及缓存主要保存在您的设备本地。连接某一内容源时，应用必然会向该来源或服务提供商发送请求。
- Showcase 不出售个人信息，也不会将所收集的数据用于跨应用广告追踪。

## 2. Information stored on your device / 保存在设备上的信息

Showcase may store the following information locally:

- application preferences, theme, display, sorting, and playback settings;
- media-source configuration, including server addresses, collection or playlist identifiers, and credentials supplied by you;
- media URLs, metadata, thumbnails, and image caches needed for playback and performance;
- your anonymous-usage consent choice;
- when anonymous usage is enabled, a randomly generated installation/device identifier and an authentication session.

Sensitive source configuration is encrypted before it is written to the application settings store where supported by the application architecture. Local caches remain until they expire, you clear them in the application, or the application/platform removes them. Some platform-protected values, such as an iOS Keychain identifier, may survive reinstallation unless they are removed by the application or operating system.

Showcase 可能在设备本地保存：

- 应用偏好、主题、展示、排序和播放设置；
- 您配置的媒体源信息，包括服务器地址、集合或歌单标识符以及凭据；
- 播放和性能所需的媒体 URL、元数据、缩略图与图片缓存；
- 您对匿名使用数据的同意选择；
- 开启匿名使用数据后生成的随机安装/设备标识符及身份验证会话。

对于应用架构支持的敏感媒体源配置，Showcase 会先加密再写入设置存储。本地缓存会保留至到期、由您在应用内清除，或由应用/操作系统清理。部分受平台保护的信息（例如 iOS 钥匙串中的标识符）可能在重新安装后仍然存在，直至被应用或操作系统删除。

## 3. Location and weather / 定位与天气

Weather is optional. If weather is displayed, Showcase asks the operating system for location permission. When permission has been granted and a native location is available, latitude and longitude are sent to [Open-Meteo](https://open-meteo.com/) to obtain current weather conditions. Showcase does not send your Showcase anonymous user ID or analytics device ID to Open-Meteo.

If permission is denied, restricted, revoked, or a native location cannot be obtained, weather remains unavailable. Showcase does not call an IP-geolocation provider and does not silently infer your location from your IP address. The current location and weather response are kept in application memory only for display and refresh purposes; Showcase does not upload them to its Supabase analytics database.

天气功能为可选功能。需要展示天气时，Showcase 会向操作系统申请定位权限。在您已授权且系统可以取得原生位置时，应用会将经纬度发送给 [Open-Meteo](https://open-meteo.com/) 以获取当前天气。Showcase 不会向 Open-Meteo 发送 Showcase 匿名用户 ID 或统计设备 ID。

如果权限被拒绝、受限、撤回，或无法取得原生位置，天气将不可用。Showcase 不会调用 IP 定位服务，也不会根据 IP 地址静默推断您的位置。当前位置和天气响应仅在应用内存中用于展示和刷新，不会上传至 Showcase 的 Supabase 统计数据库。

## 4. Optional anonymous usage data / 可选匿名使用数据

When you explicitly enable “Share Usage & Crash Data,” Showcase may process the following pseudonymous information:

- a Supabase anonymous user ID, random device/installation ID, and session ID;
- device name, device model, hardware identifier, operating-system name and version, locale, time-zone offset, application version/build, and build type;
- feature interactions, event names, event time, and limited event properties;
- crash reports, stack traces, error logs, and performance diagnostics through Sentry.

Although this data does not require your name, phone number, or account email, persistent identifiers can distinguish one installation or anonymous account from another. We therefore describe the data as **pseudonymous**, not fully anonymous.

Purposes are limited to measuring feature use, diagnosing failures, maintaining service security, and improving reliability. We do not use this information for targeted advertising or tracking across other companies’ applications or websites.

当您明确开启“共享使用与崩溃数据”时，Showcase 可能处理以下假名化信息：

- Supabase 匿名用户 ID、随机设备/安装 ID 和会话 ID；
- 设备名称、设备型号、硬件标识、操作系统名称及版本、语言区域、时区偏移、应用版本/构建号和构建类型；
- 功能交互、事件名称、事件时间及有限的事件属性；
- 通过 Sentry 收集的崩溃报告、调用栈、错误日志及性能诊断信息。

这些信息不要求您提供姓名、电话号码或账户邮箱，但持久标识符仍可以区分不同的安装实例或匿名账户。因此，我们将其称为**假名化数据**，而不是完全匿名数据。

处理目的仅限于衡量功能使用情况、诊断故障、维护服务安全及提升可靠性。我们不会将这些信息用于定向广告或跨其他公司的应用、网站进行追踪。

### Turning collection off / 关闭数据收集

Turning “Share Usage & Crash Data” off stops future optional analytics and crash-report collection from Showcase. The application signs out of its anonymous Supabase session and closes the related clients. Disabling collection does not automatically erase records already received by a service provider. To request deletion of previously collected Showcase-controlled Supabase records, contact us at the address below and include the anonymous identifier if it is available to you.

关闭“共享使用与崩溃数据”后，Showcase 会停止后续可选统计与崩溃报告收集，并退出匿名 Supabase 会话、关闭相关客户端。关闭收集不会自动删除服务提供商此前已经收到的记录。如需删除此前由 Showcase 控制的 Supabase 记录，请通过文末邮箱联系我们；如您能够取得匿名标识符，请一并提供。

## 5. Feedback / 反馈

If you choose to submit feedback while anonymous usage data is enabled, Showcase sends the feedback text, the optional contact email you enter, and the random device identifier to Supabase. This information is used only to respond to the feedback, investigate the reported issue, and improve Showcase. Do not include passwords, API keys, private media URLs, or other sensitive information in feedback.

如果您在已开启匿名使用数据的情况下主动提交反馈，Showcase 会将反馈内容、您自愿填写的联系邮箱和随机设备标识符发送到 Supabase。相关信息仅用于回复反馈、调查问题及改进应用。请勿在反馈中填写密码、API Key、私人媒体 URL 或其他敏感信息。

## 6. Media sources and third-party services / 媒体源与第三方服务

Showcase can connect to services selected or configured by you, including local-network servers, cloud storage, RSS feeds, GitHub/Gitee, Immich, WebDAV, S3-compatible storage, Unsplash, Pexels, TMDB, and supported music services. The exact services contacted depend on the sources you enable.

When a request is made, the relevant provider may receive your IP address, request time, request headers, API credential or access token for that provider, search or collection identifiers, and requested media identifiers. These providers process information under their own terms and privacy policies. Showcase does not control their independent logging, security, or retention practices.

Notable providers include:

- [Supabase Privacy Policy](https://supabase.com/privacy)
- [Sentry Privacy Policy](https://sentry.io/privacy/)
- [Open-Meteo Privacy Policy](https://open-meteo.com/en/terms)
- [Unsplash Privacy Policy](https://unsplash.com/privacy)
- [Pexels Privacy Policy](https://www.pexels.com/privacy-policy/)
- [TMDB Privacy Policy](https://www.themoviedb.org/privacy-policy)

Showcase 可以连接由您选择或配置的服务，包括局域网服务器、云存储、RSS、GitHub/Gitee、Immich、WebDAV、兼容 S3 的存储、Unsplash、Pexels、TMDB 以及受支持的音乐服务。实际访问哪些服务取决于您启用的媒体源。

发送请求时，相应提供商可能接收到您的 IP 地址、请求时间、请求头、该提供商的 API 凭据或访问令牌、搜索/集合标识符以及所请求的媒体标识符。各提供商依据其自身条款和隐私政策处理信息；Showcase 无法控制其独立的日志、安全及保留策略。

## 7. Local network / 局域网

Showcase may request local-network access to discover or connect to devices and media servers that you configure, such as SMB, FTP, SFTP, WebDAV, Immich, or compatible storage services. Local-network addresses and credentials are used to establish the requested connection. Showcase does not upload your local-network credentials to its analytics database.

Showcase 可能申请局域网权限，以发现或连接您配置的设备和媒体服务器，例如 SMB、FTP、SFTP、WebDAV、Immich 或兼容存储服务。局域网地址及凭据仅用于建立您要求的连接，不会上传到 Showcase 的统计数据库。

## 8. Retention and deletion / 保留与删除

- Local settings and caches remain until you clear them, delete a source, uninstall the application, or the platform removes them.
- Supabase anonymous-account, device, analytics, and feedback records are retained while needed to operate, secure, diagnose, and improve Showcase, or until a valid deletion request is completed.
- Sentry retains diagnostic records according to the retention configured for the Showcase project and Sentry’s applicable terms.
- Third-party media and weather providers determine retention for requests they receive under their own policies.

You may clear application caches in Settings, revoke location permission in system settings, disable anonymous usage data in Showcase, and contact us to request access to or deletion of Showcase-controlled server records. We may need the anonymous device or user identifier to locate pseudonymous records. We may retain limited records when required by law, security, fraud prevention, or dispute resolution.

- 本地设置和缓存会保留至您清除缓存、删除媒体源、卸载应用，或平台将其移除。
- Supabase 中的匿名账户、设备、统计和反馈记录，会在运营、保障安全、诊断及改进 Showcase 所需期间保留，或保留至有效删除请求处理完成。
- Sentry 诊断记录依据 Showcase 项目的保留配置及 Sentry 适用条款保存。
- 第三方媒体及天气提供商依据各自政策决定其收到请求的保留期限。

您可以在设置中清除缓存、在系统设置中撤回定位权限、在 Showcase 中关闭匿名使用数据，并联系我们申请访问或删除由 Showcase 控制的服务端记录。为定位假名化记录，我们可能需要匿名设备或用户标识符。在法律、安全、防欺诈或争议处理要求下，我们可能保留有限记录。

## 9. Security / 安全

We use reasonable technical and organizational measures, including platform-protected storage where available, encrypted local source configuration, HTTPS for fixed public services, and limited-purpose credentials. No method of storage or transmission is completely secure. You are responsible for protecting credentials for media services and for using trusted local-network servers.

我们采取合理的技术与组织措施，包括在可用时使用平台保护存储、加密本地媒体源配置、固定公共服务使用 HTTPS，以及限制凭据用途。但任何存储或传输方式都无法保证绝对安全。您有责任保护媒体服务凭据，并仅连接可信的局域网服务器。

## 10. Children / 儿童隐私

Showcase is not directed to children under 13, or the higher minimum age required by applicable law. We do not knowingly request a child’s name, contact details, or account information. If you believe a child has provided personal information through feedback or diagnostics, contact us so that we can investigate and delete it where required.

Showcase 并非面向 13 岁以下儿童，或适用法律规定的更高最低年龄人群。我们不会主动要求儿童提供姓名、联系方式或账户信息。如您认为儿童通过反馈或诊断功能提交了个人信息，请联系我们处理。

## 11. International processing / 跨境处理

Supabase, Sentry, Open-Meteo, and media providers may process requests in countries or regions different from yours. By enabling an optional service or configuring a remote source, information may be transferred according to that provider’s infrastructure and legal terms. Mandatory rights under your local data-protection and consumer laws continue to apply.

Supabase、Sentry、Open-Meteo 及媒体提供商可能在您所在国家或地区以外处理请求。启用可选服务或配置远程媒体源后，信息可能依据该提供商的基础设施与法律条款进行跨境传输。您依据当地数据保护及消费者法律享有的强制性权利不受影响。

## 12. Changes / 政策变更

We may update this policy when features, providers, or legal requirements change. The current version and effective date will be published at the policy URL and made accessible from Showcase. Material changes will be highlighted in the application or release notes where appropriate.

当功能、提供商或法律要求发生变化时，我们可能更新本政策。当前版本及生效日期会发布于隐私政策页面，并可从 Showcase 内访问；重大变化将在适当情况下通过应用或发行说明提示。

## 13. Contact / 联系我们

For privacy questions, data-access requests, or deletion requests, contact:

如有隐私问题，或需要申请访问、删除数据，请联系：

**Joe Chen**

**Email:** [mrjctech@gmail.com](mailto:mrjctech@gmail.com)
