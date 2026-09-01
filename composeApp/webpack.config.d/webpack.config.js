;(function(config) {
  config.resolve = config.resolve || {};
  config.resolve.fallback = {
    ...(config.resolve.fallback || {}),
    os: require.resolve('os-browserify/browser'),
    path: require.resolve('path-browserify')
  };

  if (config.mode === "production") {
    const TerserPlugin = require("terser-webpack-plugin");
    // Kotlin's production linker already performs dead-code elimination. Terser's
    // additional compression passes over the generated Compose modules dominate
    // bundling time. Keep name mangling and source maps, with full compression
    // available when the smaller download is worth the longer build.
    config.optimization = config.optimization || {};
    config.optimization.minimizer = [new TerserPlugin({
      terserOptions: {
        compress: process.env.SHOWCASE_FULL_JS_COMPRESSION === "true"
          ? { passes: 2 }
          : false,
        mangle: true
      }
    })];
  }

  // GitHub Pages serves the app under /<repo>/, so keep chunk URLs relative.
  if (process.env.GITHUB_PAGES === "true") {
    config.output = config.output || {};
    config.output.publicPath = "./";
  }

  if (config.devServer) {
    config.devServer.headers = [
      { key: 'Cross-Origin-Opener-Policy', value: 'same-origin' },
      { key: 'Cross-Origin-Embedder-Policy', value: 'require-corp' }
    ];
  }
})(config);
