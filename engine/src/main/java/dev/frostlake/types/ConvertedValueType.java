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

package dev.frostlake.types;

import java.util.Locale;

/**
 * The static type a conversion to a declared type produces, which is not always the declared type.
 *
 * <p>Two conversions type as something other than what was written, and both are the source's doing:</p>
 *
 * <ul>
 *   <li>a BOOLEAN into an exact number is {@code NUMBER(2,0)} whatever width the target spells —
 *       {@code TRUE::NUMBER(5,1)} is {@code NUMBER(2,0)} and its value prints {@code 1}, not
 *       {@code 1.0};</li>
 *   <li>an exact number into a TIMESTAMP flavour declares the NUMBER's own scale as its precision, the
 *       written one ignored — {@code 5::TIMESTAMP_NTZ} is {@code TIMESTAMP_NTZ(0)} while
 *       {@code '2024-01-01'::TIMESTAMP_NTZ}, whose source is a text, keeps the default nine.</li>
 * </ul>
 *
 * <p>Everything else keeps the declared type. The same rule governs a {@code ::} cast in a query and the
 * conversion a typed scripting declaration or assignment applies, which is why both ask here.</p>
 */
public final class ConvertedValueType {

    private ConvertedValueType() {
    }

    /**
     * The type a conversion of {@code source} to {@code declared} produces.
     *
     * @param source   the value's own static type, or null when it is not known
     * @param declared the type written on the cast or the declaration
     * @return the conversion's type, which is {@code declared} unless a rule above applies
     */
    public static DataType of(final DataType source, final DataType declared) {
        if (source == null || declared == null) {
            return declared;
        }
        if (declared instanceof DateTimeType && isTimestampFlavour(declared)
                && source instanceof NumericType && !NumericType.isApproximate(source)) {
            return new DateTimeType(declared.getName(), epochPrecision((NumericType) source),
                ((DateTimeType) declared).hasTimeZone());
        }
        if (declared instanceof NumericType && !NumericType.isApproximate(declared)
                && source instanceof BooleanType) {
            return new NumericType("NUMBER", 2, 0);
        }
        return declared;
    }

    /** Whether a type is one of the TIMESTAMP flavours, which all carry the word. */
    private static boolean isTimestampFlavour(final DataType type) {
        return type.getName() != null && type.getName().toUpperCase(Locale.ROOT).contains("TIMESTAMP");
    }

    /** A timestamp built from an epoch number declares the number's scale, capped at nine. */
    private static int epochPrecision(final NumericType source) {
        return Math.min(9, Math.max(0, source.getScale()));
    }
}
