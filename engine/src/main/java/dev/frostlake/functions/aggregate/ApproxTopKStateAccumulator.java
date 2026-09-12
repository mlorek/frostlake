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
import dev.frostlake.functions.MultiArgumentAccumulator;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * The accumulator of {@link ApproxTopKAccumulate}: a {@link TopKSummary} sized by the call's second
 * argument, serialised whole. The state's {@code datatype} comes from the aggregated expression's
 * declared type; when nothing declares one (an expression the typer cannot follow) the first value's
 * own class stands in, and a summary that saw nothing reports {@code MISSING}.
 */
public class ApproxTopKStateAccumulator implements AggregateFunction.Accumulator, MultiArgumentAccumulator,
        ConstantArgumentsAccumulator, DeclaredArgumentAccumulator {

    private int counters = TopKSummary.DEFAULT_COUNTERS;
    private boolean configured;
    private DataType declared;
    private Object firstValue;
    private TopKSummary summary;

    @Override
    public void setConstantArgumentTexts(final List<String> argumentTexts) {
        counters = ConstantArgumentTexts.integer(argumentTexts, 1, TopKSummary.DEFAULT_COUNTERS);
        configured = true;
    }

    @Override
    public void setDeclaredArgumentType(final DataType declaredType) {
        this.declared = declaredType;
    }

    @Override
    public void accumulate(final List<Object> argumentValues) {
        if (!configured) {
            if (argumentValues.size() > 1 && argumentValues.get(1) instanceof Number) {
                counters = ((Number) argumentValues.get(1)).intValue();
            }
            configured = true;
        }
        accumulate(argumentValues.isEmpty() ? null : argumentValues.get(0));
    }

    @Override
    public void accumulate(final Object value) {
        if (value == null) {
            return;
        }
        if (firstValue == null) {
            firstValue = value;
        }
        summary().add(value);
    }

    @Override
    public Object getResult() {
        final TopKSummary counted = summary();
        if (counted.isEmpty()) {
            return TopKSummary.stateOf(1, datatype(), TopKSummary.DEFAULT_PRECISION, 0, counted.ranked());
        }
        final boolean exact = declared instanceof NumericType && !NumericType.isApproximate(declared);
        return TopKSummary.stateOf(counters, datatype(),
            exact ? ((NumericType) declared).getPrecision() : TopKSummary.DEFAULT_PRECISION,
            exact ? ((NumericType) declared).getScale() : 0, counted.ranked());
    }

    @Override
    public void reset() {
        summary = null;
        firstValue = null;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final ApproxTopKStateAccumulator theirs = (ApproxTopKStateAccumulator) other;
        if (theirs.summary == null) {
            return;
        }
        if (firstValue == null) {
            firstValue = theirs.firstValue;
        }
        final List<TopKCounter> ranked = theirs.summary.ranked();
        for (int i = ranked.size() - 1; i >= 0; i--) {
            summary().merge(ranked.get(i).value(), ranked.get(i).count());
        }
        summary().fit(counters);
    }

    private TopKSummary summary() {
        if (summary == null) {
            summary = new TopKSummary(counters);
        }
        return summary;
    }

    /** The state's {@code datatype}: the declaration's family, else the first value's, else MISSING. */
    private String datatype() {
        if (declared != null) {
            final String name = SqlTypeNames.internalName(declared);
            if (name != null) {
                return name;
            }
        }
        return datatypeOf(firstValue);
    }

    /** The internal type name a value's own class implies, for an input nothing declared. */
    static String datatypeOf(final Object value) {
        if (value == null) {
            return TopKSummary.MISSING_DATATYPE;
        }
        if (value instanceof BigDecimal || value instanceof BigInteger || value instanceof Long
                || value instanceof Integer || value instanceof Short) {
            return "FIXED";
        }
        if (value instanceof Number) {
            return "REAL";
        }
        if (value instanceof String) {
            return "TEXT";
        }
        if (value instanceof Boolean) {
            return "BOOLEAN";
        }
        if (value instanceof LocalDate) {
            return "DATE";
        }
        if (value instanceof LocalTime) {
            return "TIME";
        }
        if (value instanceof LocalDateTime) {
            return "TIMESTAMP_NTZ";
        }
        if (value instanceof OffsetDateTime) {
            return "TIMESTAMP_LTZ";
        }
        if (value instanceof ZonedDateTime) {
            return "TIMESTAMP_TZ";
        }
        if (value instanceof BinaryValue) {
            return "BINARY";
        }
        if (value instanceof VariantValue) {
            return "VARIANT";
        }
        return "TEXT";
    }
}
