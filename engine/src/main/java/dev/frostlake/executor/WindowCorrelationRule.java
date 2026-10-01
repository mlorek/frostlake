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

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A window call in a subquery may not read the row of the query around it — in its arguments, its PARTITION
 * BY or its ORDER BY. Live refuses such a subquery while it compiles the statement, wherever the subquery
 * stands (a value, an EXISTS, an IN, a LATERAL body) and whether or not a row reaches it, with a sentence of
 * its own that names the call as its plan prints it:
 *
 * <pre>
 *   (SELECT MAX(v) OVER (PARTITION BY fz.id) FROM g LIMIT 1)   Window function [MAX(G.V) OVER (PARTITION BY FZ.ID)] contains a correlation.
 *   EXISTS (SELECT 1 FROM g QUALIFY ROW_NUMBER() OVER (ORDER BY fz.id) = 1)
 *                                                              Window function [ROW_NUMBER() OVER (ORDER BY FZ.ID ASC NULLS LAST)] contains a correlation.
 * </pre>
 *
 * <p>The refusal carries no position, and comes ahead of the refusal of the subquery's shape.
 */
final class WindowCorrelationRule {

    private final Table table;
    private final Map<String, Table> aliasToTable;
    private final List<Table> allTables;
    private final Map<String, Object> outerNames;

    /**
     * @param outerNames the names the query around the subquery binds, as it compiles the subquery
     */
    WindowCorrelationRule(final Table table, final Map<String, Table> aliasToTable, final List<Table> allTables,
                          final Map<String, Object> outerNames) {
        this.table = table;
        this.aliasToTable = aliasToTable;
        this.allTables = allTables;
        this.outerNames = outerNames;
    }

    /** Refuse the first window call of the select list, QUALIFY or ORDER BY that reads an outer name. */
    void reject(final FrostlakeParser.SelectStatementContext statement, final FrostlakeParser.SelectClauseContext clause,
                final PlanEcho echo) {
        final List<FrostlakeParser.FunctionCallExprContext> calls = new ArrayList<>();
        collectWindowCalls(clause.selectList(), calls);
        if (clause.qualifyClause() != null) {
            collectWindowCalls(clause.qualifyClause(), calls);
        }
        if (statement.orderByClause() != null) {
            collectWindowCalls(statement.orderByClause(), calls);
        }
        for (final FrostlakeParser.FunctionCallExprContext call : calls) {
            if (readsOuterName(call)) {
                throw new RuntimeException(SqlCompilationError.of(
                    "Window function [" + echo.printWindowCall(call) + "] contains a correlation."));
            }
        }
    }

    private static void collectWindowCalls(final ParseTree node, final List<FrostlakeParser.FunctionCallExprContext> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext
                && ((FrostlakeParser.FunctionCallExprContext) node).overClause() != null) {
            into.add((FrostlakeParser.FunctionCallExprContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectWindowCalls(node.getChild(i), into);
        }
    }

    private boolean readsOuterName(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext || node instanceof FrostlakeParser.LambdaFunctionContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            return isOuter(ParseTreeText.qualifiedNameParts(((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName()));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsOuterName(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** A name the subquery's own relations do not carry and the query around it does. */
    private boolean isOuter(final String[] parts) {
        final String column = parts[parts.length - 1];
        if (parts.length == 1) {
            for (final Table relation : relations()) {
                if (relation.hasColumn(column)) {
                    return false;
                }
            }
            return outerNames.containsKey(column.toUpperCase());
        }
        if (parts.length != 2) {
            return false;
        }
        if (aliasToTable != null) {
            for (final String key : aliasToTable.keySet()) {
                if (key.equalsIgnoreCase(parts[0])) {
                    return false;
                }
            }
        } else if (table != null && table.getName().equalsIgnoreCase(parts[0])) {
            return false;
        }
        return outerNames.containsKey((parts[0] + "." + column).toUpperCase());
    }

    private List<Table> relations() {
        final List<Table> relations = new ArrayList<>();
        if (allTables != null && !allTables.isEmpty()) {
            relations.addAll(allTables);
        } else if (table != null) {
            relations.add(table);
        }
        return relations;
    }
}
