# iOS 画廊改用照片资产引用的可行性

核对日期：2026-09-10。本文仅记录调研与建议，未修改应用实现。

可以避免由应用长期保存一份完整照片副本：取得 PhotoKit 访问授权后，在业务数据中保存 `PHAsset.localIdentifier`，展示时重新获取资产并请求图像。`localIdentifier` 是持久标识，并不是图片文件路径，也不携带访问权限。[Apple：PHObject](https://developer.apple.com/documentation/photos/phobject?language=objc)、[Apple：PhotoKit 与 PHPicker 示例](https://developer.apple.com/videos/play/wwdc2020/10652/)

## 权限与选择流程

- 应用请求的是 `.readWrite` 访问级别；由用户选择有限访问或完整访问，应用不能强制授予 `.limited`。有限访问只允许读取用户授权的资产；需要配置 `NSPhotoLibraryUsageDescription`，并处理拒绝、撤销及选择范围变化。[Apple：照片库隐私与有限访问](https://developer.apple.com/documentation/photokit/delivering-an-enhanced-privacy-experience-in-your-photos-app?changes=la_1_1___2&language=objc)
- **PHPicker 的选中结果不会扩大 PhotoKit 有限访问范围。** 即使配置照片库并拿到 `assetIdentifier`，也不能假定该资产已获 PhotoKit 授权。因此，“普通选图后直接保存 identifier”不足以保证日后可读取。[Apple：Meet the new Photos picker，Using PHPicker with PhotoKit 段落](https://developer.apple.com/videos/play/wwdc2020/10652/)
- 已处于有限访问时，可用 `presentLimitedLibraryPicker` 让用户调整授权范围，再从可访问资产中选择要加入应用画廊的项目。授权范围与应用自己的画廊成员关系应分别管理；授权变化可通过照片库变更观察机制感知。[Apple：presentLimitedLibraryPicker](https://developer.apple.com/documentation/photos/phphotolibrary/presentlimitedlibrarypicker%28from%3A%29?language=swift)

## 加载与存储边界

建议新增独立的资产引用类型，由 iOS 加载层根据 identifier 获取 `PHAsset`，交给 `PHImageManager` 请求所需尺寸的图片。缩略图与临时缓存可以保留，但无需为每个新增画廊项目永久写入一份原图。这是基于上述 API 的实现建议，并非当前代码行为。

这不能保证“绝不占用额外空间”：显示图片仍需内存及系统缓存。若图片仅在 iCloud 中，`PHImageRequestOptions.isNetworkAccessAllowed = true` 会允许下载，并可用 `progressHandler` 展示进度；默认值为 false，云端图片可能不可用。应处理离线、下载失败、资产删除和权限收回。[Apple：isNetworkAccessAllowed](https://developer.apple.com/documentation/photos/phimagerequestoptions/isnetworkaccessallowed)

## 当前项目对应点

以下代码情况由本次主任务本地检查确认：

- `GalleryMediaPicker.nonWeb.kt` 的 `persistForGalleryIfNeeded` 在 iOS 读取字节、计算哈希并写入 `gallery_media`，所以目前采用的是应用文件副本。
- `Platform.ios.kt` 的 `ensureGalleryReadPermissionIfNeeded` 直接返回 true，目前没有执行 PhotoKit 读取授权。
- `ImageLoaderConfig.ios.kt` 尚无 PhotoKit 资产加载适配；`Info.plist` 只有 `NSPhotoLibraryAddUsageDescription`，缺少读取用途说明。

因此需要同时调整 iOS 授权、选择结果、持久化引用和图片加载；仅删除复制步骤不足以实现可重启后读取。

对既有 `gallery_media` 文件，若历史记录没有保存源资产 identifier，就不能仅凭应用文件路径可靠反推唯一的照片库资产。建议兼容保留旧记录，新加入的照片使用资产引用；需要迁移时由用户重新选择并确认匹配，不应直接删除旧副本。这是根据当前持久化信息作出的工程判断。
