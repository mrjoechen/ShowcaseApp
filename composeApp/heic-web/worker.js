import { decodeHeic } from './decoder.js';
import { decodeBase64, encodePixels } from './pixels.js';

self.onmessage = async ({ data: encoded }) => {
  try {
    self.postMessage(encodePixels(await decodeHeic(decodeBase64(encoded))));
  } catch (error) {
    self.postMessage({ error: String(error) });
  }
};
