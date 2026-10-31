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

import dev.frostlake.transaction.TransactionManager;
import dev.frostlake.types.DecimalText;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * SYSTEM$ABORT_TRANSACTION (live-verified). The argument is a transaction's id as CURRENT_TRANSACTION() answers it:
 * a number, or the digits of one in a string. An open transaction of that id — the caller's own or another
 * session's — is ended and its writes rolled back, and the answer is {@code Aborted transaction id: <id>}; an id
 * nothing open has answers {@code Could not abort txn: <id>} and ends nothing, a transaction the caller holds
 * included. A number with a fraction, a text that is no number and a value inside a VARIANT answer
 * {@code Bad transaction id}, and NULL answers NULL. A text is read as the number it spells, rounded half away
 * from zero, so {@code '1e2'} names 100 and {@code '12.5'} names 13; a number wider than 64 bits wraps into them.
 */
public final class TransactionAbort {

    /** The answer for an argument that reads as no id. */
    public static final String BAD = "Bad transaction id";

    /** The most digits a NUMBER holds before its decimal point. */
    private static final int WIDEST = 38;

    private TransactionAbort() {
    }

    /**
     * Reads a number written as one, which names an id only when it has no fraction.
     *
     * @param value the number
     * @return the id as a Long, or {@link #BAD}
     */
    public static Object exactNumber(final Number value) {
        final BigDecimal number = decimalOf(value);
        if (number == null || number.stripTrailingZeros().scale() > 0) {
            return BAD;
        }
        return rounded(number);
    }

    /**
     * Reads a FLOAT, which names the id it rounds to.
     *
     * @param value the number
     * @return the id as a Long, or {@link #BAD} for what is no finite number
     */
    public static Object roundedNumber(final Number value) {
        final BigDecimal number = decimalOf(value);
        return number == null ? BAD : rounded(number);
    }

    /**
     * Reads a text, which names the id of the number it spells.
     *
     * @param value the text
     * @return the id as a Long, or {@link #BAD}
     */
    public static Object text(final String value) {
        try {
            return rounded(DecimalText.parse(value.trim()));
        } catch (final NumberFormatException noNumber) {
            return BAD;
        }
    }

    /**
     * Reads a value handed over with nothing known of how it was written.
     *
     * @param value the argument's value
     * @return the id as a Long, {@link #BAD}, or null for NULL
     */
    public static Object byValue(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return exactNumber((Number) value);
        }
        return value instanceof String ? text((String) value) : BAD;
    }

    /**
     * Carries the call out for what its argument read as.
     *
     * @param read         the id as a Long, {@link #BAD}, or null
     * @param transactions the engine's transactions
     * @param insideBlock  whether a Snowflake Scripting block or a procedure is running the call
     * @return the function's answer
     */
    public static Object answer(final Object read, final TransactionManager transactions, final boolean insideBlock) {
        if (!(read instanceof Long)) {
            return read;
        }
        final long id = ((Long) read).longValue();
        return transactions.abort(id, insideBlock) ? "Aborted transaction id: " + id : "Could not abort txn: " + id;
    }

    private static BigDecimal decimalOf(final Number value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        try {
            return DecimalText.parse(value.toString());
        } catch (final NumberFormatException notFinite) {
            return null;
        }
    }

    private static Object rounded(final BigDecimal number) {
        if (number.precision() - number.scale() > WIDEST) {
            return BAD;
        }
        return Long.valueOf(number.setScale(0, RoundingMode.HALF_UP).toBigInteger().longValue());
    }
}
