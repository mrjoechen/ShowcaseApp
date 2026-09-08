#ifndef SHOWCASE_FACE_DETECTION_BRIDGE_H
#define SHOWCASE_FACE_DETECTION_BRIDGE_H

#include <stddef.h>
#include <stdint.h>

#define SHOWCASE_FACE_NO_FACE 0
#define SHOWCASE_FACE_DETECTED 1
#define SHOWCASE_FACE_INDETERMINATE 2

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Synchronously inspects an encoded image using the bundled YuNet model.
 * encoded must remain readable for length bytes until this call returns; it is
 * never retained. NULL, empty/invalid data, and native failures are indeterminate.
 * Calls share a lazily initialized detector and are serialized internally.
 * NO_FACE is returned only after successful inference with no valid visible face.
 * No caller cleanup is needed. No C++ exception crosses this boundary.
 */
__attribute__((visibility("default")))
int showcase_face_inspect(const unsigned char *encoded, size_t length);

#ifdef __cplusplus
}
#endif

#endif /* SHOWCASE_FACE_DETECTION_BRIDGE_H */
