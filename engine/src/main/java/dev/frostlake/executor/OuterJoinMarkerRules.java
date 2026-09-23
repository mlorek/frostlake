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

import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The Oracle {@code (+)} outer-join markers of a query's WHERE, judged as live judges them once the names resolve and
 * before any argument or predicate type (live-verified, every rule). The WHERE is read as its top-level AND conjuncts,
 * through the parentheses round a conjunction: {@code (a = b (+) AND a = 2)} is two conjuncts, as it is unbracketed.
 *
 * <ul>
 *   <li>a conjunct naming columns of exactly two relations outer-joins each marked relation to the other: its rows
 *       are null-supplied to the other's; a conjunct naming one relation, or three, joins nothing;</li>
 *   <li>conjunct by conjunct as written, those whose markers stand under no OR are judged first: one reading a
 *       relation through a marked column and an unmarked one is refused ({@code Outer join column 'T2.B(+)' appears
 *       in expression with non-outer join column 'T2.B'.}, naming the last of each written); a relation outer-joined
 *       to a second relation is refused there ({@code Table 'T2' is outer joined to multiple tables: 'T3' and
 *       'T'.}, the later partner first); and so is a conjunct marking both its relations, a cycle of its own;</li>
 *   <li>then relations outer-joined round a cycle are refused: {@code Outer join predicates form a cycle between 'T'
 *       and 'T2'.}, naming the greatest name in the cycle and the relation outer-joined to it;</li>
 *   <li>then, conjunct by conjunct as written, a marker under an OR is refused ({@code Outer join column
 *       'T2.B(+)' appears in an OR predicate.}), and so is a marked column whose relation no conjunct outer-joins
 *       ({@code Column 'T.A(+)' not from an outer joined table.}).</li>
 * </ul>
 *
 * <p>A relation is named as the query names it, its alias when it has one.
 */
final class OuterJoinMarkerRules {

    private OuterJoinMarkerRules() {
    }

    /**
     * The top-level AND conjuncts of a WHERE, read through the parentheses round a conjunction.
     *
     * @param where the WHERE's condition
     * @return its conjuncts, in written order
     */
    static List<FrostlakeParser.BooleanExprContext> conjuncts(final FrostlakeParser.BooleanExprContext where) {
        final List<FrostlakeParser.BooleanExprContext> conjuncts = new ArrayList<>();
        split(where, conjuncts);
        return conjuncts;
    }

    /**
     * The relations the WHERE's markers null-supply, by the name each answers to.
     *
     * @param where     the WHERE's condition
     * @param relations the query's relations, by name
     * @return the marked relations' names
     */
    static Set<String> markedRelations(final ParseTree where, final Map<String, Table> relations) {
        final List<ParseTree> markers = new ArrayList<>();
        final List<ParseTree> unused = new ArrayList<>();
        collect(where, false, markers, unused, new ArrayList<ParseTree>());
        final Set<String> names = new LinkedHashSet<>();
        for (final ParseTree marker : markers) {
            final String relation = relationOf(nameOf(marker), relations);
            if (relation != null) {
                names.add(relation);
            }
        }
        return names;
    }

