# JARVIS Engineering Status

**Audit date:** 2026-08-19  
**Repository:** `webwithroni/voice-jarvis`  
**Branch / commit:** `main` / `d214c40`  
**Version:** `0.4.0`  
**Roadmap:** `PLAN.md` on the repository `main` branch (869 lines; no local copy is currently checked out)

## Executive Summary

The repository contains a functioning Android voice-assistant prototype with a cinematic home UI, a foreground audio service, Gemini Live WebSocket transport, fallback speech components, Firebase authentication and telemetry, capability checks, Android actions, recovery helpers, and an instrumentation smoke test.

It is **not yet a production-grade JARVIS runtime**. The central gaps are durable canonical runtime state, explicit provider/privacy policy, durable tasks and memory, complete lifecycle recovery, test coverage, and release/security hardening.

The JDK 17 and Android SDK baseline is now working. JVM tests and the debug APK build pass. Instrumentation remains blocked by the Codespaces host killing the headless emulator during boot under memory pressure; no physical device is attached.

## Current Architecture

- **Presentation:** `MainActivity` owns the home surface and binds to `JarvisService`; `JarvisScreensActivity` builds settings, history, tools, and diagnostics screens programmatically. XML layouts and custom orb/glass components provide the visual system.
- **Voice runtime:** `JarvisService` owns the active voice turn, audio focus, wake lock, Gemini Live client, fallback STT/TTS, tool execution, confirmation state, and UI callbacks. `AudioEngine` captures 16 kHz PCM and plays 24 kHz PCM. `GeminiLiveClient` manages a reconnecting OkHttp WebSocket.
- **State:** `JarvisState` remains the legacy UI callback enum. `JarvisRuntimeState` and `JarvisRuntimeReducer` provide a platform-independent canonical snapshot/reducer for lifecycle, interaction, connection, transcripts, pending confirmation, interruption, cancellation, and recovery. `JarvisService` publishes all runtime mutations through a synchronized reducer boundary while preserving the existing UI callback contract.
- **Capabilities/actions:** `CapabilityManager` reports permission/device capability state. `CapabilityBus` and `CapabilityBusToolBridge` normalize and gate actions; `RiskEngine`, `ActionPlanner`, `ActionExecutor`, and `ToolExecutor` perform or launch Android actions.
- **AI/providers:** Gemini Live is the primary realtime provider. `FallbackLLM` and `ResearchRouter` call cloud providers directly using `BuildConfig` keys. There is no verified local model implementation or explicit local-only policy boundary.
- **Persistence:** Firebase Firestore stores conversation summaries and turns; SharedPreferences stores selected preferences and Firebase/session flags. There is no durable task repository, memory model, runtime snapshot, idempotency store, or local encrypted data layer.
- **Observability/recovery:** Firebase Analytics, Crashlytics, Performance, App Check, `CrashLogger`, and recovery classes exist. Their coverage and redaction behavior still require production validation.
- **Build/CI:** AGP 8.6.1, Kotlin 2.3.0, Gradle wrapper 8.7, JDK target 17, compile SDK 35, target SDK 34. CI builds a debug APK, inspects its signing certificate, and runs one Android instrumentation smoke-test job. Local JDK 17, SDK platform 35, Build Tools 34/35, platform-tools, emulator, and API 35 Google APIs image are installed outside the repository.

## Classification

### Implemented

- Android project builds are configured for namespace/application ID, SDK 35/34/26, Java/Kotlin 17, View Binding, and Firebase services.
- Authentication activities and Firebase Auth integration exist.
- Home/orb visual shell, voice state labels, settings/history/tools navigation, and onboarding layouts exist.
- Foreground microphone service declaration and notification channel exist.
- Audio capture/playback, optional echo cancellation/noise suppression, audio focus, wake lock, Gemini Live transport, streamed transcripts, streamed audio, and server interruption callbacks exist in code.
- Fallback Android `SpeechRecognizer` and `TextToSpeech` components exist.
- Capability registry, permission checks, accessibility integration, risk classification, action confirmation, and several Android actions exist.
- Firestore rules scope user documents by authenticated UID and deny unknown paths.
- Crash logging/recovery helpers and Firebase telemetry managers exist.
- CI configuration and an Android UI inflation smoke test exist.

### Partially Implemented

- **Canonical runtime:** `JarvisService` is the de facto voice authority and now exposes a synchronized reducer-backed runtime snapshot for lifecycle, interaction/connection state, transcripts, interruption, cancellation, recovery, and confirmation. Task state and persistence are not yet represented by that contract.
- **Voice lifecycle:** realtime listening/speaking/interruption behavior is implemented, but the planned deterministic state machine includes additional lifecycle/error semantics and requires lifecycle, permission, Bluetooth, background, and process-death tests.
- **AI integration:** Gemini Live and multiple cloud fallbacks exist, but provider selection is not centralized and cloud escalation/local-only behavior is not enforced by policy.
- **Tools and trusted execution:** capability/risk helpers exist, but `ToolExecutor` also directly performs side effects and returns optimistic success for actions that launch external UIs. Verification, audit metadata, timeouts, retry/idempotency, and one execution authority are incomplete.
- **Conversations/history:** Firestore history and transcript detail screens exist, but the active service only caches the last conversation in memory and history previews are generic rather than sourced from stored content.
- **Permissions/onboarding:** microphone and several capability settings are represented, but there is no complete product permission matrix or systematic denial/revocation/background-flow coverage.
- **Recovery/observability:** recovery classes and Firebase instrumentation exist, but durable task reconciliation, trace/session/task correlation, sensitive-data redaction, and crash-side-effect protection are not proven.
- **UI/accessibility:** the visual direction is cohesive and custom, but every important loading/error/offline/permission/task state, dynamic text size, reduced motion, screen reader semantics, and keyboard/navigation behavior are not verified.

