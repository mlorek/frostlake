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
 * The fields an interval type spans, with the three facts the account attaches to each: the type's
 * spelling after {@code INTERVAL} (what DESCRIBE and SYSTEM$TYPEOF print), its storage tag, and the code
 * Snowflake's JDBC driver reports as the column's SCALE. The driver carries no interval precision (0),
 * and uses the scale to say which fields the interval spans — live-verified for every qualifier:
 *
 * <pre>
 *   YEAR(9) TO MONTH       0  SB8      DAY(9) TO SECOND(9)     3  SB16
 *   YEAR(9)                1  SB8      DAY(9) TO MINUTE        4  SB16
 *   MONTH(9)               2  SB4      DAY(9) TO HOUR          5  SB16
 *                                      DAY(9)                  6  SB16
 *                                      HOUR(9) TO SECOND(9)    7  SB16
 *                                      HOUR(9) TO MINUTE       8  SB16
 *                                      HOUR(9)                 9  SB16
 *                                      MINUTE(9) TO SECOND(9) 10  SB16
 *                                      MINUTE(9)              11  SB16
 *                                      SECOND(9,9)            12  SB8
 * </pre>
 */
public enum IntervalQualifier {

    YEAR_TO_MONTH("YEAR(9) TO MONTH", 0, "SB8", false),
    YEAR("YEAR(9)", 1, "SB8", false),
    MONTH("MONTH(9)", 2, "SB4", false),
    DAY_TO_SECOND("DAY(9) TO SECOND(9)", 3, "SB16", true),
    DAY_TO_MINUTE("DAY(9) TO MINUTE", 4, "SB16", true),
    DAY_TO_HOUR("DAY(9) TO HOUR", 5, "SB16", true),
    DAY("DAY(9)", 6, "SB16", true),
    HOUR_TO_SECOND("HOUR(9) TO SECOND(9)", 7, "SB16", true),
    HOUR_TO_MINUTE("HOUR(9) TO MINUTE", 8, "SB16", true),
    HOUR("HOUR(9)", 9, "SB16", true),
    MINUTE_TO_SECOND("MINUTE(9) TO SECOND(9)", 10, "SB16", true),
    MINUTE("MINUTE(9)", 11, "SB16", true),
    SECOND("SECOND(9,9)", 12, "SB8", true);

    private final String spelling;
    private final int driverScale;
    private final String storageTag;
    private final boolean dayTime;

    IntervalQualifier(final String spelling, final int driverScale, final String storageTag,
                      final boolean dayTime) {
        this.spelling = spelling;
        this.driverScale = driverScale;
        this.storageTag = storageTag;
        this.dayTime = dayTime;
    }

    /** @return the type's name, {@code INTERVAL DAY(9)} and the like */
    public String typeName() {
        return "INTERVAL " + spelling;
    }

    /** @return the column scale Snowflake's driver reports for this qualifier */
    public int driverScale() {
        return driverScale;
    }

    /** @return the storage tag SYSTEM$TYPEOF prints, {@code SB16} and the like */
    public String storageTag() {
        return storageTag;
    }

    /** @return whether this is a day-time qualifier rather than a year-month one */
    public boolean isDayTime() {
        return dayTime;
    }

    /** @return whether the trailing field is SECOND, the one field that carries fractional digits */
    public boolean endsInSecond() {
        return this == DAY_TO_SECOND || this == HOUR_TO_SECOND || this == MINUTE_TO_SECOND || this == SECOND;
    }

    /** @return the fields without their precisions, {@code DAY TO SECOND} and the like */
    public String fieldsName() {
        return name().replace("_TO_", " TO ");
    }

