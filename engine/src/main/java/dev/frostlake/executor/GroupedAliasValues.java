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

import dev.frostlake.types.DataType;
import dev.frostlake.values.ValueRange;

import java.util.HashMap;
import java.util.Map;

/**
 * The values of a grouped select list's aliased items by the alias upper-cased, filled item by item as each group's
 * row is computed, with each alias's static type and the interval its values lie in, worked out once for the list.
 * An item that reads an earlier alias is typed from the expression the alias names, as an ungrouped select list
 * types it (live-verified):
 *
 * <pre>
 *   SELECT COUNT(*) AS k, SYSTEM$TYPEOF(k) FROM t                      NUMBER(18,0)[SB1]
 *   SELECT v AS w, SYSTEM$TYPEOF(w) FROM t GROUP BY v                  v's declared type, VARCHAR(10)[LOB]
 * </pre>
 */
final class GroupedAliasValues extends HashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    private final Map<String, DataType> types;

    private final Map<String, ValueRange> ranges;

    /**
     * An empty set of values over the list's alias types.
     *
     * @param types  the aliases' static types, by the alias upper-cased
     * @param ranges the intervals the aliases' values lie in, by the alias upper-cased
     */
    GroupedAliasValues(final Map<String, DataType> types, final Map<String, ValueRange> ranges) {
        this.types = types;
        this.ranges = ranges;
    }

    /**
     * Has the evaluator of one of the list's items read the earlier items' values, each typed as its alias is.
     *
     * @param evaluator the item's evaluator
     */
    void offerTo(final ExpressionEvaluator evaluator) {
        evaluator.setOuterLateralContext(this);
        evaluator.setOutputAliasTypes(types);
        evaluator.setOutputAliasRanges(ranges);
    }

    /**
     * Empty values over the same alias types, for another run of the list: a grouping set's.
     *
     * @return the values, none filled yet
     */
    GroupedAliasValues newRun() {
        return new GroupedAliasValues(types, ranges);
    }
}
