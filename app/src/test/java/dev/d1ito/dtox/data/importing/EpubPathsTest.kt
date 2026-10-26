package dev.d1ito.dtox.data.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpubPathsTest {
    @Test
    fun resolvesRelativeToThePackageDirectory() {
        assertEquals("OEBPS/Text/one.xhtml", resolveArchivePath("OEBPS", "Text/one.xhtml"))
        assertEquals("one.xhtml", resolveArchivePath("", "one.xhtml"))
    }

    @Test
    fun decodesPercentEscapesButKeepsPlusSigns() {
        assertEquals(
            "OEBPS/chapter 1+2 ñ.xhtml",
            resolveArchivePath("OEBPS", "chapter%201+2%20%C3%B1.xhtml"),
        )
    }

    @Test
    fun appliesDotSegmentsAndDropsFragmentAndQuery() {
        assertEquals(
            "OEBPS/Text/one.xhtml",
            resolveArchivePath("OEBPS/Styles", "./../Text/./one.xhtml#part2"),
        )
        assertEquals("OEBPS/one.xhtml", resolveArchivePath("OEBPS", "one.xhtml?version=2"))
    }

    @Test
    fun leadingSlashIsRelativeToTheArchiveRoot() {
        assertEquals("Text/one.xhtml", resolveArchivePath("OEBPS", "/Text/one.xhtml"))
    }

    @Test
    fun rejectsPathsOutsideTheArchive() {
        assertNull(resolveArchivePath("OEBPS", "../../etc/passwd"))
        assertNull(resolveArchivePath("", "../one.xhtml"))
        assertNull(resolveArchivePath("OEBPS", "https://example.invalid/one.xhtml"))
        assertNull(resolveArchivePath("OEBPS", "file:///etc/passwd"))
    }

    @Test
    fun rejectsEmptyAndMalformedHrefs() {
        assertNull(resolveArchivePath("OEBPS", ""))
        assertNull(resolveArchivePath("OEBPS", "#only-fragment"))
        assertNull(resolveArchivePath("OEBPS", "bad%G1.xhtml"))
        assertNull(resolveArchivePath("OEBPS", "trailing%"))
    }
}
