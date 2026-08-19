# JARVIS — Production Engineering Master Plan

> **Repository:** `webwithroni/voice-jarvis`  
> **Local project:** `~/voice-jarvis`  
> **Platform:** Android  
> **Language:** Kotlin  
> **Current branch:** `main`  
> **Current state:** Active development / production hardening

## 1. Executive Objective

JARVIS is not being built as a simple voice chatbot. The objective is to evolve the existing Android application into a production-grade personal AI runtime supporting voice interaction, conversational AI, local/cloud AI providers, memory, tools, Android capabilities, background and concurrent tasks, live task progress, notifications, screen understanding, device control, project context, diagnostics, crash recovery, and privacy controls.

The system must behave as **one JARVIS**, regardless of which internal capability handles a request.

## 2. Current Repository Baseline

The current repository is an Android/Kotlin application.

```text
namespace:      com.webwithroni.voicejarvis
applicationId:  com.webwithroni.voicejarvis
compileSdk:     35
targetSdk:      34
minSdk:         26
versionCode:    1
versionName:    0.4.0
Java:           17
Kotlin JVM:     17
```

Current infrastructure includes Android UI, Kotlin runtime, View Binding, BuildConfig, Google authentication, Firebase Auth, Firestore, Crashlytics, Performance, Analytics, App Check, Android instrumentation testing, OkHttp, Kotlin Coroutines, and Android Lifecycle.

Current configured provider/service properties include `GEMINI_API_KEY`, `TAVILY_API_KEY`, `GROQ_API_KEY`, `OPENROUTER_API_KEY`, and `DEEPSEEK_API_KEY`.

The exact implementation of each subsystem must be verified in the repository before changing architecture.

## 3. Current Development Reality

Recent repository work has focused on Android UI test command normalization, emulator configuration, UI inflation hardening, emulator QA, and activity lifecycle behavior.

Use:

```text
Understand current implementation
        ↓
Establish production baseline
        ↓
Finish current phase
        ↓
Validate
        ↓
Harden
        ↓
Advance
```

Do not combine unrelated infrastructure and feature work into giant changesets.

## 4. Core Architecture — One Brain, One Runtime

Target architecture:

```text
                    JARVIS UI
              ┌───────────────────┐
              │ Voice             │
              │ Chat              │
              │ Visual UI         │
              └─────────┬─────────┘
                        │
                        ▼
              ┌───────────────────┐
              │   JARVIS RUNTIME  │
              │ Canonical Authority│
              └─────────┬─────────┘
                        │
       ┌────────────────┼─────────────────┐
       │                │                 │
       ▼                ▼                 ▼
   Reasoning         Memory            Tasks
       │                │                 │
       └────────────────┼─────────────────┘
                        │
                        ▼
                 Capability Layer
                        │
                        ▼
                  Tool Execution
                        │
                        ▼
                Trusted Execution
                        │
                        ▼
                 External Effects
```

Voice, chat, and future interfaces are presentation/control surfaces. They must not become competing AI runtimes.

## 5. Canonical Runtime Ownership

The JARVIS Runtime owns identity, session state, active conversation, interaction state, tasks, lifecycle state, execution state, and capability truth.

Capability states:

```text
AVAILABLE
UNAVAILABLE
REQUIRES_PERMISSION
TEMPORARILY_UNAVAILABLE
NOT_SUPPORTED
FAILED
```

The AI model must never invent capability availability.

## 6. Model Provider Architecture

Models are reasoning engines, not system authorities.

```text
ModelProvider
├── LocalProvider
├── GeminiProvider
├── GroqProvider
├── OpenRouterProvider
├── DeepSeekProvider
└── FutureProvider
```

Provider selection belongs to runtime/policy. The model must not independently decide whether cloud use, a tool, an Android capability, or an external side effect is allowed.

## 7. API Key Security

The current application injects provider credentials through Gradle/BuildConfig. Treat this as a production security concern.

Requirements:

- Never commit real secrets.
- Never log secrets.
- Never expose provider keys through UI.
- Never ship privileged backend credentials unnecessarily.
- Treat an APK as extractable.
- Separate public configuration from privileged credentials.
- Move privileged operations behind backend controls when appropriate.

