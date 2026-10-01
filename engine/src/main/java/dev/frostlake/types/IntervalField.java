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

import java.math.BigInteger;

/**
 * One field an interval qualifier names, coarsest first. A year-month field is counted in months and a
 * day-time field in nanoseconds, which is how the account stores the value — and so what decides the
 * storage tag a leading precision needs: the smallest of SB2, SB4, SB8 and SB16 that holds ten to the
 * precision of the leading field, less one unit of storage (live-verified: {@code YEAR(1)} through
 * {@code YEAR(3)} are SB2, {@code YEAR(4)} to {@code YEAR(8)} SB4, {@code YEAR(9)} SB8; {@code MONTH(4)} SB2,
 * {@code MONTH(5)} SB4; {@code DAY(5)} and {@code HOUR(6)} SB8, {@code DAY(6)} and {@code HOUR(7)} SB16;
 * every {@code SECOND(p,f)} SB8).
 */
public enum IntervalField {

    YEAR(false, 12L),
    MONTH(false, 1L),
    DAY(true, 86_400_000_000_000L),
    HOUR(true, 3_600_000_000_000L),
    MINUTE(true, 60_000_000_000L),
    SECOND(true, 1_000_000_000L);

    private final boolean dayTime;
    private final long storageUnits;

    IntervalField(final boolean dayTime, final long storageUnits) {
        this.dayTime = dayTime;
        this.storageUnits = storageUnits;
    }

    /** @return whether this is a day-time field rather than a year-month one */
    public boolean isDayTime() {
        return dayTime;
    }

    /** @return the months (year-month) or nanoseconds (day-time) one of this field holds */
    public long storageUnits() {
        return storageUnits;
    }

    /**
     * The storage tag a leading field of this kind needs at a leading precision.
     *
     * @param leadingPrecision the digits the leading field holds
     * @return {@code SB2}, {@code SB4}, {@code SB8} or {@code SB16}
     */
    public String storageTag(final int leadingPrecision) {
        final int bits = BigInteger.TEN.pow(leadingPrecision).multiply(BigInteger.valueOf(storageUnits))
            .subtract(BigInteger.ONE).bitLength();
        if (bits < Short.SIZE) {
            return "SB2";
        }
        if (bits < Integer.SIZE) {
            return "SB4";
        }
        return bits < Long.SIZE ? "SB8" : "SB16";
    }

    /**
     * The field a qualifier leads with.
     *
     * @param qualifier the qualifier
     * @return its leading field
     */
    public static IntervalField leadingOf(final IntervalQualifier qualifier) {
        switch (qualifier) {
            case YEAR_TO_MONTH:
            case YEAR:
                return YEAR;
            case MONTH:
                return MONTH;
            case DAY_TO_SECOND:
            case DAY_TO_MINUTE:
            case DAY_TO_HOUR:
            case DAY:
                return DAY;
            case HOUR_TO_SECOND:
            case HOUR_TO_MINUTE:
            case HOUR:
                return HOUR;
            case MINUTE_TO_SECOND:
            case MINUTE:
                return MINUTE;
            default:
                return SECOND;
        }
    }

    /**
     * The field a qualifier ends with.
     *
     * @param qualifier the qualifier
     * @return its trailing field, the leading one for a one-field qualifier
     */
    public static IntervalField trailingOf(final IntervalQualifier qualifier) {
        switch (qualifier) {
            case YEAR_TO_MONTH:
            case MONTH:
                return MONTH;
            case YEAR:
                return YEAR;
            case DAY:
                return DAY;
            case DAY_TO_HOUR:
            case HOUR:
                return HOUR;
            case DAY_TO_MINUTE:
            case HOUR_TO_MINUTE:
            case MINUTE:
                return MINUTE;
            default:
                return SECOND;
        }
    }

    /**
     * The qualifier spanning two fields, the one-field qualifier when both are the same.
     *
     * @param lead  the leading field
     * @param trail the trailing field
     * @return the qualifier, or null when no qualifier runs from {@code lead} to {@code trail} — a finer
     *         field first, or the two families mixed
     */
    public static IntervalQualifier qualifier(final IntervalField lead, final IntervalField trail) {
        if (lead == trail) {
            return IntervalQualifier.valueOf(lead.name());
        }
        if (lead.dayTime != trail.dayTime || lead.ordinal() > trail.ordinal()) {
            return null;
        }
        return IntervalQualifier.valueOf(lead.name() + "_TO_" + trail.name());
    }
}
