import { decodeHeic } from './decoder.js';

self.onmessage = async ({ data: encoded }) => {
  try {
    const bytes = Uint8Array.from(atob(encoded), char => char.charCodeAt(0));
    const result = await decodeHeic(bytes);
    // Strings cross both Kotlin/JS and Kotlin/Wasm without platform-specific array wrappers.
    const chunks = [];
    for (let offset = 0; offset < result.data.length; offset += 32768) {
      chunks.push(String.fromCharCode(...result.data.subarray(offset, offset + 32768)));
    }
    self.postMessage({ width: result.width, height: result.height, pixels: btoa(chunks.join('')) });
  } catch (error) {
    self.postMessage({ error: String(error) });
  }
};
