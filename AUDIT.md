# VOICE JARVIS V2 — Repository Architecture Audit (Read-Only)

- **Repository:** `webwithroni/voice-jarvis` (public), branch `main`
- **Type:** Native **Android / Kotlin** app (single `:app` module)
- **App id / namespace:** `com.webwithroni.voicejarvis`
- **Version:** `0.4.0` (versionCode 1) · minSdk 26 · targetSdk 34 · compileSdk 34
- **Toolchain:** AGP 8.5.2, Kotlin 2.3.0, JDK 17, Gradle wrapper (CI uses Gradle 8.7)
- **Firebase project:** `voice-jarvis-7c0ff`
- **Audit scope:** analysis only — no files created, modified, or deleted in the repo.
- **Source of truth reviewed:** 90 files mirrored read-only via GitHub raw URLs.

> Emergent-stack reality check: this environment (React/FastAPI/MongoDB + web preview, Linux
> container) **cannot build or run native Android/Kotlin**. Per your decision, native Android
> stays in the Kotlin repo; Emergent is used only for the **cloud/backend + web dashboard** side.

---

## A. CURRENT ARCHITECTURE

### High-level runtime
```
User voice
  → AudioEngine (PCM capture)                     [AudioEngine.kt]
  → GeminiLiveClient (WebSocket, realtime)        [GeminiLiveClient.kt]
  → JarvisService  (the "brain", foreground svc)  [JarvisService.kt]
       ├─ onToolCall → route:
       │     ├─ migrated device tools → CapabilityBusToolBridge → CapabilityBus
       │     └─ everything else       → ToolExecutor / ResearchRouter
       ├─ pendingConfirmation (MEDIUM+ risk gate)
       └─ FallbackLLM + SpeechController + TtsController (backup mode)
  → AudioEngine playback + Orb visual state
```

**Key fact:** the current agent is a **single-turn, model-driven tool-calling loop**. Gemini Live
receives the transcript + tool declarations and decides tool calls; the app executes each call and
returns a result. There is **no explicit multi-step Goal→Plan→Task runtime** — planning is implicit
inside the model per turn. (`grep` confirms 0 references to `Goal`, `Task`, `TaskStep`, `Objective`,
`Plan` steps, `currentStep`, `maxSteps`.)

### Modules that DO exist (by concern)

**Voice layer**
- `GeminiLiveClient.kt` (1255 LOC) — Gemini Live over OkHttp WebSocket: `connect`, `sendSetup`,
  `handleMessage`, `sendAudioChunk`, `sendToolResponse`, `sendInterrupt` (barge-in), reconnect/backoff.
- `AudioEngine.kt` (866 LOC) — record/playback PCM, playback queue, RMS/amplitude for Orb.
- Fallback chain: `SpeechController.kt` (Android STT), `FallbackLLM.kt` (Groq → OpenRouter → DeepSeek,
  OpenAI-compatible), `TtsController.kt` (Android TTS). Entered via `enterFallbackMode()`.

**Agent "brain" / orchestration**
- `JarvisService.kt` (2897 LOC) — foreground `microphone` service. Owns session lifecycle,
  system prompts (`buildPrimarySystemPrompt` / `buildFallbackSystemPrompt`), tool dispatch
  (`onToolCall`), confirmation state machine (`setPendingConfirmation`, `isAffirmativeConfirmation`,
  `resumePendingConfirmation`), state pushes, notification, Firebase turn recording.

**Capability Bus + safety substrate (strong, deterministic)**
- `CapabilityBus.kt` — pipeline: `plan → validate → RecoveryEngine.execute(execute+verify)`; captures
  immutable screen fingerprints (no `AccessibilityNodeInfo` escapes). Non-recursive by design.
- `ActionPlanner.kt` — normalizes action names, attaches authoritative risk, validates capability.
- `RiskEngine.kt` — deterministic `SAFE/LOW/MEDIUM/HIGH/CRITICAL`; `requiresConfirmation` (MEDIUM+),
  `requiresVerification`. Explicitly does **not** trust model-supplied risk; unknown actions fail closed to MEDIUM.
