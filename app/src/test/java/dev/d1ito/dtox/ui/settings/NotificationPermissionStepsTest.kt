package dev.d1ito.dtox.ui.settings

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionStepsTest {
    private val android12 = Build.VERSION_CODES.S_V2
    private val android13 = Build.VERSION_CODES.TIRAMISU

    @Test
    fun notificationsEnabled_turnsOnWithoutAsking() {
        listOf(android12, android13).forEach { sdkInt ->
            assertEquals(
                NotificationPermissionStep.TurnOn,
                stepToTurnRemindersOn(
                    sdkInt,
                    permissionGranted = true,
                    notificationsEnabled = true,
                ),
            )
        }
    }

    @Test
    fun android13WithoutPermission_asks() {
        assertEquals(
            NotificationPermissionStep.Ask,
            stepToTurnRemindersOn(
                android13,
                permissionGranted = false,
                notificationsEnabled = false,
            ),
        )
    }

    @Test
    fun android13WithPermissionButNotificationsOff_staysOffUntilSystemSettings() {
        assertEquals(
            NotificationPermissionStep.StayOff(NotificationRefusal.BLOCKED),
            stepToTurnRemindersOn(
                android13,
                permissionGranted = true,
                notificationsEnabled = false,
            ),
        )
    }

    @Test
    fun belowAndroid13WithNotificationsOff_neverAsksAndStaysOff() {
        assertEquals(
            NotificationPermissionStep.StayOff(NotificationRefusal.BLOCKED),
            stepToTurnRemindersOn(
                android12,
                permissionGranted = false,
                notificationsEnabled = false,
            ),
        )
    }

    @Test
    fun granted_turnsOn() {
        assertEquals(
            NotificationPermissionStep.TurnOn,
            stepAfterPermissionAnswer(granted = true, canAskAgain = false),
        )
    }

    @Test
    fun firstNo_staysOffAsDenied() {
        assertEquals(
            NotificationPermissionStep.StayOff(NotificationRefusal.DENIED),
            stepAfterPermissionAnswer(granted = false, canAskAgain = true),
        )
    }

    @Test
    fun noThatCannotBeAskedAgain_staysOffAsBlocked() {
        assertEquals(
            NotificationPermissionStep.StayOff(NotificationRefusal.BLOCKED),
            stepAfterPermissionAnswer(granted = false, canAskAgain = false),
        )
    }
}
