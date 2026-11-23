package dev.d1ito.dtox.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.d1ito.dtox.R

/**
 * What the "Avisos" switch needs to turn reminders on only when notifications can be shown.
 *
 * @property refusal why reminders stayed off after the last try, or `null`.
 * @property canOpenSystemSettings `false` once the system had no notification settings screen.
 * @property turnOn turns reminders on, first asking for the permission if needed (#113).
 * @property openSystemSettings opens the app's notification settings; only on an explicit tap.
 */
class NotificationPermissionGate(
    val refusal: NotificationRefusal?,
    val canOpenSystemSettings: Boolean,
    val turnOn: () -> Unit,
    val openSystemSettings: () -> Unit,
)

/**
 * Follows [stepToTurnRemindersOn] and [stepAfterPermissionAnswer] with the system question
 * (`ActivityResultContracts.RequestPermission`). Nothing is asked until
 * [NotificationPermissionGate.turnOn] runs, and a "no" only leaves a sentence: no second question,
 * no reminder to allow them.
 *
 * @param onPermitted turns reminders on once notifications can be shown.
 */
@Composable
fun rememberNotificationPermissionGate(onPermitted: () -> Unit): NotificationPermissionGate {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnPermitted by rememberUpdatedState(onPermitted)
    var refusal by rememberSaveable { mutableStateOf<NotificationRefusal?>(null) }
    var canOpenSystemSettings by rememberSaveable { mutableStateOf(true) }
    val follow: (NotificationPermissionStep) -> Unit = { step ->
        refusal = (step as? NotificationPermissionStep.StayOff)?.refusal
        if (step == NotificationPermissionStep.TurnOn) currentOnPermitted()
    }
    val question =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            follow(stepAfterPermissionAnswer(granted, canAskAgain(activity)))
        }
    return NotificationPermissionGate(
        refusal = refusal,
        canOpenSystemSettings = canOpenSystemSettings,
        turnOn = {
            val step = currentStepToTurnRemindersOn(context)
            if (step == NotificationPermissionStep.Ask && runtimePermissionExists()) {
                question.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                follow(step)
            }
        },
        openSystemSettings = { canOpenSystemSettings = openNotificationSettings(context) },
    )
}

/** The sentence that explains [NotificationPermissionGate.refusal] and, if blocked, the way out. */
@Composable
fun NotificationRefusalNote(gate: NotificationPermissionGate) {
    when (gate.refusal) {
        null -> Unit
        NotificationRefusal.DENIED -> {
            MutedText(stringResource(R.string.notification_permission_denied))
        }
        NotificationRefusal.BLOCKED -> {
            MutedText(stringResource(R.string.notification_permission_blocked))
            if (gate.canOpenSystemSettings) {
                TextAction(
                    stringResource(R.string.notification_permission_open_settings),
                    gate.openSystemSettings,
                )
            }
        }
    }
}

/** Lint-visible check that Android 13's `POST_NOTIFICATIONS` exists on this device. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
private fun runtimePermissionExists(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** [stepToTurnRemindersOn] with what the system says now. */
private fun currentStepToTurnRemindersOn(context: Context): NotificationPermissionStep {
    val notifications = NotificationManagerCompat.from(context)
    return stepToTurnRemindersOn(
        sdkInt = Build.VERSION.SDK_INT,
        permissionGranted = runtimePermissionExists() && permissionGranted(context),
        notificationsEnabled = notifications.areNotificationsEnabled(),
    )
}

@SuppressLint("InlinedApi") // Only called after runtimePermissionExists().
private fun permissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/** `shouldShowRequestPermissionRationale`: `false` once the system will not ask again. */
private fun canAskAgain(activity: Activity?): Boolean =
    activity != null &&
        runtimePermissionExists() &&
        ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.POST_NOTIFICATIONS,
        )

/**
 * Opens the system screen with the app's notification settings.
 *
 * @return `false` when the system has no such screen (some devices lack it); the refusal
 *   sentence already says where to allow notifications.
 */
private fun openNotificationSettings(context: Context): Boolean {
    val intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    return try {
        context.startActivity(intent)
        true
    } catch (expectedMissingScreen: ActivityNotFoundException) {
        false
    }
}
