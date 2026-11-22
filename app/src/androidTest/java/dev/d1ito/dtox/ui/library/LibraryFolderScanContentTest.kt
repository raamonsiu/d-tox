package dev.d1ito.dtox.ui.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.d1ito.dtox.R
import dev.d1ito.dtox.data.storage.FolderSource
import dev.d1ito.dtox.data.storage.LibraryEntry
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.domain.WatchedFolder
import dev.d1ito.dtox.ui.theme.DTOXTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val novels = WatchedFolder("content://provider/tree/primary%3ANovelas")

@RunWith(AndroidJUnit4::class)
class LibraryFolderScanContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun text(resource: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)

    @Test
    fun withoutWatchedFolders_thereIsNoScanAction() {
        show(LibraryUiState())

        composeRule.onNodeWithText(text(R.string.library_scan)).assertDoesNotExist()
    }

    @Test
    fun withWatchedFolders_theScanActionScans() {
        var scans = 0
        show(LibraryUiState(hasWatchedFolders = true), onScanFolders = { scans++ })

        composeRule.onNodeWithText(text(R.string.library_scan)).performClick()

        assertEquals(1, scans)
    }

    @Test
    fun whileScanning_theScanActionIsDisabled() {
        show(LibraryUiState(hasWatchedFolders = true, isScanning = true))

        composeRule.onNodeWithText(text(R.string.library_scanning)).assertIsNotEnabled()
    }

    @Test
    fun onlyTheBookGoneFromItsFolder_isMarked() {
        val gone = entry("fedcba9876543210fedcba9876543210", "Niebla", missing = true)
        val present = entry("0123456789abcdef0123456789abcdef", "Cartas a Lucilio", missing = false)
        show(LibraryUiState(books = listOf(gone, present), hasWatchedFolders = true))

        composeRule.onNodeWithText("Niebla").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.library_book_missing)).assertIsDisplayed()
    }

    @Test
    fun anUnreadableFolder_isSaidPlainly() {
        show(LibraryUiState(message = LibraryMessage.FolderScanIncomplete))

        composeRule.onNodeWithText(text(R.string.library_scan_incomplete)).assertIsDisplayed()
    }

    private fun show(
        state: LibraryUiState,
        onScanFolders: () -> Unit = {},
    ) {
        composeRule.setContent {
            DTOXTheme {
                LibraryContent(
                    state,
                    LibraryActions(
                        onAddBook = {},
                        onOpenBook = {},
                        onRemoveBook = {},
                        onScanFolders = onScanFolders,
                    ),
                )
            }
        }
    }

    private fun entry(
        hex: String,
        title: String,
        missing: Boolean,
    ) = LibraryEntry(
        BookIdentifier.fromHex(hex).getOrThrow(),
        title,
        author = null,
        fileName = "$hex.epub",
        lastOpenedAt = null,
        finished = false,
        source = FolderSource(novels, "${novels.treeUri}/document/$hex", missing),
    )
}
