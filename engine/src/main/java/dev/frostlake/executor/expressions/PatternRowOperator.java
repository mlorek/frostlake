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
 * The value operator written right after a LIKE ANY's list of several patterns, which that list — a ROW —
 * cannot take. Live refuses it while the statement compiles, naming the operator's function and the ROW of
 * the patterns' types (live-verified):
 *
 * <pre>
 *   'a' LIKE ANY ('a', 'b') || ''          Invalid argument types for function '||': (ROW(VARCHAR(1), VARCHAR(1)), VARCHAR(1))
 *   'a' LIKE ANY ('a', 'b') IS NULL        Invalid argument types for function 'IS NULL': (ROW(VARCHAR(1), VARCHAR(1)))
 *   'a' LIKE ANY ('a', 'b')[0]             Invalid argument types for function 'GET': (ROW(VARCHAR(1), VARCHAR(1)), NUMBER(1,0))
 *   'a' LIKE ANY ('a', 'b') IS DISTINCT …  Invalid argument types for function 'EQUAL_NULL': (ROW(…), VARCHAR(1))
 * </pre>
 *
 * <p>Each is positioned at the operator, except the EQUAL_NULL of IS DISTINCT FROM, which live places
 * nowhere ("error line 0 at position -1"). A cast and a COLLATE are refused in their own sentences, with the ROW
 * written out: {@code ::VARCHAR} is "invalid type [CAST(ROW('a', 'b') AS VARCHAR(134217728))] for parameter
 * 'TO_VARCHAR'" and {@code COLLATE 'de'} "argument needs to be a string: 'ROW('a', 'b')'".
 */
final class PatternRowOperator {

    private final String function;
    private final Expression other;
    private final SourcePosition position;
    private final String castTarget;
    private final boolean collated;

    private PatternRowOperator(final String function, final Expression other, final SourcePosition position,
                               final String castTarget, final boolean collated) {
        this.function = function;
        this.other = other;
        this.position = position;
        this.castTarget = castTarget;
        this.collated = collated;
    }

    /**
     * An operator with an operand beside the ROW.
     *
     * @param function the name live gives the operator's function
     * @param other    the other operand
     * @param position where the operator stands, or null where live places the refusal nowhere
     * @return the operator
     */
    static PatternRowOperator beside(final String function, final Expression other, final SourcePosition position) {
        return new PatternRowOperator(function, other, position, null, false);
    }

    /**
     * A cast of the ROW.
     *
     * @param targetType the target type as written
     * @return the operator
     */
    static PatternRowOperator cast(final String targetType) {
        return new PatternRowOperator(null, null, null, targetType, false);
    }

    /**
     * A COLLATE over the ROW.
     *
     * @return the operator
     */
    static PatternRowOperator collate() {
        return new PatternRowOperator(null, null, null, null, true);
    }

    /**
     * An operator over the ROW alone.
     *
     * @param function the name live gives the operator's function
     * @param position where the operator stands
     * @return the operator
     */
    static PatternRowOperator alone(final String function, final SourcePosition position) {
        return new PatternRowOperator(function, null, position, null, false);
    }

    /** The target type of a cast of the ROW as written, or null for any other operator. */
    String getCastTarget() {
        return castTarget;
    }

    /** Whether the operator is a COLLATE over the ROW. */
    boolean isCollate() {
        return collated;
    }

    /** The name live gives the operator's function. */
    String getFunction() {
        return function;
    }

    /** The operand beside the ROW, or null for an operator over the ROW alone. */
    Expression getOther() {
        return other;
    }

    /** Where the operator stands, or null where live places the refusal nowhere. */
    SourcePosition getPosition() {
        return position;
    }
}
