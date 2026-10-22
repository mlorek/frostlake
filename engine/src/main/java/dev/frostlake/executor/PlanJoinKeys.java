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
import java.util.Collections;
import java.util.List;

/**
 * The conditions an EXPLAIN plan's join lists: the equalities that key it, each a pair of expressions, one of each
 * side, and the other conditions over both sides. A join lists them as {@code joinKey: (EU.A = ET.A) AND (EU.B =
 * ET.B), joinFilter: ET.B > EU.B}, each key with the expression of the side listed first on its left; an outer join's
 * Filter checks the keys of the side whose rows are not all kept, {@code EU.A IS NOT NULL} (live-verified).
 */
final class PlanJoinKeys {

    private final PlanNode left;
    private final PlanNode right;
    private final List<String> leftKeys = new ArrayList<>();
    private final List<String> rightKeys = new ArrayList<>();
    private final List<String> filters = new ArrayList<>();

    /**
     * @param left  the plan of the join's left side, as written
     * @param right the plan of its right side
     */
    PlanJoinKeys(final PlanNode left, final PlanNode right) {
        this.left = left;
        this.right = right;
    }

    /**
     * The conditions of a join whose conditions the plan does not spell.
     *
     * @return the conditions
     */
    static PlanJoinKeys none() {
        return new PlanJoinKeys(null, null);
    }

    /**
     * Adds an equality that keys the join.
     *
     * @param leftText  the expression of the left side
     * @param rightText the expression of the right side
     */
    void key(final String leftText, final String rightText) {
        leftKeys.add(leftText);
        rightKeys.add(rightText);
    }

    /**
     * Adds a condition over both sides that keys nothing.
     *
     * @param text the condition
     */
    void filter(final String text) {
        filters.add(text);
    }

    /**
     * What the join and its JoinFilter list, the keys oriented to the side listed first.
     *
     * @param first the plan the join lists first
     * @return the expressions
     */
    List<String> texts(final PlanNode first) {
        final List<String> texts = new ArrayList<>();
        if (!leftKeys.isEmpty()) {
            final boolean leftFirst = first != right;
            final StringBuilder keys = new StringBuilder();
            for (int i = 0; i < leftKeys.size(); i++) {
                keys.append(i > 0 ? " AND " : "").append('(')
                    .append(leftFirst ? leftKeys.get(i) : rightKeys.get(i)).append(" = ")
                    .append(leftFirst ? rightKeys.get(i) : leftKeys.get(i)).append(')');
            }
            texts.add("joinKey: " + keys);
        }
        if (!filters.isEmpty()) {
            texts.add("joinFilter: " + PlanExpressionText.conjoined(filters));
        }
        return texts;
    }

    /**
     * The checks an outer join's Filter makes of one side: each of its keys IS NOT NULL.
     *
     * @param side the plan of the side
     * @return the checks
     */
    List<String> nullChecks(final PlanNode side) {
        final List<String> keys = side == left ? leftKeys : side == right ? rightKeys : Collections.<String>emptyList();
        final List<String> checks = new ArrayList<>();
        for (final String key : keys) {
            checks.add(key + " IS NOT NULL");
        }
        return checks;
    }
}
