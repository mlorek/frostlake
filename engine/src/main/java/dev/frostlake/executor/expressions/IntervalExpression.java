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
 * Represents an INTERVAL expression (e.g., INTERVAL '10' DAY)
 */
public class IntervalExpression implements Expression {
    private final Expression valueExpression;
    private final IntervalUnit unit;
    // Further parts of a multi-part interval literal ('1 day, 2 hours'), applied in order after this one.
    private final IntervalExpression rest;

    public IntervalExpression(final Expression valueExpression, final IntervalUnit unit) {
        this(valueExpression, unit, null);
    }

    public IntervalExpression(final Expression valueExpression, final IntervalUnit unit, final IntervalExpression rest) {
        this.valueExpression = valueExpression;
        this.unit = unit;
        this.rest = rest;
    }

    public Expression getValueExpression() {
        return valueExpression;
    }

    /**
     * Whether the unit was written INSIDE the quoted string ({@code INTERVAL '5 days'}) rather than as
     * a keyword after it ({@code INTERVAL '5' DAY}). The two spellings are not interchangeable for a
     * DAY amount added to a DATE: the in-string form leaves a DATE a DATE, the keyword form promotes it
     * to TIMESTAMP_NTZ (live-verified, both spellings, on a column and on a literal alike). Every other
     * unit agrees across the two — year and month preserve, hour and finer promote.
     */
    private boolean unitInString;

    public boolean isUnitInString() {
        return unitInString;
    }

    /** Marks this part, and every part chained behind it, as having its unit inside the string. */
    public void markUnitInString() {
        this.unitInString = true;
        if (rest != null) {
            rest.markUnitInString();
        }
    }

    public IntervalUnit getUnit() {
        return unit;
    }

    public IntervalExpression getRest() {
        return rest;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitInterval(this);
    }

    @Override
    public String toString() {
        return "INTERVAL " + valueExpression + " " + unit + (rest == null ? "" : ", " + rest);
    }
}
