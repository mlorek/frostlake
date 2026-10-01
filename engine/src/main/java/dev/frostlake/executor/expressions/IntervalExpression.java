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
    // The unit-suffixed spelling's text and qualifier; null for the quoted-string form.
    private final IntervalLiteralSpec literal;

    public IntervalExpression(final Expression valueExpression, final IntervalUnit unit) {
        this(valueExpression, unit, (IntervalExpression) null);
    }

    public IntervalExpression(final Expression valueExpression, final IntervalUnit unit, final IntervalExpression rest) {
        this.valueExpression = valueExpression;
        this.unit = unit;
        this.rest = rest;
        this.literal = null;
    }

    /**
     * A unit-suffixed literal, {@code INTERVAL '1 02' DAY TO HOUR}: a typed value whose text is read when a row
     * reaches it. Its unit is its leading field's.
     *
     * @param valueExpression the text as an expression print shows it
     * @param unit            the leading field's unit
     * @param literal         the text and the qualifier
     */
    public IntervalExpression(final Expression valueExpression, final IntervalUnit unit,
                              final IntervalLiteralSpec literal) {
        this.valueExpression = valueExpression;
        this.unit = unit;
        this.rest = null;
        this.literal = literal;
    }

    /** @return the unit-suffixed spelling's text and qualifier, or null for the quoted-string form */
    public IntervalLiteralSpec getLiteral() {
        return literal;
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

    /** The amount of a part inside the string, as written: {@code +01}, {@code 1.5}; null for any other part. */
    private String writtenAmount;

    /** The unit word of a part inside the string, as written: {@code Hour}, {@code h}; null when none. */
    private String writtenUnit;

    /**
     * Records how a part inside the string was written, which is how the plan names the literal.
     *
     * @param amount the amount as written, its sign included
     * @param unit   the unit word as written, or null when the part has none
     */
    public void recordWritten(final String amount, final String unit) {
        this.writtenAmount = amount;
        this.writtenUnit = unit;
    }

    /** @return the amount as written inside the string, or null */
    public String getWrittenAmount() {
        return writtenAmount;
    }

    /** @return the unit word as written inside the string, or null */
    public String getWrittenUnit() {
        return writtenUnit;
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
        if (literal != null) {
            return "INTERVAL '" + literal.getText() + "' " + literal.qualifierText();
        }
        return "INTERVAL " + valueExpression + " " + unit + (rest == null ? "" : ", " + rest);
    }
}
