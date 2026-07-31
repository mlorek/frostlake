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
import dev.frostlake.functions.scalar.SharedFunctionHelpers;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulator for LISTAGG: concatenates the non-null accumulated values with {@link #delimiter}.
 * The delimiter defaults to the empty string (Snowflake's default for a single-argument LISTAGG) and
 * is overridden from the optional second LISTAGG argument via {@link #setDelimiter}. Extracted to its
 * own file (rather than nested in {@link ListAgg}) so the executor can set the delimiter on it.
 */
public class ListAggAccumulator implements AggregateFunction.Accumulator {
    private final List<String> values = new ArrayList<>();
    private String delimiter = "";

    /** Set the separator placed between values; a null is treated as the empty string. */
    public void setDelimiter(final String delimiter) {
        this.delimiter = delimiter != null ? delimiter : "";
    }

    @Override
    public void accumulate(final Object value) {
        if (value != null) {
            // Snowflake output text for temporals (space + FF3), not java.time's T-separated form.
            values.add(SharedFunctionHelpers.textOf(value));
        }
    }

    @Override
    public Object getResult() {
        return String.join(delimiter, values);
    }

    @Override
    public void reset() {
        values.clear();
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        values.addAll(((ListAggAccumulator) other).values);
    }
}