- `CapabilityManager.kt` — authoritative "can this device do X?" registry + permission/accessibility state.
- `RecoveryEngine.kt` — bounded recovery (`MAX_ATTEMPTS=2`); executes → verifies → classifies → retries.
- `FailureClassifier.kt` / `RecoveryPolicy.kt` / `RecoveryFailure.kt` — deterministic failure taxonomy;
  **only `scroll` auto-recovers** in V2; side-effect-uncertain actions are never blindly retried.
- `VerificationEngine.kt` — post-action verification (app launch, screen change, home, typed text) via
  screen fingerprints. Enforces `EXECUTED != VERIFIED`.
- `ActionExecutor.kt` (1687 LOC) — concrete handlers (battery, flashlight, call, sms, media, volume,
  alarm, timer, scroll/swipe/tap/type/read screen, global back/home/recents, launch app).
- `ActionModels.kt` — `ActionRisk`, `ActionStatus`, `ActionRequest`, `ActionResult`, `CapabilityState`.

**Android device control**
- `VoiceJarvisAccessibilityService.kt` + `ScreenController.kt` — accessibility tree read, tap by
  point/text/node, type, scroll/swipe via gesture dispatch, global actions, screen fingerprinting.

**Tools**
- `ToolDeclarations.kt` — 28 Gemini function schemas (call_contact, send_whatsapp, send_sms, open_app,
  flashlight, alarm/timer, battery, search_web, deep_research, media_control, set_volume, browser,
  google search, navigate_to, lookup_contact, clipboard, location, read_screen, tap_element, type_text,
  scroll_screen, go_back/home, send_last_message, answer/end call, open_accessibility_settings).
- `CapabilityBusToolBridge.kt` — 14 tools migrated onto the safe Capability Bus; the rest still go
  through the legacy `ToolExecutor.kt`.

**Research**
- `ResearchRouter.kt` (1201 LOC) — Tavily search + multi-source normalization + LLM synthesis
  (`search_web` shallow, `deep_research` deep), citation/domain extraction, news detection.

**Cloud (Firebase)**
- `FirebaseManager.kt` — anonymous Auth + Firestore writes under `/users/{uid}/…`
  (`conversations`, `conversations/{id}/turns`, `telemetry`, `errors`, `feedback`); redacts key-like
  strings; audio never uploaded; single telemetry on/off flag persisted in `SharedPreferences`.
- `FirebaseAnalyticsManager.kt`, `FirebaseCrashlyticsManager.kt`, `FirebasePerformanceManager.kt` —
  event/latency/crash instrumentation with name/value sanitization.
- `MainActivity.kt` — installs **App Check (Play Integrity)**.
- `firestore.rules` — users can only read/write their own UID subtree; `training_candidates` and
  `training_dataset` are **server-only (`allow … if false`)**; deny-by-default elsewhere.

**Orb (genuinely state-driven, not decorative)**
- `orb/OrbState.kt` (LISTENING/HEARING/THINKING/SPEAKING/ERROR/PAUSED/PERMISSION_REQUIRED) +
  `OrbActivity` (SEARCHING/RESEARCHING/EXECUTING_TOOL/CONTROLLING_DEVICE/WAITING_CONFIRMATION/SUCCESS).
- `OrbRenderer`, `OrbMotionController`, `OrbParticleSystem`, `OrbFaceRenderer`, `OrbAudioReactive`,
  `HumanoidOrbView`, `OrbColors`, `OrbConfig` — Canvas particle system, audio-reactive, state-mapped color.

**UI**
- Mostly **programmatic Kotlin UI** (no Jetpack Compose). `activity_main.xml` is the only large layout.
  `JarvisScreensActivity.kt` (2463 LOC) builds History, Conversation detail, Settings, Onboarding,
  and Permission screens in code. Design tokens: near-black bg, cyan/blue/violet accents, red error
  (`colors.xml`, `voice_jarvis_design.xml`) — matches the spec's visual language.

