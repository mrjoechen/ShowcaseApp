config.set({
  client: {
    mocha: {
      // Each new Web Worker loads Karma's shared development bundle (~86 MB),
      // which alone can take about 10 seconds before the worker runs.
      timeout: 30000,
    },
  },
});
