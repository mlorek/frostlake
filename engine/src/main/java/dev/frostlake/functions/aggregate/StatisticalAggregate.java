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

package dev.frostlake.functions.aggregate;

import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.DataType;

import java.util.List;

/**
 * A statistical aggregate — a moment, a covariance, a correlation or a regression — over a fixed number of
 * arguments. Live refuses an argument such an aggregate cannot read as a number at compile time, in the words of
 * the internal operation it reaches first: the multiplication of its sum of squares or of its cross products, or
 * the DOUBLE conversion of the argument. That operation is the same whichever kind the argument is, so a
 * semi-structured, a BOOLEAN, a temporal, a BINARY and a VECTOR argument are refused in one set of words
 * (live-verified). An aggregate that converts only one of its arguments refuses only that one; the others are
 * merely null-checked and take any type.
 */
public abstract class StatisticalAggregate extends AggregateFunction {

    /** The refused position of an aggregate that refuses every argument alike. */
    private static final int EVERY_ARGUMENT = -1;

    private final int argumentCount;
    private final SemiStructuredRejection refusal;
    private final int refusedPosition;

    /**
     * An aggregate that refuses every argument alike.
     *
     * @param name the function's name
     * @param returnType the type it answers
     * @param argumentCount the number of arguments it takes
     * @param refusal the words an argument it cannot read is refused in
     */
    protected StatisticalAggregate(final String name, final DataType returnType, final int argumentCount,
                                   final SemiStructuredRejection refusal) {
        this(name, returnType, argumentCount, refusal, EVERY_ARGUMENT);
    }

    /**
     * An aggregate that refuses only the one argument it converts.
     *
     * @param name the function's name
     * @param returnType the type it answers
     * @param argumentCount the number of arguments it takes
     * @param refusal the words that argument is refused in
     * @param refusedPosition the argument's position, from 0
     */
    protected StatisticalAggregate(final String name, final DataType returnType, final int argumentCount,
                                   final SemiStructuredRejection refusal, final int refusedPosition) {
        super(name, returnType);
        this.argumentCount = argumentCount;
        this.refusal = refusal;
        this.refusedPosition = refusedPosition;
    }

    /** The refusal of an argument at the position the aggregate cannot read. */
    private SemiStructuredRejection refusal(final int position) {
        return refusedPosition == EVERY_ARGUMENT || position == refusedPosition
            ? refusal : SemiStructuredRejection.NONE;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return null;
    }

    @Override
    public int getMinArgCount() {
        return argumentCount;
    }

    @Override
    public int getMaxArgCount() {
        return argumentCount;
    }

    @Override
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return refusal(position);
    }

    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return refusal(position);
    }

    @Override
    public SemiStructuredRejection temporalRejection(final int position) {
        return refusal(position);
    }

    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return refusal(position);
    }

    @Override
    public SemiStructuredRejection vectorRejection(final int position) {
        return refusal(position);
    }
}
