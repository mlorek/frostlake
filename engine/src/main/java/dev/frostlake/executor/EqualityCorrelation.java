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
 *
 * <p>A correlation may also carry a residual: conjuncts that read the outer row other than through the equalities,
 * {@code b.k > a.v} beside {@code b.id = a.id}. The rows the subquery keeps are then computed once with the columns
 * the residual reads, and an outer row tests the residual over the rows of its own keys alone (see
 * {@link CorrelatedSubqueryRule#residualCorrelation}).
 */
public final class EqualityCorrelation {

    private final List<String> innerKeys;
    private final List<String> outerKeys;
    private final String from;
    private final List<String> otherConjuncts;
    private final String aggregateItem;
    private final boolean rowPerKeptRow;
    private final String memberItem;
    private final List<String> residual = new ArrayList<>();
    private final List<String> residualItems = new ArrayList<>();

    /**
     * @param innerKeys      each equality's side over the subquery's relations, as written
     * @param outerKeys      each equality's side over the outer row, as written, in the same order
     * @param from           the subquery's FROM clause after the keyword, as written
     * @param otherConjuncts the WHERE's other conjuncts, as written
     * @param aggregateItem  the select list's one item when it is an aggregate expression, as written, or null
     * @param rowPerKeptRow  whether no select item aggregates, so the subquery answers a row for each row it keeps
     * @param memberItem     the select list's one item when it is an expression that aggregates nothing, as
     *                       written, or null: the member an IN over the subquery compares with
     */
    public EqualityCorrelation(final List<String> innerKeys, final List<String> outerKeys, final String from,
                               final List<String> otherConjuncts, final String aggregateItem,
                               final boolean rowPerKeptRow, final String memberItem) {
        this.innerKeys = new ArrayList<>(innerKeys);
        this.outerKeys = new ArrayList<>(outerKeys);
        this.from = from;
        this.otherConjuncts = new ArrayList<>(otherConjuncts);
        this.aggregateItem = aggregateItem;
        this.rowPerKeptRow = rowPerKeptRow;
        this.memberItem = memberItem;
    }

    /**
     * @param conjuncts the conjuncts reading the outer row other than the equalities, each over the columns
     *                  {@code "$R<n>"} of the items
     * @param items     each column's expression over the subquery's relations, as written, in column order
     * @return this correlation with those conjuncts as its residual
     */
    public EqualityCorrelation withResidual(final List<String> conjuncts, final List<String> items) {
        final EqualityCorrelation copy = new EqualityCorrelation(innerKeys, outerKeys, from, otherConjuncts,
            aggregateItem, rowPerKeptRow, memberItem);
        copy.residual.addAll(conjuncts);
        copy.residualItems.addAll(items);
        return copy;
    }

    /**
     * @return the conjuncts reading the outer row other than the equalities, joined by AND, each part reading the
     *         subquery's relations alone written as the column {@code "$R<n>"} of {@link #residualRowsQuery}'s rows
     *         after the keys; null where the equalities are the subquery's only reading of the outer row
     */
    public String getResidual() {
        if (residual.isEmpty()) {
            return null;
        }
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < residual.size(); i++) {
            text.append(i > 0 ? " AND (" : "(").append(residual.get(i)).append(')');
        }
        return text.toString();
    }

    /** @return how many columns the residual reads */
    public int residualItemCount() {
        return residualItems.size();
    }

    /**
     * @param heldKeys a conjunct read ahead of the other conjuncts, or null for none
     * @return the query of the inner keys and the residual's columns of each row the other conjuncts keep, the keys
     *         first
     */
    public String residualRowsQuery(final String heldKeys) {
        final StringBuilder items = new StringBuilder();
        for (int i = 0; i < residualItems.size(); i++) {
            items.append(", ").append(residualItems.get(i)).append(" AS \"$R").append(i).append('"');
        }
        return "SELECT " + String.join(", ", innerKeys) + items + " FROM " + from + filter(heldKeys);
    }

    /** @return the select list's one item an IN compares with, or null when the select list is anything else */
    public String getMemberItem() {
        return memberItem;
    }

    /**
     * @return the query of the inner keys and the member of each row the subquery's other conjuncts keep, the keys
     *         first
     */
    public String membersQuery() {
        return membersQuery(null);
    }

    /**
     * @param heldKeys a conjunct read ahead of the other conjuncts, or null for none
     * @return {@link #membersQuery()} over the rows the conjunct keeps as well
     */
    public String membersQuery(final String heldKeys) {
        return "SELECT " + String.join(", ", innerKeys) + ", " + memberItem + " FROM " + from + filter(heldKeys);
    }

    /** @return the equalities' sides over the subquery's relations, as written */
    public List<String> getInnerKeys() {
        return new ArrayList<>(innerKeys);
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

    /**
     * @return every text the subquery computes over its own relations: the inner keys, the other conjuncts and the
     *         residual's columns
     */
    public List<String> innerTexts() {
        final List<String> texts = new ArrayList<>(otherConjuncts);
        texts.addAll(innerKeys);
        texts.addAll(residualItems);
        return texts;
    }

    /**
     * @return the query of the inner keys the subquery's other conjuncts keep, one row for each row they keep
     */
    public String keysQuery() {
        return keysQuery(null);
    }

    /**
     * @param heldKeys a conjunct read ahead of the other conjuncts, or null for none
     * @return {@link #keysQuery()} over the rows the conjunct keeps as well
     */
    public String keysQuery(final String heldKeys) {
        return "SELECT " + String.join(", ", innerKeys) + " FROM " + from + filter(heldKeys);
    }

    /**
     * @return the query of the aggregate item's value for each combination of inner keys the other conjuncts keep,
     *         the keys first
     */
    public String groupedQuery() {
        final String keys = String.join(", ", innerKeys);
        return "SELECT " + keys + ", " + aggregateItem + " FROM " + from + filter(null) + " GROUP BY " + keys;
    }

    /** The WHERE of the other conjuncts, a conjunct read ahead of them first where one is given. */
    private String filter(final String heldKeys) {
        final List<String> conjuncts = new ArrayList<>();
        if (heldKeys != null) {
            conjuncts.add(heldKeys);
        }
        conjuncts.addAll(otherConjuncts);
        if (conjuncts.isEmpty()) {
            return "";
        }
        final StringBuilder where = new StringBuilder(" WHERE ");
        for (int i = 0; i < conjuncts.size(); i++) {
            where.append(i > 0 ? " AND (" : "(").append(conjuncts.get(i)).append(')');
        }
        return where.toString();
    }
}