    /**
     * The type's name with the given precisions, as DESCRIBE and SYSTEM$TYPEOF print it: the leading
     * field carries its digits, a trailing SECOND its fractional digits, and a lone SECOND both —
     * {@code INTERVAL DAY(3) TO SECOND(3)}, {@code INTERVAL HOUR(9) TO MINUTE}, {@code INTERVAL SECOND(3,9)}.
     *
     * @param leading  the leading field's digits
     * @param fraction the fractional second digits, ignored when no field is SECOND
     * @return the name
     */
    public String typeName(final int leading, final int fraction) {
        if (this == SECOND) {
            return "INTERVAL SECOND(" + leading + "," + fraction + ")";
        }
        final String fields = fieldsName();
        final int to = fields.indexOf(" TO ");
        if (to < 0) {
            return "INTERVAL " + fields + "(" + leading + ")";
        }
        return "INTERVAL " + fields.substring(0, to) + "(" + leading + ")" + fields.substring(to)
            + (endsInSecond() ? "(" + fraction + ")" : "");
    }

    /**
     * The storage tag a leading precision needs: the smallest signed width holding the widest span the
     * type admits, counted in nanoseconds for a day-time interval and in months for a year-month one,
     * never under two bytes — {@code DAY(5)} is SB8 and {@code DAY(6)} SB16, {@code MONTH(2)} SB2 and
     * {@code MONTH(5)} SB4 (live-verified).
     *
     * @param leading the leading field's digits
     * @return the tag
     */
    public String storageTag(final int leading) {
        final BigInteger widest = BigInteger.TEN.pow(leading).multiply(BigInteger.valueOf(leadingUnitSize()))
            .subtract(BigInteger.ONE);
        if (dayTime) {
            return widest.bitLength() < Long.SIZE ? "SB8" : "SB16";
        }
        if (widest.bitLength() < Short.SIZE) {
            return "SB2";
        }
        return widest.bitLength() < Integer.SIZE ? "SB4" : "SB8";
    }

    /** One unit of the leading field: in nanoseconds for a day-time qualifier, in months otherwise. */
    public long leadingUnitSize() {
        switch (this) {
            case YEAR_TO_MONTH:
            case YEAR:
                return 12L;
            case MONTH:
                return 1L;
            case DAY_TO_SECOND:
            case DAY_TO_MINUTE:
            case DAY_TO_HOUR:
            case DAY:
                return 86_400_000_000_000L;
            case HOUR_TO_SECOND:
            case HOUR_TO_MINUTE:
            case HOUR:
                return 3_600_000_000_000L;
            case MINUTE_TO_SECOND:
            case MINUTE:
                return 60_000_000_000L;
            default:
                return 1_000_000_000L;
        }
    }

    /** One unit of the trailing field, in the same measure as {@link #leadingUnitSize}. */
    public long trailingUnitSize() {
        switch (this) {
            case YEAR:
                return 12L;
            case YEAR_TO_MONTH:
            case MONTH:
                return 1L;
            case DAY:
                return 86_400_000_000_000L;
            case DAY_TO_HOUR:
            case HOUR:
                return 3_600_000_000_000L;
            case DAY_TO_MINUTE:
            case HOUR_TO_MINUTE:
            case MINUTE:
                return 60_000_000_000L;
            default:
                return 1_000_000_000L;
        }
    }

    /**
     * The qualifier a pair of written fields names, or null when the pair is no interval type: the
     * leading field must come before the trailing one within one family.
     *
     * @param leading  the leading field, {@code DAY} and the like
     * @param trailing the trailing field, or null for a single field
     * @return the qualifier, or null
     */
    public static IntervalQualifier ofFields(final String leading, final String trailing) {
        final String name = trailing == null ? leading : leading + "_TO_" + trailing;
        for (final IntervalQualifier qualifier : values()) {
            if (qualifier.name().equals(name)) {
                return qualifier;
            }
        }
        return null;
    }

    /**
     * The qualifier a driver-reported scale stands for, within one family.
     *
     * @param dayTimeFamily whether the column is a day-time interval
     * @param scale         the reported scale
     * @return the qualifier, or the family's widest one for a scale outside the table
     */
    public static IntervalQualifier ofDriverScale(final boolean dayTimeFamily, final int scale) {
        for (final IntervalQualifier qualifier : values()) {
            if (qualifier.dayTime == dayTimeFamily && qualifier.driverScale == scale) {
                return qualifier;
            }
        }
        return dayTimeFamily ? DAY_TO_SECOND : YEAR_TO_MONTH;
    }
}
