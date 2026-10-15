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
import dev.frostlake.types.VariantType;
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

/** GET_IGNORE_CASE(obj, key) — like GET but matches object keys case-insensitively. */
public class GetIgnoreCase extends BuiltInFunction {
    /** The digits a whole number may have and still be held in 64 bits. */
    private static final int WHOLE_DIGITS = 18;
    /** The magnitude below which a whole double is told as a whole number. */
    private static final double WHOLE_LIMIT = 1e18;

    public GetIgnoreCase() { super("GET_IGNORE_CASE", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        final String key = args.get(1).toString();
        final JsonNode node = ArrayFunctionHelper.parseNode(args.get(0));
        if (node == null || !node.isObject()) return null;
        // A key spelled exactly so is the one read; only without one does another case match (live-verified).
        final boolean exact = node.has(key);
        for (final String name : node.propertyNames()) {
            if (exact ? name.equals(key) : name.equalsIgnoreCase(key)) {
                final JsonNode v = node.get(name);
                if (v == null) return null;
                // A key that IS present but holds JSON null keeps the typed VARIANT NULL_VALUE; only a
                // missing key is SQL NULL (live:
                // TYPEOF(GET_IGNORE_CASE(PARSE_JSON('{"b":null}'),'B')) = 'NULL_VALUE').
                // An `undefined` element reads as SQL NULL, a JSON null as the NULL_VALUE variant.
                if (VariantUndefined.isUndefined(v)) return null;
                if (v.isNull()) return VariantValue.of("null");
                if (v.isTextual()) return v.asText();
                if (v.isNumber()) {
                    return numberOf(v);
                }
                if (v.isBoolean()) return v.asBoolean();
                return v.toString();
            }
        }
        return null;
    }

    /** A number as a VARIANT tells it: a whole number as one, however it is held — 2.0 is 2 — and no trailing zero. */
    private static Object numberOf(final JsonNode held) {
        if (held.isLong() || held.isInt()) {
            return Long.valueOf(held.asLong());
        }
        if (!held.isBigDecimal()) {
            final double approximate = held.asDouble();
            return approximate == Math.rint(approximate) && Math.abs(approximate) < WHOLE_LIMIT
                ? (Object) Long.valueOf((long) approximate) : (Object) Double.valueOf(approximate);
        }
        final BigDecimal exact = held.decimalValue().stripTrailingZeros();
        return exact.scale() <= 0 && exact.precision() - exact.scale() <= WHOLE_DIGITS
            ? (Object) Long.valueOf(exact.longValue()) : exact;
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
