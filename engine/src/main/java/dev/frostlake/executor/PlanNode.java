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
    private Long rows;
    private boolean scan;
    private boolean unread;
    private boolean hidden;

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

    boolean isHidden() {
        return hidden;
    }
}
