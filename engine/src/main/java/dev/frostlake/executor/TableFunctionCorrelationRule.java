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

import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A SCALAR subquery may not correlate to a column a TABLE FUNCTION produced, unless it aggregates.
 *
 * <pre>
 * SELECT (SELECT pkey FROM parents WHERE pid = VALUE:g::VARCHAR)      -- refused
 *   FROM src s, TABLE(FLATTEN(js))
 * SELECT (SELECT MAX(pkey) FROM parents WHERE pid = VALUE:g::VARCHAR) -- allowed: it aggregates
 *   FROM src s, TABLE(FLATTEN(js))
 * </pre>
 *
 * <p>Every part of that sentence was measured, and each one moves the answer:
 *
 * <ul>
 *   <li><b>SCALAR.</b> {@code EXISTS} and {@code IN} over the same correlation are fine — only a
 *       subquery read as a VALUE is refused.</li>
 *   <li><b>correlate.</b> An uncorrelated subquery beside a table function is fine, and so is one that
 *       correlates to the BASE table's own column while a table function sits in the same FROM.</li>
 *   <li><b>a TABLE FUNCTION.</b> Not laterals in general: correlating to a {@code LATERAL (SELECT …)}
 *       column is accepted. {@code TABLE(FLATTEN(…))}, {@code LATERAL FLATTEN(…)} and
 *       {@code TABLE(SPLIT_TO_TABLE(…))} are all refused alike, so it is the table function that
 *       matters rather than FLATTEN.</li>
 *   <li><b>unless it aggregates.</b> One aggregate in the subquery's select list makes the same query
 *       legal. {@code LIMIT 1} does not, and neither does a bare {@code GROUP BY}.</li>
 * </ul>
 *
 * <p>Where the refusal lands is measured too: the projection and the WHERE clause are refused alike,
 * and the reported place is the subquery's own opening parenthesis, with the line and the position
 * BOTH counted from 1 — unlike the {@code error line 1 at position 7} family, which counts positions
 * from 0. Two conventions, one engine; the wording differs with them.
 */
final class TableFunctionCorrelationRule {

    /** The default names {@code planTableReference} gives an unaliased table-function source. */
    private static final String FLATTEN_DEFAULT_ALIAS = "flatten";
    private static final String TABLE_FUNCTION_DEFAULT_ALIAS = "table_function";

    private final FunctionRegistry functions;
    private final Catalog catalog;

    TableFunctionCorrelationRule(final FunctionRegistry functions, final Catalog catalog) {
        this.functions = functions;
        this.catalog = catalog;
    }

    /**
     * Refuse every scalar subquery in {@code select} that correlates to a table function in
     * {@code from} without aggregating. A FROM with no table function in it costs one walk and
     * returns, which is the overwhelmingly common case.
     */
    void validate(final FrostlakeParser.SelectClauseContext select,
                  final FrostlakeParser.TableExpressionContext from,
                  final Map<String, Table> aliasToTable) {
        if (select == null || from == null || aliasToTable == null) {
            return;
        }
        final Set<String> produced = tableFunctionOutputs(from, aliasToTable);
        if (produced.isEmpty()) {
            return;
        }
        final List<FrostlakeParser.ScalarSubqueryExprContext> subqueries = new ArrayList<>();
        collectOutermostScalarSubqueries(select, subqueries);
        for (final FrostlakeParser.ScalarSubqueryExprContext subquery : subqueries) {
            if (aggregates(subquery.selectStatement())
                || !correlatesTo(subquery.selectStatement(), produced)) {
                continue;
            }
            throw new RuntimeException(SqlCompilationError.of(
                "Unsupported subquery type cannot be evaluated at line "
                    + subquery.getStart().getLine() + ", position "
                    + (subquery.getStart().getCharPositionInLine() + 1)));
        }
    }

    /**
     * The column names each table function in {@code from} contributes, both bare and qualified by the
     * source's alias. The names are taken from the virtual table the FROM already built rather than
     * guessed from the function, so a UDTF's columns count exactly as FLATTEN's do.
     */
    private Set<String> tableFunctionOutputs(final FrostlakeParser.TableExpressionContext from,
                                             final Map<String, Table> aliasToTable) {
        final Set<String> produced = new HashSet<>();
        final List<FrostlakeParser.TableReferenceContext> references = new ArrayList<>();
        collectOutermostTableReferences(from, references);
        for (final FrostlakeParser.TableReferenceContext reference : references) {
            final String defaultAlias = tableFunctionDefaultAlias(reference.tableSource());
            if (defaultAlias == null) {
                continue;
            }
            final Table produced0 = aliasedTable(aliasToTable, aliasOf(reference, defaultAlias));
            if (produced0 == null) {
                continue;
            }
            final String qualifier = aliasOf(reference, defaultAlias).toUpperCase();
            for (final TableColumn column : produced0.getColumns()) {
                produced.add(column.getName().toUpperCase());
                produced.add(qualifier + "." + column.getName().toUpperCase());
            }
        }
        return produced;
    }

    /** The alias a table-function source would be filed under, or null when it is not one. */
    private String tableFunctionDefaultAlias(final FrostlakeParser.TableSourceContext source) {
        if (source == null) {
            return null;
        }
        if (source.FLATTEN() != null) {
            return FLATTEN_DEFAULT_ALIAS;
        }
        if (source.TABLE() != null && source.expression() != null) {
            return TABLE_FUNCTION_DEFAULT_ALIAS;
        }
        return null;
    }

