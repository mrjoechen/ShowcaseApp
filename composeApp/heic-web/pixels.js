export function decodeBase64(encoded) {
  return Uint8Array.from(atob(encoded), char => char.charCodeAt(0));
}

// Strings cross both Kotlin/JS and Kotlin/Wasm without platform-specific array wrappers.
export function encodePixels(result) {
  const chunks = [];
  for (let offset = 0; offset < result.data.length; offset += 32768) {
    chunks.push(String.fromCharCode(...result.data.subarray(offset, offset + 32768)));
  }
  return { width: result.width, height: result.height, pixels: btoa(chunks.join('')) };
}
