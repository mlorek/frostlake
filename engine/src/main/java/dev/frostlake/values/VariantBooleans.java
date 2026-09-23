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

import java.util.Locale;

/**
 * A VARIANT converted to BOOLEAN — by {@code ::BOOLEAN}, CAST or TO_BOOLEAN — on the account's terms: it
 * converts only when it holds a boolean. A JSON boolean is itself, a JSON null (or an absent value) is
 * NULL, and a JSON string is read the way TO_BOOLEAN reads text ({@code 'yes'}, {@code 'F'}, {@code '1'},
 * trimmed, any case). Every other value fails the row with {@code Failed to cast variant value <text> to
 * BOOLEAN}, a number included — though a SQL NUMBER converts, a variant one does not — and so does a string
 * that is no boolean spelling, quoted as the variant holds it; a DOUBLE is spelled in the fifteen-decimal form
 * a client reads, {@code 1.500000000000000e+00} (all live-verified).
 */
public final class VariantBooleans {

    private VariantBooleans() {
    }

    /**
     * @param value the variant
     * @return its boolean, or null for a JSON null
     */
    public static Boolean convert(final VariantValue value) {
        final JsonNode node = value.node();
        if (node == null || node.isNull() || VariantUndefined.isUndefined(node)) {
            return null;
        }
        if (node.isBoolean()) {
            return Boolean.valueOf(node.booleanValue());
        }
        if (node.isTextual()) {
            final String text = node.asText().trim().toLowerCase(Locale.ROOT);
            if (text.equals("true") || text.equals("t") || text.equals("yes") || text.equals("y")
                    || text.equals("on") || text.equals("1")) {
                return Boolean.TRUE;
            }
            if (text.equals("false") || text.equals("f") || text.equals("no") || text.equals("n")
                    || text.equals("off") || text.equals("0")) {
                return Boolean.FALSE;
            }
        }
        throw new RuntimeException("Failed to cast variant value " + VariantJsonText.clientTextOf(value)
            + " to BOOLEAN");
    }
}
