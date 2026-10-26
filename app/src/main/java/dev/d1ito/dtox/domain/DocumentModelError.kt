package dev.d1ito.dtox.domain

/**
 * Why a document model value could not be created. Returned inside [Result.failure] by the
 * model factories, never thrown by them.
 */
sealed class DocumentModelError(
    message: String,
) : IllegalArgumentException(message) {
    /** The text is not `sha256:` followed by 32 lowercase hexadecimal characters. */
    class InvalidBookIdentifier(
        val rejectedValue: String,
    ) : DocumentModelError("Invalid book identifier: '$rejectedValue'")

    /** A heading level outside 1..3. */
    class HeadingLevelOutOfRange(
        val rejectedLevel: Int,
    ) : DocumentModelError("Heading level must be 1..3, was $rejectedLevel")

    /** A position, index or depth that must not be negative. */
    class NegativeValue(
        val fieldName: String,
        val rejectedValue: Int,
    ) : DocumentModelError("$fieldName must be >= 0, was $rejectedValue")

    /** A chapter whose index does not match its position in the book. */
    class ChapterIndexMismatch(
        val position: Int,
        val declaredIndex: Int,
    ) : DocumentModelError("Chapter at position $position declares index $declaredIndex")

    /** A range whose end comes before its start. */
    class RangeEndsBeforeStart(
        val start: Anchor,
        val end: Anchor,
    ) : DocumentModelError("Range end $end is before start $start")
}

/**
 * @param fields `name to value` pairs that must not be negative.
 * @return a failure for the first negative field, or `null` when all are valid.
 */
internal fun <T> failureForFirstNegative(vararg fields: Pair<String, Int>): Result<T>? =
    fields
        .firstOrNull { (_, value) -> value < 0 }
        ?.let { (name, value) -> Result.failure(DocumentModelError.NegativeValue(name, value)) }