### Broken / Blocked

- **Instrumentation in this container:** the emulator binaries and API 35 image are installed, and missing host libraries `libpulse0` and `libdrm2` were resolved. The emulator reached QEMU initialization but was killed with exit 137 under the container memory limit before the instrumentation task could run. This is an external host blocker, not an app/test failure.
- **Android SDK verification:** `ANDROID_HOME` is unset and neither `adb` nor `sdkmanager` is on `PATH` in the current container. Instrumentation tests cannot be treated as runnable locally until the SDK/emulator toolchain is installed and verified.
- **Production secret posture:** provider credentials are injected into `BuildConfig` and consumed from the client APK. This is unsuitable for privileged production credentials and must be replaced with backend-controlled access or explicitly limited public/client credentials.
- **Permission declaration posture:** the manifest declares sensitive capabilities including contacts, phone, location, microphone, notifications, and foreground microphone service. Actual product need, runtime prompts, minimality, and privacy disclosures are not yet demonstrated.

### Missing / Not Started

- Durable `JarvisRuntime` contract and state store.
- Durable task model, scheduler, cancellation tokens, bounded concurrency, pause/resume, retry policy, and process-death reconciliation.
- Separate conversation history, working memory, long-term memory, and user-controlled memory with real deletion.
- Verified local provider and strict `LOCAL_ONLY` mode with explicit cloud escalation.
- Central provider policy, canonical error taxonomy, network policy, timeouts, cancellation, and redacted structured diagnostics.
- Complete trusted execution pipeline with authorization, verification, idempotency, and audit records for every external side effect.
- Project context capsule and live-action/task progress model.
- Complete offline behavior, notification/live-action UI, background restrictions, Bluetooth/headset handling, and real-device acceptance coverage.
- Unit, integration, failure, lifecycle, permission, security, accessibility, performance, and end-to-end tests.
- Release build configuration, dependency/security analysis, reproducible SDK package pinning, provenance, rollback runbook, and production release gates.

## Phase Status

| Roadmap phase | Status | Evidence / reason |
|---|---|---|
| FOUNDATION | Gate passed | JDK 17/SDK verified; JVM tests and debug APK build pass. |
| LOCAL VOICE RUNTIME | Partial | Audio, Gemini Live, fallback STT/TTS exist; no verified local model or strict local-only policy. |
| RUNTIME STATE | Partial | Synchronized canonical in-process runtime state exists; durable persistence is still planned. |
| MEMORY | Partial | Firestore conversation history exists; planned memory model and deletion semantics are absent. |
| TRUSTED EXECUTION | Partial | Risk/capability/action layers exist; execution and verification are split and incomplete. |
| PROJECT CONTEXT | Not started | No project capsule model or repository/project context persistence found. |
| LIVE ACTION | Not started | No durable live-action/task progress contract found. |
| CONTINUOUS VOICE | Partial | Service/audio code supports realtime input and interruption; acceptance coverage is missing. |
| CONCURRENT TASKS | Not started | No durable isolated task engine found. |
| DEVICE CONTROL | Partial | Several Android actions and accessibility hooks exist; capability completeness and verification are missing. |
| RELIABILITY | Partial | Recovery/telemetry code exists and reducer failure/reconnect tests pass; emulator failure testing is host-blocked. |
| PRIVACY | Partial | Firestore rules and auth exist; local/cloud policy, retention, redaction, and privacy state are incomplete. |
| REAL DEVICE ACCEPTANCE | Blocked | No physical device is attached through ADB in Codespaces. |
| PRODUCTION | Not started | Release, security, performance, accessibility, and rollback gates are incomplete. |

## Build and Test Baseline

| Check | Result |
|---|---|
| Git status | Source/docs changes pending commit; generated `app/build` is excluded from the milestone commit. |
| `./gradlew testDebugUnitTest` | Passed under JDK 17: 30 actionable tasks, build successful. |
| `./gradlew assembleDebug` | Passed under JDK 17 with one worker: APK built, ZIP integrity passed, APK v2 signature verified. |
| `./gradlew connectedDebugAndroidTest` | Blocked by emulator host exit 137 during boot; no instrumentation assertion result was produced. |
| Unit tests | 9 focused reducer tests cover lifecycle, reconnect, interruption, cancellation, transcripts, confirmation, duplicates, failure, reset, and service stop. |
| Android tests | Existing `UiInflationSmokeTest` class with three layout inflation checks; not executed locally because emulator was killed by host memory pressure. |
| CI | Debug APK and UI inflation jobs are configured, but workflow execution was not available as local evidence during this audit. |

## Highest-Priority Blockers

1. Move privileged provider access out of the client APK and define explicit provider/privacy policy.
2. Add durable task and memory persistence with process-death reconciliation.
3. Unify direct `ToolExecutor` side effects behind the existing capability/authorization pipeline.
4. Run instrumentation on CI or a physical Android device; local emulator execution is host-memory blocked.

## Exact Next Implementation Step

The foundation/runtime reliability slice is now present: `JarvisRuntimeState`/`JarvisRuntimeReducer`, synchronized service integration at lifecycle/state/transcript/confirmation boundaries, explicit cancellation/interruption/reconnect/expiry events, and JVM tests. The next implementation phase is durable task and memory persistence, followed by trusted execution consolidation. Do not reorganize packages or rewrite the existing audio/UI code during that slice.

The next phase gate is: unit tests pass, the debug build passes, and the existing Android smoke test still passes in CI/emulator. Only then should task persistence and memory implementation begin.
