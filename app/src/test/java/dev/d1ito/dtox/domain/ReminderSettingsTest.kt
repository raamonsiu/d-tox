package dev.d1ito.dtox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ReminderSettingsTest {
    private val night = QuietHours(start = time(22), end = time(8))
    private val afternoon = QuietHours(start = time(13), end = time(15))

    @Test
    fun quietHoursAcrossMidnight_containTheNightOnly() {
        assertTrue(time(22) in night)
        assertTrue(time(23, 59) in night)
        assertTrue(time(0) in night)
        assertTrue(time(7, 59) in night)
        assertFalse(time(8) in night)
        assertFalse(time(21, 59) in night)
        assertFalse(time(12) in night)
    }

    @Test
    fun quietHoursWithinADay_containTheirRangeOnly() {
        assertTrue(time(13) in afternoon)
        assertTrue(time(14, 59) in afternoon)
        assertFalse(time(15) in afternoon)
        assertFalse(time(12, 59) in afternoon)
        assertFalse(time(23) in afternoon)
    }

    @Test
    fun default_isOffWithOneEveningReminderAndQuietNights() {
        val defaults = ReminderSettings.Default

        assertFalse(defaults.enabled)
        assertEquals(listOf(time(19)), defaults.times)
        assertEquals(night, defaults.quietHours)
    }

    @Test
    fun create_sortsTheTimesInWholeMinutes() {
        val settings =
            ReminderSettings
                .create(true, listOf(time(20), LocalTime.of(9, 30, 45)), night)
                .getOrThrow()

        assertEquals(listOf(time(9, 30), time(20)), settings.times)
        assertEquals(2, settings.remindersPerDay)
    }

    @Test
    fun create_acceptsNoRemindersAtAll() {
        val settings = ReminderSettings.create(true, emptyList(), night).getOrThrow()

        assertEquals(0, settings.remindersPerDay)
    }

    @Test
    fun create_rejectsMoreThanFourReminders() {
        val five = (9..13).map { hour -> time(hour) }

        val error = ReminderSettings.create(true, five, night).exceptionOrNull()

        assertEquals(5, (error as ReminderSettingsError.TooManyReminders).rejectedCount)
    }

    @Test
    fun create_rejectsTwoRemindersAtTheSameMinute() {
        val error =
            ReminderSettings
                .create(true, listOf(time(9), LocalTime.of(9, 0, 30)), night)
                .exceptionOrNull()

        assertEquals(time(9), (error as ReminderSettingsError.RepeatedTime).repeatedTime)
    }

    @Test
    fun create_rejectsAReminderInsideTheQuietHours() {
        val error = ReminderSettings.create(true, listOf(time(23)), night).exceptionOrNull()

        assertEquals(time(23), (error as ReminderSettingsError.TimeInQuietHours).quietTime)
    }

    @Test
    fun create_rejectsQuietHoursThatStartAndEndAtOnce() {
        val empty = QuietHours(time(22), time(22))

        val error = ReminderSettings.create(true, listOf(time(9)), empty).exceptionOrNull()

        assertTrue(error is ReminderSettingsError.EmptyQuietHours)
    }

    @Test
    fun movingQuietHoursOverAReminder_isRejected() {
        val result = ReminderSettings.Default.edited(quietHours = QuietHours(time(18), time(8)))

        assertTrue(result.exceptionOrNull() is ReminderSettingsError.TimeInQuietHours)
    }

    @Test
    fun withTimeChanged_movesOnlyThatReminder() {
        val settings = ReminderSettings.create(true, listOf(time(9), time(19)), night).getOrThrow()

        val moved = settings.withTimeChanged(index = 1, time = time(18, 30)).getOrThrow()

        assertEquals(listOf(time(9), time(18, 30)), moved.times)
    }

    @Test
    fun withTimeChanged_intoTheQuietHours_isRejected() {
        val result = ReminderSettings.Default.withTimeChanged(index = 0, time = time(2))

        assertTrue(result.exceptionOrNull() is ReminderSettingsError.TimeInQuietHours)
    }

    @Test
    fun withOneMoreReminder_takesTheNextFreeHourOutsideTheQuietHours() {
        val settings = ReminderSettings.create(true, listOf(time(20, 30)), night).getOrThrow()

        val added = settings.withOneMoreReminder().getOrThrow()

        // 21:00 is free; 22:00 onwards is quiet until 08:00.
        assertEquals(listOf(time(20, 30), time(21)), added.times)
        val again = added.withOneMoreReminder().getOrThrow()
        assertEquals(listOf(time(8), time(20, 30), time(21)), again.times)
    }

    @Test
    fun withOneMoreReminder_afterNone_startsWhenTheQuietHoursEnd() {
        val none = ReminderSettings.create(true, emptyList(), night).getOrThrow()

        assertEquals(listOf(time(9)), none.withOneMoreReminder().getOrThrow().times)
    }

    @Test
    fun atFourReminders_noMoreCanBeAdded() {
        val four = (9..12).map { hour -> time(hour) }
        val settings = ReminderSettings.create(true, four, night).getOrThrow()

        assertFalse(settings.canAddReminder)
        assertTrue(
            settings.withOneMoreReminder().exceptionOrNull()
                is ReminderSettingsError.TooManyReminders,
        )
    }

    @Test
    fun withOneReminderLess_dropsTheLatestAndStopsAtZero() {
        val settings = ReminderSettings.create(true, listOf(time(9), time(19)), night).getOrThrow()

        val fewer = settings.withOneReminderLess().getOrThrow()
        val none = fewer.withOneReminderLess().getOrThrow()

        assertEquals(listOf(time(9)), fewer.times)
        assertEquals(emptyList<LocalTime>(), none.times)
        assertEquals(none, none.withOneReminderLess().getOrThrow())
    }

    private fun time(
        hour: Int,
        minute: Int = 0,
    ): LocalTime = LocalTime.of(hour, minute)
}
