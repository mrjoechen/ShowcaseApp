# 图片折叠切换预览

独立评估组件，尚未注册到 Showcase 的正式播放模式。图片通过 URL 加载，不需要账号或相册配置；首次使用需要网络。仓库和安装包不再包含示例照片。

## 查看效果

Android Studio 打开 `composeApp/src/commonMain/kotlin/com/alpha/showcase/common/ui/play/fold/FoldImagePreview.kt`：

- `FoldImageInteractivePreview`：平铺起始画面，Interactive 模式支持滑块和按钮。
- `FoldImageClosingPreview`：折叠中的横屏画面。
- `FoldImageOpeningPreview`：展开中的竖屏画面。

```sh
./gradlew :desktopApp:run --args=--fold-preview
```

Android 真机/模拟器调试预览：

```sh
./gradlew :androidApp:installDebug
adb shell am start -n com.alpha.showcase.android.dev/com.alpha.showcase.android.FoldPreviewActivity
```

Activity 仅存在于 debug variant。若本地修改了 applicationId，使用对应包名。

默认是带标题、留白和控制面板的控件预览。图片上下各预留 14% 高度，使折叠时的完整轮廓露在背景上；横屏手机采用图片在左、控制面板在右的布局。点击“进入全屏”后图片以 Crop 模式铺满屏幕，不显示按钮，左右滑动翻页；拖动不足图片宽度 20% 时松手会回弹。控件面板新增默认开启的“全屏翻页时后退”开关：翻页时以中心为支点随折叠角度缩小，最小缩至 74%，为上下透视轮廓留出空间；翻页完成或短滑动回弹时恢复 100%。缩放直接跟随进度，手势区域仍为完整屏幕；关闭开关可恢复原来的全屏裁剪效果。系统返回可退出纯图片模式，回到控件预览。Android 调试入口隐藏状态栏和导航栏，可从屏幕边缘滑动临时唤出系统栏。

拖动滑块可停在任意进度；上一张/下一张播放一次；自动播放循环浏览八张样图；拖动滑块会暂停播放；“渐变模糊”开关用于对比。

## 后续接入播放页

`FoldImageTransition` 接收已经加载的 `Painter`、进度 lambda、正反方向和 `ContentScale`。例如：

```kotlin
FoldImageTransition(
    current = currentPainter,
    next = nextPainter,
    progress = { transition.value }, // 0 = current, 1 = next
    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
    direction = FoldDirection.Forward,
)
```

播放页应先加载下一张图片，等 Painter 可用后启动动画。完成时，在同一次状态更新中将 next 提升为 current 并将进度归零。图片加载、分页和自动播放生命周期由调用方管理。进度为 0/1 时直接绘制完整图像。

## 实现与边界

- 使用两张图片的 GraphicsLayer 绘制记录，避免每帧 `toImageBitmap()` 或重新解码图片。
- 当前图片半边向中缝收起，下一张图片另一半展开；固定半边不在中途换图。
- 不绘制人工折痕线。使用完整底图、1 个物理像素的同图重叠，并将黑色底限制在图片外的透视边缘，避免抗锯齿覆盖造成暗色细缝。
- 折叠轮廓使用带透视的四边形裁剪；图像保持原来的正面投影，不随折叠被横向压扁。组件支持超出上下边界的透视轮廓；全屏 Demo 将其裁剪在屏幕范围内。
- 渐变模糊使用五个原生 BlurEffect 级别与清晰层按图像位置混合，是连续可变模糊的近似；暗部按位置和角度使用非线性曲线。
- 上下图片边界与折叠外轮廓分别处理：模糊源带三倍最大纵向模糊半径的黑色扩展区域，照片颜色与覆盖范围一起模糊，渐变混合和变暗也绘制到透视留白中。最后才用折叠轮廓裁剪，因此颜色可以越过原图边界，形成参考网页那种玻璃感的柔化边缘。它是投影图像与边缘模糊的近似，不是物理光线折射或额外的波纹变形。
- Android 12+ 和 Skia 平台支持模糊；更早 Android 保留折叠与变暗，BlurEffect 不生效。没有引入仅在 API 33+ 可用的 AGSL。
- 本实现针对照片切换，不包含手机模型、真实曲面铰链或背面材质，也不声称与参考网页像素级一致。
- 五个模糊级别会增加离屏绘制成本。当前属于效果评估阶段；正式接入全屏持续播放前，应在目标 Android 设备上验证帧耗时和显存占用。

## 素材来源

折叠与渐变效果参考：https://github.com/chuspeeism/iphone-duo 。本组件独立实现，没有使用该项目的 Apple 模型或图片。

八张风景照片通过 Unsplash 图片 CDN 的 URL 加载，统一请求 1600px 宽度、q=85 的压缩版本，遵循 https://unsplash.com/license ：

- 山脊：https://images.unsplash.com/photo-1464822759023-fed622ff2c3b
- 湖畔：https://images.unsplash.com/photo-1470770841072-f978cf4d019e
- 林间：https://images.unsplash.com/photo-1441974231531-c6227db76b6e
- 海岸：https://images.unsplash.com/photo-1507525428034-b723cf961d3e
- 沙丘：https://images.unsplash.com/photo-1509316785289-025f5b846b35
- 薄雾：https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05
- 山谷：https://images.unsplash.com/photo-1469474968028-56623f02e42e
- 高山湖：https://images.unsplash.com/photo-1501785888041-af3ef285b470

URL 列表维护在 `FoldImagePreview.kt` 的 `FoldDemoPhotoUrls` 中。使用项目已有的 Coil 网络加载器及缓存，八张图片全部就绪后才进入交互预览，确保连续翻页和反向切换时不会显示未加载图片。加载页显示进度，失败可重试；已经成功的图片不会重新请求。缓存由 Coil 管理，不作为仓库资源提交。

## 验证

```sh
./gradlew :composeApp:desktopTest --tests '*FoldImageTransitionTest' \
  :composeApp:compileAndroidMain :desktopApp:compileKotlinJvm :androidApp:assembleDebug
```

渲染测试使用程序绘制的格纹 Painter，不依赖网络或图片文件，覆盖起止画面、正反方向中缝换面、上下图片边缘向外扩散及向内柔化、静止时不扩散、全屏后退开关及横竖屏双向滑动/回弹恢复、模糊开关的像素差异、前后切换、滑块打断自动播放和竖屏布局。截图输出至 `composeApp/build/fold-verification/`。另有 Coil 加载流程测试，覆盖全部图片就绪前的等待、单张失败后的重试，以及翻页和进入全屏时不重复请求已加载图片。

模糊强度已再次增强：横向最大半径为参考值的 2.5 倍（随图片宽度缩放，上限 80dp），纵向半径额外增加 20%（上限 96dp），强化上下图片边缘的颜色扩散。相较上一版，横向半径约增加 43%，纵向约增加 71%；空间衰减指数由 1.15 调整为 1.0，让模糊覆盖更宽的折叠区域。强度仍随折叠角度变化，中缝归零，静止画面保持清晰。

控件预览和全屏模式统一使用 24dp 圆角，静止图片和折叠时的外侧边缘都做圆角处理，中缝保持连续；全屏翻页后退时同样保留圆角。组件可通过 `cornerRadius` 参数调整圆角，默认 0dp。圆角应用于绘制轮廓，不会裁掉留白中的折叠部分。
