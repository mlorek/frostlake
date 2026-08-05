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

package dev.frostlake.executor.copy;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * XML staged-file reader (JDK DOM — no external dependency). The document is loaded into a VARIANT-shaped
 * {@link JsonNode}: one record per file, keyed by the root element name (Snowflake's default, which keeps
 * the outer element). Attributes become {@code @name} fields, repeated child elements become arrays, and a
 * text-only element becomes a scalar. External entities and DOCTYPEs are disabled (XXE hardening).
 */
public class XmlStageReader implements StageFileReader {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    @Override
    public List<JsonNode> readRecords(final Path file) throws IOException {
        final List<JsonNode> records = new ArrayList<>();
        try (final InputStream in = Files.newInputStream(file)) {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            final DocumentBuilder builder = factory.newDocumentBuilder();
            // The default handler prints "[Fatal Error] …" to stderr before throwing; parse errors
            // are reported through the exception alone.
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
            final Document doc = builder.parse(in);
            final Element root = doc.getDocumentElement();
            root.normalize();
            final ObjectNode record = NODES.objectNode();
            record.set(root.getNodeName(), elementToJson(root));
            records.add(record);
        } catch (final IOException io) {
            throw io;
        } catch (final Exception xmlError) {
            throw new RuntimeException("Invalid XML in file " + file.getFileName() + ": " + xmlError.getMessage(),
                xmlError);
        }
        return records;
    }

    @Override
    public String formatType() {
        return "XML";
    }

    /** Convert one element to JSON: attributes → {@code @attr}, child elements nested (repeats → arrays), text → value. */
    private JsonNode elementToJson(final Element element) {
        final ObjectNode object = NODES.objectNode();

        final NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            final Node attribute = attributes.item(i);
            object.put("@" + attribute.getNodeName(), attribute.getNodeValue());
        }

        final NodeList children = element.getChildNodes();
        final StringBuilder text = new StringBuilder();
        boolean hasElementChild = false;
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                hasElementChild = true;
                addChild(object, child.getNodeName(), elementToJson((Element) child));
            } else if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                text.append(child.getNodeValue());
            }
        }

        final String textValue = text.toString().trim();
        if (!hasElementChild && attributes.getLength() == 0) {
            return NODES.textNode(textValue);   // a leaf element is just its text
        }
        if (!textValue.isEmpty()) {
            object.put("#text", textValue);
        }
        return object;
    }

    /** Add a child under {@code name}, promoting to an array when the name repeats. */
    private void addChild(final ObjectNode object, final String name, final JsonNode value) {
        final JsonNode existing = object.get(name);
        if (existing == null) {
            object.set(name, value);
        } else if (existing instanceof ArrayNode) {
            ((ArrayNode) existing).add(value);
        } else {
            final ArrayNode array = NODES.arrayNode();
            array.add(existing);
            array.add(value);
            object.set(name, array);
        }
    }
}
