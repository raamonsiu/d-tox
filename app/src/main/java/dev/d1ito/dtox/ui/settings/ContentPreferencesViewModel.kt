package dev.d1ito.dtox.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.d1ito.dtox.AppContainer
import dev.d1ito.dtox.data.storage.ContentPreferencesStore
import dev.d1ito.dtox.domain.feed.ContentPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The content preferences for the "Contenido" settings section. Every change is saved at once.
 *
 * @param store where the preferences are read and written.
 * @param scope runs the saves; cancelled when the ViewModel is cleared.
 */
class ContentPreferencesViewModel(
    private val store: ContentPreferencesStore,
    private val scope: CoroutineScope,
) : ViewModel(scope) {
    /** Current preferences; `null` until they are read from disk. */
    val preferences: StateFlow<ContentPreferences?> =
        store.preferences.stateIn(scope, SharingStarted.Eagerly, initialValue = null)

    private val mutableSaveFailed = MutableStateFlow(false)

    /** `true` after the last change could not be saved; the screen tells the user. */
    val saveFailed: StateFlow<Boolean> = mutableSaveFailed.asStateFlow()

    /**
     * Applies [edit] to the current preferences and saves the result. Ignored before they are
     * loaded or when [edit] fails (e.g. it would leave no category chosen).
     */
    fun change(edit: ContentPreferences.() -> Result<ContentPreferences>) {
        val edited = preferences.value?.edit()?.getOrNull() ?: return
        scope.launch { mutableSaveFailed.value = store.update(edited).isFailure }
    }

    companion object {
        /** @return a factory that builds the ViewModel with the container's preferences store. */
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ContentPreferencesViewModel(
                        store = container.contentPreferencesStore,
                        scope = CoroutineScope(SupervisorJob() + container.dispatchers.main),
                    )
                }
            }
    }
}
