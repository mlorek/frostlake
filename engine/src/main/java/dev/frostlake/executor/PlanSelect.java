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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What an EXPLAIN plan gathers for one SELECT while planning it: the names it resolves, and the aggregate calls, the
 * grouping keys and the window calls its Aggregate — a GroupingSets for ROLLUP, CUBE and GROUPING SETS — and its
 * WindowFunction list. The aggregates are those of the select list, the HAVING, the QUALIFY and the ORDER BY, each
 * call once, an average as its sum and its count (live-verified).
 */
final class PlanSelect {

    private final PlanScope scope;
    private final Set<String> aggregateKeys = new LinkedHashSet<>();
    private final List<String> aggregates = new ArrayList<>();
    private final List<String> groupKeys = new ArrayList<>();
    private final List<String> windows = new ArrayList<>();
    private PlanNode aggregate;
    private PlanNode window;

    /**
     * @param scope the names the SELECT resolves
     */
    PlanSelect(final PlanScope scope) {
        this.scope = scope;
    }

    PlanScope getScope() {
        return scope;
    }

    /**
     * Takes the aggregate calls and the window calls an expression of the SELECT was printed with.
     *
     * @param printed the printer that printed it
     */
    void collect(final PlanExpressionText printed) {
        for (int i = 0; i < printed.getAggregateKeys().size(); i++) {
            if (aggregateKeys.add(printed.getAggregateKeys().get(i))) {
                aggregates.addAll(printed.getAggregateTerms().get(i));
            }
        }
        windows.addAll(printed.getWindows());
    }

    /**
     * Takes the window calls a select item named by its alias computes.
     *
     * @param computed the window calls
     */
    void collectWindows(final List<String> computed) {
        windows.addAll(computed);
    }

    void groupBy(final String key) {
        groupKeys.add(key);
    }

    /**
     * Records the Aggregate or GroupingSets operator the SELECT computes its aggregates in.
     *
     * @param node the operator
     */
    void aggregatedBy(final PlanNode node) {
        aggregate = node;
    }

    /**
     * Records the WindowFunction operator the SELECT computes its windows in.
     *
     * @param node the operator
     */
    void windowedBy(final PlanNode node) {
        window = node;
    }

    /**
     * Writes what was gathered into the operators: {@code aggExprs: […], groupKeys: […]} for an Aggregate, each part
     * only where it lists something, the aggregates alone for a GroupingSets, and the windows for a WindowFunction.
     */
    void render() {
        if (aggregate != null) {
            final List<String> texts = new ArrayList<>();
            if ("GroupingSets".equals(aggregate.getOperation())) {
                texts.addAll(aggregates);
            } else {
                if (!aggregates.isEmpty()) {
                    texts.add("aggExprs: [" + String.join(", ", aggregates) + "]");
                }
                if (!groupKeys.isEmpty()) {
                    texts.add("groupKeys: [" + String.join(", ", groupKeys) + "]");
                }
            }
            aggregate.expressed(texts);
        }
        if (window != null) {
            window.expressed(windows);
        }
    }
}
