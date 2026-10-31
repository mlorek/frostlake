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

import dev.frostlake.functions.TableFunction;
import dev.frostlake.functions.table.Flatten;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The column names a FROM source brings into a query's scope, read from the statement and the catalog alone — no
 * query is planned or run to learn them: a table's or a view's columns, a common table expression's column list or
 * the names its body's select list gives, a derived table's alias list or the names its select list gives, a
 * star standing for every name of the relations it covers, a VALUES list's COLUMN1 to COLUMNn and a table function's
 * output columns. A name is canonical, as a reference resolves it.
 */
final class SourceColumnNames {

    private final QueryExecutor executor;

    SourceColumnNames(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * Whether {@code source} brings a column of the canonical {@code name}; false where its names are not known.
     *
     * @param source the FROM source
     * @param name   the canonical column name
     * @return whether the source carries the name
     */
    boolean carries(final FrostlakeParser.TableSourceContext source, final String name) {
        final Set<String> names = namesOf(source, new HashSet<ParserRuleContext>());
        return names != null && names.contains(name);
    }

    /** The canonical names {@code source} brings, or null where they are not known. */
    private Set<String> namesOf(final FrostlakeParser.TableSourceContext source,
                                final Set<ParserRuleContext> visiting) {
        final ParseTree parent = source.getParent();
        if (parent instanceof FrostlakeParser.TableReferenceContext) {
            final FrostlakeParser.TableReferenceContext reference = (FrostlakeParser.TableReferenceContext) parent;
            if (reference.pivotClause() != null || reference.unpivotClause() != null
                    || reference.matchRecognizeClause() != null) {
                return null;
            }
            if (reference.identifierList() != null) {
                final Set<String> listed = new HashSet<>();
                for (final FrostlakeParser.IdentifierContext identifier : reference.identifierList().identifier()) {
                    listed.add(ParseTreeText.getIdentifier(identifier));
                }
                return listed;
            }
        }
        if (source.selectStatement() != null) {
            return projectedBy(source.selectStatement(), visiting);
        }
        if (source.VALUES() != null) {
            return valuesNames(source);
        }
        if (source.FLATTEN() != null || source.tableFunctionExpr() != null || source.TABLE() != null) {
            return tableFunctionNames(source);
        }
        if (source.tableQualifiedName() == null) {
            return null;
        }
        final FrostlakeParser.CteDefinitionContext named = definitionOf(source);
        if (named != null) {
            return definedBy(named, visiting);
        }
        final Set<String> enclosing = enclosingCteNames(source);
        if (enclosing != null) {
            return enclosing;
        }
        return storedNames(source);
    }

    /**
     * The names a VALUES list gives: the column list its parenthesized alias writes, else COLUMN1 to COLUMNn, one
     * for each value of its first row.
     */
    private static Set<String> valuesNames(final FrostlakeParser.TableSourceContext source) {
        final Set<String> names = new HashSet<>();
        if (source.columnListOptional() != null) {
            for (final FrostlakeParser.NamePartContext part : source.columnListOptional().namePart()) {
                names.add(ParseTreeText.namePartText(part));
            }
            return names;
        }
        final int width = source.valueTupleList().valueTuple(0).valueList().booleanExpr().size();
        for (int i = 1; i <= width; i++) {
            names.add("COLUMN" + i);
        }
        return names;
    }

    /**
     * The names a table function's call gives: FLATTEN's six, the columns a built-in function answers whatever its
     * arguments, or the RETURNS TABLE columns every user-defined overload the name reaches shares. Null where the
     * call's columns depend on its arguments, or the name reaches no table function.
     */
    private Set<String> tableFunctionNames(final FrostlakeParser.TableSourceContext source) {
        final List<ResultSetColumn> columns;
        if (source.FLATTEN() != null) {
            columns = Flatten.columns();
        } else {
            final FrostlakeParser.FunctionNameContext called = calledName(source);
            if (called == null) {
                return null;
            }
            final String[] parts = ParseTreeText.functionNameParts(called);
            final TableFunction builtIn = parts == null || parts.length != 1 ? null
                : executor.getFunctionRegistry().getTableFunction(parts[0]);
            if (builtIn == null) {
                return udtfNames(called);
            }
            try {
                columns = builtIn.outputColumns(new HashMap<String, Object>());
            } catch (final RuntimeException dependsOnArguments) {
                return null;
            }
        }
        if (columns == null) {
            return null;
        }
        final Set<String> names = new HashSet<>();
        for (final ResultSetColumn column : columns) {
            names.add(column.getName());
        }
        return names;
    }

    /** The RETURNS TABLE column names every user-defined table function a name reaches shares; null for none. */
    private Set<String> udtfNames(final FrostlakeParser.FunctionNameContext called) {
        Set<String> shared = null;
        for (final Function overload : executor.udtfOverloads(called)) {
            final Set<String> names = new HashSet<>();
            for (final Parameter column : overload.getReturnColumns()) {
                names.add(SqlIdentifiers.canonicalText(column.getName()));
            }
            if (shared != null && !shared.equals(names)) {
                return null;
            }
            shared = names;
        }
        return shared;
    }

    /** The name a table function's call writes, inside TABLE( … ) or bare; null for any other shape. */
    private static FrostlakeParser.FunctionNameContext calledName(final FrostlakeParser.TableSourceContext source) {
        if (source.tableFunctionExpr() != null) {
            return source.tableFunctionExpr().functionName();
        }
        final FrostlakeParser.ExpressionContext call = source.expression();
        if (call instanceof FrostlakeParser.FunctionCallExprContext) {
            return ((FrostlakeParser.FunctionCallExprContext) call).functionName();
        }
        if (call instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            return ((FrostlakeParser.FunctionCallNamedArgsExprContext) call).functionName();
        }
        if (call instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            return ((FrostlakeParser.FunctionCallMixedArgsExprContext) call).functionName();
        }
        return null;
    }

    /** The names a common table expression gives: its column list's, else its body's select list's. */
    private Set<String> definedBy(final FrostlakeParser.CteDefinitionContext definition,
                                  final Set<ParserRuleContext> visiting) {
        if (definition.columnListOptional() != null) {
            final Set<String> listed = new HashSet<>();
            for (final FrostlakeParser.NamePartContext part : definition.columnListOptional().namePart()) {
                listed.add(ParseTreeText.namePartText(part));
            }
            return listed;
        }
        // A recursive expression's body reads itself: a star over it says nothing more than the names around it.
        if (!visiting.add(definition)) {
            return null;
        }
        try {
            return projectedBy(definition.selectStatement(), visiting);
        } finally {
            visiting.remove(definition);
        }
    }

    /** The names a query's first operand projects, or null where an item's name is not known. */
    private Set<String> projectedBy(final FrostlakeParser.SelectStatementContext query,
                                    final Set<ParserRuleContext> visiting) {
        if (query.selectOperand().isEmpty()) {
            return null;
        }
        final FrostlakeParser.SelectOperandContext first = query.selectOperand(0);
        if (first.selectStatement() != null) {
            return projectedBy(first.selectStatement(), visiting);
        }
        if (first.selectClause() == null) {
            return null;
        }
        final FrostlakeParser.SelectClauseContext select = first.selectClause();
        final Set<String> names = new HashSet<>();
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            if (item instanceof FrostlakeParser.ExprItemContext) {
                final String name = itemName((FrostlakeParser.ExprItemContext) item);
                if (name != null) {
                    names.add(name);
                }
            } else if (item instanceof FrostlakeParser.StarItemContext) {
                if (!((FrostlakeParser.StarItemContext) item).starModifier().isEmpty()
                        || !addEveryName(select, null, names, visiting)) {
                    return null;
                }
            } else if (item instanceof FrostlakeParser.QualifiedStarItemContext) {
                final FrostlakeParser.QualifiedStarItemContext star = (FrostlakeParser.QualifiedStarItemContext) item;
                final String[] parts = ParseTreeText.qualifiedNameParts(star.starQualifiedName());
                if (!star.starModifier().isEmpty() || !addEveryName(select, parts[parts.length - 1], names, visiting)) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return names;
    }

    /**
     * Add the names of every source of {@code select}'s FROM clause, or of the one {@code qualifier} names; false
     * where a source's names are not known or no source is so named.
     */
    private boolean addEveryName(final FrostlakeParser.SelectClauseContext select, final String qualifier,
                                 final Set<String> into, final Set<ParserRuleContext> visiting) {
        if (select.tableExpression() == null) {
            return false;
        }
        final List<FrostlakeParser.TableSourceContext> sources = new ArrayList<>();
        collectSources(select.tableExpression(), sources);
        boolean found = false;
        for (final FrostlakeParser.TableSourceContext source : sources) {
            if (qualifier != null && !qualifier.equals(keyOf(source))) {
                continue;
            }
            final Set<String> names = namesOf(source, visiting);
            if (names == null) {
                return false;
            }
            into.addAll(names);
            found = true;
        }
        return found;
    }

    /** The name a source is qualified by: its alias, else its own name's last part; null for a nameless one. */
    static String keyOf(final FrostlakeParser.TableSourceContext source) {
        if (source.getParent() instanceof FrostlakeParser.TableReferenceContext) {
            final FrostlakeParser.TableReferenceContext reference =
                (FrostlakeParser.TableReferenceContext) source.getParent();
            if (reference.aliasName() != null) {
                return ParseTreeText.getIdentifier(reference.aliasName());
            }
            if (reference.nonJoinKeywordIdentifier() != null) {
                return reference.nonJoinKeywordIdentifier().identifier() != null
                    ? ParseTreeText.getIdentifier(reference.nonJoinKeywordIdentifier().identifier())
                    : reference.nonJoinKeywordIdentifier().getText().toUpperCase(Locale.ROOT);
            }
        }
        if (source.tableQualifiedName() == null) {
            return null;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
        return parts[parts.length - 1];
    }

    /** A select item's name as a reference reaches it: its alias or its column's name; null for any other. */
    private static String itemName(final FrostlakeParser.ExprItemContext item) {
        if (item.aliasName() != null) {
            return ParseTreeText.getIdentifier(item.aliasName());
        }
        if (item.identifier() != null) {
            return ParseTreeText.getIdentifier(item.identifier());
        }
        if (item.bareAliasWord() != null) {
            return ParseTreeText.getIdentifier(item.bareAliasWord());
        }
        if (item.booleanExpr() instanceof FrostlakeParser.ValueExprContext
                && ((FrostlakeParser.ValueExprContext) item.booleanExpr()).expression()
                    instanceof FrostlakeParser.QualifiedNameExprContext) {
            final String[] parts = ParseTreeText.qualifiedNameParts(((FrostlakeParser.QualifiedNameExprContext)
                ((FrostlakeParser.ValueExprContext) item.booleanExpr()).expression()).qualifiedName());
            return parts[parts.length - 1];
        }
        return null;
    }

    /**
     * The common table expression a one-part name stands for in the statement around it: the innermost WITH clause
     * that defines the name where the source sees it — an expression's body sees the ones before it and itself, a
     * query the ones of every WITH clause around it. Null where none does.
     */
    private static FrostlakeParser.CteDefinitionContext definitionOf(final FrostlakeParser.TableSourceContext source) {
        final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
        if (parts.length != 1) {
            return null;
        }
        FrostlakeParser.CteDefinitionContext within = null;
        ParseTree child = source;
        for (ParseTree node = source.getParent(); node != null; child = node, node = node.getParent()) {
            if (node instanceof FrostlakeParser.CteDefinitionContext) {
                within = (FrostlakeParser.CteDefinitionContext) node;
            }
            if (!(node instanceof FrostlakeParser.SelectStatementContext)
                    || ((FrostlakeParser.SelectStatementContext) node).withClause() == null) {
                continue;
            }
            final FrostlakeParser.WithClauseContext with = ((FrostlakeParser.SelectStatementContext) node).withClause();
            for (final FrostlakeParser.CteDefinitionContext definition : with.cteDefinition()) {
                if (parts[0].equals(ParseTreeText.namePartText(definition.nameStartPart()))) {
                    return definition;
                }
                if (child == with && definition == within) {
                    break;
                }
            }
        }
        return null;
    }

    /** The column names of the common table expression the statement around this query materialized, or null. */
    private Set<String> enclosingCteNames(final FrostlakeParser.TableSourceContext source) {
        final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
        final Map<String, ResultSet> scope = executor.getCurrentCteContext();
        if (parts.length != 1 || scope == null || !scope.containsKey(parts[0]) || scope.get(parts[0]) == null) {
            return null;
        }
        final Set<String> names = new HashSet<>();
        for (final ResultSetColumn column : scope.get(parts[0]).getColumns()) {
            names.add(column.getName());
        }
        return names;
    }

    /** The table a name reads in the catalog, or null. */
    private Table storedTable(final FrostlakeParser.TableSourceContext source) {
        try {
            return executor.getCatalog().resolveTable(ParseTreeText.getQualifiedName(source.tableQualifiedName()));
        } catch (final RuntimeException notATable) {
            return null;
        }
    }

    /** The names of the table or the view a name reads in the catalog, or null. */
    private Set<String> storedNames(final FrostlakeParser.TableSourceContext source) {
        final Set<String> names = new HashSet<>();
        final Table table = storedTable(source);
        if (table != null) {
            for (final TableColumn column : table.getColumns()) {
                names.add(column.getName());
            }
            return names;
        }
        final View view;
        try {
            view = executor.getCatalog().resolveView(ParseTreeText.getQualifiedName(source.tableQualifiedName()));
        } catch (final RuntimeException notAView) {
            return null;
        }
        if (view == null) {
            return null;
        }
        if (view.getResolvedColumns() != null) {
            for (final TableColumn column : view.getResolvedColumns()) {
                names.add(column.getName());
            }
            return names;
        }
        if (view.hasExplicitColumnNames()) {
            names.addAll(view.getColumnNames());
            return names;
        }
        return null;
    }

    /** The sources a FROM clause reads, the queries nested in it left out. */
    private static void collectSources(final ParseTree node, final List<FrostlakeParser.TableSourceContext> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        // A parenthesized group is no source of its own: its sources and its joins' are.
        if (node instanceof FrostlakeParser.TableSourceContext
                && ((FrostlakeParser.TableSourceContext) node).tableReference() == null) {
            into.add((FrostlakeParser.TableSourceContext) node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectSources(node.getChild(i), into);
        }
    }
}
