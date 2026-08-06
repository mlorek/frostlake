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

package dev.frostlake.functions.scalar.context;

import dev.frostlake.executor.expressions.RowOrdinal;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.util.List;

/**
 * SEQ1 / SEQ2 / SEQ4 / SEQ8 — the row's 0-based ordinal, wrapped to the function's integer width.
 *
 * <p>One class for all four because they differ only in that width: the digit in the name is the
 * number of BYTES, so SEQ1 counts in 8 bits and SEQ8 in 64. Measured live:
 *
 * <pre>
 *   SEQ1()   NUMBER(3,0)    SEQ2()   NUMBER(5,0)
 *   SEQ4()   NUMBER(10,0)   SEQ8()   NUMBER(19,0)
 * </pre>
 *
 * <p>The optional argument is a SIGN, 0 or 1, and it decides what happens at the top of the range
 * rather than where counting starts — every form starts at 0. With the default 0 the count stays
 * non-negative and wraps at the signed maximum; with 1 it wraps the way a two's-complement integer
 * of that width does. Measured over 260 rows for SEQ1 and 70000 for SEQ2:
 *
 * <pre>
 *   SEQ1()    … 125 126 127 0 1 …          SEQ1(1)   … 125 126 127 -128 -127 …
 *   SEQ2()    … 32766 32767 0 1 …          SEQ2(1)   … 32766 32767 -32768 -32767 …
 *   SEQ2()  min 0 max 32767, 32768 values  SEQ2(1) min -32768 max 32767, 65536 values
 * </pre>
 *
 * <p>The sign does NOT change the declared type: {@code SEQ1(1)} is still NUMBER(3,0), wide enough
 * for -128 either way.
 *
 * <p><b>Frostlake is contiguous where Snowflake only promises to be distinct.</b> Live, the values
 * come from whatever unit of parallelism produced the row: over a 16-row join the account returned
 * 16 rows numbered between 7 and 127, not 0..15, which is why Snowflake's own documentation says not
 * to use SEQ as a key generator and to reach for ROW_NUMBER instead. That artefact is not
 * reproducible — it is not even stable run to run — so Frostlake numbers rows 0, 1, 2, … in the
 * order the operator produces them. Every single-source shape agrees with live; a query that depends
 * on the gaps is depending on something Snowflake does not promise.
 */
public class SeqFn extends BuiltInFunction {

    private final int bytes;

    /**
     * @param name  the Snowflake spelling, whose trailing digit is {@code bytes}
     * @param bytes the width in bytes: 1, 2, 4 or 8
     */
    public SeqFn(final String name, final int bytes) {
        super(name, new NumericType("NUMBER", precisionFor(bytes), 0));
        this.bytes = bytes;
    }

    /** The digits live declares for each width — 3, 5, 10, 19. */
    private static int precisionFor(final int bytes) {
        switch (bytes) {
            case 1: return 3;
            case 2: return 5;
            case 4: return 10;
            default: return 19;
        }
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return Long.valueOf(wrap(RowOrdinal.current(), bytes, signed(args)));
    }

    /**
     * The ordinal reduced to this width. Split on 64 bits because the period of a full-width
     * sequence — 2^64, and 2^63 for the unsigned half — does not fit in the long doing the
     * arithmetic; at that width a row count never reaches the wrap anyway.
     */
    static long wrap(final long ordinal, final int bytes, final boolean signed) {
        final int bits = bytes * 8;
        if (bits >= Long.SIZE) {
            return signed ? ordinal : (ordinal & Long.MAX_VALUE);
        }
        final long half = 1L << (bits - 1);
        if (!signed) {
            return Math.floorMod(ordinal, half);
        }
        final long wrapped = Math.floorMod(ordinal, half << 1);
        return wrapped >= half ? wrapped - (half << 1) : wrapped;
    }

    /**
     * The sign argument, defaulting to 0 (unsigned) when absent. Live rejects a NULL and anything
     * that is neither 0 nor 1, each with its own sentence, and accepts anything numeric-valued that
     * lands on one of them — {@code 1.0} and the string {@code '1'} both mean 1.
     */
    private boolean signed(final List<Object> args) {
        if (args.isEmpty()) {
            return false;
        }
        final Object argument = args.get(0);
        if (argument == null) {
            throw new RuntimeException("Invalid parameter value: NULL. Reason: sign must not be NULL");
        }
        final long sign = asNumber(argument).longValue();
        if (sign != 0L && sign != 1L) {
            throw new RuntimeException(
                "Invalid parameter value: " + render(argument) + ". Reason: sign must be 0 or 1");
        }
        return sign == 1L;
    }

    private BigDecimal asNumber(final Object argument) {
        if (argument instanceof Number) {
            return new BigDecimal(argument.toString());
        }
        final String text = String.valueOf(argument).trim();
        try {
            return new BigDecimal(text);
        } catch (final NumberFormatException e) {
            throw new RuntimeException("Numeric value '" + text + "' is not recognized");
        }
    }

    /** How the rejected value is spelled back: as written, without a synthesised scale. */
    private String render(final Object argument) {
        if (argument instanceof BigDecimal) {
            return ((BigDecimal) argument).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(argument);
    }

    @Override public int getMinArgCount() { return 0; }
    @Override public int getMaxArgCount() { return 1; }
}
