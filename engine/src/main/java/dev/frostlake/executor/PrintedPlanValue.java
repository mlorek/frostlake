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

import dev.frostlake.types.DataType;

/**
 * A value inside a HAVING subquery's aggregate as the decorrelated plan prints it (see HavingAggregateSubqueries):
 * its text, its type, and what printing it as an operand or converting it further takes.
 */
final class PrintedPlanValue {

    private final String text;
    private final DataType type;
    private final boolean bare;
    private final boolean literal;
    private final boolean cast;
    private final boolean conversion;
    private final String correlated;
    private final boolean fromPredicate;
    private final boolean castsAsBoolean;
    private final boolean booleanNumber;

    /**
     * @param text        the value as the plan prints it standing alone
     * @param type        its type, or null where it is not known
     * @param bare        whether it stands as an operand without parentheses: a column, a CORRELATION, a call
     * @param literal     whether it is a number written out, which a product converts no further
     * @param cast        whether it is a cast the statement writes
     * @param conversion  whether it is a conversion to a number of a BOOLEAN, a text or a FLOAT, which converts no
     *                    further either
     * @param correlated  the outer column a bare {@code CORRELATION(…)} reads, or null
     */
    PrintedPlanValue(final String text, final DataType type, final boolean bare, final boolean literal,
                     final boolean cast, final boolean conversion, final String correlated) {
        this(text, type, bare, literal, cast, conversion, correlated, false, false, false);
    }

    private PrintedPlanValue(final String text, final DataType type, final boolean bare, final boolean literal,
                             final boolean cast, final boolean conversion, final String correlated,
                             final boolean fromPredicate, final boolean castsAsBoolean, final boolean booleanNumber) {
        this.fromPredicate = fromPredicate;
        this.castsAsBoolean = castsAsBoolean;
        this.booleanNumber = booleanNumber;
        this.text = text;
        this.type = type;
        this.bare = bare;
        this.literal = literal;
        this.cast = cast;
        this.conversion = conversion;
        this.correlated = correlated;
    }

    String getText() {
        return text;
    }

    DataType getType() {
        return type;
    }

    boolean isBare() {
        return bare;
    }

    boolean isLiteral() {
        return literal;
    }

    boolean isCast() {
        return cast;
    }

    boolean isConversion() {
        return conversion;
    }

    String getCorrelated() {
        return correlated;
    }

    /**
     * This BOOLEAN as a predicate's: a comparison, an IS NULL, an IN, a NOT, a junction of them or one the statistics
     * settle, whose number goes through NUMBER(2,0) alone.
     *
     * @param casts whether the predicate stands in {@code CAST(… AS BOOLEAN)} as a BOOLEAN — a junction or a settled
     *              one stands as it is
     * @return the value so marked
     */
    PrintedPlanValue asPredicate(final boolean casts) {
        return new PrintedPlanValue(text, type, bare, literal, cast, conversion, correlated, true, casts, false);
    }

    /** This number as a BOOLEAN's, which arithmetic casts to its own type once more (see HavingAggregateSubqueries). */
    PrintedPlanValue asBooleanNumber() {
        return new PrintedPlanValue(text, type, bare, literal, cast, conversion, correlated, false, false, true);
    }

    boolean isFromPredicate() {
        return fromPredicate;
    }

    boolean isBooleanNumber() {
        return booleanNumber;
    }

    /** The value as a BOOLEAN stands in a conversion or a junction: a predicate in {@code CAST(… AS BOOLEAN)}. */
    String booleanText() {
        return castsAsBoolean ? "CAST(" + text + " AS BOOLEAN)" : text;
    }

    /** Whether the value converts to a BOOLEAN by that cast alone, as a predicate does. */
    boolean castsAsBoolean() {
        return castsAsBoolean;
    }

    /** The value as an operand: in parentheses unless it stands bare or is a literal. */
    String operand() {
        return bare || literal ? text : "(" + text + ")";
    }
}
