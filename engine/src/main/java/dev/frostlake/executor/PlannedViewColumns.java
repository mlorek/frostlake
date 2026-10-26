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
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The columns a refused GENERATOR bound reads through a view the account's plan makes of its own, which the refusal
 * names {@code SYS_VW.N_k} (all live-verified). An outer join's relations, LEFT, RIGHT or FULL, become one such view,
 * and a CTE over a set operation another; their columns are numbered from 0 among those the query reads, the join's
 * relations in the order written and a relation's columns in the order it declares them. Over
 * {@code g6 (n, m) LEFT JOIN g1 ON g1.n = g6.n} the query reads g6.n and g1.n, so {@code ROWCOUNT => g1.n} is
 * {@code found 'SYS_VW.N_1'}, and {@code WITH c AS (SELECT 4 AS n UNION ALL SELECT 5)} makes {@code c.n}
 * {@code SYS_VW.N_0}; a derived table over a set operation keeps its name, {@code D.N}.
 */
final class PlannedViewColumns {

    private static final String VIEW = "SYS_VW.N_";

    private PlannedViewColumns() {
    }

    /**
     * A bound's text with each column it reads through such a view written as the view's.
     *
     * @param executor   the executor whose catalog declares the tables' columns
     * @param expression the bound, or what is left of it
     * @return the text, the expression's own where it reads no such column
     */
    static String rewritten(final QueryExecutor executor, final ParserRuleContext expression) {
        final String written = ParseTreeText.getOriginalText(expression);
        if (expression.getStart() == null || expression.getStop() == null) {
            return written;
        }
        final List<FrostlakeParser.QualifiedNameExprContext> columns = new ArrayList<>();
        collect(expression, columns);
        final StringBuilder text = new StringBuilder(written);
        final int base = expression.getStart().getStartIndex();
        for (int i = columns.size() - 1; i >= 0; i--) {
            final FrostlakeParser.QualifiedNameExprContext column = columns.get(i);
            final String viewed = viewed(executor, column);
            if (viewed != null) {
                text.replace(column.getStart().getStartIndex() - base, column.getStop().getStopIndex() - base + 1,
                    viewed);
            }
        }
        return text.toString();
    }

    /** The view's name for a column, or null where it reads no view of the plan's own. */
    private static String viewed(final QueryExecutor executor,
                                 final FrostlakeParser.QualifiedNameExprContext column) {
        final String[] parts = ParseTreeText.qualifiedNameParts(column.qualifiedName());
        if (parts.length < 2) {
            return null;
        }
        final String qualifier = parts[parts.length - 2].toUpperCase(Locale.ROOT);
        final FrostlakeParser.TableReferenceContext item = DerivedRelationPins.fromItem(column, qualifier);
        final FrostlakeParser.TableExpressionContext from = item == null ? null : fromOf(item);
        if (from == null || !(from.getParent() instanceof FrostlakeParser.SelectClauseContext)) {
            return null;
        }
        List<FrostlakeParser.TableReferenceContext> relations = outerJoined(from, item);
        if (relations == null) {
            if (!overSetOperation(item)) {
                return null;
            }
            relations = new ArrayList<>();
            relations.add(item);
        }
        final List<Long> read = readColumns(executor, (FrostlakeParser.SelectClauseContext) from.getParent(),
            relations);
        final int position = positionOf(executor, item, parts[parts.length - 1]);
        final int index = read.indexOf(Long.valueOf(key(relations.indexOf(item), position)));
        return position < 0 || index < 0 ? null : VIEW + index;
    }

    private static FrostlakeParser.TableExpressionContext fromOf(final FrostlakeParser.TableReferenceContext item) {
        final ParseTree parent = item.getParent();
        if (parent instanceof FrostlakeParser.TableExpressionContext) {
            return (FrostlakeParser.TableExpressionContext) parent;
        }
        return parent instanceof FrostlakeParser.JoinClauseContext
            && parent.getParent() instanceof FrostlakeParser.TableExpressionContext
            ? (FrostlakeParser.TableExpressionContext) parent.getParent() : null;
    }

