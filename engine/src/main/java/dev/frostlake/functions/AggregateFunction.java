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

package dev.frostlake.functions;

import dev.frostlake.types.DataType;

/**
 * Base class for aggregate functions (SUM, COUNT, LISTAGG, …). An aggregate is a
 * {@link BuiltInFunction} whose per-group state lives in an {@link Accumulator}: the executor creates
 * one accumulator per group, folds every input value into it, and reads the group's result from it at
 * the end. {@link #evaluate} is therefore not the aggregation path, and implementations typically
 * return {@code null} from it.
 */
public abstract class AggregateFunction extends BuiltInFunction {

    /**
     * Names the aggregate and fixes its declared result type.
     *
     * @param name the SQL name the aggregate is registered under
     * @param returnType the declared type of the aggregate's result
     */
    protected AggregateFunction(final String name, final DataType returnType) {
        super(name, returnType);
    }

    /**
     * Creates the fresh, empty per-group state for one evaluation of this aggregate; the executor
     * makes one per group (or per window partition) and never shares them.
     *
     * @return a new accumulator starting from this aggregate's empty-group state
     */
    public abstract Accumulator createAccumulator();

    /**
     * The running state of one aggregate over one group: values are folded in one row at a time and
     * the group's result is read out at the end. Implementations ignore SQL NULL inputs where
     * Snowflake's aggregates do, and define the empty-group result (NULL for most, 0 for the counting
     * family).
     *
     * <p>THIS ONE STAYS NESTED, deliberately, against the house rule that interfaces live in their own
     * file. It means nothing away from its owner — an "accumulator" with no aggregate to accumulate for
     * is not a concept — which is the same reason the JDK keeps {@code Map.Entry} where it is. Moving it
     * would rename a type 43 files implement, including the frostlake-ai pack's aggregates, for no gain
     * beyond the letter of the rule. The nested-type Checkstyle rule cannot see it either way: an
     * interface nested in a class is implicitly static and never writes the keyword the rule keys on.
     */
    public interface Accumulator {
        /**
         * Folds one row's argument value into the running state.
         *
         * @param value the aggregate argument's value for one row; SQL NULL arrives as {@code null}
         */
        void accumulate(final Object value);
        /**
         * The aggregate's result over everything accumulated so far.
         *
         * @return the group's result, or {@code null} where the aggregate defines an empty group as
         *     NULL
         */
        Object getResult();
        /** Clears the state back to the empty-group starting point so the accumulator can be reused. */
        void reset();
        /**
         * Folds another accumulator's state into this one, combining two partial aggregations of the
         * same group.
         *
         * @param other a partial state of the same aggregate implementation
         */
        void merge(final Accumulator other);
    }
}
