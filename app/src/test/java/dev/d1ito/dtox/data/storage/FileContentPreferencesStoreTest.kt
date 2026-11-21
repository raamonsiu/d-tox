package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.ReadingSettings
import dev.d1ito.dtox.domain.ReadingTheme
import dev.d1ito.dtox.domain.feed.ContentPreferences
import dev.d1ito.dtox.domain.feed.ShortReadCategory
import dev.d1ito.dtox.domain.feed.ShortReadDuration
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

class FileContentPreferencesStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val settingsFile by lazy { File(temporaryFolder.root, "settings.json") }

    private val scienceShortReads =
        ContentPreferences.Default
            .edited(
                categories = setOf(ShortReadCategory.SCIENCE),
                durations = setOf(ShortReadDuration.SHORT, ShortReadDuration.LONG),
            ).getOrThrow()

    @Test
    fun missingFile_readsTheDefaults() {
        assertEquals(ContentPreferences.Default, runBlocking { store().preferences.first() })
    }

    @Test
    fun updatedPreferences_persistForANewStore() {
        runBlocking { store().update(scienceShortReads) }.getOrThrow()

        assertEquals(scienceShortReads, runBlocking { store().preferences.first() })
    }

    @Test
    fun preferences_areWrittenAsFeedValues() {
        runBlocking { store().update(scienceShortReads) }.getOrThrow()

        val content = writtenFile().getValue("content").jsonObject
        assertEquals(listOf("ciencia"), valuesOf(content, "categories"))
        assertEquals(listOf("5", "20"), valuesOf(content, "durations"))
        assertEquals("1", writtenFile().getValue("version").jsonPrimitive.content)
    }

    @Test
    fun unknownValues_areSkipped_andEmptyListsTakeTheDefaults() {
        settingsFile.writeText(
            """
            { "version": 1, "content":
              { "categories": ["historia", "ciencia", 3], "durations": [7, "10", true] } }
            """.trimIndent(),
        )

        val read = runBlocking { store().preferences.first() }

        assertEquals(setOf(ShortReadCategory.SCIENCE), read.categories)
        assertEquals(ContentPreferences.Default.durations, read.durations)
    }

    @Test
    fun corruptFile_readsTheDefaults() {
        settingsFile.writeText("{ not json")

        assertEquals(ContentPreferences.Default, runBlocking { store().preferences.first() })
    }

    @Test
    fun storesSharingTheDocument_keepEachOthersSections() {
        settingsFile.writeText("""{ "version": 1, "content": { "future": "x" } }""")
        val document = document()
        val readingStore = FileReadingSettingsStore(document)
        val contentStore = FileContentPreferencesStore(document)
        val nightTheme = ReadingSettings.Default.edited(theme = ReadingTheme.NIGHT).getOrThrow()

        runBlocking {
            contentStore.preferences.first()
            readingStore.update(nightTheme).getOrThrow()
            contentStore.update(scienceShortReads).getOrThrow()
        }

        val written = writtenFile()
        assertEquals(
            "night",
            written
                .getValue("reading")
                .jsonObject
                .getValue("theme")
                .jsonPrimitive.content,
        )
        val content = written.getValue("content").jsonObject
        assertEquals("x", content.getValue("future").jsonPrimitive.content)
        assertEquals(listOf("ciencia"), valuesOf(content, "categories"))
        assertEquals(
            nightTheme,
            runBlocking { FileReadingSettingsStore(document()).settings.first() },
        )
    }

    private fun writtenFile(): JsonObject =
        Json.parseToJsonElement(settingsFile.readText()).jsonObject

    private fun valuesOf(
        section: JsonObject,
        key: String,
    ): List<String> = section.getValue(key).jsonArray.map { item -> item.jsonPrimitive.content }

    private fun document() =
        SettingsDocument(
            settingsFile = settingsFile,
            fileWriter = SyncedAtomicFileWriter(Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
        )

    private fun store() = FileContentPreferencesStore(document())
}
