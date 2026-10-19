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
import java.util.List;
import java.util.Map;

/**
 * The names a select list gives its output columns, as an ORDER BY key reads them. A bare name inside a key
 * resolves against the output FIRST — an alias, a star's REPLACE or RENAME column, the column an unaliased item
 * projects — and only then against the relations, so {@code SELECT id * -1 AS id … ORDER BY id + 0} sorts by the
 * negated value and {@code ORDER BY fz.id + 0} by the stored one. That holds wherever the name stands in the key:
 * an operand, a function argument, a CASE branch, an aggregate's argument, a window's own PARTITION BY or
 * ORDER BY. A subquery in the key sees none of them (all live-verified).
 *
 * <p>A key is answered by writing each such name out as the expression that defines it, so every path that sorts
 * — over the source rows, over a group, in the window stage — evaluates an ordinary expression. An alias's
 * definition is itself read as the select list reads it: a name in it means a column of the relations when one
 * carries it, and otherwise the alias an EARLIER item gave ({@code SELECT id * -1 AS x, x + 1 AS y … ORDER BY
 * y + 0}).
 *
 * <p>A name two output columns carry is left as written: the refusal belongs to {@link AmbiguousOutputNames}.
 */
final class OrderKeyOutputNames {

    private final QueryExecutor executor;
    private final List<FrostlakeParser.SelectItemContext> items;
    private final Table table;
    private final Map<String, Table> aliasToTable;
    private final List<Table> relations;
    /** The output columns in select-list order, built on first use. */
    private List<OutputColumn> outputs;

    /**
     * @param executor     the executor, for expanding a star
     * @param clause       the select clause whose output the keys read
     * @param table        the relation the query reads, or null
     * @param aliasToTable its relations by the name the query gives each, or null
     * @param relations    every relation in scope, or null
     */
    OrderKeyOutputNames(final QueryExecutor executor, final FrostlakeParser.SelectClauseContext clause,
                        final Table table, final Map<String, Table> aliasToTable, final List<Table> relations) {
        this.executor = executor;
        this.items = clause.selectList().selectItem();
        this.table = table;
        this.aliasToTable = aliasToTable;
        this.relations = relations;
    }

    /**
     * A key with every output name it reads written out.
     *
     * @param key the key as the statement spells it
     * @return the key's text, unchanged when it reads no output name
     */
    String expand(final ParserRuleContext key) {
        return rewrite(key, false);
    }

    /**
     * A grouped query's key, where an alias standing outside every aggregate call is already answered from the
     * group's own output row: only a name inside an aggregate's argument, or one a relation's column would
     * otherwise claim, is written out.
     *
     * @param key the key as the statement spells it
     * @return the key's text, unchanged when nothing in it needs writing out
     */
    String expandOverGroups(final ParserRuleContext key) {
        return rewrite(key, true);
    }

    private String rewrite(final ParserRuleContext key, final boolean grouped) {
        final List<TextReplacement> replacements = new ArrayList<>();
        final String whole = wholeKeyDefinition(key, grouped);
        if (whole != null) {
            return whole;
        }
        collectOutputReads(key, grouped, false, replacements);
        return TextReplacement.apply(key, replacements);
    }

    /** The definition a key written as one output name stands for, unparenthesized, or null. */
    private String wholeKeyDefinition(final ParserRuleContext key, final boolean grouped) {
        ParseTree node = key;
        while (node.getChildCount() == 1 && !(node instanceof FrostlakeParser.QualifiedNameExprContext)) {
            node = node.getChild(0);
        }
        if (!(node instanceof FrostlakeParser.QualifiedNameExprContext)) {
            return null;
        }
        final OutputColumn read = outputRead((FrostlakeParser.QualifiedNameExprContext) node);
        if (read == null || grouped && !carriedByARelation(read.name)) {
            return null;
        }
        return read.definition();
    }

