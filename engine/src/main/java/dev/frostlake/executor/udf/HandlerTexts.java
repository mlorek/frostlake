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
package dev.frostlake.executor.udf;

import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.TypedScalarNode;
import dev.frostlake.values.VariantJsonText;
import dev.frostlake.values.VariantText;
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import dev.frostlake.values.XmlVariants;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;

/**
 * The texts a Java or a Scala handler's {@code String} parameters take for semi-structured values, a function's and
 * a procedure's alike (live-verified).
 * <ul>
 *   <li>A VARIANT, an ARRAY or an OBJECT handed to a {@code String} is its JSON as TO_JSON converts it: a string
 *       quoted, {@code "x"}, a DATE held in it too, {@code "2024-01-02"}, every DOUBLE in the fifteen-decimal form,
 *       {@code 1.000000000000000e+00} - but a non-finite DOUBLE, which is quoted into a string, {@code "NaN"},
 *       {@code "Infinity"}.</li>
 *   <li>An ARRAY's element handed to a {@code String[]}, and an OBJECT's member to a {@code Map<String, String>},
 *       is a text's content, a DOUBLE as a FLOAT prints ({@code 1}, {@code 1e+20}, {@code inf}), a BINARY's bytes
 *       read as UTF-8 text (a byte that spells no character is the replacement character), a container's JSON as
 *       TO_JSON converts it, and a JSON null or a SQL NULL is {@code null}.</li>
 * </ul>
 */
public final class HandlerTexts {

    private HandlerTexts() {
    }

    /**
     * A semi-structured argument as a {@code String} parameter takes it.
     *
     * @param value the argument, as its parameter's SQL type carries it
     * @return its JSON text
     */
    public static String json(final Object value) {
        if (value instanceof VariantValue) {
            final JsonNode node = ((VariantValue) value).node();
            return node == null || XmlVariants.isXmlElement(node) ? ((VariantValue) value).text()
                : VariantJsonText.handlerText(node);
        }
        if (value instanceof String) {
            // A semi-structured value carried as its JSON text; one that is none stays the text it is.
            final JsonNode node = ArrayFunctionHelper.parseNode(value);
            return node == null ? (String) value : VariantJsonText.handlerText(node);
        }
        return VariantJsonText.handlerText(ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value));
    }

    /**
     * An ARRAY's element or an OBJECT's member as a {@code String[]}'s element or a {@code Map}'s value takes it.
     *
     * @param member the element or the member
     * @return its text, or null for a JSON null and a SQL NULL
     */
    public static String member(final JsonNode member) {
        if (member == null || member.isNull()) {
            return null;
        }
        final Object typed = TypedScalarNode.typedValueOf(member);
        if (typed instanceof BinaryValue) {
            return new String(((BinaryValue) typed).bytes(), StandardCharsets.UTF_8);
        }
        if (member.isString()) {
            return member.stringValue();
        }
        if (member.isFloatingPointNumber() && !member.isBigDecimal()) {
            return SharedFunctionHelpers.floatText(member.doubleValue());
        }
        if (member.isContainer()) {
            return VariantJsonText.render(VariantUndefined.withNulls(member));
        }
        return VariantText.canonical(member);
    }
}
