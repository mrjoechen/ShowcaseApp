import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { decodeHeic } from './decoder.js';

const fixture = name => readFile(new URL(`../src/desktopTest/resources/heic/${name}.heic`, import.meta.url));
const color = (image, x, y) => Array.from(image.data.slice((y * image.width + x) * 4, (y * image.width + x) * 4 + 3))
  .reduce((value, channel) => (value << 8) | (channel > 128 ? 255 : 0), 0);

test('decodes primary image, clean aperture, rotation and 10-bit SDR', async () => {
  const image = await decodeHeic(await fixture('two-colors'));
  assert.deepEqual([image.width, image.height], [64, 48]);
  assert.equal(color(image, 8, 24), 0xff0000);
  assert.equal(color(image, 56, 24), 0x0000ff);
  const rotated = await decodeHeic(await fixture('rotated'));
  assert.deepEqual([rotated.width, rotated.height], [48, 64]);
  assert.equal(color(rotated, 24, 8), 0xff0000);
  assert.equal(color(rotated, 24, 56), 0x0000ff);
  const tenBit = await decodeHeic(await fixture('ten-bit'));
  assert.deepEqual([tenBit.width, tenBit.height], [64, 48]);
  assert.equal(color(tenBit, 32, 24), 0xff0000);
});

test('preserves all eight container orientations', async () => {
  const r = 0xff0000, g = 0x00ff00, b = 0x0000ff, y = 0xffff00;
  const expected = [[r,g,b,y], [g,r,y,b], [y,b,g,r], [b,y,r,g],
    [r,b,g,y], [b,r,y,g], [y,g,b,r], [g,y,r,b]];
  for (let orientation = 1; orientation <= 8; orientation++) {
    const image = await decodeHeic(await fixture(`orientation-${orientation}`));
    const corners = [[8,8], [image.width-9,8], [8,image.height-9], [image.width-9,image.height-9]];
    assert.deepEqual(corners.map(([x,y]) => color(image,x,y)), expected[orientation-1], `orientation ${orientation}`);
  }
});

test('rejects corrupt and oversized input and can decode again afterwards', async () => {
  await assert.rejects(decodeHeic(new Uint8Array([0,1,2,3])));
  const valid = await fixture('two-colors');
  await assert.rejects(decodeHeic(valid.subarray(0, valid.length / 2)));
  await assert.rejects(decodeHeic(new Uint8Array(64 * 1024 * 1024 + 1)), /64 MiB/);
  assert.equal((await decodeHeic(valid)).width, 64);
});
