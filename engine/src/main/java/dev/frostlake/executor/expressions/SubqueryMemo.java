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

import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.storage.ResultSet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-(outer-query) memo for subqueries that are evaluated once per outer row. A subquery is
 * <em>uncorrelated</em> if its execution reads no outer (lateral) value — in which case its result is
 * identical for every outer row, so it can be executed once and reused instead of re-executed per row.
 *
 * <p>Correlation is classified empirically: {@link ExpressionEvaluatorVisitor} counts actual
 * lateral-value reads, and a subquery whose first execution reads zero is provably row-independent
 * (any branch decision that depended on an outer value would itself have read one). The classification
 * is therefore exact and conservative — anything that touches the outer context is treated as
 * correlated and never cached. A subquery that DRAWS a value afresh per row (RANDOM, UUID_STRING) is
 * classified the same way, by a second counter, and is likewise never cached.
 *
 * <p>The key is one COPY of a subquery, not its text: two copies of the same text are two subqueries
 * and the account evaluates each — {@code (SELECT s1.nextval) = (SELECT s1.nextval)} is FALSE there,
 * while one copy read over many rows still answers with one value.
 *
 * <p>Scope is one {@link ExpressionEvaluator} instance — i.e. one outer query's row loop, evaluated on
 * a single thread — so plain {@link HashMap}/{@link HashSet} suffice and cached results never leak
 * across queries.
 */
public class SubqueryMemo {

    private final Map<String, List<ResultSet>> uncorrelatedResults = new HashMap<>();
    private final Map<String, PreparedInSet> inSets = new HashMap<>();
    private final Set<String> correlated = new HashSet<>();
    private final Set<String> redrawn = new HashSet<>();

    /** The cached result of a proven-uncorrelated subquery, or {@code null} if not (yet) cached. */
    public List<ResultSet> cachedResult(final String subquery) {
        return uncorrelatedResults.get(subquery);
    }

    /** True if this subquery has been proven correlated and must be re-executed per outer row. */
    public boolean isCorrelated(final String subquery) {
        return correlated.contains(subquery);
    }

    public void recordUncorrelated(final String subquery, final List<ResultSet> result) {
        uncorrelatedResults.put(subquery, result);
    }

    public void recordCorrelated(final String subquery) {
        correlated.add(subquery);
    }

    /**
     * True if this subquery draws a value afresh for every row — {@code (SELECT RANDOM())} — so it must
     * be re-executed per outer row even though it reads no outer value.
     */
    public boolean isRedrawnPerRow(final String subquery) {
        return redrawn.contains(subquery);
    }

    public void recordRedrawnPerRow(final String subquery) {
        redrawn.add(subquery);
    }

    /** Prepared IN-membership index for an uncorrelated subquery, or {@code null} if not yet built. */
    public PreparedInSet cachedInSet(final String subquery) {
        return inSets.get(subquery);
    }

    public void recordInSet(final String subquery, final PreparedInSet set) {
        inSets.put(subquery, set);
    }
}
