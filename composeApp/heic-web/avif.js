// Browsers decode AVIF natively, including grids, alpha and the irot/imir/clap
// display transforms. Skia on the web has no AV1 codec, so read back RGBA pixels.
export async function decodeAvif(bytes) {
  if (bytes.length > 64 * 1024 * 1024) throw new Error('AVIF exceeds 64 MiB');
  const bitmap = await createImageBitmap(new Blob([bytes], { type: 'image/avif' }));
  try {
    const { width, height } = bitmap;
    if (width <= 0 || height <= 0 || width * height > 64 * 1024 * 1024) {
      throw new Error('AVIF exceeds 64 megapixels');
    }
    const context = new OffscreenCanvas(width, height).getContext('2d', { willReadFrequently: true });
    if (!context) throw new Error('Unable to decode AVIF pixels');
    context.drawImage(bitmap, 0, 0);
    return context.getImageData(0, 0, width, height);
  } finally {
    bitmap.close();
  }
}
