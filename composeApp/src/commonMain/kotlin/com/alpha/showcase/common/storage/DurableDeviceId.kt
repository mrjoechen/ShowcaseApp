package com.alpha.showcase.common.storage

/**
 * Local random device UUID persistence (not an authentication credential).
 * Android: app preferences; iOS: Keychain; desktop: the existing .showcase/device_id file;
 * Web: localStorage. Existing storage keys are retained for upgrade continuity.
 */
expect fun getDurableDeviceId(): String?
expect fun saveDurableDeviceId(deviceId: String)
