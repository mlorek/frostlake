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

package dev.frostlake.functions.scalar.string;

import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * NORMALIZE(x, lo, hi) — Snowflake's min-max normalization {@code (x - lo) / (hi - lo)}, NOT
 * Unicode normalization (live-verified: {@code NORMALIZE(5, 0, 10)} is 0.5). The result is
 * unclamped ({@code NORMALIZE(15, 0, 10)} is 1.5); {@code lo >= hi} raises Snowflake's
 * invalid-range error. Numbers normalize by value, DATE by epoch day, TIME by (nano-precise)
 * second of day, TIMESTAMP by epoch second. A VARCHAR input maps to a range-INDEPENDENT fraction
 * from its bytes read as a base-256 fraction over 384 (live-verified: {@code '7'} is 55/384 and
 * {@code '77'} adds 55/98304 — the same result for any valid range).
 */
public class NormalizeFn extends TextArgumentFunction {

    public NormalizeFn() { super("NORMALIZE", NumericType.DOUBLE); }

    /** A DATE, TIME or timestamp normalizes by its value on the time line, never by its text. */
    @Override
    public boolean readsTemporalsAsText() {
        return false;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null || args.get(2) == null) return null;
        final double lo = toLinear(args.get(1));
        final double hi = toLinear(args.get(2));
        if (lo >= hi) {
            // Live-verified wording.
            throw new RuntimeException("Invalid range for normalization function: lower bound "
                + boundText(args.get(1)) + ", upper bound " + boundText(args.get(2)));
        }
        final Object value = args.get(0);
        if (value instanceof CharSequence) {
            return textFraction(value.toString());
        }
        return (toLinear(value) - lo) / (hi - lo);
    }

    /** A value on the linear scale normalization runs over. */
    private static double toLinear(final Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof LocalDate) {
            return ((LocalDate) value).toEpochDay();
        }
        if (value instanceof LocalTime) {
            final LocalTime t = (LocalTime) value;
            return t.toSecondOfDay() + t.getNano() / 1_000_000_000.0;
        }
        if (value instanceof LocalDateTime) {
            final LocalDateTime ts = (LocalDateTime) value;
            return ts.toEpochSecond(ZoneOffset.UTC) + ts.getNano() / 1_000_000_000.0;
        }
        return new BigDecimal(value.toString()).doubleValue();
    }

    /**
     * The live-verified VARCHAR mapping: the string's bytes as a base-256 fraction divided by 384,
     * independent of the range bounds.
     */
    private static double textFraction(final String text) {
        final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        double fraction = 0;
        double divisor = 384.0;
        for (int i = 0; i < bytes.length && i < 8; i++) {
            fraction += (bytes[i] & 0xFF) / divisor;
            divisor *= 256.0;
        }
        return fraction;
    }

    private static String boundText(final Object bound) {
        final String text = bound.toString();
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 3; }
}
