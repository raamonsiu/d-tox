package dev.d1ito.dtox.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.feed.ContentPreferences
import dev.d1ito.dtox.domain.feed.ShortReadCategory
import dev.d1ito.dtox.domain.feed.ShortReadDuration
import dev.d1ito.dtox.ui.theme.DTOXTheme

private val categoryLabels =
    mapOf(
        ShortReadCategory.PHILOSOPHY to R.string.content_settings_category_philosophy,
        ShortReadCategory.SCIENCE to R.string.content_settings_category_science,
    )

/** Receives each edit of the current content preferences. */
typealias ContentPreferencesEdit = (ContentPreferences.() -> Result<ContentPreferences>) -> Unit

/**
 * What the "Contenido" settings section needs.
 *
 * @property preferences values shown.
 * @property saveFailed shows that the last change could not be saved.
 * @property onChange receives each edit; an edit that fails (e.g. it would leave no category
 *   chosen) must change nothing.
 */
class ContentSettings(
    val preferences: ContentPreferences,
    val saveFailed: Boolean,
    val onChange: ContentPreferencesEdit,
)

/** @return what the "Contenido" section shows now, or `null` until it is read from disk. */
@Composable
fun ContentPreferencesViewModel.contentSettings(): ContentSettings? {
    val loadedPreferences by preferences.collectAsState()
    val lastSaveFailed by saveFailed.collectAsState()
    return loadedPreferences?.let { current -> ContentSettings(current, lastSaveFailed, ::change) }
}

/**
 * The "Contenido" section (RF-32, docs/04-design.md §Ajustes): which categories and durations of
 * short reads are offered. Several of each can be chosen; the chosen ones are bold and in the text
 * color. Nothing is shown while [content] is `null` (not read from disk yet).
 */
@Composable
fun ContentSettingsSection(content: ContentSettings?) {
    if (content == null) return
    val preferences = content.preferences
    Text(
        stringResource(R.string.content_settings_section),
        style = MaterialTheme.typography.titleMedium,
    )
    if (content.saveFailed) MutedText(stringResource(R.string.settings_save_failed))
    MultipleChoiceSetting(
        R.string.content_settings_categories,
        options =
            ContentPreferences.AVAILABLE_CATEGORIES.map { category ->
                category to stringResource(categoryLabels.getValue(category))
            },
        chosen = preferences.categories,
    ) { category, chosen -> content.onChange { withCategory(category, chosen) } }
    MultipleChoiceSetting(
        R.string.content_settings_durations,
        options =
            ShortReadDuration.entries.map { duration ->
                duration to
                    stringResource(R.string.content_settings_duration_minutes, duration.minutes)
            },
        chosen = preferences.durations,
    ) { duration, chosen -> content.onChange { withDuration(duration, chosen) } }
}

/**
 * A setting where any of a few named values can be chosen; tapping one toggles it.
 *
 * @param options each value with its label, in the order shown.
 * @param onToggle receives the tapped value and whether it should now be chosen.
 */
@Composable
private fun <Value> MultipleChoiceSetting(
    @StringRes label: Int,
    options: List<Pair<Value, String>>,
    chosen: Set<Value>,
    onToggle: (Value, Boolean) -> Unit,
) {
    Column {
        MutedText(stringResource(label))
        FlowRow {
            options.forEach { (value, optionLabel) ->
                val isChosen = value in chosen
                TextButton(
                    onClick = { onToggle(value, !isChosen) },
                    modifier =
                        Modifier
                            .sizeIn(minWidth = minimumTouchTarget, minHeight = minimumTouchTarget)
                            .semantics { selected = isChosen },
                ) {
                    val palette = DTOXTheme.palette
                    Text(
                        text = optionLabel,
                        color = if (isChosen) palette.text else palette.textMuted,
                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
