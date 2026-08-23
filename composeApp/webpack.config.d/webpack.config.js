;(function(config) {
  config.resolve = config.resolve || {};
  config.resolve.fallback = {
    ...(config.resolve.fallback || {}),
    os: require.resolve('os-browserify/browser'),
    path: require.resolve('path-browserify')
  };

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
