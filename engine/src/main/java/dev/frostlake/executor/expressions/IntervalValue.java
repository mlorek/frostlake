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

package dev.frostlake.executor.expressions;

/**
 * Represents an interval value for date/time arithmetic
 */
class IntervalValue {
    private final Object value;
    private final IntervalUnit unit;
    private final IntervalValue rest;

    public IntervalValue(final Object value, final IntervalUnit unit) {
        this(value, unit, null);
    }

    /** Whether the unit was written inside the quoted string — see IntervalExpression#isUnitInString. */
    private boolean unitInString;

    boolean isUnitInString() {
        return unitInString;
    }

    void markUnitInString() {
        this.unitInString = true;
        if (rest != null) {
            rest.markUnitInString();
        }
    }

    public IntervalValue(final Object value, final IntervalUnit unit, final IntervalValue rest) {
        this.value = value;
        this.unit = unit;
        this.rest = rest;
    }

    public IntervalValue getRest() {
        return rest;
    }

    public Object getValue() {
        return value;
    }

    public IntervalUnit getUnit() {
        return unit;
    }

    public long getValueAsLong() {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (final NumberFormatException e) {
            throw new RuntimeException("Invalid interval value: " + value);
        }
    }

    @Override
    public String toString() {
        return "INTERVAL " + value + " " + unit;
    }
}
