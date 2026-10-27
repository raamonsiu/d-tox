package dev.d1ito.dtox.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.d1ito.dtox.R
import dev.d1ito.dtox.domain.PageMargins
import dev.d1ito.dtox.domain.ReadingFont
import dev.d1ito.dtox.domain.ReadingSettings
import dev.d1ito.dtox.domain.ReadingTheme
import dev.d1ito.dtox.ui.theme.DTOXTheme
import java.util.Locale

/** Step of the line spacing buttons; the allowed range is 1.5–1.7. */
private const val LINE_SPACING_STEP = 0.1
private const val TENTHS_PER_UNIT = 10.0

private val screenMargin = 24.dp
private val settingSpacing = 20.dp

/** Minimum touch target (docs/04-design.md: zonas táctiles ≥ 48 dp). */
private val minimumTouchTarget = 48.dp

private val themeLabels =
    listOf(
        ReadingTheme.SYSTEM to R.string.settings_theme_system,
        ReadingTheme.PAPER to R.string.settings_theme_paper,
        ReadingTheme.SEPIA to R.string.settings_theme_sepia,
        ReadingTheme.NIGHT to R.string.settings_theme_night,
    )

private val fontLabels =
    listOf(
        ReadingFont.LIBRON to R.string.settings_font_libron,
        ReadingFont.OPEN_DYSLEXIC to R.string.settings_font_open_dyslexic,
        ReadingFont.IBM_PLEX_MONO to R.string.settings_font_ibm_plex_mono,
    )

private val marginLabels =
    listOf(
        PageMargins.NARROW to R.string.settings_margins_narrow,
        PageMargins.NORMAL to R.string.settings_margins_normal,
        PageMargins.WIDE to R.string.settings_margins_wide,
    )

private val switchLabels = listOf(true to R.string.settings_on, false to R.string.settings_off)

/**
 * Flat text list of settings (docs/04-design.md §Ajustes). Only the Lectura section exists in the
 * MVP; Avisos, Contenido and Datos arrive with their phases. Changes apply at once.
 *
 * @param settings values shown.
 * @param saveFailed shows that the last change could not be saved.
 * @param onChange receives each edit of the current settings.
 * @param onBack closes the screen.
 */
@Suppress("LongParameterList") // Four inputs plus the conventional Compose modifier.
@Composable
fun SettingsContent(
    settings: ReadingSettings,
    saveFailed: Boolean,
    onChange: SettingsEdit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(DTOXTheme.palette.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(screenMargin),
        verticalArrangement = Arrangement.spacedBy(settingSpacing),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.sizeIn(minHeight = minimumTouchTarget)) {
            Text(stringResource(R.string.settings_back), color = DTOXTheme.palette.text)
        }
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.settings_section_reading),
            style = MaterialTheme.typography.titleMedium,
        )
        if (saveFailed) MutedText(stringResource(R.string.settings_save_failed))
        ReadingSection(settings, onChange)
    }
}

/** Receives each edit of the current settings. */
private typealias SettingsEdit = (ReadingSettings.() -> Result<ReadingSettings>) -> Unit

@Composable
private fun ReadingSection(
    settings: ReadingSettings,
    onChange: SettingsEdit,
) {
    AppearanceSettings(settings, onChange)
    TextSizeSettings(settings, onChange)
    PageSettings(settings, onChange)
}

@Composable
private fun AppearanceSettings(
    settings: ReadingSettings,
    onChange: SettingsEdit,
) {
    ChoiceSetting(R.string.settings_theme, themeLabels, settings.theme) { theme ->
        onChange { edited(theme = theme) }
    }
    ChoiceSetting(R.string.settings_font, fontLabels, settings.font) { font ->
        onChange { edited(font = font) }
    }
}

