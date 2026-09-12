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
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * The accumulator of {@link ApproxTopKCombine}: every input state is read into one unbounded
 * {@link TopKSummary}, which is trimmed to the result's counter limit only once all of them are in.
 */
public class ApproxTopKCombineAccumulator implements AggregateFunction.Accumulator, MultiArgumentAccumulator,
        ConstantArgumentsAccumulator {

    private static final String DIFFERENT_COUNTERS =
        "Invalid parameter value: ApproxTopK state. Reason: combining states with different numbers of counters";

    private final TopKSummary summary = new TopKSummary(Integer.MAX_VALUE);
    private Integer configuredCounters;
    private Integer inputCounters;
    private String datatype = TopKSummary.MISSING_DATATYPE;
    private int precision = TopKSummary.DEFAULT_PRECISION;
    private int scale;
    private boolean sawState;

    @Override
    public void setConstantArgumentTexts(final List<String> argumentTexts) {
        final int limit = ConstantArgumentTexts.integer(argumentTexts, 1, -1);
        configuredCounters = limit > 0 ? Integer.valueOf(limit) : null;
    }

    @Override
    public void accumulate(final List<Object> argumentValues) {
        if (configuredCounters == null && argumentValues.size() > 1 && argumentValues.get(1) instanceof Number) {
            configuredCounters = Integer.valueOf(((Number) argumentValues.get(1)).intValue());
        }
        accumulate(argumentValues.isEmpty() ? null : argumentValues.get(0));
    }

    @Override
    public void accumulate(final Object value) {
        if (value == null) {
            return;
        }
        final JsonNode state = TopKSummary.stateNode(value);
        final int counters = TopKSummary.countersOf(state);
        if (inputCounters != null && inputCounters.intValue() != counters) {
            throw new RuntimeException(DIFFERENT_COUNTERS);
        }
        inputCounters = Integer.valueOf(counters);
        if (!sawState) {
            final JsonNode declared = state.get("datatype");
            datatype = declared != null && declared.isTextual() ? declared.asText() : TopKSummary.MISSING_DATATYPE;
            final JsonNode width = state.get("precision");
            precision = width != null && width.isNumber() ? width.intValue() : TopKSummary.DEFAULT_PRECISION;
            final JsonNode decimals = state.get("scale");
            scale = decimals != null && decimals.isNumber() ? decimals.intValue() : 0;
            sawState = true;
        }
        summary.absorb(state);
    }

    @Override
    public Object getResult() {
        final int limit = configuredCounters != null ? configuredCounters.intValue()
            : inputCounters != null ? inputCounters.intValue() : 1;
        summary.fit(limit);
        return TopKSummary.stateOf(limit, datatype, precision, scale, summary.ranked());
    }

    @Override
    public void reset() {
        summary.fit(0);
        inputCounters = null;
        sawState = false;
        datatype = TopKSummary.MISSING_DATATYPE;
        precision = TopKSummary.DEFAULT_PRECISION;
        scale = 0;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final ApproxTopKCombineAccumulator theirs = (ApproxTopKCombineAccumulator) other;
        if (!theirs.sawState) {
            return;
        }
        if (inputCounters != null && theirs.inputCounters != null
                && inputCounters.intValue() != theirs.inputCounters.intValue()) {
            throw new RuntimeException(DIFFERENT_COUNTERS);
        }
        if (!sawState) {
            datatype = theirs.datatype;
            precision = theirs.precision;
            scale = theirs.scale;
            sawState = true;
        }
        inputCounters = theirs.inputCounters;
        final List<TopKCounter> ranked = theirs.summary.ranked();
        for (int i = ranked.size() - 1; i >= 0; i--) {
            summary.merge(ranked.get(i).value(), ranked.get(i).count());
        }
    }
}
