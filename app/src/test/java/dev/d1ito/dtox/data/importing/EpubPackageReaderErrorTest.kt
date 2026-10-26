package dev.d1ito.dtox.data.importing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubPackageReaderErrorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val reader = EpubPackageReader()

    @Test
    fun spineWithoutReadableItems_isEmptySpine() {
        val opf =
            packageDocument(
                manifest = xhtmlItem("gone", "gone.xhtml"),
                spine = """<itemref idref="gone"/>""",
            )
        val epub = temporaryFolder.epubFile(containerFor("content.opf"), "content.opf" to opf)

        assertEpubFailure<EpubPackageError.EmptySpine>(reader.read(epub))
    }

    @Test
    fun missingContainer_isMissingContainer() {
        val epub = temporaryFolder.epubFile(container = null, "content.opf" to packageDocument())

        assertEpubFailure<EpubPackageError.MissingContainer>(reader.read(epub))
    }

    @Test
    fun containerWithoutRootFile_isMissingContainer() {
        val epub = temporaryFolder.epubFile("<container><rootfiles/></container>")

        assertEpubFailure<EpubPackageError.MissingContainer>(reader.read(epub))
    }

    @Test
    fun malformedForeignOrMissingPackageDocument_isInvalidPackageDocument() {
        val container = containerFor("content.opf")
        val epubs =
            listOf(
                temporaryFolder.epubFile(container, "content.opf" to "<package><metadata>"),
                temporaryFolder.epubFile(container, "content.opf" to "<html/>"),
                temporaryFolder.epubFile(container),
            )

        epubs.forEach { epub ->
            assertEpubFailure<EpubPackageError.InvalidPackageDocument>(reader.read(epub))
        }
    }

    @Test
    fun fileThatIsNotAZip_isNotAnArchive() {
        val notZip = temporaryFolder.newFile("book.epub").apply { writeText("plain text") }

        assertEpubFailure<EpubPackageError.NotAnArchive>(reader.read(notZip))
    }

    @Test
    fun externalEntitiesAreNeverLoaded() {
        val secret = temporaryFolder.newFile("secret.txt").apply { writeText("SECRET") }
        val packageBody =
            packageDocument(metadata = "<dc:title>Title &leak;</dc:title>").substringAfter("?>")
        val opf =
            "<?xml version=\"1.0\"?>\n" +
                "<!DOCTYPE package [<!ENTITY leak SYSTEM \"${secret.toURI()}\">]>" + packageBody
        val epub =
            temporaryFolder.epubFile(
                containerFor("content.opf"),
                "content.opf" to opf,
                "one.xhtml" to TEST_CHAPTER,
            )

        val title =
            reader
                .read(epub)
                .getOrThrow()
                .metadata.title
                .orEmpty()

        assertTrue("Unexpected title '$title'", title.startsWith("Title"))
        assertFalse("External entity was expanded: '$title'", title.contains("SECRET"))
    }
}