## 8. Local AI Mode

If local-only mode exists, it must actually be local.

When `LOCAL_ONLY` is active, live user content, live audio, sensitive context, and local memory remain local. No hidden cloud fallback is allowed.

If the local model fails:

```text
Local model unavailable
        ↓
Explain limitation
        ↓
Offer explicit cloud escalation
```

Never silently switch to cloud.

## 9. Voice Runtime

Target pipeline:

```text
Microphone → Audio Session → VAD → STT → JARVIS Runtime → Reasoning → Streaming Response → TTS → Speaker
```

Canonical state machine:

```text
IDLE
LISTENING
THINKING
SPEAKING
INTERRUPTED
ERROR
```

Barge-in:

```text
SPEAKING → USER SPEAKS → INTERRUPTED → LISTENING
```

Do not allow unrelated components to mutate conflicting boolean states.

## 10. Voice Controls

Keep these operations distinct:

- Stop speaking
- Stop listening
- Cancel task

`Stop speaking` stops TTS but may leave the task running. `Stop listening` stops microphone capture. `Cancel task` propagates cancellation and changes the task to `CANCELLED`.

## 11. Continuous Voice

Target flow:

```text
IDLE → LISTENING → THINKING → SPEAKING → LISTENING
```

Requirements include VAD, audio focus, barge-in, interruption, microphone lifecycle, Bluetooth/headset support, permission handling, background behavior, cancellation, TTS interruption, and lifecycle recovery.

Continuous voice must not create a parallel runtime.

## 12. Task Engine

Target task model:

```text
Task
├── id
├── type
├── request
├── status
├── priority
├── createdAt
├── startedAt
├── completedAt
├── progress
├── result
├── error
├── cancellationState
└── provenance
```

States:

```text
QUEUED
RUNNING
WAITING
PAUSED
COMPLETED
FAILED
CANCELLED
EXPIRED
```

Tasks must be durable where the product contract requires it.

## 13. Android Lifecycle

`UI state != durable runtime state`.

Critical runtime state must be persisted appropriately.

Target recovery:

```text
Application Boot
      ↓
Load Runtime State
      ↓
Reconcile Tasks
      ↓
Verify Capabilities
      ↓
Restore Project Context
      ↓
Resume Safe Work
```

Never blindly repeat an external side effect after a crash.

## 14. Concurrent Tasks

JARVIS should support multiple isolated tasks. Requirements include unique IDs, isolated execution contexts, cancellation tokens, bounded concurrency, resource ownership, priority, retry policy, and persistence where required.

Task A must not mutate Task B.

## 15. Resource Coordination

Resources requiring explicit ownership include microphone, camera, network, Bluetooth, screen capture, location, and CPU-heavy model execution.

Conflicts must be detected and handled rather than silently allowed.

## 16. Live Actions

Long-running tasks require real progress and controls such as pause and cancel. Never display fake progress. The UI must reflect actual runtime state.

## 17. Project Context

Target persistent project capsule:

```text
Project
├── objective
├── context
├── constraints
├── decisions
├── tasks
├── artifacts
├── dependencies
├── currentState
└── nextActions
```

This enables reliable continuation of previous work.

## 18. Memory Architecture

Separate conversation history, working memory, long-term memory, and user-controlled memory.

Memory records should contain:

```text
id
content
source
timestamp
confidence
scope
privacyClassification
```

Deletion must be real.

## 19. Tool Architecture

Every tool requires an explicit contract:

```text
Tool
├── id
├── description
├── inputSchema
├── outputSchema
├── permissions
├── sideEffectLevel
├── availability
├── timeout
├── retryPolicy
└── auditMetadata
```

Examples include `OPEN_APP`, `SEND_MESSAGE`, `SEARCH_WEB`, `CREATE_FILE`, `READ_SCREEN`, `TAKE_SCREENSHOT`, `SET_ALARM`, and `PLAY_MEDIA`.

The model must never receive unrestricted Android API access.

