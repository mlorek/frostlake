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
import dev.frostlake.types.DecimalText;
import dev.frostlake.types.NumericType;
import java.math.BigDecimal;

/**
 * A value as a CLIENT reads it — the text a driver hands back from {@code getString}.
 *
 * <p>★ A FLOAT READS AS JAVA SPELLS ITS DOUBLE. On the results it asks for by default, the account's
 * driver hands a FLOAT back as {@code Double.toString} of the double: {@code 0.30000000000000004} for
 * {@code 0.1 + 0.2}, {@code 1.4142135623730951} for a square root, {@code 3.0} for a whole one,
 * {@code 1.0E18}, {@code -0.0} keeping its sign, {@code NaN} and {@code Infinity} (live-verified). The
 * SQL text of the same value, TO_VARCHAR's, keeps Snowflake's shorter width, {@code 0.3} and
 * {@code 1.414213562}; so does that driver on JSON results, which carry the server's text, not the double.
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
     * The object a client reads for a DECFLOAT: the BigDecimal of its text, as the account's driver builds it —
     * 1e40 is {@code 1.0000000000000000303786028427003666891E+40} and 1E-10 {@code 1E-10} (live-verified).
     *
     * @param value the DECFLOAT, not null
     * @return the decimal
     */
    public static BigDecimal decfloatObject(final Number value) {
        return DecimalText.parse(Decfloats.text(Decfloats.of(value, "TO_DECFLOAT")));
    }

    /**
     * The text a client reads for a FLOAT, by both transports: Java's {@code Double.toString} of its double,
     * as the account's driver spells it on its default results.
     *
     * @param value the double
     * @return its client text
     */
    public static String approximateText(final double value) {
        return Double.toString(value);
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
            return approximateText(((Number) value).doubleValue());
        }
        if (value instanceof Number && NumericType.isDecfloat(declared)) {
            // A DECFLOAT reads as its own text, scientific past 38 digits (live-verified through the driver).
            return Decfloats.text(Decfloats.of((Number) value, "TO_DECFLOAT"));
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
            if (SharedFunctionHelpers.isNativeTemporal(value)) {
                // A temporal held in a VARIANT is a JSON string of its variant text: "2024-01-15",
                // "20201-01-15" past 9999 and "-1-01-01" before year one (live-verified).
                return VariantJsonText.unwrappedStringText(SharedFunctionHelpers.variantTemporalText(value));
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
