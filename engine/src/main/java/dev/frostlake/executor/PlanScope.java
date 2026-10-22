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

import dev.frostlake.executor.expressions.ColumnReferenceExpression;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The names one SELECT of an EXPLAIN plan resolves: the relations of its FROM list, in the order written, and its
 * select items. A qualified name is read by the relation its qualifier names; a bare one by the first relation that
 * answers it — the left one for a column a USING or a NATURAL join shares — and otherwise by the select item it is
 * the alias of; a name this SELECT does not resolve is read in the enclosing one.
 */
final class PlanScope {

    private final PlanScope outer;
    private final List<PlanFromItem> items = new ArrayList<>();
    private final List<PlanColumn> selected = new ArrayList<>();

    /** The names the plan gives the select list's sequence reads, by sequence, in the order they are read. */
    private final Map<String, Deque<String>> sequenceReads = new HashMap<>();

    /**
     * @param outer the scope of the enclosing SELECT, or null
     */
    PlanScope(final PlanScope outer) {
        this.outer = outer;
    }

    void add(final PlanFromItem item) {
        items.add(item);
    }

    List<PlanFromItem> getItems() {
        return items;
    }

    /**
     * Records the select items, which aliases and positions name.
     *
     * @param columns the items, in order
     */
    void select(final List<PlanColumn> columns) {
        selected.clear();
        selected.addAll(columns);
    }

    List<PlanColumn> getSelected() {
        return selected;
    }

    /**
     * Names the select list's sequence reads in the order written: the first read of a sequence is
     * {@code getNextVal_SQ}, each later one {@code getNextVal_SQ_0}, {@code getNextVal_SQ_1} and on (live-verified).
     *
     * @param sequences each read's sequence, upper-cased, in the order written
     */
    void readSequences(final List<String> sequences) {
        sequenceReads.clear();
        final Map<String, Integer> seen = new HashMap<>();
        for (final String sequence : sequences) {
            final Integer before = seen.get(sequence);
            final int count = before == null ? 0 : before.intValue();
            seen.put(sequence, Integer.valueOf(count + 1));
            Deque<String> names = sequenceReads.get(sequence);
            if (names == null) {
                names = new ArrayDeque<>();
                sequenceReads.put(sequence, names);
            }
            names.add("getNextVal_" + sequence + (count == 0 ? "" : "_" + (count - 1)));
        }
    }

    /**
     * The name of the next read of a sequence the select list reads, taken in the order written.
     *
     * @param sequence the sequence's name, as the reference's qualifier ends
     * @return the name, or null once the select list reads it no more
     */
    String nextSequenceRead(final String sequence) {
        final Deque<String> names = sequenceReads.get(sequence.toUpperCase(Locale.ROOT));
        return names == null ? null : names.poll();
    }

    /** The select item at a position counted from one; null past the list. */
    PlanColumn selectedAt(final int position) {
        return position >= 1 && position <= selected.size() ? selected.get(position - 1) : null;
    }

    /** The relation a qualifier names here; null where none of this SELECT's is so named. */
    PlanFromItem item(final String qualifier) {
        for (final PlanFromItem item : items) {
            if (item.getKey().equalsIgnoreCase(qualifier)) {
                return item;
            }
        }
        return null;
    }

    /**
     * The column a reference names, or null where neither this SELECT nor an enclosing one resolves it.
     *
     * @param reference the reference
     * @return the column
     */
    PlanColumn resolve(final ColumnReferenceExpression reference) {
        final String name = reference.getColumnName();
        if (name == null) {
            return null;
        }
        if (reference.isQualified()) {
            final String qualifier = reference.getTableName();
            final int dot = qualifier.lastIndexOf('.');
            final PlanFromItem item = item(dot < 0 ? qualifier : qualifier.substring(dot + 1));
            if (item != null) {
                return item.column(name);
            }
            return outer == null ? null : outer.resolve(reference);
        }
        for (final PlanFromItem item : items) {
            final PlanColumn column = item.column(name);
            if (column != null) {
                return column;
            }
        }
        for (final PlanColumn column : selected) {
            if (name.equals(column.getName())) {
                return column;
            }
        }
        return outer == null ? null : outer.resolve(reference);
    }

    /**
     * The column of a name in the first of the given relations that answers it, as a USING column is read on each
     * side of its join.
     *
     * @param keys the relations, by name
     * @param name the column's canonical name
     * @return the column, or null
     */
    PlanColumn resolveIn(final Set<String> keys, final String name) {
        for (final PlanFromItem item : items) {
            if (keys.contains(item.getKey())) {
                final PlanColumn column = item.column(name);
                if (column != null) {
                    return column;
                }
            }
        }
        return null;
    }
}
