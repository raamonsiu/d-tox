package dev.d1ito.dtox.ui.settings

import dev.d1ito.dtox.data.storage.ReminderSettingsStore
import dev.d1ito.dtox.domain.ReminderSettings
import dev.d1ito.dtox.domain.ReminderSettingsError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalTime

class ReminderSettingsViewModelTest {
    private val store = InMemoryReminderSettingsStore()
    private val viewModel = ReminderSettingsViewModel(store, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun settings_comeFromTheStore() {
        assertEquals(ReminderSettings.Default, viewModel.settings.value)
    }

    @Test
    fun change_isSavedAndShownAtOnce() {
        viewModel.change { edited(enabled = true) }

        assertTrue(store.settings.value.enabled)
        assertEquals(true, viewModel.settings.value?.enabled)
        assertFalse(viewModel.saveFailed.value)
    }

    @Test
    fun invalidChange_isNotSavedAndSaysWhy() {
        viewModel.change { withTimeChanged(index = 0, time = LocalTime.of(23, 0)) }

        assertEquals(0, store.updates)
        assertEquals(ReminderSettings.Default, viewModel.settings.value)
        assertTrue(viewModel.rejectedChange.value is ReminderSettingsError.TimeInQuietHours)
    }

    @Test
    fun acceptedChange_clearsTheLastRefusal() {
        viewModel.change { withTimeChanged(index = 0, time = LocalTime.of(23, 0)) }

        viewModel.change { withTimeChanged(index = 0, time = LocalTime.of(20, 0)) }

        assertNull(viewModel.rejectedChange.value)
        assertEquals(listOf(LocalTime.of(20, 0)), store.settings.value.times)
    }

    @Test
    fun failedSave_isReportedUntilASaveSucceeds() {
        store.failUpdates = true
        viewModel.change { edited(enabled = true) }
        assertTrue(viewModel.saveFailed.value)

        store.failUpdates = false
        viewModel.change { edited(enabled = true) }
        assertFalse(viewModel.saveFailed.value)
    }

    private class InMemoryReminderSettingsStore : ReminderSettingsStore {
        override val settings: StateFlow<ReminderSettings>
            get() = current

        private val current = MutableStateFlow(ReminderSettings.Default)
        var failUpdates = false
        var updates = 0

        override suspend fun update(settings: ReminderSettings): Result<Unit> {
            updates++
            if (failUpdates) return Result.failure(IOException("disk full"))
            current.value = settings
            return Result.success(Unit)
        }
    }
}
