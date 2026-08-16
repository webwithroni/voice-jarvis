# VOICE JARVIS V2 — Working PRD / Memory

## Problem statement (summary)
Harden and evolve `webwithroni/voice-jarvis` (native Android/Kotlin personal
autonomous agent) toward a production-grade autonomous agent: voice, multi-step
planning, tool calling, Android control, memory, skills, recovery, verification,
learning, safety. Work incrementally; never fake functionality; never rewrite the
working safety substrate.

## Environment constraint
Emergent container = Linux (React/FastAPI/Mongo + web). CANNOT build/run native
Android. User decision: native Android stays in the Kotlin repo; Emergent is used
only for cloud/backend + web dashboard side. Deliverables for native phases are
reviewable code + CI; validation of native pieces happens on GitHub Actions.

## Architecture (current, v0.4.0) — see /app/AUDIT.md
Native Android/Kotlin. Single-turn Gemini Live voice tool-calling loop in
`JarvisService`. Strong deterministic safety substrate: CapabilityBus →
ActionPlanner → RiskEngine → CapabilityManager → RecoveryEngine → ActionExecutor
→ VerificationEngine. Accessibility screen control. ResearchRouter (Tavily+LLM).
Firebase (Auth anon, Firestore, Analytics, Crashlytics, Perf, App Check). Genuine
state-driven procedural Orb. CI builds debug APK only. No tests, no Agent Runtime,
no memory/skills/learning/wake-word/overlay.

## Phase log
- **Audit (done):** full read-only architecture map delivered (sections A–J) in
  `/app/AUDIT.md` and chat. Accepted by user.
- **P0 (done, this session):** Test + CI + Capability truth hardening.
  Deliverable at `/app/p0` (repo-relative delta). See `/app/p0/P0_REPORT.md`.
  - Added deterministic, Android-free capability truth-up layer
    (CapabilityContract/Catalog/Environment/AvailabilityResolver + PermissionState
    + CapabilityRuntimeState) with 5 states
    (AVAILABLE/UNAVAILABLE/PERMISSION_REQUIRED/SERVICE_REQUIRED/NOT_SUPPORTED).
  - Truth-up: camera & notifications → NOT_SUPPORTED (option B, no fake support);
    fixed latent bug where flashlight/volume/alarm_timer/device_control were
    falsely unavailable through the bus.
  - `CapabilityManager` refactored (public API preserved); `CapabilityBus` gets one
    additive `availability()` method; `build.gradle.kts` adds JVM test deps.
  - 66 unit tests / 11 files. 42 pure-JVM tests EXECUTED locally (kotlinc 2.1 + JDK
    17 + JUnit4) → PASS. 24 Robolectric/MockK tests + APK build = NATIVE, run on CI
    (pending first GitHub Actions run — not runnable in Emergent).
  - New `ci.yml`: config → compile → unit tests (fatal) → lint (report) → APK.
  - No god-object refactor (deferred). No manifest/release/secret changes.

## Backlog (post-P0, not started)
- P1: Agent Runtime models + bounded TaskRuntime wrapping CapabilityBus (additive).
- P2: Room local persistence (offline-first source of truth).
- P3: Multi-step planner + extract "brain" from JarvisService.
- P4: Semantic/Procedural memory + versioned Skill Registry.
- P5: Privacy modes + consent + client-side learning candidates.
- P6: Emergent trusted backend (FastAPI+Mongo) + web dashboard (agentRuns/failures/
  evaluations, learning pipeline, dataset versioning, benchmarks).
- P7: Wake word + minimized overlay (NATIVE_ANDROID_REQUIRED).
- P8: Benchmark framework + release hardening (signing, R8, App Check enforce).

## Notes
- Do NOT auto-convert Kotlin → React Native.
- Do NOT touch: RiskEngine, ActionPlanner, RecoveryEngine, VerificationEngine,
  ActionExecutor, orb/*, GeminiLiveClient, AudioEngine, ResearchRouter,
  firestore.rules (extend only), google-services.json, gradle wrapper.
