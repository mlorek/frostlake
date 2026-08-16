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

import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import java.math.BigDecimal;

/**
 * A value as a CLIENT reads it — the text a driver hands back from {@code getString}.
 *
 * <p>★ AN APPROXIMATE VALUE IS SPELLED THE SAME WAY EVERYWHERE. TO_VARCHAR over a FLOAT already used
 * Snowflake's width; the driver path did not, and handed back Java's own {@code Double.toString}
 * instead — so the same value read two ways gave two answers, and neither the client nor the engine
 * could say which was the value's text. Live's driver returns the SQL text: {@code 1.414213562} for
 * a square root, {@code 1} for a whole one, {@code -0} keeping its sign, {@code inf} in lower case.
 *
 * <p>★ THE DECLARED TYPE DECIDES, not the carrier. A FLOAT column holds a double, but a FLOAT-declared
 * value can still arrive in an exact carrier — an expression the engine computed exactly, or a value
 * restored from an older snapshot — and asking the object what it is would then call it an exact number
 * and spell it the wrong way. Only the column's declared type separates the families.
 *
 * <p>★ A SEMI-STRUCTURED DOUBLE IS THE VARIANT'S TEXT. {@code TO_VARIANT(1.5::FLOAT)}, and a DOUBLE read
 * out of a VARIANT, read {@code 1.500000000000000e+00} where the same value in a FLOAT column reads
 * {@code 1.5} (see {@link VariantJsonText}).
 */
public final class ClientValueText {

    private ClientValueText() {
    }

    /**
     * The text a client sees for one value.
     *
     * @param value the value
     * @param declared the column's declared type, which is what tells the numeric families apart
     * @return its client text, or null for a null value
     */
    public static String render(final Object value, final DataType declared) {
        if (value instanceof Boolean) {
            // Only a BOOLEAN column prints the driver's upper-case spelling; a boolean inside a
            // VARIANT is that column's JSON text, lower-case like every SQL conversion.
            return declared instanceof BooleanType
                ? booleanText(((Boolean) value).booleanValue()) : value.toString();
        }
        if (value instanceof Number && NumericType.isApproximate(declared)) {
            return SharedFunctionHelpers.floatText(((Number) value).doubleValue());
        }
        // A semi-structured column hands back JSON text, as the account's driver does: a string in a
        // VARIANT keeps its quotes ("abc" for TO_VARIANT('abc')), whether it arrives whole or unwrapped by
        // a path. A container keeps the session's JSON_INDENT layout through the path below, and a cast
        // to VARCHAR is a text column, which reads bare.
        if (VariantJsonText.isSemiStructured(declared)) {
            if (value instanceof VariantValue && ((VariantValue) value).node().isTextual()) {
                return VariantJsonText.clientTextOf((VariantValue) value);
            }
            if (value instanceof String) {
                return VariantJsonText.unwrappedStringText((String) value);
            }
        }
        // A container already displays its DOUBLEs the account's way; the whole-value double and the
        // one read out unwrapped are the two spelled here.
        if (value instanceof VariantValue && ((VariantValue) value).node().isNumber()) {
            final String displayed = VariantJsonText.displayTextOf((VariantValue) value);
            if (displayed != null) {
                return displayed;
            }
        }
        final String unwrapped = VariantJsonText.unwrappedDoubleText(value, declared);
        if (unwrapped != null) {
            return unwrapped;
        }
        if (value instanceof BigDecimal) {
            // The digits in place, as the account's driver hands them over — never BigDecimal's own
            // scientific 1E-8 / 0E-20 for a small or zero value at a deep scale.
            return ((BigDecimal) value).toPlainString();
        }
        return TemporalText.render(value, declared);
    }

    /**
     * The text a driver prints for a BOOLEAN cell: the account's driver spells it upper-case, where
     * every SQL conversion of the same value to text — TO_VARCHAR, the VARCHAR cast, concatenation,
     * a VARIANT's JSON — spells it lower-case. Two surfaces, two spellings; this is the driver's.
     */
    public static String booleanText(final boolean value) {
        return value ? "TRUE" : "FALSE";
    }
}