    private String aliasOf(final FrostlakeParser.TableReferenceContext reference, final String fallback) {
        if (reference.aliasName() != null) {
            return ParseTreeText.getIdentifier(reference.aliasName()).toUpperCase();
        }
        if (reference.nonJoinKeywordIdentifier() != null) {
            return reference.nonJoinKeywordIdentifier().getText().toUpperCase();
        }
        return fallback;
    }

    private Table aliasedTable(final Map<String, Table> aliasToTable, final String alias) {
        for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(alias)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Whether the subquery's SELECT LIST holds an aggregate call — the one thing that makes it legal.
     * The select list and nowhere else: a bare GROUP BY over a non-aggregated list is still refused.
     */
    private boolean aggregates(final FrostlakeParser.SelectStatementContext subquery) {
        final FrostlakeParser.SelectListContext selectList = selectListOf(subquery);
        if (selectList == null) {
            return false;
        }
        final List<FrostlakeParser.FunctionNameContext> names = new ArrayList<>();
        collectFunctionNames(selectList, names);
        for (final FrostlakeParser.FunctionNameContext name : names) {
            if (functions.hasAggregateFunction(name.getText().toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    /** The subquery's own select list, unwrapping the parenthesized-operand spelling. */
    private FrostlakeParser.SelectListContext selectListOf(
            final FrostlakeParser.SelectStatementContext subquery) {
        FrostlakeParser.SelectOperandContext operand =
            subquery.selectOperand().isEmpty() ? null : subquery.selectOperand(0);
        while (operand != null && operand.selectClause() == null) {
            operand = operand.selectStatement() == null
                || operand.selectStatement().selectOperand().isEmpty()
                    ? null : operand.selectStatement().selectOperand(0);
        }
        return operand == null ? null : operand.selectClause().selectList();
    }

    /**
     * Whether the subquery reads one of {@code produced}. A bare name that the subquery's OWN tables
     * carry is not a correlation at all — it resolves inside — so those are subtracted first, exactly
     * as row-time resolution does.
     */
    private boolean correlatesTo(final FrostlakeParser.SelectStatementContext subquery,
                                 final Set<String> produced) {
        final Set<String> shadowed = innerColumnNames(subquery);
        final List<FrostlakeParser.QualifiedNameExprContext> references = new ArrayList<>();
        collectQualifiedNames(subquery, references);
        for (final FrostlakeParser.QualifiedNameExprContext reference : references) {
            final String name = reference.qualifiedName().getText().toUpperCase();
            if (produced.contains(name) && !shadowed.contains(name)) {
                return true;
            }
        }
        return false;
    }

    /** The column names the subquery's own FROM brings into scope, as far as the catalog can say. */
    private Set<String> innerColumnNames(final FrostlakeParser.SelectStatementContext subquery) {
        final Set<String> names = new HashSet<>();
        final List<FrostlakeParser.TableReferenceContext> references = new ArrayList<>();
        collectOutermostTableReferences(subquery, references);
        for (final FrostlakeParser.TableReferenceContext reference : references) {
            if (reference.tableSource() == null || reference.tableSource().tableQualifiedName() == null) {
                continue;
            }
            // The reference must be canonicalised before it is resolved — raw parse-tree text spells the
            // name as the writer typed it, and resolution matches the RESOLVED name (bare folded to upper,
            // quoted verbatim). This is a validation rule, so a name that does not resolve at all is simply
            // one that contributes no columns: it is the statement's own execution that reports it.
            final Table inner;
            try {
                inner = catalog.resolveTable(SqlIdentifiers.canonicalText(
                    reference.tableSource().tableQualifiedName().getText()));
            } catch (final RuntimeException unresolvable) {
                continue;
            }
            if (inner == null) {
                continue;
            }
            for (final TableColumn column : inner.getColumns()) {
                names.add(column.getName().toUpperCase());
            }
        }
        return names;
    }

    // ── tree walks ────────────────────────────────────────────────────────────────
    // "Outermost" means: stop at the first match on a branch. A scalar subquery inside a scalar
    // subquery correlates to the MIDDLE query's scope, not this one's, and is judged when that query
    // runs; a table reference inside a subquery is that subquery's FROM, not this one's.

    private void collectOutermostScalarSubqueries(
            final ParseTree node, final List<FrostlakeParser.ScalarSubqueryExprContext> into) {
        if (node instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            into.add((FrostlakeParser.ScalarSubqueryExprContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectOutermostScalarSubqueries(node.getChild(i), into);
        }
    }

    private void collectOutermostTableReferences(
            final ParseTree node, final List<FrostlakeParser.TableReferenceContext> into) {
        if (node instanceof FrostlakeParser.TableReferenceContext) {
            into.add((FrostlakeParser.TableReferenceContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectOutermostTableReferences(node.getChild(i), into);
        }
    }

    private void collectFunctionNames(final ParserRuleContext node,
                                      final List<FrostlakeParser.FunctionNameContext> into) {
        if (node instanceof FrostlakeParser.FunctionNameContext) {
            into.add((FrostlakeParser.FunctionNameContext) node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChild(i) instanceof ParserRuleContext) {
                collectFunctionNames((ParserRuleContext) node.getChild(i), into);
            }
        }
    }

    private void collectQualifiedNames(final ParserRuleContext node,
                                       final List<FrostlakeParser.QualifiedNameExprContext> into) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            into.add((FrostlakeParser.QualifiedNameExprContext) node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChild(i) instanceof ParserRuleContext) {
                collectQualifiedNames((ParserRuleContext) node.getChild(i), into);
            }
        }
    }
}