    /** The relations joined with the item since the last comma, where an outer join is among them; null otherwise. */
    private static List<FrostlakeParser.TableReferenceContext> outerJoined(
            final FrostlakeParser.TableExpressionContext from, final FrostlakeParser.TableReferenceContext item) {
        List<FrostlakeParser.TableReferenceContext> group = new ArrayList<>();
        boolean outer = false;
        boolean holdsItem = false;
        for (int i = 0; i < from.getChildCount(); i++) {
            final ParseTree child = from.getChild(i);
            if (child instanceof FrostlakeParser.TableReferenceContext) {
                if (holdsItem) {
                    break;
                }
                group = new ArrayList<>();
                outer = false;
                group.add((FrostlakeParser.TableReferenceContext) child);
                holdsItem = child == item;
            } else if (child instanceof FrostlakeParser.JoinClauseContext) {
                final FrostlakeParser.JoinClauseContext join = (FrostlakeParser.JoinClauseContext) child;
                final FrostlakeParser.JoinTypeContext type = join.joinType();
                outer |= type != null && (type.LEFT() != null || type.RIGHT() != null || type.FULL() != null);
                group.add(join.tableReference());
                holdsItem |= join.tableReference() == item;
            }
        }
        return holdsItem && outer ? group : null;
    }

    /** Whether a FROM item names a CTE whose body is a set operation. */
    private static boolean overSetOperation(final FrostlakeParser.TableReferenceContext item) {
        final FrostlakeParser.TableSourceContext source = item.tableSource();
        if (source == null || source.tableQualifiedName() == null) {
            return false;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
        final FrostlakeParser.CteDefinitionContext cte = parts.length == 1
            ? DerivedRelationPins.cteNamed(item, parts[0]) : null;
        return cte != null && cte.selectStatement().selectOperand().size() > 1;
    }

    /**
     * The columns a select reads of some relations, in order: the relations as listed, each one's columns in the
     * order it declares them, each as {@link #key}.
     */
    private static List<Long> readColumns(final QueryExecutor executor,
                                          final FrostlakeParser.SelectClauseContext select,
                                          final List<FrostlakeParser.TableReferenceContext> relations) {
        final List<Long> read = new ArrayList<>();
        final List<FrostlakeParser.QualifiedNameExprContext> columns = new ArrayList<>();
        collect(select, columns);
        for (final FrostlakeParser.QualifiedNameExprContext column : columns) {
            final String[] parts = ParseTreeText.qualifiedNameParts(column.qualifiedName());
            final String name = parts[parts.length - 1];
            for (int r = 0; r < relations.size(); r++) {
                final FrostlakeParser.TableReferenceContext relation = relations.get(r);
                final boolean named = parts.length == 1 || parts.length == 2
                    && parts[0].toUpperCase(Locale.ROOT).equals(GeneratorBounds.qualifierOf(relation));
                final int position = named ? positionOf(executor, relation, name) : -1;
                if (position >= 0 && !read.contains(Long.valueOf(key(r, position)))) {
                    read.add(Long.valueOf(key(r, position)));
                }
            }
        }
        Collections.sort(read);
        return read;
    }

    /** A relation's column by its place, ordered as the view numbers them. */
    private static long key(final int relation, final int position) {
        return (long) relation << 32 | position;
    }

    /** The place a relation declares a column at: a catalog table's, a CTE's; -1 where it is not known. */
    private static int positionOf(final QueryExecutor executor, final FrostlakeParser.TableReferenceContext relation,
                                  final String column) {
        final FrostlakeParser.TableSourceContext source = relation.tableSource();
        if (source == null || source.tableQualifiedName() == null) {
            return -1;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
        final FrostlakeParser.CteDefinitionContext cte = parts.length == 1
            ? DerivedRelationPins.cteNamed(relation, parts[0]) : null;
        if (cte != null) {
            return ctePosition(cte, column);
        }
        try {
            final Table table = executor.getCatalog().resolveTable(
                ParseTreeText.getQualifiedName(source.tableQualifiedName()));
            return table == null || !table.hasColumn(column) ? -1
                : table.getColumnIndex(table.getColumn(column).getName());
        } catch (final RuntimeException noTable) {
            return -1;
        }
    }

    private static int ctePosition(final FrostlakeParser.CteDefinitionContext cte, final String column) {
        if (cte.columnListOptional() != null) {
            final List<FrostlakeParser.NamePartContext> names = cte.columnListOptional().namePart();
            for (int i = 0; i < names.size(); i++) {
                if (column.equalsIgnoreCase(ParseTreeText.namePartText(names.get(i)))) {
                    return i;
                }
            }
            return -1;
        }
        final FrostlakeParser.SelectClauseContext first = cte.selectStatement().selectOperand(0).selectClause();
        if (first == null) {
            return -1;
        }
        final List<FrostlakeParser.SelectItemContext> items = first.selectList().selectItem();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof FrostlakeParser.ExprItemContext && column.equalsIgnoreCase(
                    DerivedRelationPins.labelOf((FrostlakeParser.ExprItemContext) items.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    private static void collect(final ParseTree node, final List<FrostlakeParser.QualifiedNameExprContext> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            into.add((FrostlakeParser.QualifiedNameExprContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), into);
        }
    }
}
