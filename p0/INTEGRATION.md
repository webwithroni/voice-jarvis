# P0 Integration Guide — Voice Jarvis (webwithroni/voice-jarvis @ main)

This folder (`/app/p0`) contains the P0 deliverable as a **delta** laid out in
repo-relative paths. Because this Emergent container cannot build native
Android, integrate these into your local clone of `voice-jarvis` and push via
the normal flow / CI.

Package for all Kotlin files: `com.webwithroni.voicejarvis`
Source root: `app/src/main/java/com/webwithroni/voicejarvis/`
Test root:   `app/src/test/java/com/webwithroni/voicejarvis/`

---

## 1. Copy NEW source files (main)

```
app/src/main/java/com/webwithroni/voicejarvis/PermissionState.kt
app/src/main/java/com/webwithroni/voicejarvis/CapabilityRuntimeState.kt
app/src/main/java/com/webwithroni/voicejarvis/RuntimeService.kt
app/src/main/java/com/webwithroni/voicejarvis/CapabilityAvailability.kt
app/src/main/java/com/webwithroni/voicejarvis/CapabilityContract.kt
app/src/main/java/com/webwithroni/voicejarvis/CapabilityEnvironment.kt
app/src/main/java/com/webwithroni/voicejarvis/CapabilityCatalog.kt
app/src/main/java/com/webwithroni/voicejarvis/CapabilityAvailabilityResolver.kt
app/src/main/java/com/webwithroni/voicejarvis/AndroidCapabilityEnvironment.kt
```

## 2. Copy NEW test files

```
app/src/test/java/com/webwithroni/voicejarvis/FakeCapabilityEnvironment.kt
app/src/test/java/com/webwithroni/voicejarvis/RiskEngineTest.kt
app/src/test/java/com/webwithroni/voicejarvis/FailureClassifierTest.kt
app/src/test/java/com/webwithroni/voicejarvis/RecoveryPolicyTest.kt
app/src/test/java/com/webwithroni/voicejarvis/CapabilityAvailabilityResolverTest.kt
app/src/test/java/com/webwithroni/voicejarvis/CapabilityCatalogContractTest.kt
app/src/test/java/com/webwithroni/voicejarvis/ActionPlannerTest.kt
app/src/test/java/com/webwithroni/voicejarvis/RecoveryEngineTest.kt
app/src/test/java/com/webwithroni/voicejarvis/VerificationEngineTest.kt
app/src/test/java/com/webwithroni/voicejarvis/CapabilityManagerTest.kt
app/src/test/java/com/webwithroni/voicejarvis/CapabilityBusFlowTest.kt
```

## 3. Replace MODIFIED files

- `app/build.gradle.kts` — adds `testOptions.unitTests` + the JVM test
  dependencies (JUnit4, Robolectric, AndroidX test, MockK). No production
  dependency or release-config change.
- `app/src/main/java/com/webwithroni/voicejarvis/CapabilityManager.kt` —
  truth-up rewrite. **Public API is unchanged** (`get`, `all`, `canExecute`,
  `capabilityForAction`, id constants), so `ActionExecutor`, `CapabilityBus`
  and `ActionPlanner` compile without changes. Adds new availability API.

## 4. Apply the ONE additive patch to `CapabilityBus.kt`

`CapabilityBus` is otherwise untouched. Add a single additive method so
availability is queryable from the bus/tool-bridge (spec §5). Insert it right
after the existing `capabilityFor(...)` method:

```kotlin
    /**
     * P0: deterministic availability query for the tool bridge / diagnostics.
     *
     * Delegates to the authoritative CapabilityManager truth-up layer so a
     * capability is never advertised as usable unless it is actually AVAILABLE.
     */
    fun availability(
        action: String
    ): CapabilityAvailability {

        return capabilityManager.availabilityForAction(action)
    }
```

No existing `CapabilityBus` logic changes. (`capabilityManager` is the existing
private field; `CapabilityAvailability` is in the same package.)

## 5. Add CI + helper script

```
.github/workflows/ci.yml           # new quality pipeline (see below)
scripts/ci_test_summary.py         # JUnit XML -> Markdown summary for CI
```

The existing `.github/workflows/build-debug-apk.yml` is **superseded** by
`ci.yml` (which also builds the APK). You may keep it or delete it — P0 does
not delete files.

---

## Running the tests locally

```bash
./gradlew testDebugUnitTest        # all P0 unit tests (JVM + Robolectric)
./gradlew lintDebug                # static analysis report
./gradlew assembleDebug -PGEMINI_API_KEY=... # (keys optional; default to "")
```

## What the truth-up changes at runtime

- `camera` and `notifications` now resolve to **NOT_SUPPORTED** (no working
  implementation / no declared service), so they can never be advertised as
  available. No camera/notification tool exists today, so no tool behaviour
  changes — only the self-reported capability catalog becomes honest.
- `flashlight`, `volume`, `alarm_timer`, `device_control` are now correctly
  reported **available** (they are implemented in `ActionExecutor`). The old
  `CapabilityManager.get()` fell through to an "unregistered" branch for these,
  which meant the Capability Bus path (`bus.executeSafe`) would have blocked
  them as UNAVAILABLE. This corrects a latent inconsistency.
- All other capabilities keep identical availability semantics
  (permission / accessibility based).

Nothing in the release build configuration or signing is changed (out of P0
scope).
