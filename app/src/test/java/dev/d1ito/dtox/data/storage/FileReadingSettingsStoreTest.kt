package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.PageMargins
import dev.d1ito.dtox.domain.ReadingFont
import dev.d1ito.dtox.domain.ReadingSettings
import dev.d1ito.dtox.domain.ReadingTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileReadingSettingsStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val settingsFile by lazy { File(temporaryFolder.root, "settings.json") }

    private val customized =
        ReadingSettings.Default
            .edited(
                textSizeSp = 22,
                lineSpacing = 1.5,
                margins = PageMargins.WIDE,
                font = ReadingFont.OPEN_DYSLEXIC,
                theme = ReadingTheme.SEPIA,
                pageAnimation = false,
                progressIndicator = false,
            ).getOrThrow()

    @Test
    fun missingFile_readsTheDefaults() {
        assertEquals(ReadingSettings.Default, runBlocking { store().settings.first() })
    }

    @Test
    fun updatedSettings_persistForANewStore() {
        runBlocking { store().update(customized) }.getOrThrow()

        assertEquals(customized, runBlocking { store().settings.first() })
    }

    @Test
    fun update_isEmittedToCollectors() {
        val store = store()

        val emitted =
            runBlocking {
                val collected = launch(Dispatchers.Unconfined) { store.settings.take(2).toList() }
                yield()
                store.update(customized).getOrThrow()
                collected.join()
                store.settings.first()
            }

        assertEquals(customized, emitted)
    }

    @Test
    fun invalidOrMissingValues_fallBackToTheirDefaults() {
        settingsFile.writeText(
            """
            { "version": 1, "reading":
              { "textSizeSp": 99, "lineSpacing": "big", "font": "comic_sans", "theme": "night" } }
            """.trimIndent(),
        )

        val read = runBlocking { store().settings.first() }

        assertEquals(ReadingSettings.Default.edited(theme = ReadingTheme.NIGHT).getOrThrow(), read)
    }

    @Test
    fun corruptFile_readsTheDefaults() {
        settingsFile.writeText("{ not json")

        assertEquals(ReadingSettings.Default, runBlocking { store().settings.first() })
    }

    @Test
    fun update_keepsOtherSectionsAndUnknownFields() {
        settingsFile.writeText(
            """
            { "version": 1, "reminders": { "hour": 21 },
              "reading": { "textSizeSp": 20, "future": "x" } }
            """.trimIndent(),
        )

        runBlocking { store().update(customized) }.getOrThrow()

        val written = Json.parseToJsonElement(settingsFile.readText()).jsonObject
        assertEquals(
            "21",
            written
                .getValue("reminders")
                .jsonObject
                .getValue("hour")
                .jsonPrimitive.content,
        )
        val reading = written.getValue("reading").jsonObject
        assertEquals("x", reading.getValue("future").jsonPrimitive.content)
        assertEquals("22", reading.getValue("textSizeSp").jsonPrimitive.content)
        assertEquals("open_dyslexic", reading.getValue("font").jsonPrimitive.content)
    }

    private fun store() =
        FileReadingSettingsStore(
            settingsFile = settingsFile,
            fileWriter = SyncedAtomicFileWriter(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
        )
}
