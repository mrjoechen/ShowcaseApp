# AI image feature migration

> Historical design: the cross-repository sharing decision below was superseded. ShowcaseApp now owns `ai-model-capabilities/`; the original showcase module has been restored to JVM. See the local module README for current build instructions.

Port the existing showcase Android image generation and image-summary experience to ShowcaseApp. Preserve Material styling, source-image flow, style presets, provider/model configuration, progress/error states, generated-image persistence and retry/cancellation semantics. Enable AI only on installed clients (Android, iOS, desktop); hide all AI entry points and avoid execution on Web/JS/Wasm.

Convert showcase/ai-model-capabilities in its original repository into a KMP library with shared provider-neutral contracts, validation, built-in adapters and request normalization. Both applications consume this one module; do not copy its implementation into ShowcaseApp. Keep existing Android callers compatible or migrate them alongside the module, and retain regression coverage.

Use a configurable Gradle project directory with a sibling showcase default for local source reuse. Keep native client networking separate from browser execution. Persist application data through existing KMP storage conventions. Preserve existing source image resolution and theme components.

Changes live in isolated worktrees on new codex branches because both original checkouts contain unrelated uncommitted changes. Commit only this work locally and never push.
