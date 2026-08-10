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

package dev.frostlake.values;

import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.TreeMap;

/**
 * Snowflake's XML-in-VARIANT model. An XML element is an ordinary variant OBJECT with the shape
 * {@code {"$": content, "@": "tagname", "@attr": value, "childname": index}} — the classification is
 * STRUCTURAL (live-verified: {@code PARSE_JSON('{"$":1,"@":"b"}')} reports TYPEOF XML and renders as
 * {@code <b>1</b>}), so no separate runtime type exists: any object carrying both a {@code "$"}
 * member and a textual {@code "@"} member is XML. Content is a scalar (single text), a single child
 * element object, or an array of text/element pieces; attributes are {@code "@name"} members; each
 * distinct child element name also appears as a member holding the content index of its first
 * occurrence.
 *
 * <p>This class renders that model back to XML text in Snowflake's compact form (the
 * {@code TO_XML} / {@code ::VARCHAR} rendering: no indentation, attributes sorted by name,
 * {@code <a></a>} for empty elements, {@code &amp;}/{@code &lt;}/{@code &gt;} escaped).
 */
public final class XmlVariants {

    private XmlVariants() {
    }

    /** Whether the node is an XML element in Snowflake's structural sense. */
    public static boolean isXmlElement(final JsonNode node) {
        return node != null
            && node.isObject()
            && node.has("$")
            && node.has("@")
            && node.get("@").isTextual();
    }

    /**
     * Fast pre-check for canonical variant TEXT before parsing: every XML element's canonical JSON
     * starts with <code>{"$":</code> because object keys sort and {@code $} precedes every other key.
     */
    public static boolean mightBeXmlText(final String canonicalJson) {
        return canonicalJson.startsWith("{\"$\":");
    }

    /** The compact XML text of an element node (the TO_XML rendering). */
    public static String compactXml(final JsonNode element) {
        final StringBuilder out = new StringBuilder();
        renderElement(element, out);
        return out.toString();
    }

    /**
     * The DISPLAY text of an XML variant — Snowflake pretty-prints: an element whose content holds
     * child ELEMENTS puts each child on its own line at +2 spaces and the closing tag back at the
     * parent's indent (live: {@code <r a="1">\n  <b>2</b>\n</r>}); text-only elements stay inline.
     */
    public static String prettyXml(final JsonNode element) {
        final StringBuilder out = new StringBuilder();
        renderPretty(element, out, 0);
        return out.toString();
    }

    private static void renderPretty(final JsonNode element, final StringBuilder out, final int indent) {
        if (!hasElementChildren(element.get("$"))) {
            renderElement(element, out);
            return;
        }
        final String tag = element.get("@").asText();
        out.append('<').append(tag);
        final Map<String, JsonNode> attributes = new TreeMap<String, JsonNode>();
        for (final Map.Entry<String, JsonNode> member : element.properties()) {
            final String key = member.getKey();
            if (key.length() > 1 && key.charAt(0) == '@') {
                attributes.put(key.substring(1), member.getValue());
            }
        }
        for (final Map.Entry<String, JsonNode> attribute : attributes.entrySet()) {
            out.append(' ').append(attribute.getKey()).append("=\"");
            escapeAttribute(scalarText(attribute.getValue()), out);
            out.append('"');
        }
        out.append('>');
        appendPrettyContent(element.get("$"), out, indent + 2);
        out.append('\n');
        for (int i = 0; i < indent; i++) {
            out.append(' ');
        }
        out.append("</").append(tag).append('>');
    }

    private static void appendPrettyContent(final JsonNode content, final StringBuilder out, final int indent) {
        if (content == null || content.isNull()) {
            return;
        }
        if (content.isArray()) {
            for (final JsonNode piece : content) {
                appendPrettyContent(piece, out, indent);
            }
            return;
        }
        out.append('\n');
        for (int i = 0; i < indent; i++) {
            out.append(' ');
        }
        if (isXmlElement(content)) {
            renderPretty(content, out, indent);
            return;
        }
        final String text = scalarText(content);
        if (!text.isEmpty()) {
            escapeText(text, out);
        }
    }

    private static boolean hasElementChildren(final JsonNode content) {
        if (content == null || content.isNull()) {
            return false;
        }
        if (isXmlElement(content)) {
            return true;
        }
        if (content.isArray()) {
            for (final JsonNode piece : content) {
                if (isXmlElement(piece)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void renderElement(final JsonNode element, final StringBuilder out) {
        final String tag = element.get("@").asText();
        out.append('<').append(tag);
        final Map<String, JsonNode> attributes = new TreeMap<String, JsonNode>();
        for (final Map.Entry<String, JsonNode> member : element.properties()) {
            final String key = member.getKey();
            if (key.length() > 1 && key.charAt(0) == '@') {
                attributes.put(key.substring(1), member.getValue());
            }
        }
        for (final Map.Entry<String, JsonNode> attribute : attributes.entrySet()) {
            out.append(' ').append(attribute.getKey()).append("=\"");
            escapeAttribute(scalarText(attribute.getValue()), out);
            out.append('"');
        }
        out.append('>');
        renderContent(element.get("$"), out);
        out.append("</").append(tag).append('>');
    }

    private static void renderContent(final JsonNode content, final StringBuilder out) {
        if (content == null || content.isNull()) {
            return;
        }
        if (content.isArray()) {
            for (final JsonNode piece : content) {
                renderContent(piece, out);
            }
            return;
        }
        if (isXmlElement(content)) {
            renderElement(content, out);
            return;
        }
        final String text = scalarText(content);
        if (!text.isEmpty()) {
            escapeText(text, out);
        }
    }

    private static String scalarText(final JsonNode node) {
        return node.isTextual() ? node.asText() : node.toString();
    }

    /** Escapes element text content: {@code & < >}. */
    public static void escapeText(final String text, final StringBuilder out) {
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '&') {
                out.append("&amp;");
            } else if (c == '<') {
                out.append("&lt;");
            } else if (c == '>') {
                out.append("&gt;");
            } else {
                out.append(c);
            }
        }
    }

    /** Escapes attribute values: {@code & < > "}. */
    public static void escapeAttribute(final String text, final StringBuilder out) {
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '&') {
                out.append("&amp;");
            } else if (c == '<') {
                out.append("&lt;");
            } else if (c == '>') {
                out.append("&gt;");
            } else if (c == '"') {
                out.append("&quot;");
            } else {
                out.append(c);
            }
        }
    }
}
