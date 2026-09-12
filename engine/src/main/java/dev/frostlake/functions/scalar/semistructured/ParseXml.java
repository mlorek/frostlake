/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.functions.scalar.semistructured;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.ObjectType;
import dev.frostlake.values.VariantValue;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * PARSE_XML(text [, disable_auto_convert]) — parses an XML document into Snowflake's XML-in-VARIANT
 * model (see {@link dev.frostlake.values.XmlVariants}): {@code {"$": content, "@": tag,
 * "@attr": value, "childname": firstContentIndex}} with object keys sorted. Element text and
 * attribute values are auto-converted to numbers unless the second argument is TRUE, following
 * Snowflake's live-verified rules: plain integers and decimals convert (leading zeros, a leading
 * {@code +}, or a bare leading dot keep the text), exponent forms become DOUBLE, and whitespace is
 * trimmed. An empty or all-whitespace input yields SQL NULL; malformed XML raises
 * {@code Error parsing XML: …}.
 */
public class ParseXml extends BuiltInFunction {

    private static final Pattern INTEGER_TEXT = Pattern.compile("-?(0|[1-9][0-9]*)");
    private static final Pattern DECIMAL_TEXT = Pattern.compile("-?(0|[1-9][0-9]*)\\.[0-9]+");
    private static final Pattern EXPONENT_TEXT = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?[eE][+-]?[0-9]+");

    public ParseXml() { super("PARSE_XML", ObjectType.OBJECT); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final String xml = args.get(0).toString();
        final boolean disableAutoConvert = args.size() > 1 && SharedFunctionHelpers.isTruthy(args.get(1));
        final JsonNode model;
        try {
            model = parseDocument(xml, disableAutoConvert);
        } catch (final IllegalArgumentException e) {
            throw new RuntimeException("Error parsing XML: " + e.getMessage());
        }
        return model == null ? null : VariantValue.ofNode(model);
    }

    /**
     * Parses XML text to the variant model; returns null for an empty/whitespace-only input and
     * throws {@link IllegalArgumentException} with the bare parser message on malformed XML.
     */
    public static JsonNode parseDocument(final String xml, final boolean disableAutoConvert) {
        String text = xml;
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        if (text.trim().isEmpty()) {
            return null;
        }
        // ★ The FAULT is decided before any parse is attempted, because the Java parser's own message
        // shares nothing with live's — and because it refuses documents live ACCEPTS. Only once the
        // scanner is satisfied is the text handed on, with its unquoted attribute values quoted so
        // the parser will take them.
        final String fault = XmlFaultReader.faultOf(text);
        if (fault != null) {
            throw new IllegalArgumentException(fault);
        }
        text = XmlAttributeQuoting.quoted(text);
        final Document document;
        try {
            final DocumentBuilder builder = newDocumentBuilder();
            document = builder.parse(new InputSource(new StringReader(text)));
        } catch (final Exception e) {
            throw new IllegalArgumentException(terseMessage(e));
        }
        document.normalize();
        return buildElement(document.getDocumentElement(), disableAutoConvert);
    }

    private static DocumentBuilder newDocumentBuilder() throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setCoalescing(true);
        factory.setIgnoringComments(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(true);
        // Internal DTD subsets are accepted (as in Snowflake) but nothing external is ever fetched.
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        final DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(final SAXParseException exception) {
            }

            @Override
            public void error(final SAXParseException exception) throws SAXParseException {
                throw exception;
            }

            @Override
            public void fatalError(final SAXParseException exception) throws SAXParseException {
                throw exception;
            }
        });
        return builder;
    }

    private static String terseMessage(final Exception e) {
        if (e instanceof SAXParseException) {
            final SAXParseException sax = (SAXParseException) e;
            return sax.getMessage() + ", pos " + Math.max(sax.getColumnNumber(), 1);
        }
        return String.valueOf(e.getMessage());
    }

    private static ObjectNode buildElement(final Element element, final boolean disableAutoConvert) {
        // Content pieces in document order: trimmed non-empty text runs and child elements.
        final List<JsonNode> pieces = new ArrayList<JsonNode>();
        // First-occurrence content index per child element name (part of Snowflake's model).
        final Map<String, Integer> childIndexes = new LinkedHashMap<String, Integer>();
        final NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                final Element childElement = (Element) child;
                if (!childIndexes.containsKey(childElement.getTagName())) {
                    childIndexes.put(childElement.getTagName(), pieces.size());
                }
                pieces.add(buildElement(childElement, disableAutoConvert));
            } else if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                final String piece = child.getNodeValue().trim();
                if (!piece.isEmpty()) {
                    pieces.add(convertText(piece, disableAutoConvert));
                }
            }
        }
        // Assemble with sorted keys ("$" < "@" < "@attr" < child names) so the canonical JSON text
        // matches Snowflake's TO_JSON output ordering.
        final Map<String, JsonNode> members = new TreeMap<String, JsonNode>();
        members.put("$", content(pieces));
        members.put("@", ArrayFunctionHelper.MAPPER.getNodeFactory().textNode(element.getTagName()));
        final NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            final Attr attribute = (Attr) attributes.item(i);
            members.put("@" + attribute.getName(), convertText(attribute.getValue(), disableAutoConvert));
        }
        for (final Map.Entry<String, Integer> childIndex : childIndexes.entrySet()) {
            members.put(childIndex.getKey(),
                ArrayFunctionHelper.MAPPER.getNodeFactory().numberNode(childIndex.getValue().intValue()));
        }
        final ObjectNode node = ArrayFunctionHelper.MAPPER.createObjectNode();
        for (final Map.Entry<String, JsonNode> member : members.entrySet()) {
            node.set(member.getKey(), member.getValue());
        }
        return node;
    }

    private static JsonNode content(final List<JsonNode> pieces) {
        if (pieces.isEmpty()) {
            return ArrayFunctionHelper.MAPPER.getNodeFactory().textNode("");
        }
        if (pieces.size() == 1) {
            return pieces.get(0);
        }
        final ArrayNode array = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (final JsonNode piece : pieces) {
            array.add(piece);
        }
        return array;
    }

    private static JsonNode convertText(final String text, final boolean disableAutoConvert) {
        if (!disableAutoConvert) {
            if (INTEGER_TEXT.matcher(text).matches()) {
                try {
                    return ArrayFunctionHelper.MAPPER.getNodeFactory().numberNode(Long.parseLong(text));
                } catch (final NumberFormatException overflow) {
                    return ArrayFunctionHelper.MAPPER.getNodeFactory().numberNode(new BigDecimal(text).toBigInteger());
                }
            }
            if (DECIMAL_TEXT.matcher(text).matches()) {
                return ArrayFunctionHelper.MAPPER.getNodeFactory().numberNode(new BigDecimal(text));
            }
            if (EXPONENT_TEXT.matcher(text).matches()) {
                return ArrayFunctionHelper.MAPPER.getNodeFactory().numberNode(Double.parseDouble(text));
            }
            // Booleans ARE sniffed (live re-probed: TO_JSON(PARSE_XML('<a>true</a>')) is {"$":true,...}).
            if (text.equals("true") || text.equals("false")) {
                return ArrayFunctionHelper.MAPPER.getNodeFactory().booleanNode(text.equals("true"));
            }
        }
        return ArrayFunctionHelper.MAPPER.getNodeFactory().textNode(text);
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