@Composable
private fun TextSizeSettings(
    settings: ReadingSettings,
    onChange: SettingsEdit,
) {
    val size = settings.textSizeSp
    StepperSetting(
        R.string.settings_text_size,
        StepperValue(
            text = size.toString(),
            canDecrease = size > ReadingSettings.TEXT_SIZE_RANGE.first,
            canIncrease = size < ReadingSettings.TEXT_SIZE_RANGE.last,
        ),
    ) { step -> onChange { edited(textSizeSp = textSizeSp + step) } }
    val spacing = settings.lineSpacing
    StepperSetting(
        R.string.settings_line_spacing,
        StepperValue(
            text = String.format(Locale.getDefault(), "%.1f", spacing),
            canDecrease = steppedLineSpacing(spacing, -1) < spacing,
            canIncrease = steppedLineSpacing(spacing, 1) > spacing,
        ),
    ) { step -> onChange { edited(lineSpacing = steppedLineSpacing(lineSpacing, step)) } }
}

@Composable
private fun PageSettings(
    settings: ReadingSettings,
    onChange: SettingsEdit,
) {
    ChoiceSetting(R.string.settings_margins, marginLabels, settings.margins) { margins ->
        onChange { edited(margins = margins) }
    }
    ChoiceSetting(R.string.settings_page_animation, switchLabels, settings.pageAnimation) { on ->
        onChange { edited(pageAnimation = on) }
    }
    ChoiceSetting(
        R.string.settings_progress_indicator,
        switchLabels,
        settings.progressIndicator,
    ) { shown ->
        onChange { edited(progressIndicator = shown) }
    }
}

/**
 * @property text the value as shown.
 * @property canDecrease the − button is enabled.
 * @property canIncrease the + button is enabled.
 */
private class StepperValue(
    val text: String,
    val canDecrease: Boolean,
    val canIncrease: Boolean,
)

/**
 * [current] plus [step] tenths, rounded to one decimal (so 1.5 + 0.1 + 0.1 is exactly 1.7, not a
 * binary fraction just above it) and kept inside the allowed range.
 */
private fun steppedLineSpacing(
    current: Double,
    step: Int,
): Double =
    (Math.round((current + step * LINE_SPACING_STEP) * TENTHS_PER_UNIT) / TENTHS_PER_UNIT)
        .coerceIn(ReadingSettings.LINE_SPACING_RANGE)

/** A setting with a few named values; the chosen one is bold and in the text color. */
@Composable
private fun <Value> ChoiceSetting(
    @StringRes label: Int,
    options: List<Pair<Value, Int>>,
    selected: Value,
    onSelect: (Value) -> Unit,
) {
    Column {
        MutedText(stringResource(label))
        FlowRow {
            options.forEach { (value, optionLabel) ->
                val isSelected = value == selected
                TextButton(
                    onClick = { onSelect(value) },
                    modifier =
                        Modifier
                            .sizeIn(minWidth = minimumTouchTarget, minHeight = minimumTouchTarget)
                            .semantics { this.selected = isSelected },
                ) {
                    val palette = DTOXTheme.palette
                    Text(
                        text = stringResource(optionLabel),
                        color = if (isSelected) palette.text else palette.textMuted,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/** A numeric setting with − and + buttons, disabled at the ends of its range. */
@Composable
private fun StepperSetting(
    @StringRes label: Int,
    value: StepperValue,
    onStep: (Int) -> Unit,
) {
    val decreaseDescription = stringResource(R.string.settings_decrease, stringResource(label))
    val increaseDescription = stringResource(R.string.settings_increase, stringResource(label))
    Column {
        MutedText(stringResource(label))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", decreaseDescription, value.canDecrease) { onStep(-1) }
            Text(text = value.text, color = DTOXTheme.palette.text)
            StepButton("+", increaseDescription, value.canIncrease) { onStep(1) }
        }
    }
}

@Composable
private fun StepButton(
    symbol: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier =
            Modifier
                .sizeIn(minWidth = minimumTouchTarget, minHeight = minimumTouchTarget)
                .semantics { contentDescription = description },
    ) {
        Text(
            text = symbol,
            // Libron draws − and + small at body size; the title size makes them readable.
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) DTOXTheme.palette.text else DTOXTheme.palette.textMuted,
        )
    }
}

@Composable
private fun MutedText(text: String) {
    Text(text = text, color = DTOXTheme.palette.textMuted)
}
