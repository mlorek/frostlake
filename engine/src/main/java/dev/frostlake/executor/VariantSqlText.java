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

package dev.frostlake.executor;

import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.TypedScalarNode;
import dev.frostlake.values.UuidTextNode;
import dev.frostlake.values.VariantJsonText;
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * A semi-structured value as the SQL that makes it again, for a call a block expression hands the value to.
 *
 * <p>The value's JSON read back by PARSE_JSON is the value whenever its JSON says everything about it; the text
 * spells every DOUBLE with an exponent, so that a number's family is read back too. A DATE, a TIME, a timestamp, a
 * BINARY or a UUID inside an array or an object is more than its JSON, though: read back from text it would be a
 * string, where the call must see what a query's own nested call would hand it — {@code TYPEOF(GET(ARRAY_CONSTRUCT(
 * '2024-01-02'::DATE), 0))} is {@code DATE} in a block as in a query (live-verified). Such a value is written as
 * the constructors of its containers around the typed literals of those members.
 */
public final class VariantSqlText {

    private VariantSqlText() {
    }

    /**
     * @param value the value
     * @return an SQL expression that answers the value
     */
    public static String of(final VariantValue value) {
        final JsonNode node = value.node();
        if (node == null || !holdsTypedText(node)) {
            return reread(value);
        }
        final StringBuilder out = new StringBuilder();
        append(node, out, true);
        return out.toString();
    }

    /** Whether a text of the tree remembers a type its JSON does not carry: a typed scalar or a UUID. */
    static boolean holdsTypedText(final JsonNode node) {
        if (node instanceof TypedScalarNode || node instanceof UuidTextNode) {
            return true;
        }
        if (node == null || !node.isContainer()) {
            return false;
        }
        for (final JsonNode member : node) {
            if (holdsTypedText(member)) {
                return true;
            }
        }
        return false;
    }

    private static String reread(final VariantValue value) {
        return "PARSE_JSON(" + SqlStringLiterals.encode(VariantJsonText.rereadableTextOf(value)) + ")";
    }

    private static void append(final JsonNode node, final StringBuilder out, final boolean whole) {
        if (node instanceof TypedScalarNode || node instanceof UuidTextNode) {
            // A member is the typed literal itself; the whole value is the VARIANT of it.
            out.append(whole ? "TO_VARIANT(" : "").append(typedLiteral(node)).append(whole ? ")" : "");
            return;
        }
        if (VariantUndefined.isUndefined(node)) {
            // A hole in an array is the SQL NULL its constructor was given.
            out.append("NULL");
            return;
        }
        if (!holdsTypedText(node)) {
            out.append(reread(VariantValue.ofNode(node)));
            return;
        }
        if (node.isArray()) {
            out.append("ARRAY_CONSTRUCT(");
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                append(node.get(i), out, false);
            }
            out.append(')');
            return;
        }
        out.append("OBJECT_CONSTRUCT(");
        boolean first = true;
        for (final Map.Entry<String, JsonNode> member : node.properties()) {
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append(SqlStringLiterals.encode(member.getKey())).append(", ");
            append(member.getValue(), out, false);
        }
        out.append(')');
    }

    private static String typedLiteral(final JsonNode node) {
        if (node instanceof UuidTextNode) {
            return SqlStringLiterals.encode(node.asString()) + "::UUID";
        }
        final Object typed = ((TypedScalarNode) node).typedValue();
        if (typed instanceof BinaryValue) {
            return "X'" + ((BinaryValue) typed).toHex() + "'";
        }
        final String temporal = SharedFunctionHelpers.temporalSqlLiteral(typed);
        return temporal != null ? temporal : SqlStringLiterals.encode(node.asString());
    }
}
