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

import dev.frostlake.types.DecimalText;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * The whole number a LIMIT, OFFSET or FETCH count gives. A count written with a fraction keeps its whole part —
 * {@code LIMIT 1.5} is one row and {@code LIMIT 2.0} two — and so does an exact number a block's variable binds
 * there, whatever its scale: a NUMBER(10,2) holding 2.5 limits to two rows. A variable that holds NULL is no limit.
 * Any other bind is refused while the statement compiles, with the bind as it is written: a negative number, a
 * text (one that spells a number too), a FLOAT and a BOOLEAN are {@code Invalid row count ':s' in limit clause},
 * and {@code … in result offset clause} where an OFFSET's count stands. A count past 2^62 - 1
 * (4611686018427387903) is refused the same way, as written, wherever it stands — LIMIT, OFFSET, FETCH and TOP
 * (live-verified).
 */
final class RowCountSlot {

    private static final BigInteger MOST_ROWS = BigInteger.valueOf(Integer.MAX_VALUE);

    /** The widest count taken: 2^62 - 1, whose 62 bits are the most a count may need. */
    private static final int MOST_COUNT_BITS = 62;

    private RowCountSlot() {
    }

    /**
     * @param literal the count as written, digits with or without a fraction
     * @return the rows it counts, held to the range of an int
     */
    static int ofLiteral(final String literal, final boolean offset) {
        final BigDecimal count = DecimalText.parse(literal);
        if (count.setScale(0, RoundingMode.DOWN).toBigInteger().bitLength() > MOST_COUNT_BITS) {
            throw invalid("'" + literal + "'", offset);
        }
        return whole(count);
    }

    /**
     * The count a block's variable binds.
     *
     * @param name   the variable as the statement writes it, without its colon
     * @param value  what it holds
     * @param offset whether the count is an OFFSET's
     * @return the rows it counts, or null when it holds NULL and so limits nothing
     */
    static Integer ofBind(final String name, final Object value, final boolean offset) {
        if (value == null) {
            return null;
        }
        final BigDecimal exact;
        if (value instanceof BigDecimal) {
            exact = (BigDecimal) value;
        } else if (value instanceof BigInteger) {
            exact = new BigDecimal((BigInteger) value);
        } else if (value instanceof Long || value instanceof Integer || value instanceof Short
                || value instanceof Byte) {
            exact = BigDecimal.valueOf(((Number) value).longValue());
        } else {
            exact = null;
        }
        if (exact == null || exact.signum() < 0) {
            throw invalid("':" + name + "'", offset);
        }
        return whole(exact);
    }

    private static RuntimeException invalid(final String written, final boolean offset) {
        return new RuntimeException(SqlCompilationError.of("Invalid row count " + written + " in "
            + (offset ? "result offset" : "limit") + " clause"));
    }

    private static int whole(final BigDecimal count) {
        final BigInteger rows = count.setScale(0, RoundingMode.DOWN).toBigInteger();
        return rows.compareTo(MOST_ROWS) > 0 ? Integer.MAX_VALUE : rows.intValue();
    }
}
