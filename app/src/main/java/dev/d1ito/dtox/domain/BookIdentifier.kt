package dev.d1ito.dtox.domain

private const val IDENTIFIER_PREFIX = "sha256:"
private const val DIGEST_HEX_LENGTH = 32
private const val HEX_CHARACTERS_PER_BYTE = 2
private val bookIdentifierPattern = Regex("^$IDENTIFIER_PREFIX[0-9a-f]{$DIGEST_HEX_LENGTH}$")

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

        /**
         * @param sha256Digest the full SHA-256 of the original file bytes (32 bytes); only its
         *   first 16 bytes are used, longer input is truncated by design.
         * @return the identifier built from its first 32 hex characters, or
         *   [DocumentModelError.InvalidBookIdentifier] if the digest is too short.
         */
        fun fromSha256Digest(sha256Digest: ByteArray): Result<BookIdentifier> {
            val hexPrefix =
                sha256Digest
                    .take(DIGEST_HEX_LENGTH / HEX_CHARACTERS_PER_BYTE)
                    .joinToString(separator = "") { byte -> "%02x".format(byte) }
            return parse(IDENTIFIER_PREFIX + hexPrefix)
        }
    }
}