    private void collectOutputReads(final ParseTree node, final boolean grouped, final boolean aggregated,
                                    final List<TextReplacement> out) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameExprContext name = (FrostlakeParser.QualifiedNameExprContext) node;
            final OutputColumn read = outputRead(name);
            if (read != null && (!grouped || aggregated || carriedByARelation(read.name))) {
                out.add(new TextReplacement(name, "(" + read.definition() + ")"));
            }
            return;
        }
        boolean inside = aggregated;
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            inside = inside || call.overClause() == null
                && executor.getFunctionRegistry().hasAggregateFunction(call.functionName().getText());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectOutputReads(node.getChild(i), grouped, inside, out);
        }
    }

    /**
     * The output column a bare name reads through an expression of its own, or null: when no output column
     * carries the name, when more than one does, or when the one that does is a relation's column as it stands.
     */
    private OutputColumn outputRead(final FrostlakeParser.QualifiedNameExprContext reference) {
        final String[] parts = ParseTreeText.qualifiedNameParts(reference.qualifiedName());
        if (parts.length != 1) {
            return null;
        }
        OutputColumn found = null;
        for (final OutputColumn output : outputs()) {
            if (output.name.equals(parts[0])) {
                if (found != null) {
                    return null;
                }
                found = output;
            }
        }
        return found == null || !found.derived ? null : found;
    }

    private List<OutputColumn> outputs() {
        if (outputs != null) {
            return outputs;
        }
        outputs = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            final FrostlakeParser.SelectItemContext item = items.get(i);
            if (SelectItemAccessors.isExprItem(item)) {
                final ParserRuleContext expression = SelectItemAccessors.getItemExpression(item);
                if (expression == null) {
                    continue;
                }
                final String alias = SelectItemAccessors.getItemAlias(item);
                if (alias != null) {
                    outputs.add(new OutputColumn(alias, true, this, i, null));
                    continue;
                }
                final String projected = projectedColumnName(item);
                if (projected != null) {
                    outputs.add(new OutputColumn(projected, false, this, i, null));
                }
                continue;
            }
            if (table == null || SelectItemAccessors.isObjectStarItem(item)
                    || !SelectItemAccessors.isStarItem(item) && !SelectItemAccessors.isQualifiedStarItem(item)) {
                continue;
            }
            try {
                for (final StarColumn column : executor.starItemColumns(item, table, aliasToTable)) {
                    outputs.add(new OutputColumn(column.getOutputName(), column.isReplaced() || column.isRenamed(),
                        this, -1, column.getExpression()));
                }
            } catch (final RuntimeException unexpandable) {
                // A star that cannot be expanded names no output column here; its own refusal is raised elsewhere.
            }
        }
        return outputs;
    }

    /** The column an unaliased item written as one (possibly qualified) name projects, or null. */
    private static String projectedColumnName(final FrostlakeParser.SelectItemContext item) {
        final FrostlakeParser.ExpressionContext value = SelectItemAccessors.getItemValueExpr(item);
        if (!(value instanceof FrostlakeParser.QualifiedNameExprContext)) {
            return null;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(
            ((FrostlakeParser.QualifiedNameExprContext) value).qualifiedName());
        return parts.length == 0 ? null : parts[parts.length - 1];
    }

    /**
     * A select item's expression as the select list reads it: each bare name no relation carries, and an earlier
     * item gave as its alias, written out as that item's own definition.
     *
     * @param index the item's place in the select list
     * @return the expression's text
     */
    String definitionOf(final int index) {
        final ParserRuleContext expression = SelectItemAccessors.getItemExpression(items.get(index));
        final List<TextReplacement> replacements = new ArrayList<>();
        collectEarlierAliasReads(expression, index, replacements);
        return TextReplacement.apply(expression, replacements);
    }

    private void collectEarlierAliasReads(final ParseTree node, final int index, final List<TextReplacement> out) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameExprContext name = (FrostlakeParser.QualifiedNameExprContext) node;
            final String[] parts = ParseTreeText.qualifiedNameParts(name.qualifiedName());
            if (parts.length != 1 || carriedByARelation(parts[0])) {
                return;
            }
            int definedBy = -1;
            for (int i = 0; i < index; i++) {
                if (parts[0].equals(SelectItemAccessors.getItemAlias(items.get(i)))
                        && SelectItemAccessors.getItemExpression(items.get(i)) != null) {
                    if (definedBy >= 0) {
                        return;
                    }
                    definedBy = i;
                }
            }
            if (definedBy >= 0) {
                out.add(new TextReplacement(name, "(" + definitionOf(definedBy) + ")"));
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectEarlierAliasReads(node.getChild(i), index, out);
        }
    }

    /** Whether a relation in scope carries a column of exactly this name. */
    private boolean carriedByARelation(final String name) {
        if (relations != null && !relations.isEmpty()) {
            for (final Table relation : relations) {
                if (relation != null && relation.hasColumnExactly(name)) {
                    return true;
                }
            }
            return false;
        }
        return table != null && table.hasColumnExactly(name);
    }
}
