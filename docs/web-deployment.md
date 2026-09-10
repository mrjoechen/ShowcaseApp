# Web 构建与部署

在仓库根目录生成完整的 JS 生产发布包：

```sh
./gradlew :composeApp:jsBrowserDistribution --profile --console=plain
```

发布目录是 `composeApp/build/dist/js/productionExecutable/`。`jsBrowserProductionWebpack` 只是其中的打包步骤；不要直接部署 `build/kotlin-webpack/` 下的中间产物。

## 完整部署同一版本的资源

发布包的结构如下：

```text
productionExecutable/
├── index.html
└── _showcase/
    └── <hash>/
        ├── ShowcaseApp.js
        ├── composeResources/
        ├── *.wasm
        └── …其他脚本、字体和样式
```

根目录的 `index.html` 已包含相对路径 `<base href="./_showcase/<hash>/">`。它让脚本、Compose 字符串、字体、WASM 和异步加载的资源使用同一版内容，可部署在 `/`、`/app/` 或 `/仓库/web/` 下。目录访问地址应保留结尾的 `/`。

- 复制 **整个 `productionExecutable/` 的内容**，保持目录结构；不要只上传 JS，也不要手动改写 `<base>`。
- 自行管理服务器时，建议先上传新 `_showcase/<hash>/`，确认文件完整后，再替换根 `index.html`。
- 这类服务器上应保留旧 hash 目录，直到旧页面退出、旧入口缓存失效，避免已经打开的页面后续加载资源时遇到 404。自动同步时不要立即删除旧版本目录。
- 入口 HTML 应及时重新验证缓存，例如使用 `Cache-Control: no-cache`；带 hash 的资源目录可长期缓存。

当前 GitHub Pages 工作流每次上传完整站点，不会自动保留上次发布的 hash 目录；部署后，长期打开的旧页面可能需要刷新。

首次切换到此结构时，如果 CDN 仍缓存旧的根 `index.html`，需要更新或清除 **HTML 入口的缓存**，并检查 CDN 没有覆盖入口的缓存策略。后续发布只要入口及时更新，就不需要每次手动清理同名 JS 缓存：资源内容变化会生成新的目录 URL。

本次新包已在本地 Chromium 和 Safari 26.5.2 验证 Showcase、中文首页及 `/app/`、`/repo/web/` 路径。线上固定 URL 曾执行不同的旧 bundle 并报 `onWasmReady` 未定义，而增加查询参数的对照能启动。`con` 的精确成因未复现；整包版本化用于防止资源混版，线上和手机仍待重新部署后验收。

## 生产打包速度与体积

默认保留 Kotlin 生产模式的死代码消除、Terser 变量名缩短和 source maps，关闭耗时较长的额外 Terser 压缩变换。需要更小的下载包、可以接受较长构建时，启用原来的两轮压缩：

```sh
SHOWCASE_FULL_JS_COMPRESSION=true ./gradlew :composeApp:jsBrowserDistribution --profile --console=plain
```

该环境变量已登记为 webpack 任务输入，切换时会使原有任务结果失效；取消变量或设为 `false` 恢复默认模式。

2026-09-01 的本机测量：

| 配置 | webpack 耗时 | 主 JS | 主 JS gzip |
| --- | ---: | ---: | ---: |
| 原两轮压缩，保留 maps | 828 秒 | 10.30 MB | 2.62 MB |
| 默认快速模式，保留 maps | 83 秒 | 11.72 MB | 2.91 MB |

828 秒来自此前成功构建的日志；83 秒是在已有 Kotlin 编译产物上单独运行 webpack 的对照，并非完整重新编译时间，也不是 GitHub Actions 已验证的耗时。对照使用 Node 25.6.1、webpack 5.101.3、terser-webpack-plugin 5.6.1。gzip 按 level 9 测量，实际传输大小还取决于服务器压缩设置。

随后用修改后的源码运行完整 `jsBrowserDistribution --profile` 成功，总耗时 2 分 4 秒：生产 Kotlin 链接 46.4 秒，webpack 任务 66.0 秒，资源发布 0.8 秒。该次构建复用了已有依赖和部分模块缓存，不代表全新 CI 环境的冷构建时间。最终主 JS 为 11.72 MB，gzip level 9 为 2.91 MB。

## 定位 GitHub Actions 耗时

工作流已启用 `--build-cache --profile --console=plain`，并在构建后尝试上传 `js-build-timing` artifact，保留 7 天。下载后打开其中的 HTML 报告，在任务耗时表中区分 Kotlin 编译/链接、`jsBrowserProductionWebpack` 和资源发布步骤。不要把整个 Build 步骤的时间全部算成 webpack 时间。

本机报告也位于 `build/reports/profile/`。如果任务被强制终止、进程崩溃或尚未结束，报告可能还未生成，此时需结合 Actions 构建日志判断最后执行到的任务。

旧配置的 [运行 33421032877](https://github.com/mrjoechen/ShowcaseApp/actions/runs/33421032877) 已通过登录后的完整日志确认：JS 构建步骤成功，耗时 38 分 28 秒；其中 [webpack 为 1,849,205 毫秒，约 30 分 49 秒](https://github.com/mrjoechen/ShowcaseApp/actions/runs/33421032877/job/99583177629#step:11:334)。随后 `Assemble Pages artifact` 在创建 `site/web` 时[报 `Permission denied`](https://github.com/mrjoechen/ShowcaseApp/actions/runs/33421032877/job/99583177629#step:14:64)，上传和部署均未执行。

Jekyll 容器生成的 `site/` 归 root 所有，后续步骤无法写入。工作流现仅恢复生成目录 `site/` 的所有权，再组装发布包；不会更改整个仓库权限。新组装逻辑已在本地验证，仍待 Actions 实际运行验收。