    /**
     * Refuse the first marker rule the WHERE breaks.
     *
     * @param where     the WHERE's condition
     * @param relations the query's relations, by name
     */
    static void validate(final FrostlakeParser.BooleanExprContext where, final Map<String, Table> relations) {
        final List<FrostlakeParser.BooleanExprContext> conjuncts = conjuncts(where);
        final List<List<ParseTree>> markersOf = new ArrayList<>();
        final List<List<ParseTree>> orMarkersOf = new ArrayList<>();
        final List<List<ParseTree>> namesOf = new ArrayList<>();
        boolean anyMarker = false;
        for (final FrostlakeParser.BooleanExprContext conjunct : conjuncts) {
            final List<ParseTree> markers = new ArrayList<>();
            final List<ParseTree> orMarkers = new ArrayList<>();
            final List<ParseTree> names = new ArrayList<>();
            collect(conjunct, false, markers, orMarkers, names);
            markersOf.add(markers);
            orMarkersOf.add(orMarkers);
            namesOf.add(names);
            anyMarker = anyMarker || !markers.isEmpty();
        }
        if (!anyMarker) {
            return;
        }
        // Each marked relation's partner, the relation it is null-supplied to.
        final Map<String, String> partners = new LinkedHashMap<>();
        for (int i = 0; i < conjuncts.size(); i++) {
            if (markersOf.get(i).isEmpty() || !orMarkersOf.get(i).isEmpty()) {
                continue;
            }
            rejectMixedColumns(namesOf.get(i), relations);
            final Set<String> referenced = new LinkedHashSet<>();
            for (final ParseTree name : namesOf.get(i)) {
                final String relation = relationOf(nameOf(name), relations);
                if (relation != null) {
                    referenced.add(relation);
                }
            }
            if (referenced.size() != 2) {
                continue;
            }
            final Set<String> markedHere = new LinkedHashSet<>();
            for (final ParseTree marker : markersOf.get(i)) {
                final String marked = relationOf(nameOf(marker), relations);
                if (marked == null) {
                    continue;
                }
                markedHere.add(marked);
                String other = null;
                for (final String relation : referenced) {
                    if (!relation.equals(marked)) {
                        other = relation;
                    }
                }
                final String known = partners.get(marked);
                if (known == null) {
                    partners.put(marked, other);
                } else if (!known.equals(other)) {
                    throw new RuntimeException(SqlCompilationError.of("Table '" + marked
                        + "' is outer joined to multiple tables: '" + other + "' and '" + known + "'."));
                }
            }
            if (markedHere.size() == 2) {
                // Both relations of one conjunct outer-joined to each other: a cycle there and then.
                rejectCycle(partners);
            }
        }
        rejectCycle(partners);
        for (int i = 0; i < conjuncts.size(); i++) {
            if (!orMarkersOf.get(i).isEmpty()) {
                throw new RuntimeException(SqlCompilationError.of("Outer join column '"
                    + echo(orMarkersOf.get(i).get(0), relations) + "' appears in an OR predicate."));
            }
            for (final ParseTree marker : markersOf.get(i)) {
                final String marked = relationOf(nameOf(marker), relations);
                if (marked != null && !partners.containsKey(marked)) {
                    throw new RuntimeException(SqlCompilationError.of("Column '" + echo(marker, relations)
                        + "' not from an outer joined table."));
                }
            }
        }
    }

    /** Refuse a cycle among the partners, naming its greatest relation and the one outer-joined to it. */
    private static void rejectCycle(final Map<String, String> partners) {
        for (final String start : partners.keySet()) {
            final List<String> walk = new ArrayList<>();
            String node = start;
            while (node != null && !walk.contains(node)) {
                walk.add(node);
                node = partners.get(node);
            }
            if (node == null) {
                continue;
            }
            final List<String> cycle = walk.subList(walk.indexOf(node), walk.size());
            String greatest = cycle.get(0);
            for (final String member : cycle) {
                if (member.compareTo(greatest) > 0) {
                    greatest = member;
                }
            }
            String before = null;
            for (final String member : cycle) {
                if (greatest.equals(partners.get(member))) {
                    before = member;
                }
            }
            throw new RuntimeException(SqlCompilationError.of("Outer join predicates form a cycle between '"
                + before + "' and '" + greatest + "'."));
        }
    }

    /**
     * Refuse a conjunct reading one relation through a marked column and an unmarked one, naming the last marked and
     * the last unmarked column of that relation as written.
     */
    private static void rejectMixedColumns(final List<ParseTree> names, final Map<String, Table> relations) {
        final Map<String, ParseTree> lastMarked = new LinkedHashMap<>();
        final Map<String, ParseTree> lastUnmarked = new LinkedHashMap<>();
        for (final ParseTree name : names) {
            final String relation = relationOf(nameOf(name), relations);
            if (relation != null) {
                (isMarker(name) ? lastMarked : lastUnmarked).put(relation, name);
            }
        }
        for (final Map.Entry<String, ParseTree> marked : lastMarked.entrySet()) {
            final ParseTree unmarked = lastUnmarked.get(marked.getKey());
            if (unmarked != null) {
                throw new RuntimeException(SqlCompilationError.of("Outer join column '"
                    + echo(marked.getValue(), relations) + "' appears in expression with non-outer join column '"
                    + spelled(unmarked, relations) + "'."));
            }
        }
    }

