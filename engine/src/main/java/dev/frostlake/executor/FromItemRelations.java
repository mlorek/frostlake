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

import dev.frostlake.executor.operators.JoinType;
import dev.frostlake.metastore.model.JoinedRelations;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;

/**
 * The comma-separated items of one FROM clause, and which item each relation in scope came from.
 *
 * <p>A USING or NATURAL join merges its left input and its right side into one name scope (see
 * {@link JoinedRelations}), but its left input is its OWN item only: live refuses a bare {@code k} over
 * {@code K3, K1 JOIN K2 USING (b)} as ambiguous, because {@code K3} stays a relation of its own beside
 * the merged one, and answers it over {@code K1 JOIN K3 USING (k), K2}. The executor applies every comma
 * item before the explicit joins, and the joins inside a parenthesized item before the others, so a join's
 * left input holds the other items' relations too, and one item's joins need not run one after another;
 * this record tells each relation's item apart.
 */
final class FromItemRelations {

    /** The first token of each comma-separated item, in written order. */
    private final List<Integer> itemStarts = new ArrayList<>();
    /** The item each relation in scope came from, by identity. */
    private final Map<Table, Integer> items = new IdentityHashMap<>();
    /** The item of the join reached last. */
    private int joinItem = -1;
    /** How many relations were in scope when the join reached last was reached. */
    private int joinStart;

    /**
     * @param tableExpr the FROM clause as written, or null for none
     */
    FromItemRelations(final FrostlakeParser.TableExpressionContext tableExpr) {
        if (tableExpr == null) {
            return;
        }
        for (final FrostlakeParser.TableReferenceContext item : tableExpr.tableReference()) {
            itemStarts.add(item.getStart().getTokenIndex());
        }
    }

    /**
     * Records the relation a comma-separated item (or the first one) brings into scope.
     *
     * @param written  the item's reference as written
     * @param relation its relation
     */
    void head(final ParserRuleContext written, final Table relation) {
        items.put(relation, itemOf(written));
    }

    /**
     * Records that a join is reached, before it brings its relations into scope: whatever the previous join
     * brought belongs to that join's item.
     *
     * @param join    the join
     * @param inScope every relation in scope when it is reached, in combined-row order
     */
    void joinReached(final FrostlakeParser.JoinClauseContext join, final List<Table> inScope) {
        settle(inScope);
        joinItem = itemOf(join);
        joinStart = inScope.size();
    }

    /**
     * Gives a USING or NATURAL join's merged relation its name scopes: the join's left input and its right
     * side become one, except for the relations of the FROM clause's other items. Any other join is left
     * as {@link QueryExecutor#mergeTableMetadata} recorded it.
     *
     * @param joined    the join's merged relation
     * @param left      the join's left input
     * @param right     the join's right side
     * @param join      the join as written
     * @param joinType  the join's kind
     * @param keyNames  the join's key columns
     * @param inScope   every relation in scope, the right side's included, in combined-row order
     */
    void mergeScopes(final Table joined, final Table left, final Table right,
                     final FrostlakeParser.JoinClauseContext join, final JoinType joinType,
                     final Collection<String> keyNames, final List<Table> inScope) {
        settle(inScope);
        if (join.USING() == null && join.NATURAL() == null) {
            return;
        }
        final boolean leftNullExtended = joinType == JoinType.RIGHT || joinType == JoinType.FULL;
        final boolean rightNullExtended = joinType == JoinType.LEFT || joinType == JoinType.FULL;
        joined.setJoinedRelations(JoinedRelations.merging(left, right, leftNullExtended, rightNullExtended,
            keyNames, apart(join, inScope)));
    }

    /** Records the item of every relation the join reached last brought into scope. */
    private void settle(final List<Table> inScope) {
        if (joinItem < 0) {
            return;
        }
        for (int i = joinStart; i < inScope.size(); i++) {
            if (!items.containsKey(inScope.get(i))) {
                items.put(inScope.get(i), joinItem);
            }
        }
    }

    /** The relations in scope that belong to another item than the join's. */
    private List<Table> apart(final FrostlakeParser.JoinClauseContext join, final List<Table> inScope) {
        final List<Table> apart = new ArrayList<>();
        if (itemStarts.size() <= 1) {
            return apart;
        }
        final int item = itemOf(join);
        for (final Table relation : inScope) {
            final Integer from = items.get(relation);
            if (from != null && from.intValue() != item) {
                apart.add(relation);
            }
        }
        return apart;
    }

    /** The index of the item a written node belongs to. */
    private int itemOf(final ParserRuleContext written) {
        final int at = written.getStart().getTokenIndex();
        int item = 0;
        for (int i = 0; i < itemStarts.size(); i++) {
            if (itemStarts.get(i) <= at) {
                item = i;
            }
        }
        return item;
    }
}
