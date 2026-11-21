package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.QuietHours
import dev.d1ito.dtox.domain.ReadingSettings
import dev.d1ito.dtox.domain.ReadingTheme
import dev.d1ito.dtox.domain.ReminderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalTime

/** The `"reminders"` section that [FileReadingSettingsStore] keeps in `settings.json`. */
class FileReminderSettingsStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val settingsFile by lazy { File(temporaryFolder.root, "settings.json") }

    private val customized =
        ReminderSettings
            .create(
                enabled = true,
                times = listOf(LocalTime.of(9, 30), LocalTime.of(18, 0)),
                quietHours = QuietHours(LocalTime.of(23, 0), LocalTime.of(7, 0)),
            ).getOrThrow()

    @Test
    fun missingFile_readsTheDefaults() {
        assertEquals(ReminderSettings.Default, runBlocking { store().reminderSettings.first() })
    }

    @Test
    fun updatedReminders_persistForANewStore() {
        runBlocking { store().updateReminders(customized) }.getOrThrow()

        assertEquals(customized, runBlocking { store().reminderSettings.first() })
    }

    @Test
    fun updatedReminders_areWrittenAsDocumented() {
        runBlocking { store().updateReminders(customized) }.getOrThrow()

        val written = Json.parseToJsonElement(settingsFile.readText()).jsonObject
        assertEquals("1", written.getValue("version").jsonPrimitive.content)
        val reminders = written.getValue("reminders").jsonObject
        assertEquals("true", reminders.getValue("enabled").jsonPrimitive.content)
        assertEquals(
            listOf("09:30", "18:00"),
            reminders.getValue("times").jsonArray.map { time -> time.jsonPrimitive.content },
        )
        val quietHours = reminders.getValue("quietHours").jsonObject
        assertEquals("23:00", quietHours.getValue("start").jsonPrimitive.content)
        assertEquals("07:00", quietHours.getValue("end").jsonPrimitive.content)
    }

    @Test
    fun readingAndReminderUpdates_keepEachOther() {
        val store = store()
        val reading = ReadingSettings.Default.edited(theme = ReadingTheme.SEPIA).getOrThrow()

        runBlocking {
            store.updateReminders(customized).getOrThrow()
            store.update(reading).getOrThrow()
        }

        val reread = store()
        assertEquals(customized, runBlocking { reread.reminderSettings.first() })
        assertEquals(reading, runBlocking { reread.settings.first() })
    }

    @Test
    fun update_keepsUnknownFieldsOfTheRemindersSection() {
        settingsFile.writeText("""{ "version": 1, "reminders": { "future": "x" } }""")

        runBlocking { store().updateReminders(customized) }.getOrThrow()

        val reminders = writtenReminders()
        assertEquals("x", reminders.getValue("future").jsonPrimitive.content)
    }

    @Test
    fun invalidValues_fallBackToTheirDefaults() {
        settingsFile.writeText(
            """
            { "version": 1, "reminders":
              { "enabled": "yes", "times": ["25:00", 7, "09:00"],
                "quietHours": { "start": "late", "end": "06:00" } } }
            """.trimIndent(),
        )

        val read = runBlocking { store().reminderSettings.first() }

        val expected =
            ReminderSettings.Default
                .edited(
                    times = listOf(LocalTime.of(9, 0)),
                    quietHours = QuietHours(LocalTime.of(22, 0), LocalTime.of(6, 0)),
                ).getOrThrow()
        assertEquals(expected, read)
    }

    @Test
    fun quietRepeatedOrExtraTimes_areDropped() {
        settingsFile.writeText(
            """
            { "reminders": { "enabled": true,
              "times": ["23:00", "09:00", "09:00", "10:00", "11:00", "12:00", "13:00"] } }
            """.trimIndent(),
        )

        val read = runBlocking { store().reminderSettings.first() }

        assertEquals((9..12).map { hour -> LocalTime.of(hour, 0) }, read.times)
    }

    @Test
    fun emptyQuietHours_readAsTheDefaultOnes() {
        settingsFile.writeText(
            """{ "reminders": { "quietHours": { "start": "22:00", "end": "22:00" } } }""",
        )

        val read = runBlocking { store().reminderSettings.first() }

        assertEquals(ReminderSettings.Default.quietHours, read.quietHours)
    }

    private fun writtenReminders(): JsonObject =
        Json
            .parseToJsonElement(settingsFile.readText())
            .jsonObject
            .getValue("reminders")
            .jsonObject

    private fun store() =
        FileReadingSettingsStore(
            settingsFile = settingsFile,
            fileWriter = SyncedAtomicFileWriter(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
        )
}
