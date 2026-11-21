package dev.d1ito.dtox.ui.settings

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.ReminderSettings
import dev.d1ito.dtox.domain.ReminderSettingsError
import dev.d1ito.dtox.ui.theme.DTOXTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Receives each edit of the current reminder settings. */
internal typealias ReminderEdit = (ReminderSettings.() -> Result<ReminderSettings>) -> Unit

/** Times as the user's locale writes them, e.g. "19:00". */
private val shortTimeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/**
 * The "Avisos" section (RF-33/RF-34, docs/04-design.md §Ajustes) for [viewModel]'s settings;
 * nothing is shown until they are read from disk.
 */
@Composable
fun ReminderSettingsSection(viewModel: ReminderSettingsViewModel) {
    val settings by viewModel.settings.collectAsState()
    val saveFailed by viewModel.saveFailed.collectAsState()
    val rejectedChange by viewModel.rejectedChange.collectAsState()
    settings?.let { loaded ->
        ReminderSettingsContent(loaded, saveFailed, rejectedChange, viewModel::change)
    }
}

/**
 * The global switch and, while reminders are on, how many a day, their times and the quiet
 * hours. A time is changed in place with a plain [TimeInput]; no dialog, no animation.
 *
 * @param saveFailed shows that the last change could not be saved.
 * @param rejectedChange why the last change was refused, shown as a quiet note.
 * @param onChange receives each edit; invalid ones come back as [rejectedChange].
 */
@Composable
internal fun ReminderSettingsContent(
    settings: ReminderSettings,
    saveFailed: Boolean,
    rejectedChange: ReminderSettingsError?,
    onChange: ReminderEdit,
) {
    Text(stringResource(R.string.reminders_section), style = MaterialTheme.typography.titleMedium)
    if (saveFailed) MutedText(stringResource(R.string.reminders_save_failed))
    rejectedChange?.let { error -> MutedText(stringResource(messageOf(error))) }
    ChoiceSetting(R.string.reminders_enabled, switchLabels, settings.enabled) { on ->
        onChange { edited(enabled = on) }
    }
    if (settings.enabled) {
        val count = settings.remindersPerDay
        StepperSetting(
            R.string.reminders_per_day,
            StepperValue(
                text = count.toString(),
                canDecrease = count > 0,
                canIncrease = settings.canAddReminder,
            ),
        ) { step -> onChange { if (step > 0) withOneMoreReminder() else withOneReminderLess() } }
        ReminderTimes(settings.times, onChange)
        QuietHoursSetting(settings, onChange)
    }
}

@Composable
private fun ReminderTimes(
    times: List<LocalTime>,
    onChange: ReminderEdit,
) {
    if (times.isEmpty()) return
    Column {
        MutedText(stringResource(R.string.reminders_times))
        times.forEachIndexed { index, time ->
            TimeSetting(time, R.string.reminders_time_description) { chosen ->
                onChange { withTimeChanged(index, chosen) }
            }
        }
    }
}

@Composable
private fun QuietHoursSetting(
    settings: ReminderSettings,
    onChange: ReminderEdit,
) {
    val quietHours = settings.quietHours
    Column {
        MutedText(stringResource(R.string.reminders_quiet_hours))
        FlowRow {
            TimeSetting(quietHours.start, R.string.reminders_quiet_start_description) { chosen ->
                onChange { edited(quietHours = quietHours.copy(start = chosen)) }
            }
            Text(
                text = "–",
                color = DTOXTheme.palette.text,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            TimeSetting(quietHours.end, R.string.reminders_quiet_end_description) { chosen ->
                onChange { edited(quietHours = quietHours.copy(end = chosen)) }
            }
        }
    }
}

/**
 * A time shown as a text button; tapping it opens a [TimeEditor] in its place.
 *
 * @param description TalkBack label with one `%1$s` for the time.
 */
@Composable
private fun TimeSetting(
    time: LocalTime,
    @StringRes description: Int,
    onTimeChosen: (LocalTime) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val shownTime = time.format(shortTimeFormat)
    if (editing) {
        TimeEditor(time) { chosen ->
            editing = false
            chosen?.let(onTimeChosen)
        }
    } else {
        val label = stringResource(description, shownTime)
        TextButton(
            onClick = { editing = true },
            modifier =
                Modifier
                    .sizeIn(minWidth = minimumTouchTarget, minHeight = minimumTouchTarget)
                    .semantics { contentDescription = label },
        ) {
            Text(shownTime, color = DTOXTheme.palette.text)
        }
    }
}

/**
 * Hour and minute fields with "Aceptar" and "Cancelar".
 *
 * @param onDone receives the chosen time, or `null` when cancelled.
 */
@OptIn(ExperimentalMaterial3Api::class) // TimeInput is the only Material3 time field.
@Composable
private fun TimeEditor(
    initial: LocalTime,
    onDone: (LocalTime?) -> Unit,
) {
    val state =
        rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
    Column {
        TimeInput(state)
        Row {
            TextAction(stringResource(R.string.reminders_time_cancel)) { onDone(null) }
            TextAction(stringResource(R.string.reminders_time_accept)) {
                onDone(LocalTime.of(state.hour, state.minute))
            }
        }
    }
}

/** @return the calm sentence that explains [error]. */
@StringRes
private fun messageOf(error: ReminderSettingsError): Int =
    when (error) {
        is ReminderSettingsError.TimeInQuietHours -> R.string.reminders_error_quiet_time
        is ReminderSettingsError.RepeatedTime -> R.string.reminders_error_repeated_time
        is ReminderSettingsError.EmptyQuietHours -> R.string.reminders_error_empty_quiet_hours
        is ReminderSettingsError.TooManyReminders -> R.string.reminders_error_too_many
    }
