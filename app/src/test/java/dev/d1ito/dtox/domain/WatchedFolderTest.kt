package dev.d1ito.dtox.domain

import org.junit.Assert.assertEquals
import org.junit.Test

private const val EXTERNAL_STORAGE_TREE = "content://com.android.externalstorage.documents/tree/"

class WatchedFolderTest {
    @Test
    fun displayName_isTheLastFolderOfTheTreePath() {
        val folder = WatchedFolder(EXTERNAL_STORAGE_TREE + "primary%3ABooks%2FNovelas%20cortas")

        assertEquals("Novelas cortas", folder.displayName)
    }

    @Test
    fun displayName_ofAFolderAtTheVolumeTop_isTheFolder() {
        assertEquals("Books", WatchedFolder(EXTERNAL_STORAGE_TREE + "primary%3ABooks").displayName)
    }

    @Test
    fun displayName_ofAVolumeRoot_isTheVolume() {
        assertEquals("1A2B-3C4D", WatchedFolder(EXTERNAL_STORAGE_TREE + "1A2B-3C4D%3A").displayName)
    }

    @Test
    fun displayName_ignoresADocumentPathAfterTheTree() {
        val folder =
            WatchedFolder(
                EXTERNAL_STORAGE_TREE + "primary%3ABooks/document/primary%3ABooks%2Fa.epub",
            )

        assertEquals("Books", folder.displayName)
    }

    @Test
    fun displayName_keepsAPlusSign() {
        assertEquals("C++", WatchedFolder(EXTERNAL_STORAGE_TREE + "primary%3AC%2B%2B").displayName)
        assertEquals("C++", WatchedFolder(EXTERNAL_STORAGE_TREE + "primary%3AC++").displayName)
    }

    @Test
    fun displayName_ofAProviderWithoutVolume_isTheTreeIdentifier() {
        val downloads = "content://com.android.providers.downloads.documents/tree/downloads"

        assertEquals("downloads", WatchedFolder(downloads).displayName)
    }

    @Test
    fun displayName_withoutTreeOrWithBadEncoding_isTheWholeUri() {
        val notATree = "content://com.example/document/1"
        val badEncoding = EXTERNAL_STORAGE_TREE + "primary%3ABo%ZZoks"

        assertEquals(notATree, WatchedFolder(notATree).displayName)
        assertEquals(badEncoding, WatchedFolder(badEncoding).displayName)
    }
}
