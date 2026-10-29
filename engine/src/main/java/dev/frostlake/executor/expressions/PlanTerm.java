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
 * A term of a formula the plan writes for an aggregate, as an echo prints it: its text, whether it stands bare beside
 * an operator, and the type the plan gives it, a FLOAT or an exact number of so many leading digits and scale.
 */
final class PlanTerm {

    /** The most digits an exact number holds. */
    static final int MAX_DIGITS = 38;

    private final String text;
    private final boolean bare;
    private final boolean real;
    private final int leading;
    private final int scale;

    /**
     * A term.
     *
     * @param text    its text
     * @param bare    whether it stands bare as an operand, as a column, a literal or a variable does
     * @param real    whether it is a FLOAT
     * @param leading an exact number's digits before the point, at most {@link #MAX_DIGITS} with its scale
     * @param scale   an exact number's digits after the point
     */
    PlanTerm(final String text, final boolean bare, final boolean real, final int leading, final int scale) {
        this.text = text;
        this.bare = bare;
        this.real = real;
        this.leading = leading + scale > MAX_DIGITS ? MAX_DIGITS - scale : leading;
        this.scale = scale;
    }

    String getText() {
        return text;
    }

    /** The term as an operator's operand: bare, or in parentheses. */
    String operand() {
        return bare ? text : "(" + text + ")";
    }

    boolean isReal() {
        return real;
    }

    int getLeading() {
        return leading;
    }

    int getScale() {
        return scale;
    }

    /** The type a cast to this term's type names: FLOAT, or the exact number's precision and scale. */
    String typeName() {
        return real ? "FLOAT" : "NUMBER(" + Math.min(MAX_DIGITS, leading + scale) + "," + scale + ")";
    }
}
