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

import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.UuidType;
import dev.frostlake.values.ValueRange;


/**
 * What {@code SYSTEM$TYPEOF} answers: a DECLARED type name and a physical storage tag, and the two come
 * from different places — which is the whole point of this class.
 *
 * <pre>
 *   SYSTEM$TYPEOF(&lt;NUMBER(10,2) holding 1.00&gt;)      NUMBER(10,2)[SB1]
 *   SYSTEM$TYPEOF(&lt;NUMBER(10,2) holding 1000.00&gt;)   NUMBER(10,2)[SB4]
 *   SYSTEM$TYPEOF(&lt;VARCHAR(5)&gt;)                      VARCHAR(5)[LOB]
 *   SYSTEM$TYPEOF(UPPER(&lt;VARCHAR(5)&gt;))               VARCHAR(15)[LOB]
 * </pre>
 *
 * <p>★ THE NAME IS THE DECLARED TYPE, THE TAG IS THE INTERVAL OF VALUES THE PLAN CAN SEE. The two
 * cells above share a declaration and differ only in what they hold, and their tags differ — so the
 * tag cannot be derived from the type, and the name cannot be derived from the value. Frostlake used
 * to read the VALUE for both, which is why a DATE answered TIMESTAMP_NTZ, a TIME and a BINARY answered
 * VARCHAR, and any expression that happened to evaluate to NULL answered NULL however well typed.
 *
 * <p>★ THE TAG IS NOT THE ROW'S OWN VALUE EITHER. A column is tagged by its statistics — the widest
 * value anywhere in the table, on every row alike, whatever a WHERE keeps — and an expression by the
 * interval those statistics propagate through it ({@link ValueRange}): {@code c + 1} widens the
 * interval by one, {@code SUM(c)} multiplies it by a trillion, {@code MAX(c)} narrows it to the greatest
 * value, {@code COUNT(c)} runs from zero to the row count. Where no interval can be known — a
 * ROUND, a LENGTH, a ROW_NUMBER, a cast from text — the tag is the declared width's own.
 *
 * <p>★ SBn IS THE SMALLEST SIGNED-INTEGER WIDTH THAT HOLDS THE UNSCALED VALUE — the rule itself lives
 * in {@link SignedStorageWidth}, shared with the out-of-representable-range refusal, which reads the same
 * tag off the value it could not store. Measured at the byte
 * boundaries rather than guessed: 127 is SB1 and 128 is SB2; 32767 is SB2 and 32768 is SB4; eleven
 * digits are SB8 and twenty-six are SB16. A NUMBER(38,0) holding only 1 is SB1, so the DECLARED
 * precision enters only when nothing narrower is known.
 *
 * <p>★ EVERY OTHER FAMILY'S TAG IS FIXED: BOOLEAN is SB1, DATE is SB4, TIME is SB8, all three
 * TIMESTAMP flavours are SB16, FLOAT is DOUBLE, and the variable-length families — VARCHAR, BINARY,
 * ARRAY, OBJECT, VARIANT — are LOB. So [LOB] is not the universal tag it looked like from the string
 * cells alone. The one exception is a boolean PREDICATE — a comparison, IS NULL, IN, LIKE, a predicate
 * function, or NOT / AND / OR over one — which the plan carries as ROWINDEX:
 * {@code SYSTEM$TYPEOF(1 = 2)} is {@code BOOLEAN[ROWINDEX]} where {@code SYSTEM$TYPEOF(NOT TRUE)} is
 * {@code BOOLEAN[SB1]}.
 *
 * <p>★ A NULL VALUE KEEPS ITS DECLARED TYPE and takes the family's smallest tag, so
 * {@code CAST(NULL AS NUMBER(10,2))} is {@code NUMBER(10,2)[SB1]}. Only an argument with no type at
 * all — a bare NULL — answers {@code NULL[LOB]}.
 */
public final class SystemTypeOfDescription {

    /** The tag every variable-length family carries, and the one an untyped argument falls back to. */
    private static final String LOB = "LOB";

    private SystemTypeOfDescription() {
    }

    /**
     * The description for an argument of {@code declared} type whose values lie in {@code range}.
     *
     * @param declared  the argument's declared type, or null when it has none
     * @param typeText  the declared type as live spells it, e.g. {@code NUMBER(10,2)}
     * @param range     the interval the argument's values lie in, or null when none is known — that
     *                  decides an exact numeric's tag and nothing else
     * @param predicate whether the argument is a predicate, which a BOOLEAN is tagged ROWINDEX for
     */
    public static String of(final DataType declared, final String typeText, final ValueRange range,
                            final boolean predicate) {
        if (declared == null || typeText == null || typeText.isEmpty()) {
            return "NULL[" + LOB + "]";
        }
        return typeText + "[" + storageTag(declared, range, predicate) + "]";
    }

    private static String storageTag(final DataType declared, final ValueRange range, final boolean predicate) {
        if (declared instanceof BooleanType) {
            return predicate ? "ROWINDEX" : "SB1";
        }
        if (declared instanceof NumericType) {
            final NumericType numeric = (NumericType) declared;
            if (numeric.getName().equalsIgnoreCase("FLOAT") || numeric.getName().equalsIgnoreCase("DOUBLE")) {
                return "DOUBLE";
            }
            return range != null ? SignedStorageWidth.tagOfRange(range, numeric.getScale())
                : SignedStorageWidth.tagOfPrecision(numeric.getPrecision());
        }
        if (declared instanceof DateTimeType) {
            final String name = declared.getName().toUpperCase();
            if (name.equals("DATE")) {
                return "SB4";
            }
            // The tag is the byte width the DECLARED precision's value range needs, the same rule the
            // numbers keep: a TIME holds up to 86,400 x 10^p units of a day (SB4 through p = 4), a
            // TIMESTAMP up to ~2.5 x 10^11 x 10^p units since the epoch (SB8 through p = 7). Live
            // reads TIME(3) as SB4, TIME(9) as SB8, TIMESTAMP_NTZ(3) as SB8 and TIMESTAMP_NTZ(9) as SB16.
            final int precision = ((DateTimeType) declared).getPrecision();
            final int digits = precision < 0 ? 9 : precision;
            if (name.startsWith("TIMESTAMP")) {
                return digits <= 7 ? "SB8" : "SB16";
            }
            return digits <= 4 ? "SB4" : "SB8";
        }
        if (declared instanceof UuidType) {
            // A UUID is its 128 bits (live-verified).
            return "SB16";
        }
        return LOB;
    }

}