## 20. Trusted Execution

All real-world side effects pass through:

```text
User Intent
    ↓
JARVIS Runtime
    ↓
Policy
    ↓
Capability
    ↓
Authorization
    ↓
Tool
    ↓
Execution
    ↓
Verification
    ↓
Result
```

The runtime must know who requested the action, what will happen, which capability executes it, whether authorization is valid, whether execution occurred, whether it succeeded, and whether success can be verified.

## 21. Approval Policy

Suggested risk classification:

```text
LOW       = READ_ONLY
MEDIUM    = REVERSIBLE_CHANGE
HIGH      = EXTERNAL_SIDE_EFFECT
CRITICAL  = DESTRUCTIVE / IRREVERSIBLE
```

Approval logic must be centralized.

## 22. Android Capability Layer

Target adapters:

```text
AndroidCapability
├── AppLauncher
├── Notifications
├── Media
├── Phone
├── Messaging
├── ScreenCapture
├── Camera
├── Location
├── Accessibility
└── SystemSettings
```

Each capability defines required permissions, Android version requirements, availability, security impact, failure behavior, and lifecycle requirements.

## 23. Error Architecture

Use a canonical error model:

```text
JarvisError
├── AuthenticationError
├── PermissionError
├── CapabilityUnavailable
├── ModelError
├── NetworkError
├── ToolError
├── TaskError
├── ExecutionDenied
├── ExecutionFailed
├── TimeoutError
├── CancellationError
└── InternalError
```

Each error should expose `code`, `message`, `userMessage`, `retryable`, `recoverable`, and `diagnosticContext`.

## 24. Network Reliability

Every network operation requires timeout, retry policy, backoff where appropriate, cancellation, offline behavior, and error classification.

Differentiate network errors, auth errors, rate limits, server errors, model errors, invalid requests, timeouts, and cancellations.

## 25. Idempotency

External side effects must protect against duplicate execution. Use idempotency keys where appropriate.

## 26. Security

Production requirements:

- Secure credential handling
- No API secrets in Git
- No secrets in logs
- No sensitive raw data in diagnostics
- Secure local storage
- Minimal permissions
- Runtime permission validation
- Secure network communication
- Protected backend operations
- Strict tool authorization

## 27. Prompt Injection Defense

External content is untrusted data. Potential sources include web pages, emails, documents, notifications, tool results, and screen content.

Prompt injection defense must be architectural, not only prompt-based.

## 28. Privacy

JARVIS should expose clear privacy state for `LOCAL`, `CLOUD`, `MEMORY`, `MICROPHONE`, `SCREEN`, and `DEVICE CONTROL`.

Requirements include no hidden cloud fallback, no unnecessary raw audio retention, no unnecessary sensitive logging, explicit permission behavior, and user-controlled memory.

## 29. Observability

Important execution should carry:

```text
traceId
sessionId
taskId
toolId
provider
latency
status
errorCode
```

Track voice latency, task success/failure, provider latency/failures, fallback rate, crash-free sessions, ANR, memory, CPU, and battery impact where practical.

## 30. Testing Strategy

### Unit Tests

Test runtime state, state machines, task transitions, memory, policy, tool contracts, and provider selection.

### Integration Tests

Test runtime + model, runtime + memory, runtime + task engine, runtime + tools, and runtime + voice.

### Android Tests

Test permissions, lifecycle, background behavior, notifications, audio, UI inflation, and emulator behavior.

### End-to-End Tests

Example:

```text
"Remind me tomorrow at 9 AM."
        ↓
STT
        ↓
Runtime
        ↓
Intent
        ↓
Tool
        ↓
Trusted Execution
        ↓
Android Alarm
        ↓
Verification
        ↓
Voice Confirmation
```

## 31. Failure Testing

Deliberately test application termination during speech/task execution, network/model/STT/TTS failure, revoked permissions, Bluetooth disconnects, process recreation, cancellation, duplicate requests, malformed tool output, and Android background restrictions.

## 32. CI/CD

Every pull request should validate:

