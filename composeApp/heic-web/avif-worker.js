import { decodeAvif } from './avif.js';
import { decodeBase64, encodePixels } from './pixels.js';

self.onmessage = async ({ data: { id, encoded } }) => {
  try {
    self.postMessage({ id, ...encodePixels(await decodeAvif(decodeBase64(encoded))) });
  } catch (error) {
    self.postMessage({ id, error: String(error) });
  }
};
