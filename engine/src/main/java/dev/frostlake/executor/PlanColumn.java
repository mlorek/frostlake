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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A column a relation or a query answers, as an EXPLAIN plan spells it: the name it is referred to by, the expression
 * it stands for, resolved — {@code ET.A}, {@code SUM(ET.A)} — its type where it is known, the scan columns reading it
 * takes, and the window calls it computes.
 */
final class PlanColumn {

    private final String name;
    private final String text;
    private final DataType type;
    private final List<PlanRead> reads;
    private final List<String> windows;
    private final boolean constantNull;
    private String owner;

    /**
     * @param name         the canonical name the column is referred to by, or null for an unnamed expression
     * @param text         the expression as the plan spells it
     * @param type         the column's type, or null where it is not known
     * @param reads        the scan columns reading it takes
     * @param windows      the window calls it computes, as the plan spells them
     * @param constantNull whether it is the bare NULL, which a Result does not list
     */
    PlanColumn(final String name, final String text, final DataType type, final List<PlanRead> reads,
               final List<String> windows, final boolean constantNull) {
        this.name = name;
        this.text = text;
        this.type = type;
        this.reads = new ArrayList<>(reads);
        this.windows = new ArrayList<>(windows);
        this.constantNull = constantNull;
    }

    /**
     * A column that reads one column of a scan.
     *
     * @param name the canonical name
     * @param text the column as the plan spells it
     * @param type the column's type
     * @param read the scan column it reads
     * @return the column
     */
    static PlanColumn scanned(final String name, final String text, final DataType type, final PlanRead read) {
        return new PlanColumn(name, text, type, Collections.singletonList(read), Collections.<String>emptyList(),
            false);
    }

    /**
     * This column under another name, as a column list after an alias renames it.
     *
     * @param renamed the canonical name
     * @return the column
     */
    PlanColumn named(final String renamed) {
        final PlanColumn column = new PlanColumn(renamed, text, type, reads, windows, constantNull);
        column.owner = owner;
        return column;
    }

    /**
     * This column as a relation of a FROM list answers it.
     *
     * @param relation the name the relation is referred to by, upper-cased
     * @return the column
     */
    PlanColumn ownedBy(final String relation) {
        final PlanColumn column = new PlanColumn(name, text, type, reads, windows, constantNull);
        column.owner = relation;
        return column;
    }

    /** The relation of the FROM list that answers this column, upper-cased; null for a select item. */
    String getOwner() {
        return owner;
    }

    String getName() {
        return name;
    }

    String getText() {
        return text;
    }

    DataType getType() {
        return type;
    }

    List<PlanRead> getReads() {
        return reads;
    }

    List<String> getWindows() {
        return windows;
    }

    boolean isConstantNull() {
        return constantNull;
    }

    /** Records every scan column reading this column takes. */
    void markReads() {
        for (final PlanRead read : reads) {
            read.mark();
        }
    }
}