```text
Lint
Unit Tests
Integration Tests
Static Analysis
Dependency Checks
Android Compilation
APK Validation
Android Instrumentation Tests
```

CI is the source of truth for reproducibility.

## 33. Android Build Reproducibility

Document and pin Gradle, Android Gradle Plugin, Kotlin, compileSdk, targetSdk, Build Tools, JDK, and Android SDK packages.

Local and CI environments must not silently diverge.

## 34. Current SDK / Environment Blocker

The current development environment has shown missing Android SDK components such as `platform-tools/adb` and `cmdline-tools/latest/bin/sdkmanager`. Gradle has attempted to resolve components including `build-tools;34.0.0` and `platforms;android-35`.

Treat SDK/toolchain failures as environment blockers until verified. Do not modify application code merely to bypass SDK problems.

## 35. Firebase

Current project infrastructure includes:

```text
Firebase Analytics
Firebase Auth
Firebase Firestore
Firebase Crashlytics
Firebase Performance
Firebase App Check
```

Do not disable production instrumentation merely to make local development easier.

## 36. Release Management

Current release identity:

```text
versionCode = 1
versionName = 0.4.0
```

Target release flow:

```text
Development → Internal → Closed Beta → Production
```

Every release requires version, commit SHA, build provenance, release notes, test status, known issues, and rollback strategy.

## 37. Production Signing

Keep debug and release signing separate. CI signing credentials must be environment-controlled. Never commit keystores, passwords, or private signing keys.

## 38. Performance

Measure startup time, UI responsiveness, voice latency, memory, CPU, battery, network usage, model loading, and task latency. Continuous voice must remain battery-conscious.

## 39. Accessibility

Support screen readers, dynamic text sizes, accessible touch targets, sufficient contrast, reduced motion, keyboard navigation where relevant, and voice-state information that is not communicated only through animation.

## 40. UI Truth Principle

The UI must represent real runtime state. Do not display fake processing or fake progress.

## 41. Design System

Centralize colors, typography, spacing, component styles, animations, elevation, status indicators, task cards, voice states, and error states.

Maintain consistency across Home, Conversation, History, Voice, Tasks, Projects, Memory, Settings, Diagnostics, Permissions, and Live Actions.

## 42. Architecture Boundaries

Target conceptual package structure:

```text
runtime/
├── identity/
├── session/
├── conversation/
├── reasoning/
├── memory/
├── capabilities/
├── tools/
├── tasks/
├── execution/
├── permissions/
├── lifecycle/
├── notifications/
├── voice/
├── liveaction/
├── diagnostics/
└── security/
```

This is a target architecture. Do not blindly reorganize the current repository before mapping current modules.

## 43. No God Classes

Do not create giant classes responsible for voice, AI, memory, tools, Android, UI, Firebase, and tasks simultaneously. Each subsystem needs explicit ownership.

## 44. No Duplicate Runtime

Do not introduce competing authorities such as `VoiceRuntime`, `ChatRuntime`, `TaskRuntime`, and `BackgroundRuntime`. The JARVIS Runtime owns canonical state.

## 45. No Fake Completion

Never claim `Done`, `Sent`, `Saved`, or `Remembered` unless the corresponding operation actually succeeded.

## 46. No Hidden Side Effects

Every external side effect must be intentional, authorized, observable, verifiable, and recoverable where possible.

## 47. No Scope Creep

Use:

```text
Phase → Implementation → Tests → Validation → Merge → Next Phase
```

Do not combine unrelated feature and infrastructure changes into giant changesets.

## 48. Phase-Gated Roadmap

Target roadmap:

```text
FOUNDATION
    ↓
LOCAL VOICE RUNTIME
    ↓
RUNTIME STATE
    ↓
MEMORY
    ↓
TRUSTED EXECUTION
    ↓
PROJECT CONTEXT
    ↓
LIVE ACTION
    ↓
CONTINUOUS VOICE
    ↓
CONCURRENT TASKS
    ↓
DEVICE CONTROL
    ↓
RELIABILITY
    ↓
PRIVACY
    ↓
REAL DEVICE ACCEPTANCE
    ↓
PRODUCTION
```