**CI/CD**
- `.github/workflows/build-debug-apk.yml` — on push to `main`: JDK 17 → Android SDK → Gradle 8.7 →
  `assembleDebug` with API keys injected via `-P…=${{ secrets.* }}` (GEMINI/TAVILY/GROQ/OPENROUTER/
  DEEPSEEK) → upload APK artifact. **No secrets committed** (keys come from Gradle properties/Secrets).

---

## B. REUSABLE COMPONENTS (preserve — do not rewrite)

1. **Entire safety substrate** — `RiskEngine`, `ActionPlanner`, `CapabilityManager`, `RecoveryEngine`,
   `FailureClassifier`, `RecoveryPolicy`, `RecoveryFailure`, `VerificationEngine`, `CapabilityBus`,
   `ActionModels`. This already implements Sections 6–9 (Capability Bus, Risk, Verification, Recovery)
   at a quality level most projects lack. **This is the crown jewel — build the Agent Runtime around it.**
2. **Voice pipeline** — `GeminiLiveClient`, `AudioEngine`, plus fallback (`SpeechController`,
   `FallbackLLM`, `TtsController`). Covers Section 20 primary + fallback + barge-in + reconnect.
3. **Android control** — `VoiceJarvisAccessibilityService`, `ScreenController`, `ActionExecutor`.
4. **Research** — `ResearchRouter` (Section 19 mostly satisfied).
5. **Orb engine** — the whole `orb/` package (Section 22 satisfied and state-driven).
6. **Firebase gateway + rules foundation** — `FirebaseManager`, analytics/crashlytics/perf managers,
   `firestore.rules` server-only training collections (Sections 12–13 partial, privacy-aware).
7. **CI secret-injection pattern** — keep and extend.
8. **Tool schema + bridge pattern** — `ToolDeclarations` + `CapabilityBusToolBridge` (good migration seam).

---

## C. MISSING COMPONENTS (vs. target spec)

| Spec § | Capability | Status |
|---|---|---|
| 4 | **Agent Runtime** (Agent/Goal/Objective/Plan/Task/TaskStep/Action/Observation/ToolCall/ToolResult/Verification/Failure/Recovery/LearningEvent) | **Absent** — no models, no task IDs/status/timeout/retry/maxSteps |
| 4 | **Task state machine** (IDLE…WAITING_FOR_CONFIRMATION, 13 states) | Absent (only `JarvisState`: 6 UI states) |
| 5 | **Autonomous multi-step loop** with max steps / timeout / cancellation | Absent (single-turn model loop only) |
| 4 | **GoalManager / Planner / TaskRuntime** (explicit planning) | Absent (implicit in Gemini) |
| 10 | **Memory layers** (Working / Episodic / Semantic / Procedural) | Absent (only telemetry + 1 prefs flag) |
| 11 | **Local persistence** (Room/SQLite offline-first: tasks, history, memories, skills, traces, pending actions) | Absent (no Room/SQLite; Firestore is effectively the only store) |
| 18 | **Versioned Skill Registry** | Absent |
| 15–17 | **Learning system + training pipeline + self-improvement** (sanitize→PII→quality→consent→candidate→eval→promote; versioned datasets; sandbox/canary) | Absent (only `firestore.rules` placeholders; no client or backend pipeline) |
| 14 | **Privacy modes** PRIVATE / PERSONALIZED / CONTRIBUTE + consent | Absent (single telemetry boolean only) |
| 21 | **Wake word** ("JARVIS") local detection | Absent (only a Settings label + `WakeLock`) |
| 24 | **Minimized floating overlay** assistant | Absent → **NATIVE_ANDROID_REQUIRED** |
| 25–26 | **Centralized permission manager + progressive/contextual onboarding** | Partial — screens exist (`showOnboarding`, `showPermission…`) but not a centralized requester; `CapabilityManager` reads state but does not request |
| 12 | **Remote Config, Cloud Functions, Cloud Storage** | Absent (deps: analytics/auth/firestore/crashlytics/perf/appcheck only) |
| 13 | **Trusted backend** for `/agentRuns`, `/agentFailures`, `/agentEvaluations`, `/trainingCandidates`, `/skillCandidates`, `/datasetVersions`, `/modelExperiments` | Absent (rules deny client, but nothing writes them) |
| 29 | **Observability**: ANR tracking, task/tool success dashboards | Partial (analytics events exist; no ANR, no dashboard) |
| 30 | **Agent benchmark framework** | Absent |
| 31/33 | **Tests + lint/typecheck CI stages** | **Absent — zero tests**, CI only builds |

