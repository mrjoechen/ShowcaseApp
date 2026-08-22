;(function(config) {
  config.resolve = config.resolve || {};
  config.resolve.fallback = {
    ...(config.resolve.fallback || {}),
    os: require.resolve('os-browserify/browser'),
    path: require.resolve('path-browserify')
  };

  config.devServer.headers = [
    { key: 'Cross-Origin-Opener-Policy', value: 'same-origin' },
    { key: 'Cross-Origin-Embedder-Policy', value: 'require-corp' }
  ]
})(config);
