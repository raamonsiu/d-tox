package dev.d1ito.dtox.data.export

import dev.d1ito.dtox.data.storage.BookData
import dev.d1ito.dtox.data.storage.merged
import dev.d1ito.dtox.domain.AnnotationIdentifier
import dev.d1ito.dtox.domain.BookAnnotation
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant

private const val PROGRESS_KEY = "progress"
private const val ANNOTATIONS_KEY = "annotations"
private const val ANNOTATION_IDENTIFIER_KEY = "id"

/**
 * Merges the data of one book read from an export into the local data, without losing anything
 * more recent on this device (docs/02-data-formats.md §10). Pure.
 *
 * - No local data: [imported] as it is.
 * - Title, author and source: the local ones.
 * - Progress: the one with the later `updatedAt`; on a tie, the local one.
 * - Annotations: union by `id`; with the same `id`, the later `updatedAt`, on a tie the local one.
 * - Unknown fields: those of whichever side each value came from; top-level fields only the
 *   import has are added.
 *
 * @return the data to store for the book of [imported].
 */
fun mergedBookData(
    local: BookData?,
    imported: BookData,
): BookData {
    if (local == null) return imported
    val progressFromImport = isLater(imported.progress?.updatedAt, local.progress?.updatedAt)
    val localByIdentifier = local.annotations.associateBy(::identifierOf)
    val importedWinners =
        imported.annotations
            .filter { annotation ->
                val localAnnotation = localByIdentifier[identifierOf(annotation)]
                localAnnotation == null ||
                    isLater(annotation.metadata.updatedAt, localAnnotation.metadata.updatedAt)
            }.associateBy(::identifierOf)
    val annotations = mergedAnnotations(local.annotations, importedWinners)
    return local.copy(
        progress = if (progressFromImport) imported.progress else local.progress,
        annotations = annotations,
        preservedFields =
            mergedPreservedFields(local, imported, progressFromImport, importedWinners.keys),
    )
}

/**
 * [local] in its order, each replaced by its winning import, then the [importedWinners] that are
 * new to this device.
 */
private fun mergedAnnotations(
    local: List<BookAnnotation>,
    importedWinners: Map<AnnotationIdentifier, BookAnnotation>,
): List<BookAnnotation> {
    val localIdentifiers = local.map(::identifierOf).toSet()
    val replaced =
        local.map { annotation ->
            importedWinners[identifierOf(annotation)] ?: annotation
        }
    val added = importedWinners.filterKeys { identifier -> identifier !in localIdentifiers }.values
    return replaced + added
}

/** `true` when [candidate] exists and is strictly after [current] (or [current] is missing). */
private fun isLater(
    candidate: Instant?,
    current: Instant?,
): Boolean = candidate != null && (current == null || candidate > current)

private fun identifierOf(annotation: BookAnnotation): AnnotationIdentifier =
    annotation.metadata.identifier

/**
 * The JSON the merged data is written over: the local fields, plus top-level fields only the
 * import has, with the progress and each annotation taken from the side that won it.
 */
private fun mergedPreservedFields(
    local: BookData,
    imported: BookData,
    progressFromImport: Boolean,
    annotationsFromImport: Set<AnnotationIdentifier>,
): JsonObject {
    val base =
        merged(imported.preservedFields, local.preservedFields) - PROGRESS_KEY - ANNOTATIONS_KEY
    val progressSource = if (progressFromImport) imported else local
    val progress =
        progressSource.preservedFields[PROGRESS_KEY]?.let { value ->
            PROGRESS_KEY to
                value
        }
    val annotations =
        preservedAnnotations(local) { identifier -> identifier !in annotationsFromImport } +
            preservedAnnotations(imported) { identifier -> identifier in annotationsFromImport }
    return JsonObject(base + listOfNotNull(progress) + (ANNOTATIONS_KEY to JsonArray(annotations)))
}

/** The preserved annotation objects of [bookData] whose `id` is [accepted]. */
private fun preservedAnnotations(
    bookData: BookData,
    accepted: (AnnotationIdentifier) -> Boolean,
): List<JsonObject> =
    (bookData.preservedFields[ANNOTATIONS_KEY] as? JsonArray)
        .orEmpty()
        .filterIsInstance<JsonObject>()
        .filter { annotation ->
            val text = (annotation[ANNOTATION_IDENTIFIER_KEY] as? JsonPrimitive)?.contentOrNull
            val identifier = text?.let { value -> AnnotationIdentifier.parse(value).getOrNull() }
            identifier != null && accepted(identifier)
        }
