package dev.d1ito.dtox.domain

/** Reading typeface; all are bundled under the SIL OFL (docs/04-design.md, Tipografía). */
enum class ReadingFont { LIBRON, OPEN_DYSLEXIC, IBM_PLEX_MONO }

/** Color theme; [SYSTEM] follows the system light/dark mode with Paper/Night. */
enum class ReadingTheme { SYSTEM, PAPER, SEPIA, NIGHT }

/** Side margins of the page around the reading column. */
enum class PageMargins { NARROW, NORMAL, WIDE }

/**
 * How the reader shows text (docs/04-design.md). Only created through [create] or [edited], so
 * every value is inside its allowed range.
 *
 * @property textSizeSp body text size, [TEXT_SIZE_RANGE].
 * @property lineSpacing line height as a multiple of the text size, [LINE_SPACING_RANGE].
 * @property pageAnimation slide on page turns; the system "Remove animations" setting still wins.
 * @property progressIndicator show the faint progress indicator.
 */
@ConsistentCopyVisibility
data class ReadingSettings private constructor(
    val textSizeSp: Int,
    val lineSpacing: Double,
    val margins: PageMargins,
    val font: ReadingFont,
    val theme: ReadingTheme,
    val pageAnimation: Boolean,
    val progressIndicator: Boolean,
) {
    /** @return these settings with the given values changed, or a [ReadingSettingsError]. */
    @Suppress("LongParameterList") // One optional parameter per setting, named at call sites.
    fun edited(
        textSizeSp: Int = this.textSizeSp,
        lineSpacing: Double = this.lineSpacing,
        margins: PageMargins = this.margins,
        font: ReadingFont = this.font,
        theme: ReadingTheme = this.theme,
        pageAnimation: Boolean = this.pageAnimation,
        progressIndicator: Boolean = this.progressIndicator,
    ): Result<ReadingSettings> =
        create(textSizeSp, lineSpacing, margins, font, theme, pageAnimation, progressIndicator)

    companion object {
        val TEXT_SIZE_RANGE = 14..28
        val LINE_SPACING_RANGE = 1.5..1.7
        private const val DEFAULT_TEXT_SIZE_SP = 18
        private const val DEFAULT_LINE_SPACING = 1.6

        /** Defaults from docs/04-design.md. */
        val Default =
            ReadingSettings(
                textSizeSp = DEFAULT_TEXT_SIZE_SP,
                lineSpacing = DEFAULT_LINE_SPACING,
                margins = PageMargins.NORMAL,
                font = ReadingFont.LIBRON,
                theme = ReadingTheme.SYSTEM,
                pageAnimation = true,
                progressIndicator = true,
            )

        /** @return the settings, or a [ReadingSettingsError] for a value out of its range. */
        @Suppress("LongParameterList") // One parameter per setting, all named at call sites.
        fun create(
            textSizeSp: Int,
            lineSpacing: Double,
            margins: PageMargins,
            font: ReadingFont,
            theme: ReadingTheme,
            pageAnimation: Boolean,
            progressIndicator: Boolean,
        ): Result<ReadingSettings> =
            when {
                textSizeSp !in TEXT_SIZE_RANGE ->
                    Result.failure(ReadingSettingsError.TextSizeOutOfRange(textSizeSp))
                lineSpacing !in LINE_SPACING_RANGE ->
                    Result.failure(ReadingSettingsError.LineSpacingOutOfRange(lineSpacing))
                else ->
                    Result.success(
                        ReadingSettings(
                            textSizeSp,
                            lineSpacing,
                            margins,
                            font,
                            theme,
                            pageAnimation,
                            progressIndicator,
                        ),
                    )
            }
    }
}

/** Why reading settings could not be created. Returned inside [Result.failure], never thrown. */
sealed class ReadingSettingsError(
    message: String,
) : IllegalArgumentException(message) {
    class TextSizeOutOfRange(
        val rejectedSizeSp: Int,
    ) : ReadingSettingsError(
            "Text size must be ${ReadingSettings.TEXT_SIZE_RANGE} sp, was $rejectedSizeSp",
        )

    class LineSpacingOutOfRange(
        val rejectedSpacing: Double,
    ) : ReadingSettingsError(
            "Line spacing must be ${ReadingSettings.LINE_SPACING_RANGE}, was $rejectedSpacing",
        )
}
