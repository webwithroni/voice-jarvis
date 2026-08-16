package com.webwithroni.voicejarvis

/**
 * Non-permission runtime prerequisites a capability may depend on.
 *
 * These are either enabled by the user in system settings
 * (ACCESSIBILITY, OVERLAY) or require a platform service that must
 * actually be declared/implemented in the app (NOTIFICATION_LISTENER).
 */
enum class RuntimeService {
    ACCESSIBILITY,
    NOTIFICATION_LISTENER,
    OVERLAY
}
