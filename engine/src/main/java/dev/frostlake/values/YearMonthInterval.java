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

import java.io.Serializable;

/**
 * A year-month interval, the value of an {@code INTERVAL YEAR(9) TO MONTH}: a signed count of MONTHS. A
 * year is twelve of them, so {@code INTERVAL '1' YEAR} and {@code INTERVAL '12' MONTH} are one value —
 * equal, ordered and hashed by the count alone (live-verified: they compare equal and a UNION keeps one).
 */
public class YearMonthInterval implements Comparable<YearMonthInterval>, Serializable {

    private static final long serialVersionUID = 1L;

    private final long months;

    /**
     * The interval spanning {@code months}. Open to a subclass that carries more than the span — an
     * interval LITERAL keeps the field it was written in — while equality, order and hashing stay the
     * count's alone.
     *
     * @param months the span in months
     */
    protected YearMonthInterval(final long months) {
        this.months = months;
    }

    /**
     * The interval spanning a count of months.
     *
     * @param months the span in months
     * @return the interval
     */
    public static YearMonthInterval ofMonths(final long months) {
        return new YearMonthInterval(months);
    }

    /** @return the span in months */
    public long months() {
        return months;
    }

    @Override
    public final int compareTo(final YearMonthInterval other) {
        return Long.compare(months, other.months);
    }

    @Override
    public final boolean equals(final Object other) {
        return other instanceof YearMonthInterval && months == ((YearMonthInterval) other).months;
    }

    @Override
    public final int hashCode() {
        return Long.hashCode(months);
    }

    /** @return the count with its sign always spelled, the text of an {@code INTERVAL MONTH(9)} */
    @Override
    public String toString() {
        return (months < 0 ? "-" : "+") + Math.abs(months);
    }
}
