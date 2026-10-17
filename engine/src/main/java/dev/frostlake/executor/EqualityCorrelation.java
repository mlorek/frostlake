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
import java.util.List;

/**
 * How a subquery reads the outer row when it reads it only through equalities written as conjuncts of its WHERE, each
 * an expression over its own relations equal to one over the outer row: {@code EXISTS (SELECT 1 FROM b WHERE b.id =
 * a.id AND b.k = a.v AND b.n > 3)}. Every other part of the subquery reads its own relations alone, so what it keeps
 * for an outer row is what it keeps for every outer row with equal keys, and the subquery can be computed once for all
 * of them (see {@link CorrelatedSubqueryRule#equalityCorrelation}).
 */
public final class EqualityCorrelation {

    private final List<String> innerKeys;
    private final List<String> outerKeys;
    private final String from;
    private final List<String> otherConjuncts;
    private final String aggregateItem;
    private final boolean rowPerKeptRow;

    /**
     * @param innerKeys      each equality's side over the subquery's relations, as written
     * @param outerKeys      each equality's side over the outer row, as written, in the same order
     * @param from           the subquery's FROM clause after the keyword, as written
     * @param otherConjuncts the WHERE's other conjuncts, as written
     * @param aggregateItem  the select list's one item when it is an aggregate expression, as written, or null
     * @param rowPerKeptRow  whether no select item aggregates, so the subquery answers a row for each row it keeps
     */
    public EqualityCorrelation(final List<String> innerKeys, final List<String> outerKeys, final String from,
                               final List<String> otherConjuncts, final String aggregateItem,
                               final boolean rowPerKeptRow) {
        this.innerKeys = new ArrayList<>(innerKeys);
        this.outerKeys = new ArrayList<>(outerKeys);
        this.from = from;
        this.otherConjuncts = new ArrayList<>(otherConjuncts);
        this.aggregateItem = aggregateItem;
        this.rowPerKeptRow = rowPerKeptRow;
    }

    /**
     * @return whether no select item aggregates: only then is the subquery's existence for an outer row whether an
     *         inner row it keeps has the row's keys, where an aggregate's one row exists for every outer row
     */
    public boolean answersARowPerKeptRow() {
        return rowPerKeptRow;
    }

    /** @return the equalities' sides over the outer row, as written */
    public List<String> getOuterKeys() {
        return new ArrayList<>(outerKeys);
    }

    /** @return how many equalities correlate the subquery */
    public int keyCount() {
        return innerKeys.size();
    }

    /** @return the select list's one aggregate item, or null when the select list is anything else */
    public String getAggregateItem() {
        return aggregateItem;
    }

    /** @return every text the subquery computes over its own relations: the inner keys and the other conjuncts */
    public List<String> innerTexts() {
        final List<String> texts = new ArrayList<>(otherConjuncts);
        texts.addAll(innerKeys);
        return texts;
    }

    /**
     * @return the query of the inner keys the subquery's other conjuncts keep, one row for each row they keep
     */
    public String keysQuery() {
        return "SELECT " + String.join(", ", innerKeys) + " FROM " + from + filter();
    }

    /**
     * @return the query of the aggregate item's value for each combination of inner keys the other conjuncts keep,
     *         the keys first
     */
    public String groupedQuery() {
        final String keys = String.join(", ", innerKeys);
        return "SELECT " + keys + ", " + aggregateItem + " FROM " + from + filter() + " GROUP BY " + keys;
    }

    private String filter() {
        if (otherConjuncts.isEmpty()) {
            return "";
        }
        final StringBuilder where = new StringBuilder(" WHERE ");
        for (int i = 0; i < otherConjuncts.size(); i++) {
            where.append(i > 0 ? " AND (" : "(").append(otherConjuncts.get(i)).append(')');
        }
        return where.toString();
    }
}
