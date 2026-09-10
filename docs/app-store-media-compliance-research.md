# Showcase App Store 远程图片、电影海报与专辑封面合规研究

> 核验日：2026-08-22（Asia/Shanghai）  
> 范围：Apple App Store 审核，Unsplash API，Pexels API，TMDB API，以及当前实现中的专辑封面。  
> 来源：只使用 Apple、Unsplash、Pexels、TMDB 的官方条款、开发者文档与官方帮助页。应在提审前再次核验动态网页并保存当时版本。

## 先说结论

当前版本不建议直接提交 App Store。这不只是“设置页补一句声明”能解决的问题：

1. **Apple Music 专辑封面是当前最明确的阻断项。** 实现会直接请求 Apple Music 网页并解析其 `serialized-server-data`。Apple Developer Program License Agreement 明确禁止未经授权抓取、检索、缓存或索引 Apple 或其许可方的数据；即使改用 MusicKit，其专辑美术图也不能脱离音乐播放或歌单管理单独使用。只加“来源于 Apple Music”不会产生授权。[官方协议 §3.3.3(D)、§3.3.4(D)](https://developer.apple.com/support/terms/apple-developer-program-license-agreement/)
2. **Unsplash 不能只在设置/关于页统一声明。** API 条款要求每次展示照片时同时标注 Unsplash 和摄影师，并链回摄影师的 Unsplash 主页；还必须使用 API 返回的 `photo.urls.*` 热链。[官方 API Guidelines](https://help.unsplash.com/en/articles/2511245-unsplash-api-guidelines) [API Terms §6、§9](https://unsplash.com/api-terms)
3. **Pexels 的自动图片轮播也不应只在设置页声明。** API 文档要求显著链回 Pexels；官方对“自动供给未修改背景/屏保”的说明进一步要求把归属信息做进展示画面。[官方 API 文档](https://www.pexels.com/api/documentation/) [壁纸 App 指引](https://help.pexels.com/hc/en-us/articles/4405588861721-Can-I-use-the-API-as-a-wallpaper-app)
4. **TMDB 正适合在“关于/制作人员”声明，而且是强制要求。** 必须使用批准的 TMDB Logo，不得暗示背书，并显著放置官方免责文案；FAQ 明确要求在 About/Credits 中归属。[官方 FAQ](https://developer.themoviedb.org/docs/faq) [Logos & Attribution](https://www.themoviedb.org/about/logos-attribution) [API Terms §3](https://www.themoviedb.org/api-terms-of-use)
5. **如果 Showcase 是付费、带广告、订阅或以其他方式创收，TMDB 是另一个阻断项。** TMDB 免费开发者 API 仅授权非商业使用；直接或间接创收需另行签订书面商业协议。[官方 FAQ](https://developer.themoviedb.org/docs/faq) [API Terms §2](https://www.themoviedb.org/api-terms-of-use)
6. **现有隐私政策与 App Privacy 声明需重做实际数据流核对。** Apple 要求隐私政策同时出现在 App Store Connect 和 App 内，明确说明收集、用途、第三方共享、保留/删除和撤回同意。App Privacy 还必须覆盖第三方合作伙伴的数据收集。[审核指南 5.1.1](https://developer.apple.com/app-store/review/guidelines/) [App Privacy Details](https://developer.apple.com/app-store/app-privacy-details/) [App Store Connect 帮助](https://developer.apple.com/help/app-store-connect/manage-app-information/manage-app-privacy)

综合评估：不处理上述项目时，**App Store 审核拒绝/下架风险高，API Key 停用或合同违约风险高，第三方著作权、商标、肖像/隐私权风险中到高**。App Store 通过审核也不代表 Apple 替开发者确认了第三方权利。

## 设置页/关于页到底要放什么

| 内容源 | 是否必须在关于/设置页声明 | 关于页是否已经足够 | 建议展示 |
| --- | --- | --- | --- |
| TMDB | **是。** FAQ 指定 About/Credits。 | 可以，但必须同时有批准 Logo、完整免责文字与官网链接。 | 批准 TMDB Logo + `This application uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB.` + `https://www.themoviedb.org`。[条款](https://www.themoviedb.org/api-terms-of-use) |
| Unsplash API | 建议在 Legal/Credits 总说明，但强制义务在**每次图片展示**。 | **不足够。** | `Photo by <photographer> on Unsplash`；摄影师名和 Unsplash 均可点击，链回摄影师主页/Unsplash，链接带 `utm_source=showcase&utm_medium=referral`。[归属指引](https://help.unsplash.com/en/articles/2511315-guideline-attribution) |
| Pexels API | 建议在 Legal/Credits 总说明，API 文档另要求使用 API 时显著链回 Pexels。 | 对本 App 的“自动轮播未修改图片”场景，**不足够。** | `Photo by <photographer> on Pexels` + 照片页/摄影师页链接；至少显示 `Photos provided by Pexels`。自动背景/屏保需在展示画面中归属。[归属说明](https://help.pexels.com/hc/en-us/articles/900005851903-How-should-I-give-credit-Can-I-use-your-logo) [壁纸指引](https://help.pexels.com/hc/en-us/articles/4405588861721-Can-I-use-the-API-as-a-wallpaper-app) |
| Apple Music/专辑封面 | 可以有来源声明，但它不会替代内容授权。 | **完全不足够。** | 在取得符合具体用途的书面授权以前，不应把封面作为独立轮播内容。使用 MusicKit 时仍必须遵守 Apple Music Identity Guidelines 和内容用途限制。[官方协议](https://developer.apple.com/support/terms/apple-developer-program-license-agreement/) |
| App 隐私政策 | **是。** Apple 要求 App Store Connect 和 App 内都有容易访问的链接。 | 页面位置不够，内容必须与实际数据流一致。 | 单独的“法律与隐私”入口：隐私政策、第三方内容来源、缓存清理、权利投诉/下架联系方式。[审核指南 5.1.1](https://developer.apple.com/app-store/review/guidelines/) |

## 当前代码与文档的具体缺口

以下只是静态取证，本文未修改应用代码：

- [AboutView.kt](../composeApp/src/commonMain/kotlin/com/alpha/showcase/common/ui/settings/AboutView.kt) 确实已有隐私政策入口，也在“开源许可证”列表里列出 Pexels/TMDB/Unsplash API。但后者只是一个通用的 `Service API (Terms of Use)` 链接，没有 TMDB Logo/指定免责文字，也不是 Unsplash/Pexels 要求的展示级归属。
- [UnsplashData.kt](../showcase-api/src/commonMain/kotlin/com/alpha/showcase/api/unsplash/UnsplashData.kt) 的 `Photo` 模型只保留基本字段与 `urls`，没有摄影师 `user`、摄影师主页、照片页和 `links.download_location`。[UnsplashSourceRepo.kt](../composeApp/src/commonMain/kotlin/com/alpha/showcase/common/repo/UnsplashSourceRepo.kt) 又把对象压缩为 URL/ID/MIME，因而当前数据模型无法完成强制归属和 download-event 上报。
- [PexelData.kt](../showcase-api/src/commonMain/kotlin/com/alpha/showcase/api/pexels/PexelData.kt) 虽然解析了 `photographer`、`photographer_url` 和照片 `url`，[PexelSourceRepo.kt](../composeApp/src/commonMain/kotlin/com/alpha/showcase/common/repo/PexelSourceRepo.kt) 只保留 `src.original` 和 ID，展示层无法给出摄影师归属。
- [TmdbSourceRepo.kt](../composeApp/src/commonMain/kotlin/com/alpha/showcase/common/repo/TmdbSourceRepo.kt) 直接把 TMDB 海报/背景图 CDN URL 变为轮播项；未见与内容同步的权利归属数据，About 也没有指定声明。
- [AlbumApi.kt](../showcase-api/src/commonMain/kotlin/com/alpha/showcase/api/album/AlbumApi.kt) 会下载 Apple Music 网页 HTML，再从 `#serialized-server-data` 提取 JSON；[AlbumSourceRepo.kt](../composeApp/src/commonMain/kotlin/com/alpha/showcase/common/repo/AlbumSourceRepo.kt) 将返回的 Apple Music 封面 URL 当作独立图片播放。这同时命中 Apple 对抓取和 MusicKit 封面独立使用的限制。
- 非 Apple 的专辑封面经由可远程配置的聚合服务取得，并支持 QQ 音乐/网易云歌单识别。仓库中没有看到这两家内容授权、聚合服务向 Showcase 转授权的证据或下架机制。在未向各权利方完成一手条款/书面授权核验前，应将该功能视为未清除的发行阻断项，不能因“只展示元数据”就推定免责。
- [App.kt](../composeApp/src/commonMain/kotlin/App.kt) 为所有图片源共享 Coil 磁盘缓存，只有容量上限，没有按提供商分类的最长存续期。[NetworkFileCacheService.kt](../composeApp/src/commonMain/kotlin/com/alpha/showcase/common/cache/NetworkFileCacheService.kt) 另会把远程图片 URL 保存到 Room。这不等于已证明“必然非法”，但与 Unsplash 视图计数/TMDB 六个月上限不能靠一个无期限通用缓存安全满足。
- [privacypolicy.md](privacypolicy.md) 生效日仍为 2023-04-01，列出了分析/崩溃/后端等服务，但未列 Unsplash、Pexels、TMDB 以及其直连 CDN/API 的数据流，也没有清楚给出所有数据的保留/删除、撤回同意和用户请求删除流程。其“诊断数据不包含任何可识别信息”的绝对表述也需与 IP、设备数据、Sentry/Supabase/分析 SDK 实际配置重新核对。
- 仓库中未找到 `PrivacyInfo.xcprivacy`。这一点本身还不足以断定必然拒审，因为是否必须由实际使用的 Required Reason API 和已打包第三方 SDK 决定。但提审前必须用 Xcode 生成 Privacy Report，核对第三方 SDK 签名/隐私清单和 App Store Connect 标签。[官方 Privacy Manifest 文档](https://developer.apple.com/documentation/bundleresources/privacy-manifest-files) [第三方 SDK 要求](https://developer.apple.com/support/third-party-SDK-requirements/)

## Apple App Store 审核要求

### 1. 第三方内容授权不能用“有来源声明”替代

- App Review Guideline 5.2.1 要求 App 中受保护的商标、著作权作品等必须有许可。
- Guideline 5.2.2 要求：如果 App 使用、访问、变现访问或展示第三方服务的内容，必须确保服务条款“具体允许”；Apple 要求时要能提供授权。
- Guideline 2.3.9 要求开发者拥有 App Store 图标、截图和预览中所有素材的权利。因此不要在商店截图里随机放当时的海报、专辑封面或人像照，除非已经确认该具体营销用途的权利。

官方来源：[App Review Guidelines，2.3.8–2.3.9、5.2.1–5.2.3](https://developer.apple.com/app-store/review/guidelines/)。

### 2. 远程图片本身不被禁止，但内容、功能和审核路径都必须透明

Apple 没有一条规则笼统禁止 App 加载远程媒体。但审核时后端和 URL 必须可用，账号/配置应让审核人员看到完整功能，远程下发功能不得隐藏于审核之外。远程图片/JSON 是内容，不是可执行代码；但不能借它改变 App 核心功能或载入未审核代码。[审核指南 2.1、2.3、2.5.2](https://developer.apple.com/app-store/review/guidelines/)

Showcase 是多数据源展示/屏幕播放工具，不一定被视为单纯壁纸 App；但 Apple 的 4.2/4.3 对缺乏持久工具价值的内容聚合、简单壁纸类 App 审查更严。提审材料应把设备发现、多端控制、用户自有存储源、排序/轮播等原生工具价值说清楚，不要把 Unsplash/Pexels 免费图库包装成主要价值。[审核指南 4.2、4.3](https://developer.apple.com/app-store/review/guidelines/)

### 3. UGC/上游用户上传内容

Unsplash、Pexels 和 TMDB 中有用户/贡献者上传内容。Showcase 不直接托管社区不意味着可忽略远程内容安全。Apple Guideline 1.2 要求具有 UGC/社交内容的 App 提供不当内容过滤、举报和及时处理、屏蔽滥用者、公开联系信息。上游 API 是否已审核、App 内是否有用户互动，会影响 Apple 是否对所有子要求严格适用；但至少应实现：

- 默认使用经上游筛选的内容，不默认显示 NSFW/成人内容；
- 用户可举报当前照片/海报，应用方能根据 provider + content ID 迅速屏蔽；
- 可停用一个问题源/集合/用户，并有公开权利投诉联系方式；
- 谨慎对待用户自填 RSS/GitHub/URL 等任意远程内容，不应将其选为商店截图/默认源。

商店图标、截图和预览无论 App 年龄分级如何，都应符合 Apple 对全年龄友好元数据的要求，并如实回答年龄分级问题。[审核指南 1.1、1.2、2.3.6、2.3.8](https://developer.apple.com/app-store/review/guidelines/)

## Unsplash

### 授权与商业使用

Unsplash 普通图片 License 允许免费个人和商业使用、复制、修改和分发，普通 License 不要求归属；但不得销售未实质修改的照片，也不得编辑照片以复制类似/竞争服务。[官方 License](https://unsplash.com/license) [Terms](https://unsplash.com/terms)

但 Showcase 使用的是 **API**，更严格的 API Terms/Guidelines 优先适用：

- 每次展示都强制归属 Unsplash + 摄影师 + 摄影师主页链接；
- 应使用带 `utm_source`/`utm_medium` 的链接；
- 不得为访问 API 内容收费，不应在 API 内容附近放广告；高交互业务还可能被 Unsplash 要求协商技术费；
- 不得复制 Unsplash 核心用户体验（包括非官方客户端、壁纸 App）。Showcase 有多个非 Unsplash 数据源，这对“有独立价值”有利；但自动图库轮播仍接近官方列举的高风险边界，最稳妥的做法是在上线前向 Unsplash 取得对这一具体产品流程的书面确认。

官方来源：[API Terms §9、§12](https://unsplash.com/api-terms) [High-quality Experiences](https://help.unsplash.com/en/articles/2511256-guideline-high-quality-authentic-experiences) [Replicating Unsplash](https://help.unsplash.com/en/articles/2511257-guideline-replicating-unsplash)。

### 热链、缓存与“下载事件”

- 必须直接使用 API `photo.urls.*` 返回的热链；不应把原图下载后再托管到自己 CDN。只有图片已被混合为不再是原始照片的派生创作时，官方才说无需热链。[热链指引](https://help.unsplash.com/en/articles/2511271-guideline-hotlinking-images)
- 当用户“使用”图片，例如设为背景/页眉或插入文章时，必须请求 `photo.links.download_location` 的事件端点。该端点用于计数，不是图片 URL。Showcase 中“选定一张照片作为固定背景/保存为离线内容”明显属于类似事件；“仅被自动轮播一次”是否每张都要触发不够清晰，应向 Unsplash 书面确认。[触发下载指引](https://help.unsplash.com/en/articles/2511258-guideline-triggering-a-download)
- 官方没有在公开 API Terms 中给普通客户端 HTTP/图片缓存一个明确时长。因为长期磁盘缓存可以使后续显示不再命中 Unsplash CDN，会影响视图计数，建议对 Unsplash 禁止预取/离线保存、设置较短的 HTTP 缓存且遵循 CDN cache headers，或先获得书面确认。不应把无明确时限误写成“任意长期缓存已获许可”。

### 图片中的第三方权利

Unsplash 明确提醒：其照片版权 License 不等于同时授予画面中商标/Logo、可识别人物、艺术品或其他受保护内容的权利。商业或敏感语境可能需要模特、物业、商标或作品权利人许可。[官方品牌/Logo FAQ](https://help.unsplash.com/en/articles/14224409-what-if-there-is-a-brand-logo-in-an-image-on-unsplash) [Releases and Trademarks](https://help.unsplash.com/en/articles/2612329-releases-and-trademarks)

## Pexels

### 授权、归属与产品边界

Pexels 普通 License 允许免费个人/商业使用和修改，普通 License 不要求归属。但 Terms 禁止以 Standalone 形式销售或分发内容；只做滤镜、改色、缩放或裁切仍属 Standalone。也不得批量/系统复制、复制竞争服务、用于未授权数据集或 ML/AI。[官方 License](https://www.pexels.com/legal-pages/license/) [Terms，最后更新 2024-11-15](https://www.pexels.com/terms-of-service/) [API 条款说明，更新 2026-08-20](https://help.pexels.com/hc/en-us/articles/900005880463-What-are-the-Terms-and-Conditions)

API 集成要遵守更具体的官方指引：

- 进行 API 请求时显示显著 Pexels 链接，可用 `Photos provided by Pexels` 或 Logo；
- 尽可能标注摄影师，例如 `Photo by John Doe on Pexels` 并链接照片页；申请 unlimited calls 时，Pexels 和贡献者归属都是硬性要求；
- 不得复制 Pexels 核心功能，不得做成以供图/直接下载为主的壁纸或图库 App；
- 官方对多功能平台留有例外：Pexels 可以增强用户体验，但不能成为用户体验本身。如图片自动作为未修改背景/屏保，归属必须做进展示中。

官方来源：[API Documentation](https://www.pexels.com/api/documentation/) [Credit FAQ，更新 2026-08-17](https://help.pexels.com/hc/en-us/articles/900005851903-How-should-I-give-credit-Can-I-use-your-logo) [Wallpaper FAQ，更新 2026-07-14](https://help.pexels.com/hc/en-us/articles/4405588861721-Can-I-use-the-API-as-a-wallpaper-app)。

对 Showcase 的判断是：“多数据源、远程控制、用户自有存储”有利于证明 Pexels 只是增强功能；但“自动轮播 Pexels 原图”刚好是官方指引要求屏上归属的情形。如 Pexels 是付费功能的主要内容，应先取得具体书面确认。

### 缓存与下载

Pexels 没有 Unsplash 式的强制热链或 download-event 端点。其官方建议为节省请求配额缓存 API 响应，并给出 **24 小时**作为良好缓存时长。这是建议，不是“超过 24 小时即当然违约”的明文禁令。[官方缓存建议，更新 2026-08-07](https://help.pexels.com/hc/en-us/articles/900006470063-What-steps-can-I-take-to-avoid-hitting-the-rate-limit)

但 Pexels 明确禁止 Standalone 分发、批量系统复制和竞争图库。因此：

- 不提供“下载 Pexels 原图”或离线图库导出；
- 不将图片长期镜像到自有 CDN；
- API 结果按 24 小时级别刷新，下架/失效内容能快速清除；
- 如业务必须长期离线保存原图，先向 Pexels 取得书面确认。

Pexels 条款也明确表示，可识别人物、商标/Logo、建筑/设计等可能存在额外权利，Pexels 不保证所有需要的同意或许可已取得，确认特定用途的责任归使用者。[官方 Terms §5](https://www.pexels.com/terms-of-service/)

## TMDB

### 非商业/商业分界

TMDB 官方将“主要目的是为所有者创造收入”的项目视为商业。API Terms 的例子包括：对集成 TMDB 的 App 收费、销售引入 TMDB 内容的 App、通过包含 TMDB 数据/图片的网站或推荐服务创收等。商业使用必须先签书面协议，可能付费。[官方 FAQ](https://developer.themoviedb.org/docs/faq) [API Terms §2](https://www.themoviedb.org/api-terms-of-use)

Showcase 现有隐私政策把 App 称为 `Freemium`，About 还有捐赠入口。“捐赠”是否使整个项目成为 TMDB 所定义的商业项目取决于实际模式，不应由开发者单方推定。最稳妥方案是在付费/广告/订阅上线前把收益模式书面发给 TMDB，获得“可用开发者 Key”或商业许可的书面答复。未确认前，App Store 构建应移除 TMDB 功能或保持全项目非商业且不创收。

### 归属、品牌和缓存

- 使用批准 TMDB Logo，Logo 不得比 Showcase 主标识显著，不得暗示 TMDB 背书；
- 在 About/Credits 放置显著免责文字，并链回 `https://www.themoviedb.org`；
- Logo 不得改色、改变纵横比、翻转或旋转，只用官方批准资产；
- 任何从 TMDB/API 取得的信息缓存不得超过 **6 个月**；许可终止时立即停止使用并删除/清除全部 TMDB 内容和缓存；
- 不得使用 TMDB 作为横幅广告等的通用图像托管服务，不得制作 TMDB API/内容派生物。普通海报适配的裁切/缩放是否构成禁止的“派生物”没有足够清晰，如需较大改变应先书面确认。

官方来源：[FAQ](https://developer.themoviedb.org/docs/faq) [API Terms §1–3](https://www.themoviedb.org/api-terms-of-use) [Logos & Attribution](https://www.themoviedb.org/about/logos-attribution) [Image Basics](https://developer.themoviedb.org/docs/image-basics)。

TMDB 还明确声明它不主张 API 中图片/数据的所有权，并按 DMCA 通知删除侵权内容。这意味着“TMDB 允许调用 API”不是对每张制片厂海报、明星肖像或 Logo 的不侵权保证。[官方 FAQ，API legal notice](https://developer.themoviedb.org/docs/faq)

## 专辑封面与音乐元数据

专辑封面通常是受著作权保护的美术作品，还可能包含艺人肖像、商标、摄影作品与唱片公司 Logo。歌名、艺术家名等单个事实字段的权利问题与整个数据库、排列、图片和 API 合同权利不同；不能因元数据包含事实就忽略服务条款和数据库/图片权利。Apple 要求 App 所有内容由开发者拥有或已得到内容所有者许可。[官方协议 §3.3.4(A)](https://developer.apple.com/support/terms/apple-developer-program-license-agreement/)

当前 Apple Music 实现的两个独立问题：

1. **抓取方式未授权。** Apple 协议禁止 App 使用 robot/spider/search/retrieval 工具抓取、检索、缓存、分析或索引 Apple/许可方数据，除非数据是 Apple 明确为该服务提供的。直接解析 Apple Music 网页内部 JSON 明显不是文档化 API。[官方协议 §3.3.3(D)](https://developer.apple.com/support/terms/apple-developer-program-license-agreement/)
2. **使用目的不符 MusicKit 边界。** Apple 说明 MusicKit Content 不得下载/上传/修改或同其他内容同步；专辑图和音乐文字不得脱离音乐播放或歌单管理使用。Showcase 将专辑图作为独立视觉轮播，与官方列出的允许用途不同。[官方协议 §3.3.4(D)](https://developer.apple.com/support/terms/apple-developer-program-license-agreement/)

建议选择其一：

- App Store 版先移除远程专辑封面源，只让用户选择自有/本地图片；
- 改成官方 MusicKit，且产品体验真正是音乐播放或歌单管理，专辑图与具体音乐紧密结合，同时遵守 Identity Guidelines；
- 从唱片公司、版权数据供应商或图片权利人获得明确覆盖“应用内独立循环展示、商业发行、缓存、全球地区”的书面许可。

由用户选择本地封面能降低“开发者主动分发未授权内容”的风险，但仍建议在 Terms 中要求用户只使用其有权展示的内容，并提供侵权投诉/屏蔽路径。

## 缓存、下载与离线使用的实施建议

| 提供商 | API/元数据缓存 | 图片磁盘缓存 | 用户下载/导出 |
| --- | --- | --- | --- |
| Unsplash | 仅保留完成展示/归属所需元数据，按 API/cache headers 刷新。 | 必须保持 `photo.urls.*` 热链作为实际图像来源；避免预取和长期离线镜像，持久客户端缓存边界先书面确认。 | 如用户将照片用作背景/页眉/插入/保存，调用 `download_location` 事件端点；不做原图仓库式导出。 |
| Pexels | 官方建议约 24 小时；遵守请求配额。 | 没有明文热链要求，但应允许快速清除被删内容，不镜像到自有 CDN。 | 不提供 Standalone 原图下载/分发，不做离线图库。 |
| TMDB | 硬上限 6 个月；许可终止立即清理。 | 为 TMDB 单独设置最长存续期 ≤ 6 个月和可定向清缓存机制。 | 不提供原始海报/背景图导出，不把 TMDB CDN 当通用图床。 |
| Apple Music/其他专辑封面 | 没有明确授权前不缓存。 | 当前用途下不应启用。MusicKit 内容只能在协议允许的播放/歌单管理上下文渲染。 | 不下载、导出、重托管或将封面作为独立内容包。 |

## 隐私政策与 App Privacy 标签

Unsplash API Terms 明确说它可通过图片 URL 跟踪视图、下载、搜索和点赞，用随机标识符估算设备数量，并会处理客户端 IP。Unsplash 隐私政策还列出设备信息、IP/位置、服务器日志和使用趋势。[API Terms §6–7](https://unsplash.com/api-terms) [Unsplash Privacy Policy](https://unsplash.com/privacy)

Pexels 隐私政策列出服务器日志、交互行为、设备标识符、由 IP 推断的位置等。是否对第三方 App 中的单纯 API/CDN 请求使用所有这些类别，需向 Pexels 获取针对 Showcase 集成的确认，不应未核实就在 App Privacy 中全部否认。[Pexels Privacy Policy](https://www.pexels.com/privacy-policy/)

Apple 对 `collect` 的定义是：数据被传出设备，且开发者或第三方伙伴可访问的时间超过实时处理该请求所必需的时间。仅为服务实时请求、随即丢弃的 IP/token 可能不需声明；但第三方保留的 Product Interaction、Device ID、由 IP 得出的大致位置等可能需要。需根据提供商实际保留/联系方式逐项判定，不能只因开发者自己没有服务器就回答“No data collected”。[Apple App Privacy Details](https://developer.apple.com/app-store/app-privacy-details/)

新隐私政策至少要包含：

1. 完整数据图：App 直连哪些 API/CDN/后端，每个服务收到的类型、目的和法律依据；
2. Unsplash 的强制图片视图/使用事件上报，以及提供商隐私政策链接；
3. 用户填写的 API Key、集合 ID、用户名、歌单 URL 在哪里加密存储，是否同步到 Supabase/其他服务；
4. URL 元数据缓存和图片磁盘缓存的保留期、容量上限、清空方法和提供商终止时的清理；
5. 用户如何关闭诊断/分析、撤回授权、删除账号/云端数据、联系开发者行使权利；
6. App Store Connect 标签、`PrivacyInfo.xcprivacy`、第三方 SDK privacy manifests 与公开隐私政策三者的一致性。

## 上架前整改优先级

### P0：未完成则不提审

- 移除 Apple Music 网页抓取与封面独立轮播，或获得明确书面授权并改用官方授权 API/允许用途。
- 对 QQ 音乐、网易云和聚合音乐服务建立完整的授权链；未取得前不在 App Store 构建提供该功能。
- Unsplash 数据模型保留摄影师、主页、照片页和 `download_location`；在每张照片展示时做强制归属/回链，实现需要的 download-event。
- Pexels 展示项保留摄影师和照片页元数据；对自动轮播在屏上显示归属。
- 在 Legal/Credits 加 TMDB 批准 Logo、指定免责文字和官网链接。
- 如存在任何付费、广告、订阅或收益目的，取得 TMDB 书面商业协议；否则从 App Store 构建移除 TMDB。
- 重写隐私政策，在 App Store Connect 和 App 内更新，并完成 App Privacy/SDK/privacy manifest 实际数据流核对。

### P1：审核前完成

- 对 Unsplash、Pexels、TMDB 分别实现缓存策略和定向清理，TMDB 最长不超过 6 个月。
- 不提供远程原图/海报/封面的通用下载、导出或离线内容包。
- 加当前图片举报、provider + content ID 屏蔽、下架和权利人联系路径。
- 只用已确认商店营销权利、适合全年龄的固定示例图片制作 App Store 截图/预览，不使用随机专辑封面或电影海报。
- App Review Notes 明确说明每个远程源、审核测试步骤、内容安全与归属位置，提供可用测试配置。

### P2：持续治理

- 保存 API 申请、Production/unlimited 批准、TMDB 商业协议、供应商电邮和条款版本快照，便于 Apple 按 5.2.2 要求时提供。
- 每次发版前自动/人工复核 API 条款、品牌资产和隐私标签；监控下架通知并快速清缓存。
- 限制生产 API Key，避免把 Secret 打包到客户端；Unsplash 技术指引明确说 Access Key/Secret Key 必须保密，必要时经服务端代理。[官方 API Guidelines](https://help.unsplash.com/en/articles/2511245-unsplash-api-guidelines)

## 建议保存给 App Review 的证据包

- Unsplash/Pexels 生产配额批准与实际归属截图；
- TMDB 非商业用途的书面确认，或商业协议；
- Legal/Credits、单张 Unsplash/Pexels 归属、举报/屏蔽流程截图；
- 专辑封面授权合同或“App Store 版未包含该功能”的构建说明；
- 缓存保留/定向清理测试记录，尤其是 TMDB 6 个月上限与终止清理；
- 当版隐私政策、App Privacy 问卷导出、Xcode Privacy Report 与 SDK privacy manifests。

## 来源版本备注

- Apple App Review Guidelines：官方页面在核验时显示最后更新 2026-06-08；绑定的最新协议应以开发者账户中已接受版本为准。
- Pexels Terms of Service：页面标注 Last updated 2024-11-15；Pexels API 条款 FAQ 更新 2026-08-20，Credit FAQ 更新 2026-08-17，Wallpaper FAQ 更新 2026-07-14，Cache FAQ 更新 2026-08-07。
- TMDB API Terms 搜索索引所示页面最后更新 2023-10-20；TMDB FAQ 在核验时标注 Updated 11 months ago。
- Unsplash API Terms/License 动态页面未给出清晰版本日；API Guidelines 在核验时标注 Updated over 3 weeks ago。因此必须保存 2026-08-22 时的条款快照并定期复查。

## 非法律意见声明

本文是基于公开官方条款和当前仓库代码的工程合规研究，**不构成法律意见，也不能替代针对发行地区、业务模式、实际内容和合同文本的律师审查**。尤其是商业版 TMDB、唱片公司封面权利、肖像/商标使用、中国大陆与上架地各自的隐私/内容法规，应由有资格律师结合最终产品审查。
