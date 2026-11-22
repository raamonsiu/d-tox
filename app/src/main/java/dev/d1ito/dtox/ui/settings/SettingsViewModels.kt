package dev.d1ito.dtox.ui.settings

/**
 * The ViewModels behind the settings screen, one per section, passed around together.
 *
 * @property reading the Lectura section; the reader uses it too.
 * @property content the Contenido section.
 * @property reminders the Avisos section.
 */
class SettingsViewModels(
    val reading: ReadingSettingsViewModel,
    val content: ContentPreferencesViewModel,
    val reminders: ReminderSettingsViewModel,
)
