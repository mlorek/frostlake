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

import dev.frostlake.types.DataType;

/**
 * The types aggregates declare, for callers that aggregate a column outside an expression tree. A PIVOT is
 * one: it has the aggregated column's declared type but no call for the type inferencer to walk. The rules
 * are the inferencer's own, so a pivoted column declares what the same aggregate declares in a grouped
 * SELECT.
 */
public final class AggregateResultTypes {

    private AggregateResultTypes() {
    }

    /**
     * What a computing aggregate (SUM, AVG, MEDIAN or a variance) declares over an argument of the given
     * type.
     *
     * @param funcName the aggregate's upper-case name
     * @param argument the argument's type
     * @return the declared type, or null for another aggregate or an argument type with no rule
     */
    public static DataType computing(final String funcName, final DataType argument) {
        return TypeInferencer.computingAggregateType(funcName, argument);
    }
}
