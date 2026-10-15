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

import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The outer joins the {@code (+)} markers of a WHERE ask for over a comma-separated FROM clause, and an order to run
 * them in. Each relation a marker null-supplies joins with a LEFT JOIN whose ON holds the conjuncts carrying its
 * markers, once every relation those conjuncts read has joined; the conjuncts without a marker stay the WHERE. Live
 * answers the documented chain {@code p.department_id(+) = d.department_id AND e.project_id(+) = p.project_id} as two
 * left outer joins, whatever the order the FROM clause writes the three relations in (live-verified).
 *
 * <p>Relations are named as {@link OuterJoinMarkerRules} names them: the alias, else the table's name, upper-cased.
 */
final class LegacyOuterJoins {

    /** The conjuncts joining each null-supplied relation, by its name, in written order. */
    private final Map<String, List<FrostlakeParser.BooleanExprContext>> conditions;
    /** The relations each null-supplied relation's conjuncts read beside it, by its name. */
    private final Map<String, Set<String>> requires;
    /** The conjuncts carrying no marker, in written order. */
    private final List<FrostlakeParser.BooleanExprContext> residual;

    LegacyOuterJoins(final Map<String, List<FrostlakeParser.BooleanExprContext>> conditions,
                     final Map<String, Set<String>> requires,
                     final List<FrostlakeParser.BooleanExprContext> residual) {
        this.conditions = conditions;
        this.requires = requires;
        this.residual = residual;
    }

    /**
     * Whether a marker null-supplies the relation.
     *
     * @param relation the relation's name
     * @return true when it joins with a LEFT JOIN
     */
    boolean isNullSupplied(final String relation) {
        return conditions.containsKey(relation);
    }

    /**
     * The conjuncts that join a null-supplied relation: its ON.
     *
     * @param relation the relation's name
     * @return its conjuncts, in written order
     */
    List<FrostlakeParser.BooleanExprContext> conditionOf(final String relation) {
        return conditions.get(relation);
    }

    /** @return the conjuncts carrying no marker, which stay the WHERE, in written order */
    List<FrostlakeParser.BooleanExprContext> residual() {
        return residual;
    }

    /**
     * The order to join the FROM clause's items in: as written, but for a null-supplied relation, which waits until
     * the relations its conjuncts read have joined. The first item joined is a relation no marker null-supplies, and
     * a LATERAL item joins once every item written before it has.
     *
     * @param names each item's relation name, in written order; null for a LATERAL item
     * @return the items' positions in join order, or null when no order joins each relation after those it reads
     */
    List<Integer> order(final List<String> names) {
        final List<Integer> order = new ArrayList<>();
        final Set<String> joined = new HashSet<>();
        final boolean[] placed = new boolean[names.size()];
        while (order.size() < names.size()) {
            int next = -1;
            for (int i = 0; i < names.size() && next < 0; i++) {
                if (!placed[i] && ready(i, names, placed, joined, order.isEmpty())) {
                    next = i;
                }
            }
            if (next < 0) {
                return null;
            }
            placed[next] = true;
            order.add(next);
            if (names.get(next) != null) {
                joined.add(names.get(next));
            }
        }
        return order;
    }

    /** Whether item {@code i} may join next. */
    private boolean ready(final int i, final List<String> names, final boolean[] placed, final Set<String> joined,
                          final boolean first) {
        final String name = names.get(i);
        if (name == null) {
            if (first) {
                return false;
            }
            for (int j = 0; j < i; j++) {
                if (!placed[j]) {
                    return false;
                }
            }
            return true;
        }
        if (!conditions.containsKey(name)) {
            return true;
        }
        return !first && joined.containsAll(requires.get(name));
    }
}
