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
    private final boolean fromBoolean;
    private final String correlated;

    /**
     * @param text        the value as the plan prints it standing alone
     * @param type        its type, or null where it is not known
     * @param bare        whether it stands as an operand without parentheses: a column, a CORRELATION, a call
     * @param literal     whether it is a number written out, which a product converts no further
     * @param cast        whether it is a cast the statement writes
     * @param fromBoolean whether it is a BOOLEAN's conversion to a number, which converts no further either
     * @param correlated  the outer column a bare {@code CORRELATION(…)} reads, or null
     */
    PrintedPlanValue(final String text, final DataType type, final boolean bare, final boolean literal,
                     final boolean cast, final boolean fromBoolean, final String correlated) {
        this.text = text;
        this.type = type;
        this.bare = bare;
        this.literal = literal;
        this.cast = cast;
        this.fromBoolean = fromBoolean;
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

    boolean isFromBoolean() {
        return fromBoolean;
    }

    String getCorrelated() {
        return correlated;
    }

    /** The value as an operand: in parentheses unless it stands bare or is a literal. */
    String operand() {
        return bare || literal ? text : "(" + text + ")";
    }
}
