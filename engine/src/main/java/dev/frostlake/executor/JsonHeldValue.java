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

import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.JsonTypeHelper;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantJsonText;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.StringNode;

/**
 * What a semi-structured scripting variable holds of a value: the value as its JSON carries it.
 *
 * <p>A VARIANT, ARRAY or OBJECT variable — a DECLARE default, an assignment, a typed LET, a SELECT INTO or
 * FETCH target, a SQL body's parameter, a field of a loop's record, a value returned under a semi-structured
 * RETURNS — keeps the JSON of what it is given, read back: {@code PARSE_JSON(TO_JSON(value))}. A DATE, a TIME,
 * a timestamp, a BINARY and a UUID in it, the whole value or a member of an array or an object, are therefore
 * the text they print as, and TYPEOF answers VARCHAR for each where the same value read straight from a query
 * keeps its type:
 *
 * <pre>
 *   SELECT TYPEOF(TO_VARIANT('2024-01-02'::DATE))                      DATE
 *   v := TO_VARIANT('2024-01-02'::DATE); RETURN TYPEOF(v);             VARCHAR
 * </pre>
 *
 * <p>A number keeps its family, since its JSON spells it — a DOUBLE stays a DOUBLE, a DECIMAL a DECIMAL and an
 * INTEGER an INTEGER — but a DOUBLE is the number its sixteen digits of JSON say, no longer the one it was:
 * {@code 0.1::FLOAT + 0.2::FLOAT} held so equals {@code 0.3::FLOAT}, and {@code 3::FLOAT} reads back as the
 * text {@code 3} where the value itself reads {@code 3.0} (live-verified).
 */
public final class JsonHeldValue {

    private JsonHeldValue() {
    }

    /**
     * The value as a semi-structured variable holds it.
     *
     * @param value the value being stored
     * @return the value read back from its JSON
     */
    public static Object of(final Object value) {
        if (SharedFunctionHelpers.isNativeTemporal(value)) {
            return VariantValue.ofNode(StringNode.valueOf(SharedFunctionHelpers.variantTemporalText(value)));
        }
        if (value instanceof BinaryValue) {
            return VariantValue.ofNode(StringNode.valueOf(((BinaryValue) value).toHex()));
        }
        if (!(value instanceof VariantValue)) {
            return value;
        }
        final VariantValue variant = (VariantValue) value;
        // The conversion text is there exactly when a DOUBLE is: the one thing whose JSON differs from the
        // canonical text. A value with none of these in it reads back as itself.
        final String converted = VariantJsonText.convertedTextOf(variant);
        if (converted == null && !VariantSqlText.holdsTypedText(variant.node())) {
            return value;
        }
        final JsonNode reread = JsonTypeHelper.parseLenient(converted != null ? converted : variant.text());
        return reread == null ? value : ArrayFunctionHelper.toCanonicalVariant(reread);
    }
}
