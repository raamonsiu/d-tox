package dev.d1ito.dtox.domain

import java.time.Instant
import java.util.UUID

private val uuidPattern =
    Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

/** Crockford base32 without I, L, O and U; the first character keeps it within 128 bits. */
private val ulidPattern = Regex("^[0-7][0-9A-HJKMNP-TV-Z]{25}$", RegexOption.IGNORE_CASE)

/**
 * Identity of an annotation: a UUID or a ULID (docs/02-data-formats.md §4), so annotations made
 * on different devices can be merged without collisions.
 *
 * @property value the text form, kept as read.
 */
@JvmInline
value class AnnotationIdentifier private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        /** @return a new random (version 4) UUID. */
        fun random(): AnnotationIdentifier = AnnotationIdentifier(UUID.randomUUID().toString())

        /** @return the identifier, or [DocumentModelError.InvalidAnnotationIdentifier]. */
        fun parse(value: String): Result<AnnotationIdentifier> =
            if (uuidPattern.matches(value) || ulidPattern.matches(value)) {
                Result.success(AnnotationIdentifier(value))
            } else {
                Result.failure(DocumentModelError.InvalidAnnotationIdentifier(value))
            }
    }
}

/**
 * What every annotation carries besides its content.
 *
 * @property createdAt when the user made it, UTC.
 * @property updatedAt when it last changed, UTC.
 */
data class AnnotationMetadata(
    val identifier: AnnotationIdentifier,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Something the user attached to a book (docs/02-data-formats.md §4). Immutable: a change is a
 * new value made with the same [AnnotationMetadata.identifier].
 */
sealed interface BookAnnotation {
    val metadata: AnnotationMetadata

    /** Where it starts: the highlighted text, or the position a note or bookmark points at. */
    val start: Anchor

    /** The user's text, never blank; `null` when there is none. */
    val note: String?

    /**
     * Highlighted text, optionally with a note.
     *
     * @property text copy of the highlighted text, for exporting.
     */
    @ConsistentCopyVisibility
    data class Highlight private constructor(
        override val metadata: AnnotationMetadata,
        val range: AnchorRange,
        val text: String,
        override val note: String?,
    ) : BookAnnotation {
        override val start: Anchor get() = range.start

        companion object {
            /** @return the highlight, or [DocumentModelError.BlankNote] for a blank [note]. */
            fun create(
                metadata: AnnotationMetadata,
                range: AnchorRange,
                text: String,
                note: String? = null,
            ): Result<Highlight> =
                if (note == null || note.isNotBlank()) {
                    Result.success(Highlight(metadata, range, text, note))
                } else {
                    Result.failure(DocumentModelError.BlankNote())
                }
        }
    }

    /** A note at a position, with no highlighted text. */
    @ConsistentCopyVisibility
    data class Note private constructor(
        override val metadata: AnnotationMetadata,
        override val start: Anchor,
        override val note: String,
    ) : BookAnnotation {
        companion object {
            /** @return the note, or [DocumentModelError.BlankNote] for a blank [note]. */
            fun create(
                metadata: AnnotationMetadata,
                start: Anchor,
                note: String,
            ): Result<Note> =
                if (note.isNotBlank()) {
                    Result.success(Note(metadata, start, note))
                } else {
                    Result.failure(DocumentModelError.BlankNote())
                }
        }
    }

    /** A marked position, with neither text nor note. */
    data class Bookmark(
        override val metadata: AnnotationMetadata,
        override val start: Anchor,
    ) : BookAnnotation {
        override val note: String? get() = null
    }
}
