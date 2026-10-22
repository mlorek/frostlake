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
 * One operator of a plan while it is built: its name, the objects it reads or writes, the alias it is read under,
 * its expressions and the operators under it — and what the arrangement of the plan is decided by: the names its
 * rows can be referred to by, and the rows a source holds.
 */
final class PlanNode {

    private final String operation;
    private final List<String> objects;
    private final String alias;
    private final List<String> expressions;
    private final List<PlanNode> children = new ArrayList<>();
    private final Set<String> names = new LinkedHashSet<>();
    private final Set<String> readColumns = new LinkedHashSet<>();
    private final List<String> conjuncts = new ArrayList<>();
    private List<PlanColumn> answered;
    private Long rows;
    private boolean scan;
    private boolean unread;
    private boolean hidden;
    private boolean untold;
    private boolean countedTwice;
    private final List<String> sortColumns = new ArrayList<>();
    private PlanNode loop;

    PlanNode(final String operation, final List<String> objects, final String alias,
             final List<String> expressions) {
        this.operation = operation;
        this.objects = new ArrayList<>(objects);
        this.alias = alias;
        this.expressions = new ArrayList<>(expressions);
    }

    /**
     * Puts an operator under this one.
     *
     * @param child the operator, or null for a source that reads as none
     * @return this operator
     */
    PlanNode over(final PlanNode child) {
        if (child != null) {
            children.add(child);
        }
        return this;
    }

    /**
     * Adds an expression to this operator's.
     *
     * @param expression the expression
     * @return this operator
     */
    PlanNode with(final String expression) {
        expressions.add(expression);
        return this;
    }

    /**
     * Replaces this operator's expressions.
     *
     * @param replaced the expressions
     * @return this operator
     */
    PlanNode expressed(final List<String> replaced) {
        final List<String> kept = new ArrayList<>(replaced);
        expressions.clear();
        expressions.addAll(kept);
        return this;
    }

    /**
     * An operator like this one, with its expressions and its conditions, over none of its children.
     *
     * @return the operator
     */
    PlanNode alike() {
        final PlanNode copy = new PlanNode(operation, objects, alias, expressions);
        copy.conjuncts.addAll(conjuncts);
        return copy;
    }

    /**
     * Adds conditions to a Filter's, which it lists as one expression: a condition as it is, several each in
     * parentheses joined with AND.
     *
     * @param added the conditions' texts
     * @return this operator
     */
    PlanNode filtering(final List<String> added) {
        conjuncts.addAll(added);
        expressions.clear();
        if (!conjuncts.isEmpty()) {
            expressions.add(PlanExpressionText.conjoined(conjuncts));
        }
        return this;
    }

    /**
     * Lists this scan as a Sort with no row limit reads it: its objects twice, the columns the sort keys read before
     * its own expressions, and its partitions counted twice in GlobalStats (live-verified: EXPLAIN SELECT a FROM et
     * ORDER BY b lists ET twice and B, A, B, under GlobalStats of 2, 2 and 2048).
     *
     * @param keyColumns the columns the sort keys read, in the order the scan reads them
     * @return this scan
     */
    PlanNode readForSort(final List<String> keyColumns) {
        if (!countedTwice) {
            final List<String> once = new ArrayList<>(objects);
            objects.addAll(once);
            countedTwice = true;
        }
        sortColumns.clear();
        sortColumns.addAll(keyColumns);
        return this;
    }

    /**
     * Lists this scan as read once again: a Sort with a row limit that replaces the Sort over it reads it so
     * (live-verified: an ORDER BY … LIMIT over a derived table's ORDER BY lists the table once).
     *
     * @return this scan
     */
    PlanNode readOnce() {
        if (countedTwice) {
            final List<String> once = new ArrayList<>(objects.subList(0, objects.size() / 2));
            objects.clear();
            objects.addAll(once);
            sortColumns.clear();
            countedTwice = false;
        }
        return this;
    }

    /** The columns a Sort's keys read, which this scan lists before its own (see {@link #readForSort}). */
    List<String> getSortColumns() {
        return sortColumns;
    }

    /** Whether GlobalStats counts this scan's partitions twice (see {@link #readForSort}). */
    boolean isCountedTwice() {
        return countedTwice;
    }

    /**
     * Records a column a scan reads.
     *
     * @param column the column's canonical name
     */
    void reads(final String column) {
        readColumns.add(column);
    }

    /** The columns a scan reads, by canonical name, in the order first read. */
    Set<String> getReadColumns() {
        return readColumns;
    }

    /**
     * Records the columns this operator's rows answer, as a query or a derived table answers them.
     *
     * @param columns the columns, or null where they are not known
     * @return this operator
     */
    PlanNode answering(final List<PlanColumn> columns) {
        answered = columns == null ? null : new ArrayList<>(columns);
        return this;
    }

    /** The columns this operator's rows answer; null where they are not known. */
    List<PlanColumn> getAnswered() {
        return answered;
    }

    /**
     * Puts an expression ahead of this operator's.
     *
     * @param expression the expression
     * @return this operator
     */
    PlanNode first(final String expression) {
        expressions.add(0, expression);
        return this;
    }

    /**
     * Makes this operator the scan of a table.
     *
     * @param held the rows the table holds
     * @return this operator
     */
    PlanNode scanning(final long held) {
        scan = true;
        rows = Long.valueOf(held);
        return this;
    }

    /**
     * Records the rows a source that is no table holds.
     *
     * @param held the rows, or null where they are not known
     * @return this operator
     */
    PlanNode holding(final Long held) {
        rows = held;
        return this;
    }

    /**
     * Marks a scan the statement reads no column from: it is listed, and assigned no partition.
     *
     * @return this operator
     */
    PlanNode unread() {
        unread = true;
        return this;
    }

    /**
     * Marks an operator a plan numbers and does not list.
     *
     * @return this operator
     */
    PlanNode hidden() {
        hidden = true;
        return this;
    }

    /**
     * Replaces the names this operator's rows are referred to by.
     *
     * @param referred the names, upper-cased
     * @return this operator
     */
    PlanNode named(final Set<String> referred) {
        final Set<String> kept = new LinkedHashSet<>(referred);
        names.clear();
        names.addAll(kept);
        return this;
    }

    String getOperation() {
        return operation;
    }

    List<String> getObjects() {
        return objects;
    }

    String getAlias() {
        return alias;
    }

    List<String> getExpressions() {
        return expressions;
    }

    List<PlanNode> getChildren() {
        return children;
    }

    Set<String> getNames() {
        return names;
    }

    /**
     * The rows a source holds: a table's stored rows, the tuples of a VALUES list, the rows a generator makes.
     *
     * @return the rows, or null for an operator that is no source or a source of unknown size
     */
    Long getRows() {
        return rows;
    }

    boolean isScan() {
        return scan;
    }

    boolean isUnread() {
        return unread;
    }

    /**
     * Makes this operator one the plan neither lists nor numbers, standing where a recursive term reads its own
     * common table expression: the operator over it feeds the RecursiveWithClause back.
     *
     * @param recursion the RecursiveWithClause the term reads
     * @return this operator
     */
    PlanNode readingRecursion(final PlanNode recursion) {
        untold = true;
        loop = recursion;
        return this;
    }

    /** Whether the plan neither lists nor numbers this operator (see {@link #readingRecursion}). */
    boolean isUntold() {
        return untold;
    }

    /** The RecursiveWithClause this operator reads back, or null. */
    PlanNode getLoop() {
        return loop;
    }

    boolean isHidden() {
        return hidden;
    }
}
