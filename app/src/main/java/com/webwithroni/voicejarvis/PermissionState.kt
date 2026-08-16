package com.webwithroni.voicejarvis

/**
 * Truthful runtime state of a single Android permission.
 *
 * P0 truth-up: permission state must reflect the real device state,
 * never a hard-coded "granted".
 *
 * Note on NOT_REQUESTED:
 * Android's checkSelfPermission() cannot by itself distinguish
 * "explicitly denied" from "never asked". A future request-tracking
 * layer may populate NOT_REQUESTED; until then the Android
 * environment conservatively reports DENIED when a permission is not
 * currently granted. NOT_APPLICABLE is used for capabilities that are
 * not gated by a runtime permission on this device/SDK.
 */
enum class PermissionState {
    GRANTED,
    DENIED,
    NOT_REQUESTED,
    NOT_APPLICABLE
}
