package dev.d1ito.dtox.data.importing

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

private const val ANY_NAMESPACE = "*"
private val whitespaceRun = Regex("\\s+")

/** Hardening the JVM parser understands; Android's parser rejects them but never loads them. */
private val hardeningFeatures =
    mapOf(
        XMLConstants.FEATURE_SECURE_PROCESSING to true,
        "http://xml.org/sax/features/external-general-entities" to false,
        "http://xml.org/sax/features/external-parameter-entities" to false,
        "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
    )

/**
 * Parses an XML document from an untrusted EPUB. External entities and DTDs are never fetched:
 * the entity resolver answers every request with an empty document, on the JVM and on Android.
 *
 * @return the namespace-aware DOM, or `null` if the bytes are not well-formed XML.
 * @throws IOException if [input] fails while reading.
 */
internal fun parsedXmlOrNull(input: InputStream): Document? {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    factory.isExpandEntityReferences = false
    hardeningFeatures.forEach { (feature, enabled) ->
        setFeatureIfSupported(factory, feature, enabled)
    }
    val builder = factory.newDocumentBuilder()
    builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
    builder.setErrorHandler(FailOnFatalErrorHandler)
    return try {
        builder.parse(input)
    } catch (expectedMalformedXml: SAXException) {
        null
    }
}

private fun setFeatureIfSupported(
    factory: DocumentBuilderFactory,
    feature: String,
    enabled: Boolean,
) {
    try {
        factory.setFeature(feature, enabled)
    } catch (expectedUnsupportedFeature: ParserConfigurationException) {
        // Android's parser has no such switch; the entity resolver still blocks external loads.
    }
}

/** Stops at the first fatal error without printing to stderr; recoverable ones are ignored. */
private object FailOnFatalErrorHandler : ErrorHandler {
    override fun warning(exception: SAXParseException) = Unit

    override fun error(exception: SAXParseException) = Unit

    override fun fatalError(exception: SAXParseException): Unit = throw exception
}

/** Descendant elements with this local name, whatever their namespace prefix. */
internal fun Element.descendants(localName: String): List<Element> {
    val nodes = getElementsByTagNameNS(ANY_NAMESPACE, localName)
    return (0 until nodes.length).mapNotNull { position -> nodes.item(position) as? Element }
}

/** Value of the first attribute with this local name (`role` matches `opf:role`), or `null`. */
internal fun Element.attributeByLocalName(localName: String): String? {
    val attributes = attributes
    return (0 until attributes.length)
        .map { position -> attributes.item(position) }
        .firstOrNull { attribute -> (attribute.localName ?: attribute.nodeName) == localName }
        ?.nodeValue
}

/** Text content with whitespace runs collapsed to one space; `null` when blank. */
internal fun Element.normalizedText(): String? =
    textContent.replace(whitespaceRun, " ").trim().ifEmpty { null }
