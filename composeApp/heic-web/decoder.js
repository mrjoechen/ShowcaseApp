import libheif from 'libheif-js/wasm-bundle.js';

export async function decodeHeic(bytes) {
  if (bytes.length > 64 * 1024 * 1024) throw new Error('HEIC exceeds 64 MiB');
  const decoder = new libheif.HeifDecoder();
  const images = decoder.decode(bytes);
  try {
    const image = images.find(image => image.is_primary());
    if (!image) throw new Error('HEIC has no primary image');
    const width = image.get_width();
    const height = image.get_height();
    if (width <= 0 || height <= 0 || width * height > 64 * 1024 * 1024) {
      throw new Error('HEIC exceeds 64 megapixels');
    }
    const output = { width, height, data: new Uint8ClampedArray(width * height * 4) };
    await new Promise((resolve, reject) => image.display(output, result => {
      if (result) resolve();
      else reject(new Error('Unable to decode HEIC pixels'));
    }));
    return output;
  } finally {
    for (const image of images) image.free();
  }
}
