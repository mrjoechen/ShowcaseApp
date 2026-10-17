# AVIF test fixtures

These synthetic images were created for this project. They contain no
third-party photographs and use the project's license. No encoder is needed to
run the tests.

Unless noted, each file was written by Pillow 11.3.0 with its bundled libavif
1.3.0 at quality 90. Reference colors in the tests were verified by reopening
each file with libavif (`ImageOps.exif_transpose` after Pillow maps irot/imir
back to an orientation).

- `two-colors.avif`: 64×48, red left half, blue right half.
- `rotated.avif`: the same pattern saved with EXIF orientation 6. libavif turns
  this into `irot` and displays a 48×64 image (red top, blue bottom).
- `orientation-1.avif` through `orientation-8.avif`: 128×64 with
  red/green/blue/yellow quadrants (TL/TR/BL/BR), saved with the corresponding
  EXIF orientation, which produces the matching `irot`/`imir` properties.
- `alpha.avif`: 64×48 RGBA, opaque red left half, fully transparent right half
  (alpha auxiliary image).
- `animated.avif`: an `avis` sequence of a red frame followed by a blue frame
  (`save_all=True`, 200 ms each). Decoders show the first frame.
- `imageio.avif`: the `two-colors` pattern written by macOS 27 ImageIO
  (`sips -s format avif`), the encoder behind the originally reported failure.
- `ten-bit.avif`: 64×48 red, 10-bit 4:2:0, written by FFmpeg 8.0.1 with
  SVT-AV1 (`-pix_fmt yuv420p10le -c:v libsvtav1 -frames:v 1`).

Example encoder setup for the Pillow fixtures:

```python
from PIL import Image

image = Image.new('RGB', (128, 64), 'red')
image.paste((0, 255, 0), (64, 0, 128, 32))
image.paste('blue', (0, 32, 64, 64))
image.paste('yellow', (64, 32, 128, 64))
for orientation in range(1, 9):
    exif = Image.Exif()
    exif[274] = orientation
    image.save(f'orientation-{orientation}.avif', quality=90, exif=exif.tobytes())
```