    /** The top-level AND conjuncts of a condition, read through the parentheses round a conjunction. */
    private static void split(final FrostlakeParser.BooleanExprContext condition,
                              final List<FrostlakeParser.BooleanExprContext> out) {
        if (condition instanceof FrostlakeParser.AndExprContext) {
            final FrostlakeParser.AndExprContext and = (FrostlakeParser.AndExprContext) condition;
            split(and.booleanExpr(0), out);
            split(and.booleanExpr(1), out);
        } else if (condition instanceof FrostlakeParser.ValueExprContext
                && ((FrostlakeParser.ValueExprContext) condition).expression()
                    instanceof FrostlakeParser.ParenExprContext) {
            split(((FrostlakeParser.ParenExprContext) ((FrostlakeParser.ValueExprContext) condition).expression())
                .booleanExpr(), out);
        } else {
            out.add(condition);
        }
    }

    /**
     * The markers, the markers under an OR, and every column reference, marked or not, in written order under
     * {@code node}; a query nested in it has names of its own.
     */
    private static void collect(final ParseTree node, final boolean underOr, final List<ParseTree> markers,
                                final List<ParseTree> orMarkers, final List<ParseTree> names) {
        if (node == null || node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (isMarker(node)) {
            markers.add(node);
            names.add(node);
            if (underOr) {
                orMarkers.add(node);
            }
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            names.add(node);
            return;
        }
        final boolean or = underOr || node instanceof FrostlakeParser.OrExprContext;
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), or, markers, orMarkers, names);
        }
    }

    /** A column written with the marker: {@code a(+)}, or a column read as an operand of it. */
    private static boolean isMarker(final ParseTree node) {
        return node instanceof FrostlakeParser.OuterJoinColumnExprContext
            || (node instanceof FrostlakeParser.OuterJoinOperandExprContext
                && ((FrostlakeParser.OuterJoinOperandExprContext) node).expression()
                    instanceof FrostlakeParser.QualifiedNameExprContext);
    }

    /** The column name a reference or a marker is written with. */
    private static FrostlakeParser.QualifiedNameContext nameOf(final ParseTree node) {
        if (node instanceof FrostlakeParser.OuterJoinColumnExprContext) {
            return ((FrostlakeParser.OuterJoinColumnExprContext) node).qualifiedName();
        }
        if (node instanceof FrostlakeParser.OuterJoinOperandExprContext) {
            return ((FrostlakeParser.QualifiedNameExprContext)
                ((FrostlakeParser.OuterJoinOperandExprContext) node).expression()).qualifiedName();
        }
        return ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName();
    }

    /** The relation a column reference reads, by the name it answers to, or null when none or two carry it. */
    private static String relationOf(final FrostlakeParser.QualifiedNameContext name,
                                     final Map<String, Table> relations) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name);
        if (parts.length >= 2) {
            final String qualifier = parts[parts.length - 2];
            for (final String relation : relations.keySet()) {
                if (relation.equalsIgnoreCase(qualifier)) {
                    return relation;
                }
            }
            return null;
        }
        String found = null;
        for (final Map.Entry<String, Table> relation : relations.entrySet()) {
            if (relation.getValue().hasColumnExactly(parts[0])) {
                if (found != null) {
                    return null;
                }
                found = relation.getKey();
            }
        }
        return found;
    }

    /** A marked column as the refusals name it: its relation, its column and the marker. */
    private static String echo(final ParseTree marker, final Map<String, Table> relations) {
        return spelled(marker, relations) + "(+)";
    }

    /** A column as the refusals name it: its relation and its column. */
    private static String spelled(final ParseTree column, final Map<String, Table> relations) {
        final String[] parts = ParseTreeText.qualifiedNameParts(nameOf(column));
        final String relation = relationOf(nameOf(column), relations);
        return (relation != null ? relation + "." : "") + parts[parts.length - 1];
    }
}
