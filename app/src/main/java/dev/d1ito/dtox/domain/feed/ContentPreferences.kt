package dev.d1ito.dtox.domain.feed

private const val SHORT_MINUTES = 5
private const val MEDIUM_MINUTES = 10
private const val LONG_MINUTES = 20

/**
 * Duration bucket of a short reading; the feed rounds `minutes` to one of these
 * (docs/02-data-formats.md §6).
 *
 * @property minutes the bucket as the feed writes it.
 */
enum class ShortReadDuration(
    val minutes: Int,
) {
    SHORT(SHORT_MINUTES),
    MEDIUM(MEDIUM_MINUTES),
    LONG(LONG_MINUTES),
    ;

    companion object {
        /** @return the bucket of exactly [minutes], or `null` when there is none. */
        fun ofMinutes(minutes: Int): ShortReadDuration? =
            entries.firstOrNull { duration -> duration.minutes == minutes }
    }
}

/**
 * Which short reads the user wants (RF-32, docs/04-design.md §Ajustes). Only created through
 * [create] or [edited], so at least one category and one duration are always chosen: with none,
 * no reading would ever be offered.
 *
 * @property categories chosen among [AVAILABLE_CATEGORIES].
 * @property durations chosen duration buckets.
 */
@ConsistentCopyVisibility
data class ContentPreferences private constructor(
    val categories: Set<ShortReadCategory>,
    val durations: Set<ShortReadDuration>,
) {
    /** @return these preferences with [category] chosen or not, or a [ContentPreferencesError]. */
    fun withCategory(
        category: ShortReadCategory,
        chosen: Boolean,
    ): Result<ContentPreferences> =
        edited(categories = if (chosen) categories + category else categories - category)

    /** @return these preferences with [duration] chosen or not, or a [ContentPreferencesError]. */
    fun withDuration(
        duration: ShortReadDuration,
        chosen: Boolean,
    ): Result<ContentPreferences> =
        edited(durations = if (chosen) durations + duration else durations - duration)

    /** @return these preferences with the given values changed, or a [ContentPreferencesError]. */
    fun edited(
        categories: Set<ShortReadCategory> = this.categories,
        durations: Set<ShortReadDuration> = this.durations,
    ): Result<ContentPreferences> = create(categories, durations)

    companion object {
        /** The categories of the MVP (D-04), in the order shown. */
        val AVAILABLE_CATEGORIES = listOf(ShortReadCategory.PHILOSOPHY, ShortReadCategory.SCIENCE)

        /** Everything chosen, so nothing is hidden until the user decides. */
        val Default =
            ContentPreferences(AVAILABLE_CATEGORIES.toSet(), ShortReadDuration.entries.toSet())

        /**
         * @return the preferences, or a [ContentPreferencesError] when no category or no duration
         *   is chosen, or a category is not among [AVAILABLE_CATEGORIES].
         */
        fun create(
            categories: Set<ShortReadCategory>,
            durations: Set<ShortReadDuration>,
        ): Result<ContentPreferences> =
            when {
                categories.isEmpty() -> Result.failure(ContentPreferencesError.NoCategory())
                durations.isEmpty() -> Result.failure(ContentPreferencesError.NoDuration())
                !AVAILABLE_CATEGORIES.containsAll(categories) ->
                    Result.failure(
                        ContentPreferencesError.UnknownCategory(categories - AVAILABLE_CATEGORIES),
                    )
                else -> Result.success(ContentPreferences(categories, durations))
            }
    }
}

/** Why content preferences could not be created. Returned inside [Result.failure], never thrown. */
sealed class ContentPreferencesError(
    message: String,
) : IllegalArgumentException(message) {
    class NoCategory : ContentPreferencesError("At least one category must be chosen")

    class NoDuration : ContentPreferencesError("At least one duration must be chosen")

    class UnknownCategory(
        val rejectedCategories: Set<ShortReadCategory>,
    ) : ContentPreferencesError(
            "Unknown categories: " + rejectedCategories.joinToString { category -> category.value },
        )
}
