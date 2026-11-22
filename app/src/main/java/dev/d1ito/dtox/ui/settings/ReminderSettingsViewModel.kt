package dev.d1ito.dtox.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.d1ito.dtox.AppContainer
import dev.d1ito.dtox.data.storage.ReminderSettingsStore
import dev.d1ito.dtox.domain.ReminderSettings
import dev.d1ito.dtox.domain.ReminderSettingsError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The reminder settings of the "Avisos" section. Every valid change is saved at once; an invalid
 * one (e.g. a time inside the quiet hours) is not saved and is kept in [rejectedChange] so the
 * screen can say why.
 *
 * @param store where the settings are read and written.
 * @param scope runs the saves; cancelled when the ViewModel is cleared.
 */
class ReminderSettingsViewModel(
    private val store: ReminderSettingsStore,
    private val scope: CoroutineScope,
) : ViewModel(scope) {
    /** Current settings; `null` until they are read from disk. */
    val settings: StateFlow<ReminderSettings?> =
        store.settings.stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    private val mutableSaveFailed = MutableStateFlow(false)
    private val mutableRejectedChange = MutableStateFlow<ReminderSettingsError?>(null)

    /** `true` after the last change could not be saved; the screen tells the user. */
    val saveFailed: StateFlow<Boolean> = mutableSaveFailed.asStateFlow()

    /** Why the last change was refused, or `null` once a change is accepted. */
    val rejectedChange: StateFlow<ReminderSettingsError?> = mutableRejectedChange.asStateFlow()

    /**
     * Applies [edit] to the current settings and saves the result. Ignored before the settings
     * are loaded; a failed [edit] is reported in [rejectedChange] and nothing is saved.
     */
    fun change(edit: ReminderSettings.() -> Result<ReminderSettings>) {
        val result = settings.value?.edit() ?: return
        result
            .onSuccess { edited ->
                mutableRejectedChange.value = null
                scope.launch { mutableSaveFailed.value = store.update(edited).isFailure }
            }.onFailure { error -> mutableRejectedChange.value = error as? ReminderSettingsError }
    }

    companion object {
        /** @return a factory that builds the ViewModel with the container's reminder store. */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ReminderSettingsViewModel(
                        store = container.reminderSettingsStore,
                        scope = CoroutineScope(SupervisorJob() + container.dispatchers.main),
                    )
                }
            }
    }
}
