package dev.d1ito.dtox.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.d1ito.dtox.AppContainer
import dev.d1ito.dtox.data.storage.ReadingSettingsStore
import dev.d1ito.dtox.domain.ReadingSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The reading settings for the settings screen and the reader. Every change is saved at once, so
 * the reader applies it immediately (RF-40).
 *
 * @param store where the settings are read and written.
 * @param scope runs the saves; cancelled when the ViewModel is cleared.
 */
class ReadingSettingsViewModel(
    private val store: ReadingSettingsStore,
    private val scope: CoroutineScope,
) : ViewModel(scope) {
    /** Current settings; `null` until they are read from disk. */
    val settings: StateFlow<ReadingSettings?> =
        store.settings.stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    private val mutableSaveFailed = MutableStateFlow(false)

    /** `true` after the last change could not be saved; the screen tells the user. */
    val saveFailed: StateFlow<Boolean> = mutableSaveFailed.asStateFlow()

    /**
     * Applies [edit] to the current settings and saves the result. Ignored before the settings
     * are loaded or when [edit] fails (a value out of its range).
     */
    fun change(edit: ReadingSettings.() -> Result<ReadingSettings>) {
        val edited = settings.value?.edit()?.getOrNull() ?: return
        scope.launch { mutableSaveFailed.value = store.update(edited).isFailure }
    }

    companion object {
        /** @return a factory that builds the ViewModel with the container's settings store. */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ReadingSettingsViewModel(
                        store = container.readingSettingsStore,
                        scope = CoroutineScope(SupervisorJob() + container.dispatchers.main),
                    )
                }
            }
    }
}
