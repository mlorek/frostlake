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

import dev.frostlake.values.DayTimeInterval;

/**
 * A day-time interval type. {@code INTERVAL DAY(9) TO SECOND(9)} is the type a TIMESTAMP minus a TIMESTAMP
 * answers, whatever flavours the two sides carry, and the type a day-time interval keeps through the
 * arithmetic that takes one — plus or minus another, times or divided by an exact number, negated
 * (live-verified); its values are {@link DayTimeInterval}s. A unit-suffixed literal spans fewer fields —
 * {@code INTERVAL '1' DAY} is an {@code INTERVAL DAY(9)}, {@code INTERVAL '1' SECOND} an
 * {@code INTERVAL SECOND(9,9)} — and {@link IntervalQualifier} carries each one's name and storage tag.
 *
 * <p>A type also carries the digits its leading field holds and, when it ends in SECOND, the fractional
 * digits it keeps: {@code INTERVAL '1' DAY(2)} is an {@code INTERVAL DAY(2)}, stored in eight bytes where a
 * {@code DAY(9)} takes sixteen, {@code INTERVAL '1.25' SECOND(2,3)} an {@code INTERVAL SECOND(2,3)} and
 * {@code INTERVAL '1 02:03:04.5' DAY TO SECOND(3)} an {@code INTERVAL DAY(9) TO SECOND(3)} (live-verified).
 */
public final class IntervalDayTimeType extends DataType {

    /** The digits a leading field holds, and the fractional digits a SECOND keeps, when nothing declares them. */
    public static final int DEFAULT_PRECISION = 9;

    /** One instance per day-time qualifier at the default precisions, in the qualifier enum's order. */
    private static final IntervalDayTimeType[] BY_QUALIFIER = new IntervalDayTimeType[IntervalQualifier.values().length];

    static {
        for (final IntervalQualifier qualifier : IntervalQualifier.values()) {
            if (qualifier.isDayTime()) {
                BY_QUALIFIER[qualifier.ordinal()] = new IntervalDayTimeType(qualifier, DEFAULT_PRECISION,
                    qualifier.endsInSecond() ? DEFAULT_PRECISION : 0);
            }
        }
    }

    /** The widest day-time interval type, nine digits of days and nine of fractional seconds. */
    public static final IntervalDayTimeType DAY_TO_SECOND = of(IntervalQualifier.DAY_TO_SECOND);

    private final IntervalQualifier qualifier;
    private final int leadingPrecision;
    private final int fractionalPrecision;

    private IntervalDayTimeType(final IntervalQualifier qualifier, final int leadingPrecision,
                                final int fractionalPrecision) {
        super(qualifier.typeName(leadingPrecision, fractionalPrecision), TypeCategory.INTERVAL);
        this.qualifier = qualifier;
        this.leadingPrecision = leadingPrecision;
        this.fractionalPrecision = fractionalPrecision;
    }

    /**
     * The day-time interval type spanning a qualifier's fields, at the default precisions.
     *
     * @param qualifier a day-time qualifier
     * @return its type
     */
    public static IntervalDayTimeType of(final IntervalQualifier qualifier) {
        if (!qualifier.isDayTime()) {
            throw new IllegalArgumentException("not a day-time qualifier: " + qualifier);
        }
        return BY_QUALIFIER[qualifier.ordinal()];
    }

    /**
     * The day-time interval type spanning a qualifier's fields with declared precisions: a column declared
     * {@code INTERVAL DAY(3) TO SECOND(3)} keeps three digits of days and of milliseconds, and says so
     * wherever its type is printed (live-verified).
     *
     * @param qualifier a day-time qualifier
     * @param leading   the leading field's digits, 1 to 9
     * @param fraction  the fractional second digits, 0 to 9; ignored when no field is SECOND
     * @return its type
     */
    public static IntervalDayTimeType of(final IntervalQualifier qualifier, final int leading, final int fraction) {
        final IntervalDayTimeType standard = of(qualifier);
        final int fractional = qualifier.endsInSecond() ? fraction : 0;
        return standard.leadingPrecision == leading && standard.fractionalPrecision == fractional ? standard
            : new IntervalDayTimeType(qualifier, leading, fractional);
    }

    /** @return the fields this type spans */
    public IntervalQualifier getQualifier() {
        return qualifier;
    }

    /** @return the digits the leading field holds */
    public int getLeadingPrecision() {
        return leadingPrecision;
    }

    /** @return the fractional digits a trailing SECOND keeps; 0 when the type does not end in SECOND */
    public int getFractionalPrecision() {
        return fractionalPrecision;
    }

    /** @return the storage tag SYSTEM$TYPEOF prints: SB16 for {@code DAY(9)}, SB8 for {@code DAY(2)} */
    public String storageTag() {
        return qualifier.storageTag(leadingPrecision);
    }

    @Override
    public Object parseValue(final String value) {
        return value == null || value.equalsIgnoreCase("NULL") ? null : value;
    }

    @Override
    public String formatValue(final Object value) {
        return value == null ? "NULL" : value.toString();
    }

    @Override
    public boolean isCompatible(final DataType other) {
        return other instanceof IntervalDayTimeType;
    }

    @Override
    public DataType getCommonType(final DataType other) {
        return isCompatible(other) ? this : null;
    }

    @Override
    public int getSize() {
        return "SB16".equals(storageTag()) ? 16 : 8;
    }
}
