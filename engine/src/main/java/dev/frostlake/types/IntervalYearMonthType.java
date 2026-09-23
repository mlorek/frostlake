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

/**
 * A year-month interval type: {@code INTERVAL YEAR(9)}, {@code INTERVAL MONTH(9)} or
 * {@code INTERVAL YEAR(9) TO MONTH}. A unit-suffixed {@code INTERVAL '1' YEAR} or {@code INTERVAL '14' MONTH}
 * literal is typed this way (live-verified); its value is a whole number of months. A type also carries the
 * digits its leading field holds: {@code INTERVAL '1' YEAR(2)} is an {@code INTERVAL YEAR(2)}, stored in two
 * bytes where a {@code YEAR(9)} takes eight.
 */
public final class IntervalYearMonthType extends DataType {

    /** The digits a leading field holds when nothing declares them. */
    public static final int DEFAULT_PRECISION = 9;

    /** One instance per year-month qualifier at the default precision, in the qualifier enum's order. */
    private static final IntervalYearMonthType[] BY_QUALIFIER = new IntervalYearMonthType[IntervalQualifier.values().length];

    static {
        for (final IntervalQualifier qualifier : IntervalQualifier.values()) {
            if (!qualifier.isDayTime()) {
                BY_QUALIFIER[qualifier.ordinal()] = new IntervalYearMonthType(qualifier, DEFAULT_PRECISION);
            }
        }
    }

    private final IntervalQualifier qualifier;
    private final int leadingPrecision;

    private IntervalYearMonthType(final IntervalQualifier qualifier, final int leadingPrecision) {
        super(qualifier.typeName(leadingPrecision, 0), TypeCategory.INTERVAL);
        this.qualifier = qualifier;
        this.leadingPrecision = leadingPrecision;
    }

    /**
     * The year-month interval type spanning a qualifier's fields, at the default precision.
     *
     * @param qualifier a year-month qualifier
     * @return its type
     */
    public static IntervalYearMonthType of(final IntervalQualifier qualifier) {
        if (qualifier.isDayTime()) {
            throw new IllegalArgumentException("not a year-month qualifier: " + qualifier);
        }
        return BY_QUALIFIER[qualifier.ordinal()];
    }

    /**
     * The year-month interval type spanning a qualifier's fields with a declared leading precision:
     * {@code INTERVAL YEAR(2) TO MONTH} keeps two digits of years (live-verified).
     *
     * @param qualifier a year-month qualifier
     * @param leading   the leading field's digits, 1 to 9
     * @return its type
     */
    public static IntervalYearMonthType of(final IntervalQualifier qualifier, final int leading) {
        final IntervalYearMonthType standard = of(qualifier);
        return standard.leadingPrecision == leading ? standard : new IntervalYearMonthType(qualifier, leading);
    }

    /** @return the fields this type spans */
    public IntervalQualifier getQualifier() {
        return qualifier;
    }

    /** @return the digits the leading field holds */
    public int getLeadingPrecision() {
        return leadingPrecision;
    }

    /** @return the storage tag SYSTEM$TYPEOF prints: SB8 for {@code YEAR(9)}, SB2 for {@code YEAR(2)} */
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
        return other instanceof IntervalYearMonthType;
    }

    @Override
    public DataType getCommonType(final DataType other) {
        return isCompatible(other) ? this : null;
    }

    @Override
    public int getSize() {
        return 8;
    }
}
