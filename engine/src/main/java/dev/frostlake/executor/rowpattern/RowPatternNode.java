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

package dev.frostlake.executor.rowpattern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A node of a MATCH_RECOGNIZE row pattern: a symbol, an anchor, or an operator over the nodes it holds.
 * {@code PERMUTE(a, b)} is read as the alternation of its orders, {@code a b | b a}, in the order of the parts
 * as written.
 */
public final class RowPatternNode {

    /** The greatest number of repetitions of a quantifier that names none. */
    public static final int UNBOUNDED = -1;

    private final RowPatternKind kind;
    private final String symbol;
    private final List<RowPatternNode> parts;
    private final int least;
    private final int greatest;
    private final boolean reluctant;

    private RowPatternNode(final RowPatternKind kind, final String symbol, final List<RowPatternNode> parts,
                           final int least, final int greatest, final boolean reluctant) {
        this.kind = kind;
        this.symbol = symbol;
        this.parts = parts;
        this.least = least;
        this.greatest = greatest;
        this.reluctant = reluctant;
    }

    /**
     * A symbol.
     *
     * @param name the symbol's name, upper-cased
     * @return the node
     */
    public static RowPatternNode symbol(final String name) {
        return new RowPatternNode(RowPatternKind.SYMBOL, name, Collections.<RowPatternNode>emptyList(), 1, 1, false);
    }

    /**
     * An anchor: the start or the end of the partition.
     *
     * @param kind {@link RowPatternKind#PARTITION_START} or {@link RowPatternKind#PARTITION_END}
     * @return the node
     */
    public static RowPatternNode anchor(final RowPatternKind kind) {
        return new RowPatternNode(kind, null, Collections.<RowPatternNode>emptyList(), 1, 1, false);
    }

    /**
     * A sequence, an alternation or an exclusion of parts. A sequence or an alternation of one part is that part.
     *
     * @param kind  the operator
     * @param parts the parts, in the order written
     * @return the node
     */
    public static RowPatternNode of(final RowPatternKind kind, final List<RowPatternNode> parts) {
        if (parts.size() == 1 && kind != RowPatternKind.EXCLUSION) {
            return parts.get(0);
        }
        return new RowPatternNode(kind, null, new ArrayList<>(parts), 1, 1, false);
    }

    /**
     * A part under a quantifier.
     *
     * @param part      the part repeated
     * @param least     the least number of repetitions
     * @param greatest  the greatest, or {@link #UNBOUNDED}
     * @param reluctant whether the fewest repetitions are tried first
     * @return the node
     */
    public static RowPatternNode quantified(final RowPatternNode part, final int least, final int greatest,
                                            final boolean reluctant) {
        final List<RowPatternNode> parts = new ArrayList<>();
        parts.add(part);
        return new RowPatternNode(RowPatternKind.QUANTIFIED, null, parts, least, greatest, reluctant);
    }

    /**
     * Every order of some parts, as the alternation {@code PERMUTE} stands for: the orders run from the parts as
     * written to their reverse.
     *
     * @param parts the parts, in the order written
     * @return the alternation of their orders
     */
    public static RowPatternNode permutation(final List<RowPatternNode> parts) {
        final List<RowPatternNode> orders = new ArrayList<>();
        permute(parts, new ArrayList<RowPatternNode>(), new boolean[parts.size()], orders);
        return of(RowPatternKind.ALTERNATION, orders);
    }

    private static void permute(final List<RowPatternNode> parts, final List<RowPatternNode> order, final boolean[] taken,
                                final List<RowPatternNode> into) {
        if (order.size() == parts.size()) {
            into.add(of(RowPatternKind.SEQUENCE, order));
            return;
        }
        for (int i = 0; i < parts.size(); i++) {
            if (taken[i]) {
                continue;
            }
            taken[i] = true;
            order.add(parts.get(i));
            permute(parts, order, taken, into);
            order.remove(order.size() - 1);
            taken[i] = false;
        }
    }

    public RowPatternKind getKind() {
        return kind;
    }

    /** The symbol's name, upper-cased; null for every other node. */
    public String getSymbol() {
        return symbol;
    }

    public List<RowPatternNode> getParts() {
        return parts;
    }

    public int getLeast() {
        return least;
    }

    /** The greatest number of repetitions, or {@link #UNBOUNDED}. */
    public int getGreatest() {
        return greatest;
    }

    public boolean isReluctant() {
        return reluctant;
    }

    /**
     * Collects the symbols the pattern names.
     *
     * @param into the set the names are added to
     */
    public void collectSymbols(final Set<String> into) {
        if (kind == RowPatternKind.SYMBOL) {
            into.add(symbol);
        }
        for (final RowPatternNode part : parts) {
            part.collectSymbols(into);
        }
    }

    /**
     * Whether every match of the pattern holds a row of the given symbol: every part of an alternation does, one
     * part of a sequence does, and a quantified part does when it is repeated at least once.
     *
     * @param name the symbol's name, upper-cased
     * @return true when no match can do without the symbol
     */
    public boolean alwaysMatches(final String name) {
        switch (kind) {
            case SYMBOL:
                return symbol.equals(name);
            case ALTERNATION:
                for (final RowPatternNode part : parts) {
                    if (!part.alwaysMatches(name)) {
                        return false;
                    }
                }
                return true;
            case QUANTIFIED:
                return least > 0 && parts.get(0).alwaysMatches(name);
            case SEQUENCE:
            case EXCLUSION:
                for (final RowPatternNode part : parts) {
                    if (part.alwaysMatches(name)) {
                        return true;
                    }
                }
                return false;
            default:
                return false;
        }
    }

    /** Whether the pattern holds an exclusion, {@code {- … -}}. */
    public boolean holdsExclusion() {
        if (kind == RowPatternKind.EXCLUSION) {
            return true;
        }
        for (final RowPatternNode part : parts) {
            if (part.holdsExclusion()) {
                return true;
            }
        }
        return false;
    }
}
