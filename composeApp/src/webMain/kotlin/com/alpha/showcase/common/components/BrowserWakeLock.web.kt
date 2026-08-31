@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.alpha.showcase.common.components

import kotlin.js.JsAny
import kotlin.js.js

internal external interface BrowserWakeLockController : JsAny {
    fun setEnabled(enabled: Boolean)
}

// Keep the Promise/event lifecycle in one bridge shared by Kotlin/JS and Kotlin/Wasm.
// A controller belongs to one playback page; disabling it also detaches all listeners.
internal fun createBrowserWakeLockController(): BrowserWakeLockController = js(
    """(() => {
        let enabled = false;
        let pageActive = true;
        let generation = 0;
        let lock = null;
        let pending = null;

        const eligible = () => enabled && pageActive &&
            document.visibilityState === 'visible' && document.fullscreenElement != null;

        const release = sentinel => {
            try {
                Promise.resolve(sentinel.release()).catch(() => {});
            } catch (_) {}
        };

        const releaseLock = () => {
            const current = lock;
            lock = null;
            if (current === null) return;
            current.sentinel.removeEventListener('release', current.onRelease);
            release(current.sentinel);
        };

        const reconcile = () => {
            if (!eligible()) {
                // Invalidate requests that may resolve after hiding/exiting/disposal.
                generation += 1;
                releaseLock();
                return;
            }
            if (lock !== null || pending !== null) return;

            const request = { generation };
            let promise;
            try {
                if (!navigator.wakeLock || typeof navigator.wakeLock.request !== 'function') return;
                pending = request;
                promise = navigator.wakeLock.request('screen');
            } catch (_) {
                pending = null;
                return;
            }

            Promise.resolve(promise).then(sentinel => {
                if (request.generation !== generation || !eligible()) {
                    release(sentinel);
                    return;
                }
                if (sentinel.released) return;
                const onRelease = () => {
                    if (lock !== null && lock.sentinel === sentinel) lock = null;
                    // Respect browser/OS revocation: retry on visibility/fullscreen
                    // changes, not in an immediate release/request loop.
                };
                lock = { sentinel, onRelease };
                sentinel.addEventListener('release', onRelease, { once: true });
            }).catch(() => {
                // Unsupported contexts, permissions and power-saving policy must
                // never interrupt playback or produce an unhandled rejection.
            }).then(() => {
                if (pending === request) pending = null;
                // An older request may finish after a new playback/visibility cycle.
                if (request.generation !== generation && eligible()) reconcile();
            });
        };

        const onPageHide = () => {
            pageActive = false;
            reconcile();
        };
        const onPageShow = () => {
            pageActive = true;
            reconcile();
        };

        return {
            setEnabled(value) {
                if (enabled === value) {
                    if (value) reconcile();
                    return;
                }
                enabled = value;
                if (enabled) {
                    pageActive = true;
                    document.addEventListener('visibilitychange', reconcile);
                    document.addEventListener('fullscreenchange', reconcile);
                    window.addEventListener('pagehide', onPageHide);
                    window.addEventListener('pageshow', onPageShow);
                } else {
                    document.removeEventListener('visibilitychange', reconcile);
                    document.removeEventListener('fullscreenchange', reconcile);
                    window.removeEventListener('pagehide', onPageHide);
                    window.removeEventListener('pageshow', onPageShow);
                }
                reconcile();
            }
        };
    })()"""
)
