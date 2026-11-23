package dev.d1ito.dtox.ui.settings

import android.os.Build

/** Why reminders stayed off after the user tried to turn them on (#113). */
enum class NotificationRefusal {
    /** The user said no this time; turning reminders on again shows the system question again. */
    DENIED,

    /** Blocked for good, or turned off in the system settings; only those settings change it. */
    BLOCKED,
}

/** What turning reminders on leads to, given what the system allows. */
sealed interface NotificationPermissionStep {
    /** Notifications can be shown: turn reminders on. */
    data object TurnOn : NotificationPermissionStep

    /** Android 13+ without the permission: show the system question, once per user tap. */
    data object Ask : NotificationPermissionStep

    /** Reminders stay off; the section explains [refusal] in one sentence. */
    data class StayOff(
        val refusal: NotificationRefusal,
    ) : NotificationPermissionStep
}

/**
 * Decides what the "Avisos" switch does when the user turns it on. The permission is only asked
 * here, after that tap, never at startup (docs/03-architecture.md §Lecturas cortas).
 *
 * @param sdkInt the device's API level; below Android 13 there is no runtime permission.
 * @param permissionGranted whether `POST_NOTIFICATIONS` is granted (ignored below Android 13).
 * @param notificationsEnabled whether the system lets the app show notifications at all
 *   (`NotificationManagerCompat.areNotificationsEnabled`), which also covers notifications
 *   turned off in the system settings on any version.
 * @return [NotificationPermissionStep.TurnOn] when notifications can be shown,
 *   [NotificationPermissionStep.Ask] when the system question can still grant them, and
 *   [NotificationPermissionStep.StayOff] with [NotificationRefusal.BLOCKED] otherwise.
 */
fun stepToTurnRemindersOn(
    sdkInt: Int,
    permissionGranted: Boolean,
    notificationsEnabled: Boolean,
): NotificationPermissionStep =
    when {
        notificationsEnabled -> NotificationPermissionStep.TurnOn
        sdkInt >= Build.VERSION_CODES.TIRAMISU && !permissionGranted ->
            NotificationPermissionStep.Ask
        else -> NotificationPermissionStep.StayOff(NotificationRefusal.BLOCKED)
    }

/**
 * Decides what follows the user's answer to the system question.
 *
 * @param granted whether the permission was granted.
 * @param canAskAgain `shouldShowRequestPermissionRationale` after the answer: `true` after a first
 *   "no"; `false` once the system will not show the question again (it then answers "no" at once).
 * @return [NotificationPermissionStep.TurnOn] or [NotificationPermissionStep.StayOff]; never
 *   [NotificationPermissionStep.Ask], so a "no" is never followed by another question.
 */
fun stepAfterPermissionAnswer(
    granted: Boolean,
    canAskAgain: Boolean,
): NotificationPermissionStep =
    when {
        granted -> NotificationPermissionStep.TurnOn
        canAskAgain -> NotificationPermissionStep.StayOff(NotificationRefusal.DENIED)
        else -> NotificationPermissionStep.StayOff(NotificationRefusal.BLOCKED)
    }
