package com.webwithroni.voicejarvis

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Production [CapabilityEnvironment] backed by the real Android runtime.
 *
 * P0 truth-up: every probe reads actual device state. Nothing is
 * hard-coded to "granted"/"available".
 */
class AndroidCapabilityEnvironment(
    context: Context
) : CapabilityEnvironment {

    private val appContext = context.applicationContext

    override fun permissionState(permission: String): PermissionState {
        val granted = ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED
        return if (granted) PermissionState.GRANTED else PermissionState.DENIED
    }

    override fun isServiceEnabled(service: RuntimeService): Boolean = when (service) {
        RuntimeService.ACCESSIBILITY ->
            VoiceJarvisAccessibilityService.isEnabled(appContext)

        RuntimeService.NOTIFICATION_LISTENER ->
            isNotificationListenerEnabled()

        RuntimeService.OVERLAY ->
            Settings.canDrawOverlays(appContext)
    }

    override fun hasFeature(feature: String): Boolean =
        appContext.packageManager.hasSystemFeature(feature)

    override fun sdkInt(): Int = Build.VERSION.SDK_INT

    private fun isNotificationListenerEnabled(): Boolean = try {
        Settings.Secure.getString(
            appContext.contentResolver,
            "enabled_notification_listeners"
        )?.contains(appContext.packageName) == true
    } catch (_: Exception) {
        false
    }
}
