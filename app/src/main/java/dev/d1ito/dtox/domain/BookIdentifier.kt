package dev.d1ito.dtox.domain

private val bookIdentifierPattern = Regex("^sha256:[0-9a-f]{32}$")

/**
 * Identity of a book: `sha256:` plus the first 32 hex characters of the SHA-256 of the original
 * file bytes (docs/02-data-formats.md §2). Independent of the file path and name, so identical
 * copies share their reading data.
 *
 * @property value the canonical text form, e.g. `sha256:3f2a…`.
 */
@JvmInline
value class BookIdentifier private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        /**
         * @param value text in the canonical form, lowercase hexadecimal.
         * @return the identifier, or [DocumentModelError.InvalidBookIdentifier] if malformed.
         */
        fun parse(value: String): Result<BookIdentifier> =
            if (bookIdentifierPattern.matches(value)) {
                Result.success(BookIdentifier(value))
            } else {
                Result.failure(DocumentModelError.InvalidBookIdentifier(value))
            }
    }
}
