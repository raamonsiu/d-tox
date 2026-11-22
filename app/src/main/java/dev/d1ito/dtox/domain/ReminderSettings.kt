package dev.d1ito.dtox.domain

import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Daily window without reminders (RF-34). It may wrap midnight (22:00–08:00): [start] is inside
 * the window and [end] is not, so a reminder at exactly [end] is allowed.
 */
data class QuietHours(
    val start: LocalTime,
    val end: LocalTime,
) {
    /** @return `true` if [time] falls inside the window. */
    operator fun contains(time: LocalTime): Boolean =
        if (start <= end) {
            time >= start && time < end
        } else {
            time >= start || time < end
        }
}

/**
 * Short reading reminders (RF-33/RF-34, docs/04-design.md §Ajustes). Only created through
 * [create] or its edits, so every value follows these rules:
 * - at most [MAX_REMINDERS_PER_DAY] [times], distinct, sorted and in whole minutes; the number of
 *   reminders per day is always `times.size`, so count and times cannot disagree;
 * - no reminder time falls inside the [quietHours], and the quiet hours are not empty.
 *
 * @property enabled global switch; off, no reminder is sent whatever the [times].
 */
@ConsistentCopyVisibility
data class ReminderSettings private constructor(
    val enabled: Boolean,
    val times: List<LocalTime>,
    val quietHours: QuietHours,
) {
    /** Reminders per day, 0–[MAX_REMINDERS_PER_DAY]. */
    val remindersPerDay: Int get() = times.size

    /** `true` while one more reminder fits: below the maximum and with a free time left. */
    val canAddReminder: Boolean get() = nextFreeTime() != null

    /** @return these settings with the given values changed, or a [ReminderSettingsError]. */
    fun edited(
        enabled: Boolean = this.enabled,
        times: List<LocalTime> = this.times,
        quietHours: QuietHours = this.quietHours,
    ): Result<ReminderSettings> = create(enabled, times, quietHours)

    /** @return these settings with the reminder at [index] moved to [time]. */
    fun withTimeChanged(
        index: Int,
        time: LocalTime,
    ): Result<ReminderSettings> =
        edited(times = times.mapIndexed { position, old -> if (position == index) time else old })

    /** @return one more reminder at the first free whole hour after the last one. */
    fun withOneMoreReminder(): Result<ReminderSettings> =
        nextFreeTime()?.let { freeTime -> edited(times = times + freeTime) }
            ?: Result.failure(ReminderSettingsError.TooManyReminders(times.size + 1))

    /** @return the settings without the latest reminder of the day (unchanged if there is none). */
    fun withOneReminderLess(): Result<ReminderSettings> = edited(times = times.dropLast(1))

    /**
     * The first whole hour after the last reminder (or after the quiet hours end, with none) that
     * is neither taken nor quiet; `null` at the maximum or when the quiet hours leave no room.
     */
    private fun nextFreeTime(): LocalTime? {
        if (times.size >= MAX_REMINDERS_PER_DAY) return null
        val from = (times.lastOrNull() ?: quietHours.end).truncatedTo(ChronoUnit.HOURS)
        return (1..HOURS_PER_DAY)
            .map { offset -> from.plusHours(offset.toLong()) }
            .firstOrNull { candidate -> candidate !in times && candidate !in quietHours }
    }

    companion object {
        const val MAX_REMINDERS_PER_DAY = 4
        private const val HOURS_PER_DAY = 24
        private const val DEFAULT_REMINDER_HOUR = 19
        private const val DEFAULT_QUIET_START_HOUR = 22
        private const val DEFAULT_QUIET_END_HOUR = 8

        /**
         * Off until the user turns reminders on (the permission is asked then, #113); one reminder
         * in the evening and quiet nights, within the 1–2 a day of RF-33.
         */
        val Default =
            ReminderSettings(
                enabled = false,
                times = listOf(LocalTime.of(DEFAULT_REMINDER_HOUR, 0)),
                quietHours =
                    QuietHours(
                        start = LocalTime.of(DEFAULT_QUIET_START_HOUR, 0),
                        end = LocalTime.of(DEFAULT_QUIET_END_HOUR, 0),
                    ),
            )

        /**
         * @param times any order; seconds are dropped and the result is sorted.
         * @return the settings, or the first [ReminderSettingsError] found.
         */
        fun create(
            enabled: Boolean,
            times: List<LocalTime>,
            quietHours: QuietHours,
        ): Result<ReminderSettings> {
            val minutes = times.map { time -> time.truncatedTo(ChronoUnit.MINUTES) }.sorted()
            val repeated = minutes.zipWithNext().firstOrNull { (first, next) -> first == next }
            val quiet = minutes.firstOrNull { time -> time in quietHours }
            val error =
                when {
                    quietHours.start == quietHours.end ->
                        ReminderSettingsError.EmptyQuietHours(quietHours.start)
                    minutes.size > MAX_REMINDERS_PER_DAY ->
                        ReminderSettingsError.TooManyReminders(minutes.size)
                    repeated != null -> ReminderSettingsError.RepeatedTime(repeated.first)
                    quiet != null -> ReminderSettingsError.TimeInQuietHours(quiet)
                    else -> null
                }
            return error?.let { rule -> Result.failure(rule) }
                ?: Result.success(ReminderSettings(enabled, minutes, quietHours))
        }
    }
}

/** Why reminder settings could not be created. Returned inside [Result.failure], never thrown. */
sealed class ReminderSettingsError(
    message: String,
) : IllegalArgumentException(message) {
    class TooManyReminders(
        val rejectedCount: Int,
    ) : ReminderSettingsError(
            "At most ${ReminderSettings.MAX_REMINDERS_PER_DAY} reminders a day, was $rejectedCount",
        )

    class RepeatedTime(
        val repeatedTime: LocalTime,
    ) : ReminderSettingsError("Two reminders at $repeatedTime")

    class TimeInQuietHours(
        val quietTime: LocalTime,
    ) : ReminderSettingsError("Reminder at $quietTime falls in the quiet hours")

    class EmptyQuietHours(
        val boundary: LocalTime,
    ) : ReminderSettingsError("Quiet hours start and end at $boundary")
}