---

## D. RISKS

1. **No automated tests + CI only builds.** No `src/test`/`androidTest`, no ktlint/detekt/unit/agent
   stages. As the Agent Runtime grows this is the #1 regression risk. (Spec §31/§33.)
2. **God-objects.** `JarvisService` 2897 LOC, `JarvisScreensActivity` 2463, `ActionExecutor` 1687,
   `ScreenController` 1302, `GeminiLiveClient` 1255 — hard to test, no DI. Extraction needed before
   layering the runtime on top.
3. **Truthfulness gaps in capabilities.** `CapabilityManager` exposes `CAMERA` and `NOTIFICATIONS`
   capabilities, but the manifest declares **no `CAMERA` permission and no `NotificationListenerService`**
   → these silently report unavailable, violating "never silently fail" (§25).
4. **Firebase as de-facto only store.** No local DB → history/turns/tasks depend on network + telemetry
   flag. Spec §11 explicitly forbids making Firebase the only source of truth for device operations.
5. **Release build not hardened.** `release { isMinifyEnabled=false }`, no signing config, no R8/ProGuard.
6. **Public-repo Firebase config.** `google-services.json` is committed (normal for Firebase Android;
   the Android API key is not a secret per Google) — but this **hard-relies on App Check + `firestore.rules`**
   being correct. Keep App Check enforced and rules tight; never loosen the deny-by-default block.
7. **Emergent stack mismatch.** Emergent Mobile = Expo/RN/FastAPI/Mongo; repo = native Kotlin + Firebase.
   Mitigated by your decision (native stays; Emergent = backend/dashboard). Do **not** auto-convert to RN.
8. **`allowBackup="true"`** with sensitive local data planned — revisit before shipping memory/skills.

---

## E. NATIVE_ANDROID_REQUIRED FEATURES

These cannot be implemented in Expo/RN JS-only, nor in the Emergent cloud container — they must live in
the native Kotlin app (most already do):

- Accessibility-based screen control: read / tap / type / scroll / swipe *(already native ✔)*
- Foreground **microphone** service + Gemini Live realtime audio streaming *(already native ✔)*
- **Minimized floating overlay** (`SYSTEM_ALERT_WINDOW`, `TYPE_APPLICATION_OVERLAY`) — **not built**
- **Local wake-word detection** (on-device audio, battery/privacy) — **not built**
- Telephony: `CALL_PHONE`, answer/end call *(native ✔)*; SMS composer; contacts
- Flashlight, alarms/timers, media session control, device volume, location *(native ✔)*
- **NotificationListenerService** (read/act on notifications) — **not built**
- **Play Integrity App Check** *(native ✔)*
- Procedural Orb GPU/Canvas rendering *(native ✔)*

---

## F. PROPOSED V2 ARCHITECTURE

Decision applied: **native Android stays in the Kotlin repo; Emergent = cloud/backend + web dashboard.**
Two cooperating planes with an explicit boundary.

### Plane 1 — Native Android (Kotlin repo, in place)
```
Voice Layer (existing)
  → Agent Brain (extract from JarvisService)
      → GoalManager → Planner → TaskRuntime  (NEW multi-step loop, bounded)
          → CapabilityBus (EXISTING safety substrate — unchanged)
              → Execute → Observe → Verify → Recover/Replan
      → Memory (NEW: Room-backed Working/Episodic/Semantic/Procedural)
      → Skill Registry (NEW: versioned, local + synced)
      → Learning candidate generator (NEW: sanitized, consent-gated)
  → Orb (existing, extend state mapping to TaskRuntime states)
  + Wake word (NEW, native) + Minimized overlay (NEW, native)
  + PermissionManager (NEW, centralized + contextual)
Local store = Room (offline-first, source of truth for device ops)
User cloud = Firestore /users/{uid}/… (existing gateway)
```

