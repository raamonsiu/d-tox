package dev.d1ito.dtox.data.storage

import java.io.IOException

/**
 * In-memory [DocumentTree] that behaves like a SAF provider: a taken name gets a ` (1)` suffix on
 * creation and rename instead of replacing, and every change advances the modification time.
 *
 * @property supportsRename whether its files can be renamed.
 */
class FakeDocumentTree(
    var supportsRename: Boolean = true,
) : DocumentTree {
    private class Node(
        var name: String,
        val parentIdentifier: String?,
        val isDirectory: Boolean,
        var content: ByteArray = ByteArray(0),
        var lastModifiedMillis: Long = 0,
    )

    private val nodes = mutableMapOf<String, Node>(ROOT to Node("root", null, isDirectory = true))
    private var nextIdentifier = 0

    /** Modification times follow the clock, comparable with those of real files. */
    private var time = System.currentTimeMillis()

    /** When set, writing a file whose name ends with this fails. */
    var failWritesOfNamesEndingWith: String? = null

    /** When `true`, every call fails as after the access was revoked. */
    var revoked = false

    override val rootIdentifier: String = ROOT

    override fun children(directoryIdentifier: String): List<TreeDocument> {
        checkAccess()
        return nodes
            .filterValues { node -> node.parentIdentifier == directoryIdentifier }
            .map { (identifier, node) -> describe(identifier, node) }
    }

    override fun createDirectory(
        parentIdentifier: String,
        name: String,
    ): TreeDocument = create(parentIdentifier, name, isDirectory = true)

    override fun createFile(
        parentIdentifier: String,
        name: String,
    ): TreeDocument = create(parentIdentifier, name, isDirectory = false)

    override fun readBytes(documentIdentifier: String): ByteArray = node(documentIdentifier).content

    override fun writeBytes(
        documentIdentifier: String,
        content: ByteArray,
    ) {
        val node = node(documentIdentifier)
        failWritesOfNamesEndingWith?.let { suffix ->
            if (node.name.endsWith(suffix)) throw IOException("Injected write failure")
        }
        node.content = content.copyOf()
        node.lastModifiedMillis = ++time
    }

    override fun rename(
        documentIdentifier: String,
        newName: String,
    ): TreeDocument {
        val node = node(documentIdentifier)
        if (!supportsRename) throw IOException("Rename not supported")
        node.name = uniqueName(node.parentIdentifier, newName)
        return describe(documentIdentifier, node)
    }

    override fun delete(documentIdentifier: String) {
        node(documentIdentifier)
        nodes.remove(documentIdentifier)
    }

    /** Puts a file with [content] at [path] (e.g. `books/a.json`), creating its folders. */
    fun put(
        path: String,
        content: String,
        lastModifiedMillis: Long = ++time,
    ) {
        val parts = path.split('/')
        var parent = ROOT
        parts.dropLast(1).forEach { folder ->
            parent = identifierOf(parent, folder) ?: create(parent, folder, true).documentIdentifier
        }
        val identifier =
            identifierOf(parent, parts.last()) ?: create(parent, parts.last(), false)
                .documentIdentifier
        val node = node(identifier)
        node.content = content.toByteArray()
        node.lastModifiedMillis = lastModifiedMillis
    }

    /** @return the content of the file at [path], or `null` when there is none. */
    fun textAt(path: String): String? {
        var parent = ROOT
        path.split('/').forEach { part -> parent = identifierOf(parent, part) ?: return null }
        return node(parent).content.toString(Charsets.UTF_8)
    }

    /** @return the names of the files in [folder], sorted. */
    fun namesIn(folder: String): List<String> {
        val folderIdentifier = identifierOf(ROOT, folder) ?: return emptyList()
        return children(folderIdentifier).map { document -> document.name }.sorted()
    }

    private fun create(
        parentIdentifier: String,
        name: String,
        isDirectory: Boolean,
    ): TreeDocument {
        node(parentIdentifier)
        val identifier = "document-${nextIdentifier++}"
        val node =
            Node(uniqueName(parentIdentifier, name), parentIdentifier, isDirectory)
                .apply { lastModifiedMillis = ++time }
        nodes[identifier] = node
        return describe(identifier, node)
    }

    private fun uniqueName(
        parentIdentifier: String?,
        name: String,
    ): String {
        val taken = nodes.values.filter { node -> node.parentIdentifier == parentIdentifier }
        return if (taken.none { node -> node.name == name }) name else "$name (1)"
    }

    private fun identifierOf(
        parentIdentifier: String,
        name: String,
    ): String? =
        nodes.entries
            .firstOrNull { (_, node) ->
                node.parentIdentifier == parentIdentifier &&
                    node.name == name
            }?.key

    private fun node(identifier: String): Node {
        checkAccess()
        return nodes[identifier] ?: throw IOException("No document $identifier")
    }

    private fun checkAccess() {
        if (revoked) throw IOException("Access revoked")
    }

    private fun describe(
        identifier: String,
        node: Node,
    ) = TreeDocument(
        identifier,
        node.name,
        node.isDirectory,
        node.lastModifiedMillis,
        supportsRename,
    )

    private companion object {
        const val ROOT = "root"
    }
}
