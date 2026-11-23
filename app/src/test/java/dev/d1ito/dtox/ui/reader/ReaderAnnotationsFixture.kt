package dev.d1ito.dtox.ui.reader

import dev.d1ito.dtox.data.storage.BookData
import dev.d1ito.dtox.data.storage.BookDataAnnotationRepository
import dev.d1ito.dtox.data.storage.BookSource
import dev.d1ito.dtox.domain.Block
import dev.d1ito.dtox.domain.Book
import dev.d1ito.dtox.domain.BookAnnotation
import dev.d1ito.dtox.domain.BookIdentifier
import dev.d1ito.dtox.domain.BookMetadata
import dev.d1ito.dtox.domain.Chapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Title of [twoParagraphBook] and of the data files made for it. */
private const val TITLE = "Libro"

/**
 * [ReaderAnnotations] over [store] that dates everything at [now] and runs each change at once,
 * so a test sees its effect right after the call.
 */
internal fun readerAnnotationsOver(
    store: InMemoryBookDataStore,
    now: Instant,
): ReaderAnnotations =
    ReaderAnnotations(
        BookDataAnnotationRepository(store),
        Clock.fixed(now, ZoneOffset.UTC),
        CoroutineScope(Dispatchers.Unconfined),
    )

/** A store already holding the data file of [book] with [annotations]. */
internal fun storeHolding(
    book: Book,
    annotations: List<BookAnnotation>,
    source: BookSource,
): InMemoryBookDataStore =
    InMemoryBookDataStore(
        listOf(
            BookData(
                bookIdentifier = book.identifier,
                title = TITLE,
                author = null,
                source = source,
                progress = null,
                annotations = annotations,
            ),
        ),
    )

/** A book with one chapter of two blocks: «Primer párrafo.» and «Otro párrafo.». */
internal fun twoParagraphBook(identifier: String): Book =
    Book
        .create(
            BookIdentifier.parse(identifier).getOrThrow(),
            BookMetadata(title = TITLE, author = null, language = null),
            listOf(
                Chapter
                    .create(
                        0,
                        null,
                        listOf(
                            Block.Paragraph("Primer párrafo."),
                            Block.Paragraph("Otro párrafo."),
                        ),
                    ).getOrThrow(),
            ),
        ).getOrThrow()