Verify the actual repository before declaring a phase complete.

## 49. Definition of Done

A feature is not production-ready merely because it compiles or appears on screen.

It requires:

- Correct architecture and ownership
- Complete happy and failure paths
- Lifecycle handling
- Permission and security validation
- Secret protection
- Side-effect authorization
- Recovery, cancellation, and timeout behavior
- Persistence where necessary
- Unit, integration, Android, and regression tests
- Structured diagnostics
- Passing CI
- Documentation
- Real-device acceptance where required

## 50. Production Acceptance Gate

```text
[ ] Canonical runtime established
[ ] Runtime state is authoritative
[ ] Voice state machine is deterministic
[ ] Local mode is genuinely local
[ ] Cloud escalation is explicit
[ ] Tool execution is controlled
[ ] Trusted execution is centralized
[ ] External side effects are verifiable
[ ] Tasks are durable where required
[ ] Concurrent tasks are isolated
[ ] Android lifecycle is handled
[ ] Crash recovery is tested
[ ] Memory is persistent and user-controlled
[ ] Sensitive data is protected
[ ] API credentials are protected
[ ] Prompt injection boundaries exist
[ ] Error taxonomy is centralized
[ ] Observability exists
[ ] CI is deterministic
[ ] Android instrumentation passes
[ ] Emulator validation passes
[ ] Real-device acceptance passes
[ ] Release signing is secure
[ ] Rollback strategy exists
[ ] Privacy behavior is documented
[ ] Production runbook exists
```

## 51. Immediate Engineering Priority

Before implementing additional advanced capabilities:

```text
1. Verify repository state
2. Verify current branch/commit
3. Verify current CI state
4. Verify Android toolchain
5. Verify existing architecture
6. Identify current phase
7. Identify current blocker
8. Fix only the current blocker
9. Run canonical tests
10. Validate on Android
11. Merge
12. Advance
```

## 52. Developer Operating Rules

Before adding a state variable:

> **Who owns this state?**

Before adding a tool:

> **Who owns this capability?**

Before adding an external action:

> **Who authorizes this side effect?**

Before storing state only in memory:

> **What happens if Android kills the process?**

Before adding retries:

> **What happens if this operation runs twice?**

Before showing completion:

> **Can the user verify that this actually happened?**

## 53. Final Product Contract

JARVIS should ultimately provide:

```text
ONE IDENTITY
ONE RUNTIME
ONE SOURCE OF TRUTH
ONE CAPABILITY REGISTRY
ONE EXECUTION AUTHORITY
ONE TASK MODEL
ONE MEMORY MODEL
ONE TRUST MODEL

MANY INTERFACES
MANY PROVIDERS
MANY DEVICES
```

Future models, tools, devices, and interfaces must plug into the runtime instead of creating parallel systems.

## 54. Repository Alignment Rule

This document is an engineering target, not a claim that every subsystem described above already exists.

Continuously classify features as:

```text
IMPLEMENTED
PARTIALLY IMPLEMENTED
PLANNED
BLOCKED
NOT STARTED
```

Never convert planned architecture into a false implementation claim.

## 55. Final Engineering Directive

> **Do not build a collection of AI features. Build the runtime that makes those capabilities coherent.**

The quality bar is not:

> "JARVIS looks futuristic."

The quality bar is:

```text
JARVIS knows what it can do.
JARVIS knows what it cannot do.
JARVIS knows what it is doing.
JARVIS knows whether it succeeded.
JARVIS survives failures.
JARVIS protects user data.
JARVIS does not duplicate side effects.
JARVIS does not fabricate completion.
JARVIS remains one coherent system.
```

---

## Repository Reference

**Repository:** `webwithroni/voice-jarvis`  
**Default branch:** `main`  
**Application ID:** `com.webwithroni.voicejarvis`  
**Version:** `0.4.0`  
**Compile SDK:** `35`  
**Target SDK:** `34`  
**Minimum SDK:** `26`  
**JVM:** `17`

---

# END

**JARVIS Production Engineering Master Plan**