### Plane 2 — Emergent Cloud (this environment: FastAPI + MongoDB + React dashboard)
Trusted, **not client-writable** backend that mirrors the spec's backend-controlled collections:
```
POST /agent/runs, /agent/failures, /agent/evaluations   (ingest from device, App-Check verified)
Learning pipeline: raw → sanitize → PII/secret filter → quality → consent → candidate
                        → evaluation → dataset version → (manual) promotion
Collections: agentRuns, agentFailures, agentEvaluations, trainingCandidates,
             skillCandidates, datasetVersions, modelExperiments
Remote config / skill distribution API
Web dashboard (React): observability, benchmarks, dataset review, skill approval
```
Boundary rule (enforces §12–17): **device/operational truth = local Room + Firestore user data;
learning/training/eval/self-improvement = trusted backend, never client-writable.** This backend can
later be fronted by Firebase Cloud Functions or called directly with App Check tokens.

---

## G. PHASED IMPLEMENTATION PLAN (incremental — no giant rewrite)

- **P0 — Safety net & truth-up (native).** Add test harness (JUnit + Robolectric) and CI stages
  (ktlint/detekt, unit tests, then build). Write pure-logic unit tests for the existing substrate
  (RiskEngine, FailureClassifier, RecoveryPolicy, ActionPlanner). Fix capability truthfulness
  (declare or remove CAMERA/NOTIFICATIONS). No behavior change to the voice path.
- **P1 — Agent Runtime core (native).** Introduce `Goal/Objective/Plan/Task/TaskStep/Action/
  Observation/ToolCall/ToolResult/LearningEvent` models + `TaskState` (13 states) + a bounded
  `TaskRuntime` that **wraps the existing CapabilityBus** (max steps, timeout, retries, cancellation).
  Keep the single-turn path working; runtime is additive.
- **P2 — Local persistence (native).** Room DB: tasks, task history, working/episodic memory, settings,
  pending actions, permission state, execution traces. Make it the operational source of truth (§11).
- **P3 — Multi-step planner + loop.** Wire Planner→TaskRuntime→Verify→Recover/Replan; map Orb states to
  runtime states; extract the "brain" out of `JarvisService`.
- **P4 — Semantic/Procedural memory + versioned Skill Registry.** Retrieve relevant skills before
  complex tasks; version procedures.
- **P5 — Privacy modes + consent + learning-candidate generation (native, client-side sanitization).**
- **P6 — Emergent trusted backend + web dashboard (this env).** FastAPI+Mongo ingest for
  runs/failures/evaluations; learning pipeline; dataset versioning; benchmark reports; React dashboard.
- **P7 — Wake word (native) + minimized overlay (native).**
- **P8 — Benchmark framework (§30) + release hardening** (signing, R8/minify, App Check enforcement).

---

## H. FILES THAT SHOULD BE CREATED (future phases — not this task)

Native (Kotlin repo):
- `agent/Goal.kt`, `agent/Objective.kt`, `agent/Plan.kt`, `agent/Task.kt`, `agent/TaskStep.kt`,
  `agent/Observation.kt`, `agent/ToolCall.kt`, `agent/ToolResult.kt`, `agent/TaskState.kt`,
  `agent/LearningEvent.kt`
- `agent/GoalManager.kt`, `agent/Planner.kt`, `agent/TaskRuntime.kt` (wraps `CapabilityBus`)
- `memory/WorkingMemory.kt`, `memory/EpisodicMemory.kt`, `memory/SemanticMemory.kt`,
  `memory/ProceduralMemory.kt`, `data/AppDatabase.kt` + Room DAOs/entities
