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

import dev.frostlake.functions.StructuredArgumentFunction;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.StringType;
import dev.frostlake.values.XmlVariants;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * TO_XML(variant) — the XML text of a semi-structured value. An XML-shaped variant (see
 * {@link XmlVariants}) renders back to compact XML; every other variant renders in Snowflake's
 * generic scheme (live-verified): a {@code <SnowflakeData type="...">} wrapper whose object members
 * become elements named by their key, array elements become {@code <e>}, each carrying a
 * {@code type} attribute with the value's TYPEOF name, and a JSON null renders self-closing with
 * {@code xsi:nil="true"}. A VARCHAR argument is rejected at compile time, as in Snowflake.
 */
public class ToXml extends StructuredArgumentFunction {

    public ToXml() { super("TO_XML", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object value = args.get(0);
        if (value == null) return null;
        final JsonNode node = ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value);
        if (node == null) return null;
        if (XmlVariants.isXmlElement(node)) {
            return XmlVariants.compactXml(node);
        }
        final StringBuilder out = new StringBuilder();
        renderEntry("SnowflakeData", node, out);
        return out.toString();
    }

    private static void renderEntry(final String name, final JsonNode node, final StringBuilder out) {
        if (node.isNull()) {
            out.append('<').append(name).append(" type=\"NULL_VALUE\" xsi:nil=\"true\"/>");
            return;
        }
        if (XmlVariants.isXmlElement(node)) {
            out.append('<').append(name).append(" type=\"XML\">");
            out.append(XmlVariants.compactXml(node));
            out.append("</").append(name).append('>');
            return;
        }
        if (node.isObject()) {
            out.append('<').append(name).append(" type=\"OBJECT\">");
            for (final Map.Entry<String, JsonNode> member : node.properties()) {
                renderEntry(member.getKey(), member.getValue(), out);
            }
            out.append("</").append(name).append('>');
            return;
        }
        if (node.isArray()) {
            out.append('<').append(name).append(" type=\"ARRAY\">");
            for (final JsonNode element : node) {
                renderEntry("e", element, out);
            }
            out.append("</").append(name).append('>');
            return;
        }
        out.append('<').append(name).append(" type=\"").append(scalarTypeName(node)).append("\">");
        XmlVariants.escapeText(node.isTextual() ? node.asText() : node.toString(), out);
        out.append("</").append(name).append('>');
    }

    /** The TYPEOF name of a scalar variant member (mirrors {@link TypeOf}'s variant branch). */
    private static String scalarTypeName(final JsonNode node) {
        if (node.isBoolean()) return "BOOLEAN";
        if (node.isIntegralNumber()) return "INTEGER";
        if (node.isBigDecimal()) return "DECIMAL";
        if (node.isFloatingPointNumber()) return "DOUBLE";
        return "VARCHAR";
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }
}
