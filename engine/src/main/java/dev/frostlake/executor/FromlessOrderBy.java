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

import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.operators.Operator;
import dev.frostlake.executor.operators.StageOperator;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import org.antlr.v4.runtime.ParserRuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The ORDER BY of a FROM-less select. Its one row needs no sort, but its keys are judged all the same, over the
 * names the select's items publish and in live's order: every key's names, the ordinal range, the function
 * names, the names inside the keys' subqueries, the keys' types, and last whatever else a key's subquery refuses
 * ({@code SELECT 1 AS x ORDER BY (SELECT b FROM t GROUP BY a)} is the grouped select-list refusal). Nothing is
 * evaluated, so a key that would fault only on a row — {@code ORDER BY (SELECT 1 / 0)} — is answered. A query
 * nested in a key has a scope of its own and reads none of the published names. All live-verified.
 */
final class FromlessOrderBy {

    private final QueryExecutor executor;
    private final OrderByExecutor orderByExecutor;
    private final FrostlakeParser.SelectStatementContext statement;
    private final Map<String, Object> lateralContext;

    FromlessOrderBy(final QueryExecutor executor, final OrderByExecutor orderByExecutor,
                    final FrostlakeParser.SelectStatementContext statement, final Map<String, Object> lateralContext) {
        this.executor = executor;
        this.orderByExecutor = orderByExecutor;
        this.statement = statement;
        this.lateralContext = lateralContext;
    }

    /**
     * The stage that judges the keys once the stages before it are settled, whatever rows reach it, and passes
     * those rows on unsorted.
     *
     * @param columns the columns the items publish, filled in by the time the stage runs
     * @return the stage
     */
    Operator stage(final List<ResultSetColumn> columns) {
        return new StageOperator("ORDER BY[" + ParseTreeText.getOriginalText(statement.orderByClause()) + "]") {
            @Override
            protected List<Row> apply(final List<Row> input) {
                judge(columns);
                return input;
            }
        };
    }

    private void judge(final List<ResultSetColumn> columns) {
        final List<ParserRuleContext> keys = new ArrayList<>();
        for (final FrostlakeParser.OrderItemContext item : statement.orderByClause().orderItem()) {
            keys.add(item.expression());
        }
        final FromlessSelectScope published = new FromlessSelectScope();
        final List<TableColumn> publishedColumns = new ArrayList<>();
        for (final ResultSetColumn column : columns) {
            published.publish(column.getName(), false);
            publishedColumns.add(new TableColumn(column.getName(), column.getDataType(), true, null, false, false,
                false));
        }
        final ExpressionEvaluator outsideScope = new ExpressionEvaluator(
            new Table("DUMMY", new ArrayList<TableColumn>(), false), executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        outsideScope.setOuterLateralContext(lateralContext);
        for (final ParserRuleContext key : keys) {
            published.rejectUnresolvableNames(key, outsideScope, new Row(new ArrayList<>()));
        }
        orderByExecutor.validateOrderOrdinals(statement, FromlessDual.unread(), null);
        final List<FunctionCallExpression> unknown = executor.unresolvableCallsIn(keys);
        if (!unknown.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of(
                new ExpressionEvaluator(null, executor.getFunctionRegistry(), executor.getCatalog(), executor)
                    .unknownFunctionSentence(unknown)));
        }
        final RuntimeException waiting =
            executor.compileSubqueriesIn(keys, FromlessDual.unread(), null, null, lateralContext);
        final Table publishedTable = new Table("DUMMY", publishedColumns, false);
        for (final ParserRuleContext key : keys) {
            final String text = ParseTreeText.getOriginalText(key);
            if (OrdinalLiteral.positionOf(text) != OrdinalLiteral.NOT_AN_ORDINAL) {
                continue;
            }
            final ExpressionEvaluator typed = new ExpressionEvaluator(publishedTable, executor.getFunctionRegistry(),
                executor.getCatalog(), executor);
            typed.setScopeOpaqueToSubqueries(true);
            typed.setOuterLateralContext(lateralContext);
            final SourcePosition displaced = ExpressionSource.beginNested(
                new SourcePosition(key.getStart().getLine(), key.getStart().getCharPositionInLine()));
            try {
                typed.validateStrict(ExpressionEvaluator.parse(text));
            } finally {
                ExpressionSource.end(displaced);
            }
        }
        if (waiting != null) {
            throw waiting;
        }
    }
}
