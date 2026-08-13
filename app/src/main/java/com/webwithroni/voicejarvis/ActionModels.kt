package com.webwithroni.voicejarvis

enum class ActionRisk {
    SAFE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class ActionStatus {
    EXECUTED,
    VERIFIED,
    FAILED,
    PARTIAL,
    REQUIRES_USER,
    UNAVAILABLE,
    UNKNOWN
}

data class ActionRequest(
    val action: String,
    val target: String? = null,
    val parameters: Map<String, String> = emptyMap(),
    val risk: ActionRisk = ActionRisk.SAFE
)

data class ActionResult(
    val status: ActionStatus,
    val action: String,
    val message: String,
    val verified: Boolean = false,
    val requiresConfirmation: Boolean = false,
    val data: Map<String, String> = emptyMap()
)

data class CapabilityState(
    val id: String,
    val name: String,
    val available: Boolean,
    val level: Int,
    val risk: ActionRisk,
    val description: String,
    val setupRequired: Boolean = false
)
