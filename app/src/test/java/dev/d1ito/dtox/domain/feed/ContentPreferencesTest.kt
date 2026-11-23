package dev.d1ito.dtox.domain.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private fun readingOf(
    category: ShortReadCategory,
    minutes: Int,
): ShortRead =
    ShortRead(
        id = "lectura-001",
        title = "Lectura",
        author = null,
        category = category,
        tags = emptyList(),
        minutes = minutes,
        words = 1000,
        license = "PD",
        source = ShortReadSource("Wikisource", "https://es.wikisource.org/"),
        translator = null,
        path = "items/lectura-001.md",
        sha256 = "sha",
        publishedAt = LocalDate.of(2026, 10, 10),
    )

class ContentPreferencesTest {
    private val philosophyOnly =
        ContentPreferences.Default
            .withCategory(ShortReadCategory.SCIENCE, chosen = false)
            .getOrThrow()

    @Test
    fun defaults_chooseEveryCategoryAndDuration() {
        val defaults = ContentPreferences.Default

        assertEquals(
            setOf(ShortReadCategory.PHILOSOPHY, ShortReadCategory.SCIENCE),
            defaults.categories,
        )
        assertEquals(ShortReadDuration.entries.toSet(), defaults.durations)
    }

    @Test
    fun durations_areTheFeedBuckets() {
        assertEquals(listOf(5, 10, 20), ShortReadDuration.entries.map { it.minutes })
        assertEquals(ShortReadDuration.MEDIUM, ShortReadDuration.ofMinutes(10))
        assertNull(ShortReadDuration.ofMinutes(8))
    }

    @Test
    fun choices_canBeRemovedAndAddedBack() {
        assertEquals(setOf(ShortReadCategory.PHILOSOPHY), philosophyOnly.categories)

        val shortOnly =
            philosophyOnly
                .edited(durations = setOf(ShortReadDuration.SHORT))
                .getOrThrow()
        val restored =
            shortOnly
                .withCategory(ShortReadCategory.SCIENCE, chosen = true)
                .getOrThrow()
                .withDuration(ShortReadDuration.LONG, chosen = true)
                .getOrThrow()

        assertEquals(ContentPreferences.Default.categories, restored.categories)
        assertEquals(setOf(ShortReadDuration.SHORT, ShortReadDuration.LONG), restored.durations)
    }

    @Test
    fun removingTheLastCategory_fails() {
        val result = philosophyOnly.withCategory(ShortReadCategory.PHILOSOPHY, chosen = false)

        assertTrue(result.exceptionOrNull() is ContentPreferencesError.NoCategory)
    }

    @Test
    fun removingTheLastDuration_fails() {
        val tenMinutesOnly =
            ContentPreferences.Default
                .edited(durations = setOf(ShortReadDuration.MEDIUM))
                .getOrThrow()

        val result = tenMinutesOnly.withDuration(ShortReadDuration.MEDIUM, chosen = false)

        assertTrue(result.exceptionOrNull() is ContentPreferencesError.NoDuration)
    }

    @Test
    fun aCategoryOutsideTheMvp_isRejected() {
        val history = ShortReadCategory("historia")

        val result = ContentPreferences.Default.withCategory(history, chosen = true)

        val error = result.exceptionOrNull()
        assertTrue(error is ContentPreferencesError.UnknownCategory)
        assertEquals(
            setOf(history),
            (error as ContentPreferencesError.UnknownCategory).rejectedCategories,
        )
    }

    @Test
    fun accepts_onlyReadingsInAChosenCategoryAndDuration() {
        val shortPhilosophy =
            philosophyOnly.edited(durations = setOf(ShortReadDuration.SHORT)).getOrThrow()

        assertTrue(shortPhilosophy.accepts(readingOf(ShortReadCategory.PHILOSOPHY, 5)))
        assertFalse(shortPhilosophy.accepts(readingOf(ShortReadCategory.SCIENCE, 5)))
        assertFalse(shortPhilosophy.accepts(readingOf(ShortReadCategory.PHILOSOPHY, 10)))
    }

    @Test
    fun accepts_noReadingWhoseMinutesAreNoBucket() {
        assertFalse(ContentPreferences.Default.accepts(readingOf(ShortReadCategory.PHILOSOPHY, 8)))
    }
}