- `skills/SkillRegistry.kt`, `skills/Skill.kt`
- `learning/LearningPipeline.kt`, `learning/Sanitizer.kt`, `learning/ConsentGate.kt`
- `privacy/PrivacyMode.kt`, `permissions/PermissionManager.kt`
- `wakeword/WakeWordEngine.kt`, `overlay/OverlayService.kt` (NATIVE_ANDROID_REQUIRED)
- `benchmark/AgentBenchmark.kt`
- Tests: `app/src/test/**` (unit) and `app/src/androidTest/**` (instrumented)
- CI: `.github/workflows/ci.yml` (lint + test + build)

Emergent cloud (this environment):
- FastAPI backend (`/app/backend/…`): agent-run ingest, learning pipeline, dataset versioning, benchmarks
- React web dashboard (`/app/frontend/…`): observability, benchmarks, dataset/skill review

## I. FILES THAT SHOULD BE MODIFIED (future phases — surgical, minimal)

- `JarvisService.kt` — extract "brain"; delegate to `TaskRuntime` (keep single-turn path intact).
- `CapabilityManager.kt` — fix CAMERA/NOTIFICATIONS truthfulness; add permission-request hooks.
- `CapabilityBusToolBridge.kt` / `ToolExecutor.kt` / `ToolDeclarations.kt` — migrate remaining tools; add new tools.
- `AndroidManifest.xml` — add `SYSTEM_ALERT_WINDOW` (overlay), `NotificationListenerService`,
  camera perm (if used), correct foreground-service types.
- `app/build.gradle.kts` — add Room, test deps, Remote Config; add signing + R8 for release.
- `.github/workflows/build-debug-apk.yml` — add lint/test stages (or add `ci.yml`).
- `JarvisScreensActivity.kt` / `MainActivity.kt` — contextual onboarding, Settings sections
  (Privacy/Learning/Wake Word/Data), privacy modes, centralized permission flow.
- `firestore.rules` — extend for new user subcollections; keep backend collections server-only.

## J. FILES THAT MUST NOT BE TOUCHED (working core — do not rewrite/delete)

- **Safety substrate:** `RiskEngine.kt`, `FailureClassifier.kt`, `RecoveryPolicy.kt`,
  `RecoveryFailure.kt`, `RecoveryEngine.kt`, `VerificationEngine.kt`, `CapabilityBus.kt`,
  `ActionPlanner.kt`, `ActionModels.kt`, `RecoveryObservability.kt`, `CapabilityBusDiagnostics.kt`
- **Voice/audio:** `GeminiLiveClient.kt`, `AudioEngine.kt`, `SpeechController.kt`, `TtsController.kt`,
  `FallbackLLM.kt`
- **Android control:** `VoiceJarvisAccessibilityService.kt`, `ScreenController.kt`, `ActionExecutor.kt`
- **Orb engine:** entire `orb/` package
- **Research:** `ResearchRouter.kt`
- **Config/infra:** `google-services.json`, `gradle/wrapper/*`, `gradlew`/`gradlew.bat`,
  `settings.gradle.kts`, `firebase.json`, `.firebaserc`, `firestore.indexes.json`
- `firestore.rules` — **extend only**, never loosen the deny-by-default / server-only blocks.

> Modifications to any file above (when unavoidable) must be additive, behind tests, and reviewed —
> never a rewrite.

---

## Verdict
The repo is **not a mockup** — it is a serious native Android agent with an unusually strong,
deterministic **safety substrate** (Capability Bus, Risk, Verification, Recovery) and a genuine
state-driven Orb. What's missing is the **layer above** it: an explicit multi-step Agent Runtime,
memory, skills, learning pipeline, wake word, overlay, and tests. The correct path is to **build the
Agent Runtime around the existing CapabilityBus** and stand up the **learning/eval backend + dashboard
in Emergent**, incrementally and test-first — not to rewrite.

**Awaiting your go-ahead for the next phase (recommended start: P0 — CI + tests + capability truth-up).**
