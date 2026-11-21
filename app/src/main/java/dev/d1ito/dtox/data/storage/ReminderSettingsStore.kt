package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.QuietHours
import dev.d1ito.dtox.domain.ReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** The user's reminder settings (RF-33/RF-34), shared by the settings screen and the scheduler. */
interface ReminderSettingsStore {
    /** Current reminder settings, then every change; starts with the stored or default values. */
    val settings: Flow<ReminderSettings>

    /** @return success once [settings] are durably stored, or an [AtomicWriteError]. */
    suspend fun update(settings: ReminderSettings): Result<Unit>
}

/** Section of `settings.json` with the reminder settings (docs/02-data-formats.md §5). */
private const val REMINDERS_SECTION = "reminders"

/**
 * Keeps the reminder settings in the `"reminders"` section of `settings.json`
 * (docs/02-data-formats.md §5).
 *
 * @param document the settings file, shared with the stores of the other sections.
 */
class FileReminderSettingsStore(
    document: SettingsDocument,
) : ReminderSettingsStore {
    private val section =
        SettingsSection(document, REMINDERS_SECTION, ::reminderSettingsOf, ::reminderSettingsJson)

    override val settings: Flow<ReminderSettings> = section.values

    override suspend fun update(settings: ReminderSettings): Result<Unit> = section.update(settings)
}

/** Times are written as 24-hour `"HH:mm"`, e.g. `"19:00"`. */
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * The reminders section. A missing or invalid value takes its default; reminder times that are
 * repeated, quiet or beyond the maximum are dropped, so a hand-edited file never stops the app.
 */
internal fun reminderSettingsOf(section: JsonObject?): ReminderSettings {
    val defaults = ReminderSettings.Default
    if (section == null) return defaults
    val quietSection = section["quietHours"] as? JsonObject
    val quietHours =
        QuietHours(
            start = timeOf(quietSection?.get("start")) ?: defaults.quietHours.start,
            end = timeOf(quietSection?.get("end")) ?: defaults.quietHours.end,
        ).takeIf { hours -> hours.start != hours.end } ?: defaults.quietHours
    val times = (section["times"] as? JsonArray)?.mapNotNull(::timeOf) ?: defaults.times
    val usableTimes =
        times
            .filterNot { time -> time in quietHours }
            .distinct()
            .take(ReminderSettings.MAX_REMINDERS_PER_DAY)
    val enabled = (section["enabled"] as? JsonPrimitive)?.booleanOrNull ?: defaults.enabled
    // Every value above already follows the rules; the fallback is only a guard.
    return ReminderSettings.create(enabled, usableTimes, quietHours).getOrDefault(defaults)
}

/** The fields of the reminders section this version owns. */
internal fun reminderSettingsJson(settings: ReminderSettings): JsonObject =
    buildJsonObject {
        put("enabled", settings.enabled)
        putJsonArray("times") {
            settings.times.forEach { time -> add(JsonPrimitive(time.format(timeFormat))) }
        }
        putJsonObject("quietHours") {
            put("start", settings.quietHours.start.format(timeFormat))
            put("end", settings.quietHours.end.format(timeFormat))
        }
    }

/** A `"HH:mm"` string as a time, or `null` if it is not one. */
private fun timeOf(element: JsonElement?): LocalTime? {
    val text = (element as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content
    return try {
        text?.let { timeText -> LocalTime.parse(timeText, timeFormat) }
    } catch (expectedInvalid: DateTimeParseException) {
        null // An invalid time takes its default, like any other invalid setting.
    }
}
