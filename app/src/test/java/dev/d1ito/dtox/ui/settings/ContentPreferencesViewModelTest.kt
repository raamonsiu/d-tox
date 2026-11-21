package dev.d1ito.dtox.ui.settings

import dev.d1ito.dtox.data.storage.ContentPreferencesStore
import dev.d1ito.dtox.domain.feed.ContentPreferences
import dev.d1ito.dtox.domain.feed.ShortReadCategory
import dev.d1ito.dtox.domain.feed.ShortReadDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ContentPreferencesViewModelTest {
    private val store = InMemoryContentPreferencesStore()
    private val viewModel =
        ContentPreferencesViewModel(store, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun preferences_comeFromTheStore() {
        assertEquals(ContentPreferences.Default, viewModel.preferences.value)
    }

    @Test
    fun change_isSavedAndShownAtOnce() {
        viewModel.change { withDuration(ShortReadDuration.LONG, chosen = false) }

        val expected = setOf(ShortReadDuration.SHORT, ShortReadDuration.MEDIUM)
        assertEquals(expected, store.preferences.value.durations)
        assertEquals(expected, viewModel.preferences.value?.durations)
        assertFalse(viewModel.saveFailed.value)
    }

    @Test
    fun removingTheLastCategory_isIgnored() {
        viewModel.change { withCategory(ShortReadCategory.SCIENCE, chosen = false) }
        viewModel.change { withCategory(ShortReadCategory.PHILOSOPHY, chosen = false) }

        assertEquals(1, store.updates)
        assertEquals(setOf(ShortReadCategory.PHILOSOPHY), viewModel.preferences.value?.categories)
    }

    @Test
    fun failedSave_isReportedUntilASaveSucceeds() {
        store.failUpdates = true
        viewModel.change { withDuration(ShortReadDuration.SHORT, chosen = false) }
        assertTrue(viewModel.saveFailed.value)

        store.failUpdates = false
        viewModel.change { withDuration(ShortReadDuration.SHORT, chosen = false) }
        assertFalse(viewModel.saveFailed.value)
    }

    private class InMemoryContentPreferencesStore : ContentPreferencesStore {
        override val preferences: StateFlow<ContentPreferences>
            get() = current

        private val current = MutableStateFlow(ContentPreferences.Default)
        var failUpdates = false
        var updates = 0

        override suspend fun update(preferences: ContentPreferences): Result<Unit> {
            if (failUpdates) return Result.failure(IOException("disk full"))
            updates++
            current.value = preferences
            return Result.success(Unit)
        }
    }
}
