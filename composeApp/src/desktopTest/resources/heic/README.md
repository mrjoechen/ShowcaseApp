# HEIC test fixtures

These synthetic images were created for this project using Pillow 12.3.0 and
pillow-heif 1.7.0 (libheif). They contain no third-party photographs and use the
project's license. Python and an HEVC encoder are not needed to run the tests.

- `two-colors.heic`: 64×48, red left half, blue right half, quality 90. The coded
  image is 64×64; `clap` defines the visible area. This file is smaller than the
  Openize reader's 4096-byte buffer.
- `rotated.heic`: the same pattern with EXIF orientation 6 supplied in
  `HeifFile.info['exif']` **before** `HeifFile.save`. The encoder creates `irot`,
  and libheif displays a 48×64 image (red top, blue bottom).
- `ten-bit.heic`: 64×48 red SDR image, created from RGB;16 input. The HEIF uses
  `heix` and 10-bit HEVC, independently verified with pillow-heif.
- `orientation-1.heic` through `orientation-8.heic`: 128×64 with red/green/blue/yellow
  quadrants (TL/TR/BL/BR). Each was encoded with the corresponding EXIF orientation
  supplied before save, producing HEIF rotation/mirror properties. Reference corner
  colors in the test were verified by reopening each file with libheif.

Example encoder setup for the orientation fixtures:

```python
from PIL import Image
import pillow_heif

image = Image.new('RGB', (128, 64), 'red')
image.paste((0, 255, 0), (64, 0, 128, 32))
image.paste('blue', (0, 32, 64, 64))
image.paste('yellow', (64, 32, 128, 64))
for orientation in range(1, 9):
    exif = Image.Exif()
    exif[274] = orientation
    heif = pillow_heif.from_pillow(image)
    heif.info['exif'] = exif.tobytes()
    heif.save(f'orientation-{orientation}.heic', quality=90)
```
