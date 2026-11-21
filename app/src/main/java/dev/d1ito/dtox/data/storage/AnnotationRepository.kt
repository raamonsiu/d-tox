package dev.d1ito.dtox.data.storage

import dev.d1ito.dtox.domain.AnnotationIdentifier
import dev.d1ito.dtox.domain.BookAnnotation
import dev.d1ito.dtox.domain.BookIdentifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Reading order of annotations: by start anchor (chapter, block, offset), then by end (a
 * highlight's range end; a note or bookmark ends where it starts, so it goes before a highlight
 * starting at the same place), then by creation date and finally by identifier, which is unique,
 * so the order is total and stable.
 */
val AnnotationReadingOrder: Comparator<BookAnnotation> =
    compareBy<BookAnnotation> { annotation -> annotation.start }
        .thenBy { annotation ->
            (annotation as? BookAnnotation.Highlight)?.range?.end
                ?: annotation.start
        }.thenBy { annotation -> annotation.metadata.createdAt }
        .thenBy { annotation -> annotation.metadata.identifier.value }

/**
 * What a new data file records about a book that has none yet (e.g. its progress was never saved).
 *
 * @property source where the book came from.
 */
data class NewBookDetails(
    val title: String,
    val author: String?,
    val source: BookSource,
)

/** Why an annotation change was rejected. Returned inside [Result.failure], never thrown. */
sealed class AnnotationRepositoryError(
    message: String,
) : IllegalArgumentException(message) {
    /** The book already has an annotation with this identifier. */
    class DuplicateIdentifier(
        val identifier: AnnotationIdentifier,
    ) : AnnotationRepositoryError("Annotation $identifier already exists")

    /** The book has no annotation with this identifier. */
    class NotFound(
        val identifier: AnnotationIdentifier,
    ) : AnnotationRepositoryError("Annotation $identifier not found")
}

/**
 * Highlights, notes and bookmarks of each book (RF-20–24), kept in its data file. Every change is
 * one atomic read-modify-write of `books/<bookId>.json`, so other data (progress) is kept.
 * Failures are an [AnnotationRepositoryError] or those of [BookDataStore.update].
 */
interface AnnotationRepository {
    /**
     * @return the annotations of the book in [AnnotationReadingOrder], emitted on collection and
     *   again after each change made through this repository; a failure when the data file
     *   cannot be read (e.g. it comes from a newer app).
     */
    fun annotations(bookIdentifier: BookIdentifier): Flow<Result<List<BookAnnotation>>>

    /**
     * @param newBookDetails used only when the book has no data file yet.
     * @return success once stored, or [AnnotationRepositoryError.DuplicateIdentifier].
     */
    suspend fun add(
        bookIdentifier: BookIdentifier,
        annotation: BookAnnotation,
        newBookDetails: NewBookDetails,
    ): Result<Unit>

    /**
     * Replaces the annotation with the same identifier; the caller sets its new `updatedAt`.
     *
     * @return success once stored, or [AnnotationRepositoryError.NotFound].
     */
    suspend fun edit(
        bookIdentifier: BookIdentifier,
        annotation: BookAnnotation,
    ): Result<Unit>

    /** @return success once removed, or [AnnotationRepositoryError.NotFound]. */
    suspend fun remove(
        bookIdentifier: BookIdentifier,
        identifier: AnnotationIdentifier,
    ): Result<Unit>
}

/**
 * [AnnotationRepository] on top of the per-book data files of [store].
 *
 * @param store must be the same instance every other writer of book data uses, so updates are
 *   serialized.
 */
class BookDataAnnotationRepository(
    private val store: BookDataStore,
) : AnnotationRepository {
    /** Counts the changes made through this repository; each one makes collectors read again. */
    private val changeCount = MutableStateFlow(0L)

    override fun annotations(bookIdentifier: BookIdentifier): Flow<Result<List<BookAnnotation>>> =
        changeCount
            .map {
                store.read(bookIdentifier).map { stored ->
                    stored?.annotations.orEmpty().sortedWith(AnnotationReadingOrder)
                }
            }.distinctUntilChanged()

    override suspend fun add(
        bookIdentifier: BookIdentifier,
        annotation: BookAnnotation,
        newBookDetails: NewBookDetails,
    ): Result<Unit> =
        changeAnnotations(bookIdentifier) { stored ->
            val current = stored ?: newBookData(bookIdentifier, newBookDetails)
            val identifier = annotation.metadata.identifier
            if (current.annotations.any { existing ->
                    existing.metadata.identifier == identifier
                }
            ) {
                Result.failure(AnnotationRepositoryError.DuplicateIdentifier(identifier))
            } else {
                Result.success(current.copy(annotations = current.annotations + annotation))
            }
        }

    override suspend fun edit(
        bookIdentifier: BookIdentifier,
        annotation: BookAnnotation,
    ): Result<Unit> =
        changeExisting(bookIdentifier, annotation.metadata.identifier) { existing ->
            existing.map { stored ->
                if (stored.metadata.identifier ==
                    annotation.metadata.identifier
                ) {
                    annotation
                } else {
                    stored
                }
            }
        }

    override suspend fun remove(
        bookIdentifier: BookIdentifier,
        identifier: AnnotationIdentifier,
    ): Result<Unit> =
        changeExisting(bookIdentifier, identifier) { existing ->
            existing.filterNot { stored -> stored.metadata.identifier == identifier }
        }

    /** Applies [change] to the annotations if one has [identifier]; else fails with NotFound. */
    private suspend fun changeExisting(
        bookIdentifier: BookIdentifier,
        identifier: AnnotationIdentifier,
        change: (List<BookAnnotation>) -> List<BookAnnotation>,
    ): Result<Unit> =
        changeAnnotations(bookIdentifier) { stored ->
            val annotations = stored?.annotations.orEmpty()
            if (stored != null &&
                annotations.any { annotation -> annotation.metadata.identifier == identifier }
            ) {
                Result.success(stored.copy(annotations = change(annotations)))
            } else {
                Result.failure(AnnotationRepositoryError.NotFound(identifier))
            }
        }

    /** Updates the book data with [transform] and, on success, notifies the collectors. */
    private suspend fun changeAnnotations(
        bookIdentifier: BookIdentifier,
        transform: (BookData?) -> Result<BookData>,
    ): Result<Unit> =
        store
            .update(bookIdentifier, transform)
            .onSuccess { changeCount.update { count -> count + 1 } }
            .map { }

    private fun newBookData(
        bookIdentifier: BookIdentifier,
        details: NewBookDetails,
    ): BookData =
        BookData(
            bookIdentifier = bookIdentifier,
            title = details.title,
            author = details.author,
            source = details.source,
            progress = null,
        )
}
