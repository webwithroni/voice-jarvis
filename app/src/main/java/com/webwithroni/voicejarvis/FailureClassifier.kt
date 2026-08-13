package com.webwithroni.voicejarvis

/**
 * Converts an ActionResult into a deterministic recovery class.
 *
 * The classifier never retries anything.
 * It only describes what happened.
 */
object FailureClassifier {

    fun classify(
        request: ActionRequest,
        result: ActionResult
    ): RecoveryFailure {

        /*
         * Successful and verified actions need no recovery.
         */
        if (
            result.status ==
                ActionStatus.VERIFIED &&
            result.verified
        ) {
            return RecoveryFailure.NONE
        }

        /*
         * Explicit permission/capability failure.
         */
        if (
            result.status ==
                ActionStatus.UNAVAILABLE
        ) {
            return RecoveryFailure.CAPABILITY_UNAVAILABLE
        }

        /*
         * Explicit confirmation requirement.
         */
        if (
            result.status ==
                ActionStatus.REQUIRES_USER ||
            result.requiresConfirmation
        ) {
            return RecoveryFailure.USER_CONFIRMATION_REQUIRED
        }

        /*
         * Hard execution failure.
         *
         * Scroll is special because its first implementation
         * can legitimately fail due to weak accessibility support.
         */
        if (
            result.status ==
                ActionStatus.FAILED
        ) {

            return when (
                request.action
                    .trim()
                    .lowercase()
            ) {

                "scroll" ->
                    RecoveryFailure.STALE_UI

                else ->
                    RecoveryFailure.EXECUTION_FAILED
            }
        }

        /*
         * Verification failed/unknown after execution.
         *
         * Side-effecting actions must be treated conservatively.
         */
        if (
            result.status ==
                ActionStatus.UNKNOWN
        ) {

            return when (
                request.action
                    .trim()
                    .lowercase()
            ) {

                "scroll" ->
                    RecoveryFailure.VERIFICATION_UNKNOWN

                "swipe" ->
                    RecoveryFailure.VERIFICATION_UNKNOWN

                "tap",
                "tap_element",
                "type",
                "back",
                "home",
                "recents",
                "open_app",
                "launch_app" ->
                    RecoveryFailure.SIDE_EFFECT_UNCERTAIN

                else ->
                    RecoveryFailure.NON_RECOVERABLE
            }
        }

        /*
         * PARTIAL means execution may already have happened,
         * therefore do not blindly repeat it.
         */
        if (
            result.status ==
                ActionStatus.PARTIAL
        ) {
            return RecoveryFailure.SIDE_EFFECT_UNCERTAIN
        }

        /*
         * Anything not explicitly understood fails closed.
         */
        return RecoveryFailure.NON_RECOVERABLE
    }
}
