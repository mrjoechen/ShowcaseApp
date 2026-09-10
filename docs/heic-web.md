# Web HEIC 支持

JS 和 Wasm 通过 `WebHeicDecoder` 在 Coil 解码层读取 HEIC/HEIF 静态主图。
本地文件、远程下载、鉴权和缓存继续使用现有 Coil 链路；原文件不会被修改或上传到转换服务。

`composeApp/heic-web` 是本地 npm 模块，固定依赖 `libheif-js 1.23.2`。
Webpack 将解码器打包到独立 Worker chunk，仅在遇到 HEIC 时加载，适配现有
`script-src 'self' 'wasm-unsafe-eval'` CSP。JS/Wasm 分别提供模块桥接声明。

- 根据 `ftyp` 的 HEVC 主品牌或兼容品牌识别，不依赖文件名或 MIME；普通图片、GIF 和 AVIF 不被截获。
- 读取 primary image，支持容器旋转、镜像、clean aperture 和 10 位 SDR。
- 解码在 Worker 中进行，同一页面串行处理 HEIC；完成、失败、取消或 60 秒超时均终止 Worker，释放 WASM 堆。
- 按 Coil 请求尺寸生成不可变 Bitmap，可复用内存缓存。

## 边界

只显示静态主图，不播放 HEIF 序列或 Live Photo；不合成 HDR 增益图。
输入上限 64 MiB、主图上限 64 × 1024 × 1024 像素。解码仍需全分辨率缓冲区，
跨 Kotlin/JS 与 Kotlin/Wasm 的像素传输使用 Base64，另有内存开销；低内存设备的大图可能失败。
每张图使用独立 Worker，优先保证内存释放，代价是重新初始化解码器。

## 验证

```powershell
npm --prefix composeApp/heic-web ci --ignore-scripts
npm --prefix composeApp/heic-web test
./gradlew.bat :composeApp:jsBrowserTest :composeApp:wasmJsBrowserTest --tests '*WebHeicDecoderTest*'
./gradlew.bat :composeApp:jsBrowserDevelopmentWebpack :composeApp:wasmJsBrowserDevelopmentWebpack
```

Node 测试复用桌面端自生成的 HEIC 文件，覆盖方向、颜色、裁剪、10 位 SDR 和损坏文件恢复。
Web 测试覆盖格式识别、真实 Coil 加载、缩放和缓存复用。

本次 Windows/Chrome 验证：Node 3 项测试、JS 浏览器 3 项测试和 JS/Wasm 开发打包通过。
另验证了实际 Worker 在页面 CSP 下的解码、取消和失败恢复。
Wasm 测试源码编译通过，但测试可执行文件链接遇到 Kotlin 编译器内部错误
`Key kotlin.test/getArguments|getArguments(){}[0] is missing in the map`，未执行 Wasm 浏览器回归。

首次引入依赖后，若本机已有旧 Yarn 锁，先执行 `kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock`。
当前项目忽略 Yarn 锁文件；本地 npm 模块额外保留 package-lock.json。

## 来源与许可

- [libheif-js](https://github.com/catdad-experiments/libheif-js)，LGPL-3.0；许可全文随 Web 资源分发，关于页面提供源码链接。
- [libheif](https://github.com/strukturag/libheif)，解码能力与底层编译来源见上游说明。
