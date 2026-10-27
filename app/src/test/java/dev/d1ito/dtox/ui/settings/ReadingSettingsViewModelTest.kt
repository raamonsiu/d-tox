package dev.d1ito.dtox.ui.settings

import dev.d1ito.dtox.data.storage.ReadingSettingsStore
import dev.d1ito.dtox.domain.ReadingSettings
import dev.d1ito.dtox.domain.ReadingTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ReadingSettingsViewModelTest {
    private val store = InMemoryReadingSettingsStore()
    private val viewModel = ReadingSettingsViewModel(store, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun settings_comeFromTheStore() {
        assertEquals(ReadingSettings.Default, viewModel.settings.value)
    }

    @Test
    fun change_isSavedAndShownAtOnce() {
        viewModel.change { edited(theme = ReadingTheme.NIGHT, textSizeSp = 22) }

        assertEquals(ReadingTheme.NIGHT, store.settings.value.theme)
        assertEquals(22, viewModel.settings.value?.textSizeSp)
        assertFalse(viewModel.saveFailed.value)
    }

    @Test
    fun changeOutOfRange_isIgnored() {
        viewModel.change { edited(textSizeSp = 40) }

        assertEquals(0, store.updates)
        assertEquals(ReadingSettings.Default, viewModel.settings.value)
    }

    @Test
    fun failedSave_isReportedUntilASaveSucceeds() {
        store.failUpdates = true
        viewModel.change { edited(theme = ReadingTheme.SEPIA) }
        assertTrue(viewModel.saveFailed.value)

        store.failUpdates = false
        viewModel.change { edited(theme = ReadingTheme.SEPIA) }
        assertFalse(viewModel.saveFailed.value)
    }

    private class InMemoryReadingSettingsStore : ReadingSettingsStore {
        override val settings: StateFlow<ReadingSettings>
            get() = current

        private val current = MutableStateFlow(ReadingSettings.Default)
        var failUpdates = false
        var updates = 0

        override suspend fun update(settings: ReadingSettings): Result<Unit> {
            if (failUpdates) return Result.failure(IOException("disk full"))
            updates++
            current.value = settings
            return Result.success(Unit)
        }
    }
}
