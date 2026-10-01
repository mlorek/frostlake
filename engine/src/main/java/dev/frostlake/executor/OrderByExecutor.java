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

import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.CollatedKey;
import dev.frostlake.executor.expressions.CollationSpec;
import dev.frostlake.executor.expressions.ColumnReferenceCollectWalk;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.SortKeyRole;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnsupportedSubqueryException;
import dev.frostlake.executor.operators.Operator;
import dev.frostlake.executor.operators.StageOperator;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * ORDER BY query stage extracted from {@link QueryExecutor}. Resolves each sort key (positional
 * ordinal → N-th SELECT expression, SELECT alias → its expression, else column/expression as written),
 * materialises the per-row sort-key vector once, and stable-sorts with NULLS FIRST/LAST placement.
 * A separate post-GROUP-BY path matches ORDER BY keys to already-computed result columns. Pure value
 * comparisons come from {@link ValueComparisons}; column/qualified resolution and expression evaluation
 * are delegated back to the owning executor.
 */
final class OrderByExecutor {

    private final QueryExecutor executor;

    OrderByExecutor(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * The ORDER BY stage over the FROM-shaped rows: the keys are resolved and judged here, while planning,
     * and the sort itself runs when the pipeline reaches the stage.
     */
    Operator planOrderBy(final Table table, final FrostlakeParser.SelectStatementContext ctx,
                         final Map<String, Table> aliasToTable, final List<Table> allTables) {
        // Parse order items
        final List<String> orderColumns = new ArrayList<>();
        final List<Boolean> ascending = new ArrayList<>();
        final List<Boolean> nullsFirst = new ArrayList<>();
        // Where each key written as itself begins, so a refusal its evaluation raises is placed in the statement.
        final List<SourcePosition> keyOrigins = new ArrayList<>();

        for (final FrostlakeParser.OrderItemContext item : ctx.orderByClause().orderItem()) {
            // Resolve the ORDER BY key to text we can evaluate per row: a positional ordinal → the N-th
            // SELECT expression; a SELECT alias → that item's expression; otherwise the key as written (a
            // column name, or an arbitrary expression that resolveOrderValue evaluates against the row).
            // The key text must come from getOriginalText, NOT getText: the latter concatenates tokens
            // with no whitespace, so a key needing separators — `x IS NOT NULL` → `xISNOTNULL`,
            // `CAST(x AS NUMBER)` → `CAST(xASNUMBER)` — collapsed into an unparseable identifier and
            // failed to resolve. (Only the ordinal DETECTION needs the bare text, and digits are
            // unaffected by spacing.)
            String colName = resolveOrderOrdinal(ParseTreeText.getOriginalText(item.expression()), ctx,
                table, aliasToTable);
            colName = resolveOrderAlias(colName, ctx, table, aliasToTable);
            orderColumns.add(colName);
            keyOrigins.add(colName.equals(ParseTreeText.getOriginalText(item.expression()))
                ? new SourcePosition(item.expression().getStart().getLine(),
                    item.expression().getStart().getCharPositionInLine())
                : null);
            ascending.add(item.DESC() == null); // Default is ASC
            nullsFirst.add(ValueComparisons.nullsFirstFlag(item));
        }

        rejectFileSortKeys(orderColumns, keyOrigins, table, aliasToTable, allTables);
        validateOrderKeyScope(ctx, table, aliasToTable, allTables);
        // A key holding a subquery raises what the subquery raises as it is evaluated — its own compilation
        // refusal, or a row-time fault such as a second row — not an unresolvable key: the key's own names
        // were settled above.
        final boolean[] keyHoldsSubquery = new boolean[orderColumns.size()];
        for (int i = 0; i < orderColumns.size(); i++) {
            keyHoldsSubquery[i] = holdsSubquery(orderColumns.get(i));
        }

        return new StageOperator("ORDER BY[" + String.join(", ", orderColumns) + "]") {
            @Override
            protected List<Row> apply(final List<Row> rows) {
                if (rows.size() <= 1) {
                    return rows; // nothing to compare; also avoids resolving keys for a degenerate result
                }

                // Decorate-sort-undecorate: resolve each row's sort-key vector exactly ONCE (the old comparator
                // re-resolved every column — string parse + alias scan — for both operands on every comparison,
                // i.e. O(n log n) resolutions; this is O(n)).
                final int keyCount = orderColumns.size();
                final Object[][] sortKeys = new Object[rows.size()][keyCount];
                // Per-key resolution PLAN, learned once on the first row — a key's kind is row-invariant,
                // so later rows skip the throw-and-fall-through chain (two exception constructions plus a
                // fresh evaluator per row-and-key on the expression path).
                final int[] keyKind = new int[keyCount];
                final int[] keyIndex = new int[keyCount];
                final ExpressionEvaluator[] keyEvaluator = new ExpressionEvaluator[keyCount];
                // A key that carries a collation sorts under it, not by code point.
                final CollationSpec[] keyRules = keyCollations(orderColumns, table, aliasToTable, allTables);
                for (int r = 0; r < rows.size(); r++) {
                    final Row row = rows.get(r);
                    for (int i = 0; i < keyCount; i++) {
                        final SourcePosition displaced = keyOrigins.get(i) == null ? null
                            : ExpressionSource.beginNested(keyOrigins.get(i));
                        try {
                            // Sorting reads each key, so a cell a relation deferred raises its fault here.
                            sortKeys[r][i] = CollatedKey.of(DeferredFault.read(resolveOrderValuePlanned(row, orderColumns.get(i),
                                table, aliasToTable, allTables, keyKind, keyIndex, keyEvaluator, i,
                                keyHoldsSubquery[i])), keyRules[i]);
                        } finally {
                            if (keyOrigins.get(i) != null) {
                                ExpressionSource.end(displaced);
                            }
                        }
                    }
                }

                // Sort an index array against the precomputed keys (TimSort is stable, so equal keys keep their
                // original order — identical to the previous rows.sort), then rebuild the list.
                final Integer[] order = new Integer[rows.size()];
                for (int i = 0; i < order.length; i++) {
                    order[i] = i;
                }
                Arrays.sort(order, new Comparator<Integer>() {
                    @Override
                    public int compare(final Integer a, final Integer b) {
                        for (int i = 0; i < keyCount; i++) {
                            final int cmp = ValueComparisons.compareOrderKey(sortKeys[a][i], sortKeys[b][i], ascending.get(i), nullsFirst.get(i));
                            if (cmp != 0) {
                                return cmp;
                            }
                        }
                        return 0;
                    }
                });

                final List<Row> sorted = new ArrayList<>(rows.size());
                for (int i = 0; i < order.length; i++) {
                    sorted.add(rows.get(order[i]));
                }
                rows.clear();
                rows.addAll(sorted);
                return rows;
            }
        };
    }

    /**
     * The collation each ORDER BY key sorts under. A query where no relation declares a collation and no
     * key writes COLLATE resolves nothing: its keys sort by code point, as they always did.
     *
     * @param keys         the keys, ordinals and aliases already resolved
     * @param table        the base relation, or null
     * @param aliasToTable its alias map, or null
     * @param allTables    its joined relations, or null
     * @return one entry per key, null where the key carries no collation
     */
    private CollationSpec[] keyCollations(final List<String> keys, final Table table,
                                          final Map<String, Table> aliasToTable, final List<Table> allTables) {
        if (!KeyCollations.reachable(keys, table, aliasToTable, allTables)) {
            return new CollationSpec[keys.size()];
        }
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(table,
            executor.getFunctionRegistry(), executor.getCatalog(), executor);
        evaluator.setMultiTableContext(aliasToTable, allTables);
        return KeyCollations.resolve(keys, evaluator);
    }

    /**
     * Snowflake rejects a FILE-typed ORDER BY key at compile time ("Expressions of type FILE cannot be
     * used as ORDER BY keys") while sorting an OBJECT or VARIANT quite happily. The keys arrive with
     * ordinals and SELECT aliases already resolved to their defining expression, so one rule covers
     * {@code ORDER BY f}, {@code ORDER BY 1} and {@code ORDER BY <alias>}. Deliberately checked BEFORE
     * the single-row short circuit: the rejection is a compile-time one live, so it must not depend on
     * how many rows came back.
     */
    private void rejectFileSortKeys(final List<String> orderColumns, final List<SourcePosition> keyOrigins,
                                    final Table table, final Map<String, Table> aliasToTable,
                                    final List<Table> allTables) {
        final ExpressionEvaluator keyChecker = new ExpressionEvaluator(table,
            executor.getFunctionRegistry(), executor.getCatalog(), executor);
        if (allTables != null) {
            keyChecker.setMultiTableContext(aliasToTable, allTables);
        }
        for (int i = 0; i < orderColumns.size(); i++) {
            // Typing a key judges what it holds, so a key written as itself places a refusal in the statement.
            final SourcePosition origin = keyOrigins.get(i);
            final SourcePosition displaced = origin == null ? null : ExpressionSource.beginNested(origin);
            try {
                keyChecker.validateKey(ExpressionEvaluator.parse(orderColumns.get(i)), SortKeyRole.ORDER_BY);
            } finally {
                if (origin != null) {
                    ExpressionSource.end(displaced);
                }
            }
        }
    }

    /**
     * Plan-time scope validation of the ORDER BY keys, BEFORE the single-row short circuit — the
     * rejections are compile-time live, however many rows came back. ORDER BY resolves against the
     * SELECT output FIRST, so a key matching an output column (by position, alias, expression text
     * or produced column name) is exempt; a key that falls through to FROM-clause resolution obeys
     * the same rules as everywhere else — an invalid qualifier and a bare ON-join duplicate are
     * both rejected, the duplicate EVEN THOUGH a row-time first-match could have picked a side
     * (measured: a bare duplicate NOT in the SELECT output is "ambiguous column name" live, with
     * rows present as well). Set-operation ORDER BY resolves only against the combined output and
     * keeps its existing row-time handling.
     */
    void validateOrderKeyScope(final FrostlakeParser.SelectStatementContext ctx, final Table table,
                                       final Map<String, Table> aliasToTable, final List<Table> allTables) {
        validateOrderKeys(ctx, table, aliasToTable, allTables, false);
    }

    /**
     * {@link #validateOrderKeyScope} narrowed to the keys' column references, for a caller that raises a refusal
     * of another clause's types ahead of theirs.
     *
     * @param ctx          the statement, with its ORDER BY
     * @param table        the query's relation
     * @param aliasToTable its relations by the name the query gives each
     * @param allTables    every relation in scope
     */
    void validateOrderKeyNames(final FrostlakeParser.SelectStatementContext ctx, final Table table,
                               final Map<String, Table> aliasToTable, final List<Table> allTables) {
        validateOrderKeys(ctx, table, aliasToTable, allTables, true);
    }

    private void validateOrderKeys(final FrostlakeParser.SelectStatementContext ctx, final Table table,
                                   final Map<String, Table> aliasToTable, final List<Table> allTables,
                                   final boolean namesOnly) {
        if (ctx.selectOperand().size() > 1) {
            return;
        }
        final FrostlakeParser.SelectOperandContext firstOp = ctx.selectOperand(0);
        final FrostlakeParser.SelectClauseContext firstClause = firstOp.selectClause() != null
            ? firstOp.selectClause()
            : firstOp.selectStatement().selectOperand(0).selectClause();
        final List<FrostlakeParser.SelectItemContext> selectItems = firstClause.selectList().selectItem();
        // Aliases + star RENAME targets (via the shared helper), plus each item's produced column
        // name — the full set of names ORDER BY may resolve output-first.
        final Set<String> outputNames = new HashSet<>(executor.selectItemAliasNames(firstClause));
        for (final FrostlakeParser.SelectItemContext item : selectItems) {
            final String produced = producedColumnName(item);
            if (produced != null) {
                outputNames.add(produced.toUpperCase());
            }
        }
        // A refusal's echo prints a bare name as the OUTPUT column when it names one: an alias, an
        // unaliased column's own name, or a star's column. ORDER BY ABS(i, 1) echoes ABS(I, 1) beside
        // SELECT i, SELECT eo.i or SELECT *, and ABS(EO.I, 1) beside SELECT i AS a (live-verified).
        final Set<String> echoedBare = new HashSet<>();
        for (final String alias : executor.selectItemAliasNames(firstClause)) {
            echoedBare.add(alias.toUpperCase());
        }
        for (final FrostlakeParser.SelectItemContext item : selectItems) {
            final String produced = producedColumnName(item);
            if (produced != null && SelectItemAccessors.getItemAlias(item) == null) {
                echoedBare.add(produced.toUpperCase());
            }
            if (SelectItemAccessors.isStarItem(item) || SelectItemAccessors.isQualifiedStarItem(item)) {
                for (final StarColumn column : executor.starItemColumns(item, table, aliasToTable)) {
                    echoedBare.add(column.getOutputName().toUpperCase());
                    if (column.isReplaced()) {
                        // A REPLACE'd column keeps the source column's name while carrying an
                        // expression of its own, so the name is an OUTPUT name here — even where the
                        // source column it shadows is ambiguous across the join.
                        outputNames.add(column.getOutputName().toUpperCase());
                    }
                }
            }
        }
        for (final FrostlakeParser.OrderItemContext item : ctx.orderByClause().orderItem()) {
            final String asWritten = ParseTreeText.getOriginalText(item.expression()).trim();
            if (OrdinalLiteral.positionOf(asWritten) != OrdinalLiteral.NOT_AN_ORDINAL) {
                // A POSITION, never a name: the range check owns it, and it runs right after this one.
                // Matching it as a key instead reached the number reader with a value no int holds and
                // threw its raw complaint in place of the ordinal sentence.
                continue;
            }
            if (matchOrderItem(item.expression().getText(), selectItems, false) >= 0) {
                continue;
            }
            if (outputNames.contains(asWritten.toUpperCase())) {
                continue;
            }
            if (namesOnly) {
                executor.validateClauseColumnScope(asWritten, table, aliasToTable, allTables, outputNames,
                    item.expression());
                continue;
            }
            executor.validateClauseScope(asWritten, table, aliasToTable, allTables, outputNames,
                item.expression(), echoedBare);
        }
    }

    /**
     * Resolve one ORDER BY key with the LEARNED per-key plan: after the first row establishes how a
     * key resolves (multi-table resolver / bare column index / expression), later rows take that
     * path directly. A row-specific miss on the learned resolver path falls back to the full
     * original chain, so results are identical.
     */
    private Object resolveOrderValuePlanned(final Row row, final String colName, final Table table,
                                            final Map<String, Table> aliasToTable, final List<Table> allTables,
                                            final int[] keyKind, final int[] keyIndex,
                                            final ExpressionEvaluator[] keyEvaluator, final int i,
                                            final boolean holdsSubquery) {
        switch (keyKind[i]) {
            case 1:
                try {
                    return executor.getQualifiedColumnValueFromTables(row, allTables, aliasToTable, colName,
                        table != null ? table.getJoinKeyNames() : null);
                } catch (final InvalidQualifierException invalidQualifier) {
                    throw invalidQualifier;
                } catch (final Exception rowSpecificMiss) {
                    return resolveOrderValue(row, colName, table, aliasToTable, allTables, holdsSubquery);
                }
            case 2:
                return row.getValue(keyIndex[i]);
            case 3:
                try {
                    return keyEvaluator[i].evaluate(colName, row);
                } catch (final UnsupportedSubqueryException unsupported) {
                    throw unsupported;
                } catch (final Exception e3) {
                    throw unresolvableKey(colName, e3, holdsSubquery);
                }
            default:
                break;
        }
        // First row: run the original chain, remembering which branch answered.
        try {
            final Object value = executor.getQualifiedColumnValueFromTables(row, allTables, aliasToTable, colName,
                table != null ? table.getJoinKeyNames() : null);
            keyKind[i] = 1;
            return value;
        } catch (final InvalidQualifierException invalidQualifier) {
            throw invalidQualifier;
        } catch (final Exception notAQualifiedColumn) {
            final int colIndex = table != null ? ValueComparisons.findWrittenColumnIndex(table, colName) : -1;
            if (colIndex >= 0) {
                keyKind[i] = 2;
                keyIndex[i] = colIndex;
                return row.getValue(colIndex);
            }
            try {
                final ExpressionEvaluator evaluator = keyEvaluator(colName, table, aliasToTable, allTables);
                final Object value = evaluator.evaluate(colName, row);
                keyKind[i] = 3;
                keyEvaluator[i] = evaluator;
                return value;
            } catch (final UnsupportedSubqueryException unsupported) {
                throw unsupported;
            } catch (final Exception e3) {
                throw unresolvableKey(colName, e3, holdsSubquery);
            }
        }
    }

    /**
     * What a key's failed evaluation raises: a fault a relation deferred, or anything a subquery in the key
     * raised, as it is; otherwise the key as an identifier nothing resolves.
     */
    private static RuntimeException unresolvableKey(final String colName, final Exception failure,
                                                    final boolean holdsSubquery) {
        if (DeferredFault.isHeld(failure) || (holdsSubquery && failure instanceof RuntimeException)) {
            return (RuntimeException) failure;
        }
        return new RuntimeException(SqlCompilationError.invalidIdentifier(colName), failure);
    }

    /**
     * The evaluator of a key that names no plain column. A key reading a position ({@code $n}, {@code t.$n})
     * reads it through the FROM clause's relations, as the select list does: over a join, {@code $2} is the
     * second column of the one relation wide enough, not the combined row's second value.
     */
    private ExpressionEvaluator keyEvaluator(final String colName, final Table table,
                                             final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final ExpressionEvaluator evaluator =
            new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        if (allTables != null && allTables.size() > 1 && readsPosition(colName)) {
            evaluator.setMultiTableContext(aliasToTable, allTables);
        }
        return evaluator;
    }

    /** Whether a key's text reads a position of the row it sorts; a key that does not parse reads none. */
    private static boolean readsPosition(final String keyText) {
        final ColumnReferenceCollectWalk walk = new ColumnReferenceCollectWalk();
        try {
            ExpressionEvaluator.parse(keyText).accept(walk);
        } catch (final RuntimeException unparseable) {
            return false;
        }
        for (final ColumnReferenceExpression reference : walk.references()) {
            if (reference.getPositionalOrdinal() > 0) {
                return true;
            }
        }
        return false;
    }

    /** Whether a key's text holds a subquery; a key that does not parse holds none. */
    private static boolean holdsSubquery(final String keyText) {
        try {
            return SubqueryExpression.occursIn(ExpressionEvaluator.parse(keyText));
        } catch (final RuntimeException unparseable) {
            return false;
        }
    }

    /**
     * Resolve one ORDER BY key for a row, preserving the original resolution order: qualified column
     * (table.column / alias.column), else a bare column-index lookup, else a "column not found" error.
     */
    private Object resolveOrderValue(final Row row, final String colName, final Table table,
                                     final Map<String, Table> aliasToTable, final List<Table> allTables,
                                     final boolean holdsSubquery) {
        try {
            return executor.getQualifiedColumnValueFromTables(row, allTables, aliasToTable, colName,
                table != null ? table.getJoinKeyNames() : null);
        } catch (final InvalidQualifierException invalidQualifier) {
            // Definitive — live rejects an ORDER BY key whose qualifier names no FROM key
            // ("invalid identifier 'R.C'"); the bare-column fallbacks below must not resurrect it.
            throw invalidQualifier;
        } catch (final Exception e) {
            try {
                final int colIndex = ValueComparisons.getWrittenColumnIndex(table, colName);
                return row.getValue(colIndex);
            } catch (final Exception e2) {
                // Not a plain column: evaluate as an expression against the row so ORDER BY can sort by an
                // arbitrary expression or function (e.g. ORDER BY a+b, ABS(a)) — Snowflake allows any
                // expression over the in-scope tables as a sort key.
                try {
                    return keyEvaluator(colName, table, aliasToTable, allTables).evaluate(colName, row);
                } catch (final UnsupportedSubqueryException unsupported) {
                    throw unsupported;
                } catch (final Exception e3) {
                    throw unresolvableKey(colName, e3, holdsSubquery);
                }
            }
        }
    }

    /** ORDER BY by a SELECT alias → that item's expression text, so it can be evaluated per row. */
    private String resolveOrderAlias(final String text, final FrostlakeParser.SelectStatementContext ctx,
                                     final Table table, final Map<String, Table> aliasToTable) {
        final FrostlakeParser.SelectOperandContext firstOp = ctx.selectOperand(0);
        final FrostlakeParser.SelectClauseContext firstClause = firstOp.selectClause() != null
            ? firstOp.selectClause()
            : firstOp.selectStatement().selectOperand(0).selectClause();
        for (final FrostlakeParser.SelectItemContext item : firstClause.selectList().selectItem()) {
            // CANONICAL and EXACT: the alias arrives folded already, so comparing it case-insensitively
            // to the key AS WRITTEN let an unquoted key answer to a quoted alias — live resolves
            // `SELECT a AS "x" … ORDER BY x` to nothing at all.
            if (SelectItemAccessors.getItemAlias(item) != null
                    && (SelectItemAccessors.getItemAlias(item))
                        .equals(SqlIdentifiers.canonicalText(text))) {
                final ParserRuleContext e = SelectItemAccessors.getItemExpression(item);
                if (e != null) {
                    return ParseTreeText.getOriginalText(e);
                }
            }
        }
        return resolveStarOutputName(text, firstClause, table, aliasToTable);
    }

    /**
     * ORDER BY by the OUTPUT name of a star's column, where the star's REPLACE or RENAME gave that column
     * an expression of its own: {@code SELECT * REPLACE (id * -1 AS id) … ORDER BY id} sorts by the
     * REPLACED value, and {@code SELECT * RENAME (id AS k) … ORDER BY k} sorts by the renamed column —
     * each exactly as a written alias would. The star's output name wins over a source column of the same
     * name, and over an ambiguous one across a join, since it names an output rather than a relation.
     * A column the star passes through unchanged is not answered here, so it resolves against the
     * relation as before; a qualified key ({@code ORDER BY fz.id}) names the source column and is left
     * alone, as live leaves it.
     */
    private String resolveStarOutputName(final String text, final FrostlakeParser.SelectClauseContext clause,
                                         final Table table, final Map<String, Table> aliasToTable) {
        final String wanted = SqlIdentifiers.canonicalText(text);
        if (table == null || wanted == null) {
            return text;
        }
        for (final FrostlakeParser.SelectItemContext item : clause.selectList().selectItem()) {
            if (SelectItemAccessors.getStarModifiers(item).isEmpty()
                    || SelectItemAccessors.isObjectStarItem(item)) {
                continue;
            }
            for (final StarColumn column : executor.starItemColumns(item, table, aliasToTable)) {
                if ((column.isReplaced() || column.isRenamed()) && column.getOutputName().equals(wanted)) {
                    return column.getExpression();
                }
            }
        }
        return text;
    }

    /**
     * The out-of-range refusal for every ORDER BY ordinal, run on its own. Ordinals are ordinarily
     * checked while the sort resolves its keys, which is late; live judges the range BEFORE it resolves
     * function names, so the unknown-name scan asks for it first.
     *
     * @param stmtCtx the statement whose ORDER BY is checked
     * @param table the leading relation, for expanding a star item to its columns
     * @param allTables every relation in the FROM, same purpose
     */
    void validateOrderOrdinals(final FrostlakeParser.SelectStatementContext stmtCtx, final Table table,
                               final Map<String, Table> aliasToTable) {
        if (stmtCtx.orderByClause() == null) {
            return;
        }
        for (final FrostlakeParser.OrderItemContext item : stmtCtx.orderByClause().orderItem()) {
            // A key too wide to BE an integer is refused by the literal reader before it is a position
            // at all — the reader runs on the written number, so it outranks the range check that would
            // otherwise call the same digits an out-of-range ordinal.
            IntegerLiteralRange.reject(item.expression().getStart());
            resolveOrderOrdinal(ParseTreeText.getOriginalText(item.expression()), stmtCtx, table, aliasToTable);
        }
    }

    /** ORDER BY &lt;n&gt; positional reference: resolve to the N-th SELECT item's expression text. */
    private String resolveOrderOrdinal(final String text, final FrostlakeParser.SelectStatementContext ctx) {
        return resolveOrderOrdinal(text, ctx, null, null);
    }

    /**
     * ORDER BY &lt;n&gt; positional reference, resolved to the N-th SELECT item's expression text. A
     * position past the select list is REFUSED with live's sentence, which echoes the value the
     * literal FOLDS to — {@code [9.5]}, not the 9 it truncates to, and {@code [10]} for {@code 1e1}.
     *
     * <p>A star item makes the width depend on the relation, so the check needs it: with no relation
     * to expand against the range check is skipped rather than guessed, and the caller that DOES have
     * one ({@link #validateOrderOrdinals}) runs first at compile time.
     */
    private String resolveOrderOrdinal(final String text, final FrostlakeParser.SelectStatementContext ctx,
                                       final Table table, final Map<String, Table> aliasToTable) {
        final long position = OrdinalLiteral.positionOf(text);
        if (position == OrdinalLiteral.NOT_AN_ORDINAL) {
            return text;
        }
        final FrostlakeParser.SelectOperandContext firstOp = ctx.selectOperand(0);
        final FrostlakeParser.SelectClauseContext firstClause = firstOp.selectClause() != null
            ? firstOp.selectClause()
            : firstOp.selectStatement().selectOperand(0).selectClause();
        final List<FrostlakeParser.SelectItemContext> items = firstClause.selectList().selectItem();
        final List<String> projected = projectedExpressions(items, table, aliasToTable);
        if (projected == null) {
            // The projection could not be expanded — a star with no relation to expand against, or one
            // whose modifiers reshape it. Neither the position nor the range can be judged, so the key
            // is left as written rather than refused on a guess.
            return text;
        }
        if (position < 1 || position > projected.size()) {
            throw new RuntimeException(SqlCompilationError.of(
                "[" + OrdinalLiteral.echo(text) + "] is not a valid order by expression"));
        }
        return projected.get((int) position - 1);
    }

    /**
     * The collation each key of a projected sort compares under.
     *
     * @param items        the ORDER BY items
     * @param colIndex     the projected slot each item reads, or -1 when it is resolved per group
     * @param projected    the projected expression per slot, or null when the projection cannot be laid out
     * @param table        the base relation, or null
     * @param aliasToTable its alias map, or null
     * @return one entry per key, null where the key carries no collation
     */
    private CollationSpec[] projectedKeyCollations(final List<FrostlakeParser.OrderItemContext> items,
                                                   final int[] colIndex, final List<String> projected,
                                                   final Table table, final Map<String, Table> aliasToTable) {
        final CollationSpec[] rules = new CollationSpec[items.size()];
        final List<String> keyTexts = new ArrayList<>(items.size());
        for (int k = 0; k < items.size(); k++) {
            if (colIndex[k] >= 0 && projected != null && colIndex[k] < projected.size()) {
                keyTexts.add(projected.get(colIndex[k]));
            } else {
                keyTexts.add(ParseTreeText.getOriginalText(items.get(k).expression()));
            }
        }
        if (!KeyCollations.reachable(keyTexts, table, aliasToTable, null)) {
            return rules;
        }
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(table,
            executor.getFunctionRegistry(), executor.getCatalog(), executor);
        return KeyCollations.resolve(keyTexts, evaluator);
    }

    /**
     * The select list as the OUTPUT columns it projects, each star expanded against its relation — the
     * width a position is judged against, and the expression a position resolves to. Returns null when
     * a star cannot be expanded (no relation supplied, an unresolvable qualifier, or modifiers that
     * reshape the projection), so the caller steps aside instead of guessing.
     */
    private List<String> projectedExpressions(final List<FrostlakeParser.SelectItemContext> items,
                                              final Table table, final Map<String, Table> aliasToTable) {
        final List<String> projected = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : items) {
            if (SelectItemAccessors.isExprItem(item)) {
                final ParserRuleContext e = SelectItemAccessors.getItemExpression(item);
                if (e == null) {
                    return null;
                }
                // Original text (spacing preserved), as the alias path does — getText() would collapse
                // `x IS NOT NULL` to `xISNOTNULL` and the key would then resolve to nothing.
                projected.add(ParseTreeText.getOriginalText(e));
                continue;
            }
            if (table == null || !SelectItemAccessors.getStarModifiers(item).isEmpty()) {
                return null;
            }
            if (SelectItemAccessors.isObjectStarItem(item)) {
                projected.add(executor.objectStarExpression(item, table, aliasToTable));
                continue;
            }
            final List<StarColumn> starColumns = executor.starItemColumns(item, table, aliasToTable);
            if (starColumns.isEmpty()) {
                return null;
            }
            for (final StarColumn starColumn : starColumns) {
                projected.add(starColumn.getExpression());
            }
        }
        return projected;
    }

    /**
     * ORDER BY over rows already reshaped to the SELECT list (after GROUP BY / aggregation / window). Each
     * ORDER BY item is matched to a SELECT column by position, alias, or expression text; an item that
     * matches NONE is resolved by {@code resolver} over the row's group — a grouped column or aggregate not
     * in the SELECT is a valid Snowflake ORDER BY ({@code GROUP BY a, b ORDER BY b} / {@code ORDER BY MAX(c)}).
     * With no resolver an unmatched item is an error, as before.
     */
    List<Row> orderByAfterGroupBy(final List<Row> rows, final FrostlakeParser.SelectStatementContext ctx,
                                  final GroupOrderKeyResolver resolver, final Table table,
                                  final Map<String, Table> aliasToTable) {
        // The first selectClause carries the SELECT-list structure (UNION parts must be compatible).
        final FrostlakeParser.SelectOperandContext firstOp = ctx.selectOperand(0);
        final FrostlakeParser.SelectClauseContext firstClause = firstOp.selectClause() != null
            ? firstOp.selectClause()
            : firstOp.selectStatement().selectOperand(0).selectClause();
        final List<FrostlakeParser.SelectItemContext> selectItems = firstClause.selectList().selectItem();

        // Slot layout of the projected row: a star item spans its expanded columns, so ORDER BY
        // keys resolve to SLOTS of the row, not select-item indexes.
        final int[] itemStartSlot = new int[selectItems.size()];
        final List<List<String>> starNamesByItem = new ArrayList<>();
        int slotCount = 0;
        for (int i = 0; i < selectItems.size(); i++) {
            itemStartSlot[i] = slotCount;
            final FrostlakeParser.SelectItemContext si = selectItems.get(i);
            if (SelectItemAccessors.isStarItem(si) || SelectItemAccessors.isQualifiedStarItem(si)) {
                final List<String> names = new ArrayList<String>();
                try {
                    for (final StarColumn sc : executor.starItemColumns(si, table, aliasToTable)) {
                        names.add(sc.getOutputName().toUpperCase());
                    }
                } catch (final RuntimeException unresolvable) {
                    // an unresolvable star leaves its columns unmatchable; the resolver may still serve
                }
                starNamesByItem.add(names);
                slotCount += names.size();
            } else {
                starNamesByItem.add(null);
                slotCount++;
            }
        }
        final int totalSlots = slotCount;

        final List<FrostlakeParser.OrderItemContext> items = ctx.orderByClause().orderItem();
        final int nKeys = items.size();
        final int[] colIndex = new int[nKeys];       // matched projected-row SLOT, or -1 when resolved per-group
        final boolean[] ascending = new boolean[nKeys];
        final Boolean[] nullsFirst = new Boolean[nKeys];   // nullable — nullsFirstFlag returns null when unspecified
        for (int k = 0; k < nKeys; k++) {
            final FrostlakeParser.OrderItemContext item = items.get(k);
            ascending[k] = item.DESC() == null;
            nullsFirst[k] = ValueComparisons.nullsFirstFlag(item);
            colIndex[k] = matchOrderSlot(item.expression().getText(), selectItems, itemStartSlot,
                starNamesByItem, totalSlots, ctx.selectOperand().size() > 1);
            if (colIndex[k] == -1 && resolver == null) {
                // AS WRITTEN, not getText(): the latter concatenates tokens with no whitespace, so a
                // key with separators came back unreadable — ROW_NUMBER()OVER(ORDERBYc).
                throw new RuntimeException("ORDER BY expression not found in SELECT list: "
                    + ParseTreeText.getOriginalText(item.expression()));
            }
        }

        // Precompute each row's sort keys BEFORE sorting: sorting reorders rows, but a resolver keys off the
        // row's ORIGINAL index. A matched key is the projected column value; an unmatched key is computed
        // over that row's group.
        // A key that carries a collation sorts under it: a matched key takes the collation of the
        // projected expression it reads, an unmatched one that of the key as written.
        final CollationSpec[] keyRules = projectedKeyCollations(items, colIndex,
            projectedExpressions(selectItems, table, aliasToTable), table, aliasToTable);
        final List<Object[]> keys = new ArrayList<>(rows.size());
        for (int r = 0; r < rows.size(); r++) {
            final Object[] rowKeys = new Object[nKeys];
            for (int k = 0; k < nKeys; k++) {
                rowKeys[k] = CollatedKey.of(DeferredFault.read(colIndex[k] >= 0 ? rows.get(r).getValue(colIndex[k])
                    : resolver.resolve(r, items.get(k))), keyRules[k]);
            }
            keys.add(rowKeys);
        }

        final Integer[] order = new Integer[rows.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(final Integer a, final Integer b) {
                final Object[] ka = keys.get(a);
                final Object[] kb = keys.get(b);
                for (int k = 0; k < nKeys; k++) {
                    final int cmp = ValueComparisons.compareOrderKey(ka[k], kb[k], ascending[k], nullsFirst[k]);
                    if (cmp != 0) {
                        return cmp;
                    }
                }
                return 0;
            }
        });

        final List<Row> sorted = new ArrayList<>(rows.size());
        for (final int i : order) {
            sorted.add(rows.get(i));
        }
        return sorted;
    }

    /**
     * Plan-time scope validation of a GROUPED / windowed query's ORDER BY keys — the path whose
     * unmatched keys resolve per source group at row time, which never runs over an EMPTY input
     * while live rejects at compile time (measured per cell): an invalid qualifier and an unknown
     * name are "invalid identifier" — inside aggregate arguments too — and, for a GROUP BY query,
     * a bare FROM column that is neither a group key nor inside an aggregate is
     * "[T.C] is not a valid order by expression". Grouped columns and aggregates NOT in the
     * SELECT list stay legal, as do output aliases, ordinals, repeated group-key expressions and
     * expressions over any of these.
     */
    void validateGroupedOrderKeyScope(final FrostlakeParser.SelectStatementContext stmtCtx,
                                      final FrostlakeParser.SelectClauseContext selectCtx,
                                      final Table table, final Map<String, Table> aliasToTable,
                                      final List<Table> allTables) {
        if (stmtCtx.orderByClause() == null || stmtCtx.selectOperand().size() > 1) {
            return;
        }
        final List<FrostlakeParser.SelectItemContext> selectItems = selectCtx.selectList().selectItem();
        final Set<String> outputNames = new HashSet<>(executor.selectItemAliasNames(selectCtx));
        for (final FrostlakeParser.SelectItemContext item : selectItems) {
            final String produced = producedColumnName(item);
            if (produced != null) {
                outputNames.add(produced.toUpperCase());
            }
        }
        final Set<String> groupKeyTexts = new HashSet<>();
        final Set<String> groupKeyNames = new HashSet<>();
        final FrostlakeParser.GroupByClauseContext groupBy = selectCtx.groupByClause();
        final GroupedKeyColumns groupedColumns = new GroupedKeyColumns(table, aliasToTable, allTables, selectCtx);
        collectGroupKeys(groupBy, groupKeyTexts, groupKeyNames, groupedColumns);

        for (final FrostlakeParser.OrderItemContext item : stmtCtx.orderByClause().orderItem()) {
            rejectCaseVariantOrderKey(item, selectItems, groupBy, table, allTables);
            if (matchOrderItem(item.expression().getText(), selectItems, false) >= 0) {
                continue;
            }
            final String asWritten = ParseTreeText.getOriginalText(item.expression()).trim();
            if (outputNames.contains(asWritten.toUpperCase())
                    || groupKeyTexts.contains(item.expression().getText().toUpperCase())) {
                continue;
            }
            executor.validateClauseScope(asWritten, table, aliasToTable, allTables, outputNames,
                item.expression());
            if (groupBy != null && groupBy.ALL() == null) {
                rejectUngroupedOrderReference(ExpressionEvaluator.parse(asWritten), table, allTables,
                    outputNames, groupKeyNames, groupedColumns);
            }
        }
    }

    /**
     * A grouped query's ORDER BY key naming a column that has a case variant beside it: the key matches an output
     * column or a grouping key only when spelled exactly as it resolves, so {@code SELECT "x" … GROUP BY "x"
     * ORDER BY X} is "[CV.X] is not a valid order by expression" (live-verified).
     */
    private void rejectCaseVariantOrderKey(final FrostlakeParser.OrderItemContext item,
                                           final List<FrostlakeParser.SelectItemContext> selectItems,
                                           final FrostlakeParser.GroupByClauseContext groupBy, final Table table,
                                           final List<Table> allTables) {
        final Expression key;
        try {
            key = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(item.expression()).trim());
        } catch (final RuntimeException unparsed) {
            return;
        }
        if (!(key instanceof ColumnReferenceExpression) || groupBy == null || groupBy.ALL() != null) {
            return;
        }
        final ColumnReferenceExpression ref = (ColumnReferenceExpression) key;
        final String exact = ref.getColumnName();
        if (!CaseVariantColumns.present(table, allTables, exact)) {
            return;
        }
        for (final FrostlakeParser.SelectItemContext selectItem : selectItems) {
            final String alias = SelectItemAccessors.getItemAlias(selectItem);
            final String produced = alias != null ? alias : producedColumnName(selectItem);
            if (exact.equals(produced)) {
                return;
            }
        }
        for (final FrostlakeParser.GroupByElementContext element : groupBy.groupByElement()) {
            try {
                final Expression grouped = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(element).trim());
                if (grouped instanceof ColumnReferenceExpression
                        && exact.equals(((ColumnReferenceExpression) grouped).getColumnName())) {
                    return;
                }
            } catch (final RuntimeException unparsed) {
                // not a column key
            }
        }
        throw new RuntimeException(SqlCompilationError.of("[" + ownerQualifier(ref, table, allTables) + "."
            + SqlIdentifiers.spellCanonicalEscaped(exact) + "] is not a valid order by expression"));
    }

    /**
     * A GROUPED or DISTINCT query's ORDER BY may hold no subquery at all — correlated or not. Live refuses
     * the key by re-printing the subquery from its plan: {@code [(SELECT MAX(G.V) AS "MAX(V)" FROM G AS G)]
     * is not a valid order by expression}, where the same key over an ungrouped query is answered, and a
     * key that names the subquery through the select list's alias or by its ordinal stays legal. A query
     * that groups only implicitly ({@code SELECT MAX(id) FROM fz ORDER BY (SELECT …)}) is answered too
     * (live-verified).
     *
     * <p>A subquery an aggregate call takes as its argument is computed with the aggregate, row by row, and is
     * not a key of its own: {@code ORDER BY MAX((SELECT MAX(v) FROM g))} is answered, and a correlated one is
     * judged as the correlation rules judge it ({@code MAX((SELECT MAX(fz.id + g.v) FROM g))} is an unsupported
     * subquery). {@code ABS((SELECT …))} and {@code (SELECT …) + 1} are refused here, and so is a key holding
     * such a subquery beside an aggregated one, whichever comes first (live-verified).
     *
     * @param stmtCtx      the statement, whose ORDER BY is judged
     * @param selectCtx    its select clause
     * @param table        the relation the query reads
     * @param aliasToTable its FROM-clause keys
     * @param allTables    every relation in scope
     */
    void rejectSubqueryInGroupedOrderKey(final FrostlakeParser.SelectStatementContext stmtCtx,
                                         final FrostlakeParser.SelectClauseContext selectCtx,
                                         final Table table, final Map<String, Table> aliasToTable,
                                         final List<Table> allTables) {
        if (stmtCtx.orderByClause() == null || stmtCtx.selectOperand().size() > 1
                || selectCtx.groupByClause() == null && selectCtx.DISTINCT() == null) {
            return;
        }
        for (final FrostlakeParser.OrderItemContext item : stmtCtx.orderByClause().orderItem()) {
            final ParserRuleContext subquery = firstUnaggregatedSubqueryIn(item.expression());
            if (subquery != null) {
                throw new RuntimeException(SqlCompilationError.of("[" + plannedSubquery(subquery, table,
                    aliasToTable, allTables) + "] is not a valid order by expression"));
            }
        }
    }

    /**
     * The first subquery a key holds outside every aggregate call, outermost first, or null when it holds
     * none: a parenthesised one, or the argument a call takes as a subquery written without parentheses,
     * {@code ABS(SELECT …)}. A window call is no aggregate here: it is judged whole as a key.
     */
    private ParserRuleContext firstUnaggregatedSubqueryIn(final ParseTree node) {
        if (node instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            return (FrostlakeParser.ScalarSubqueryExprContext) node;
        }
        if (node instanceof FrostlakeParser.FunctionArgContext
                && ((FrostlakeParser.FunctionArgContext) node).selectStatement() != null) {
            return (FrostlakeParser.FunctionArgContext) node;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext
                && ((FrostlakeParser.FunctionCallExprContext) node).overClause() == null
                && executor.getFunctionRegistry().hasAggregateFunction(
                    ((FrostlakeParser.FunctionCallExprContext) node).functionName().getText())) {
            return null;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final ParserRuleContext found = firstUnaggregatedSubqueryIn(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The subquery re-printed from its plan, as the refusal echoes it; its written text where the shape
     *  is not modelled. */
    private String plannedSubquery(final ParserRuleContext subquery, final Table table,
                                   final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final String written = ParseTreeText.getOriginalText(subquery);
        try {
            final ExpressionEvaluator printer = new ExpressionEvaluator(table,
                executor.getFunctionRegistry(), executor.getCatalog(), executor);
            if (allTables != null && !allTables.isEmpty()) {
                printer.setMultiTableContext(aliasToTable, allTables);
            }
            final Expression parsed = subquery instanceof FrostlakeParser.FunctionArgContext
                ? new SubqueryExpression(ParseTreeText.getOriginalText(
                    ((FrostlakeParser.FunctionArgContext) subquery).selectStatement()))
                : ExpressionEvaluator.parse(written);
            final String printed = printer.plannedText(parsed);
            return printed == null ? written : printed;
        } catch (final RuntimeException notModelled) {
            return written;
        }
    }

    /**
     * With SELECT DISTINCT an ORDER BY key must be something the SELECT list PRODUCES. The distinct
     * step collapses the rows a key would otherwise be computed from, so live refuses every key that
     * reaches past the output — a base column, a GROUP BY key and an aggregate alike — with
     * "[X] is not a valid order by expression", while an ordinal or an alias resolves as usual.
     *
     * <p>An expression BUILT from selected items stays legal ({@code ORDER BY a + 1} beside {@code a},
     * {@code ORDER BY SUM(b) + 1} beside {@code SUM(b)}), so the walk stops at any subtree the SELECT
     * list already carries and only judges what it reaches beyond one. A star projects every column,
     * and then nothing a key can name is missing.
     *
     * <p>Live echoes a bare column QUALIFIED ({@code [GW.A]}) and anything else as the key was written
     * ({@code [COUNT(*)]}), except that it re-prints a window call canonicalised where this prints the
     * written form — the same echo difference every refusal naming a window carries.
     */
    void validateDistinctOrderKeyScope(final FrostlakeParser.SelectStatementContext stmtCtx,
                                       final FrostlakeParser.SelectClauseContext selectCtx,
                                       final Table table, final Map<String, Table> aliasToTable,
                                       final List<Table> allTables) {
        if (selectCtx.DISTINCT() == null || stmtCtx.orderByClause() == null
                || stmtCtx.selectOperand().size() > 1) {
            return;
        }
        final List<FrostlakeParser.SelectItemContext> selectItems = selectCtx.selectList().selectItem();
        final Set<String> outputNames = new HashSet<>(executor.selectItemAliasNames(selectCtx));
        for (final FrostlakeParser.SelectItemContext item : selectItems) {
            if (SelectItemAccessors.isStarItem(item) || SelectItemAccessors.isQualifiedStarItem(item)) {
                return;
            }
            final String produced = producedColumnName(item);
            if (produced != null) {
                outputNames.add(produced.toUpperCase());
            }
        }
        for (final FrostlakeParser.OrderItemContext item : stmtCtx.orderByClause().orderItem()) {
            if (matchOrderItem(item.expression().getText(), selectItems, false) >= 0) {
                continue;
            }
            // A name that resolves to NOTHING is that error first, DISTINCT or no DISTINCT — live
            // answers "invalid identifier" for ORDER BY zz rather than calling it unselected.
            executor.validateClauseScope(ParseTreeText.getOriginalText(item.expression()).trim(),
                table, aliasToTable, allTables, outputNames, item.expression());
            rejectUnselectedDistinctKey(item.expression(), selectItems, outputNames, table, allTables);
        }
    }

    /**
     * One node of a DISTINCT query's ORDER BY key: satisfied when the SELECT list carries this whole
     * subtree, refused when it is a reference or an aggregate the output does not hold, and otherwise
     * decided by its children.
     */
    private void rejectUnselectedDistinctKey(final ParseTree node,
                                             final List<FrostlakeParser.SelectItemContext> selectItems,
                                             final Set<String> outputNames, final Table table,
                                             final List<Table> allTables) {
        final String text = node.getText();
        if (text.isEmpty()) {
            return;
        }
        // Ordinals are a WHOLE-key spelling only — a bare 1 nested inside a + 1 is a literal.
        if (matchOrderItem(text, selectItems, false, false) >= 0
                || outputNames.contains(text.toUpperCase())) {
            return;
        }
        final String asWritten = originalTextOf(node);
        final Expression parsed = parsedOrNull(asWritten);
        if (parsed instanceof ColumnReferenceExpression) {
            final ColumnReferenceExpression ref = (ColumnReferenceExpression) parsed;
            // A FUNCTION NAME parses as a column reference perfectly well on its own, and the walk
            // reaches one while descending into a call: ORDER BY UPPER(n) was refused as "[EB.UPPER]",
            // naming a column no table has. Only a name some relation actually declares can be the
            // offender — anything else is a word from the syntax, and the walk carries on past it to
            // the arguments, where the real column is.
            if (!namesAColumn(ref, table, allTables)) {
                return;
            }
            if (!outputNames.contains(ref.getColumnName().toUpperCase())) {
                throw new RuntimeException(SqlCompilationError.of(
                    "[" + ownerQualifier(ref, table, allTables) + "." + ref.getColumnName().toUpperCase()
                    + "] is not a valid order by expression"));
            }
            return;
        }
        if (parsed instanceof FunctionCallExpression
                && executor.getFunctionRegistry().getAggregateFunction(
                    ((FunctionCallExpression) parsed).getFunctionName().toUpperCase()) != null) {
            throw new RuntimeException(SqlCompilationError.of(
                "[" + asWritten.trim() + "] is not a valid order by expression"));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            rejectUnselectedDistinctKey(node.getChild(i), selectItems, outputNames, table, allTables);
        }
    }

    /** A subtree as the statement spelled it; a lone token has only its own text to give. */
    private String originalTextOf(final ParseTree node) {
        if (node instanceof ParserRuleContext) {
            return ParseTreeText.getOriginalText((ParserRuleContext) node);
        }
        return node.getText();
    }

    /** The expression a fragment parses to, or null when it is not one on its own (an operator token,
     *  a stray parenthesis) — those are decided by walking on into the children. */
    private Expression parsedOrNull(final String text) {
        try {
            return ExpressionEvaluator.parse(text);
        } catch (final RuntimeException notAnExpressionOfItsOwn) {
            return null;
        }
    }

    /** The GROUP BY keys, as whitespace-free upper texts (whole-key repetition in ORDER BY is
     *  legal) and — for plain column keys — their column names. ROLLUP / CUBE / GROUPING SETS
     *  members are keys all the same. */
    private void collectGroupKeys(final FrostlakeParser.GroupByClauseContext groupBy,
                                  final Set<String> keyTexts, final Set<String> keyNames,
                                  final GroupedKeyColumns groupedColumns) {
        if (groupBy == null || groupBy.ALL() != null) {
            return;
        }
        final List<FrostlakeParser.ExpressionContext> keys = new ArrayList<>();
        for (final FrostlakeParser.GroupByElementContext element : groupBy.groupByElement()) {
            if (element.expression() != null) {
                keys.add(element.expression());
            }
            if (element.groupByColumnList() != null) {
                keys.addAll(element.groupByColumnList().expression());
            }
            if (element.groupingSetList() != null) {
                for (final FrostlakeParser.GroupingSetContext set : element.groupingSetList().groupingSet()) {
                    keys.addAll(set.expression());
                }
            }
        }
        for (final FrostlakeParser.ExpressionContext key : keys) {
            keyTexts.add(key.getText().toUpperCase());
            groupedColumns.add(ParseTreeText.getOriginalText(key));
            try {
                final Expression parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(key));
                if (parsed instanceof ColumnReferenceExpression) {
                    keyNames.add(((ColumnReferenceExpression) parsed).getColumnName().toUpperCase());
                }
            } catch (final RuntimeException unparseableKey) {
                // A key the expression parser cannot form contributes its text only.
            }
        }
    }

    /**
     * The "[T.C] is not a valid order by expression" family: within an unmatched ORDER BY key of a
     * GROUP BY query, a column reference OUTSIDE any aggregate call must be a group key (or an
     * output alias). References inside an aggregate are computed per group and always legal.
     * Node kinds this walk does not model (CASE, subqueries, window calls) keep their row-time
     * handling.
     */
    private void rejectUngroupedOrderReference(final Expression expr, final Table table,
                                               final List<Table> allTables, final Set<String> outputNames,
                                               final Set<String> groupKeyNames,
                                               final GroupedKeyColumns groupedColumns) {
        if (expr instanceof ColumnReferenceExpression) {
            final ColumnReferenceExpression ref = (ColumnReferenceExpression) expr;
            final String colName = ref.getColumnName().toUpperCase();
            // A qualified reference is held to its own relation's key: ORDER BY b.x beside GROUP BY a.x is refused.
            final boolean grouped = ref.getTableName() == null ? groupKeyNames.contains(colName)
                : groupKeyNames.contains(colName) && groupedColumns.grouped(ref.getTableName(), colName);
            if (outputNames.contains(colName) || grouped) {
                return;
            }
            throw new RuntimeException(SqlCompilationError.of(
                "[" + ownerQualifier(ref, table, allTables) + "." + colName
                + "] is not a valid order by expression"));
        }
        if (expr instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expr;
            if (executor.getFunctionRegistry().getAggregateFunction(call.getFunctionName().toUpperCase()) != null) {
                return;
            }
            for (final Expression arg : call.getArguments()) {
                rejectUngroupedOrderReference(arg, table, allTables, outputNames, groupKeyNames, groupedColumns);
            }
            return;
        }
        if (expr instanceof BinaryOperationExpression) {
            rejectUngroupedOrderReference(((BinaryOperationExpression) expr).getLeft(),
                table, allTables, outputNames, groupKeyNames, groupedColumns);
            rejectUngroupedOrderReference(((BinaryOperationExpression) expr).getRight(),
                table, allTables, outputNames, groupKeyNames, groupedColumns);
            return;
        }
        if (expr instanceof UnaryOperationExpression) {
            rejectUngroupedOrderReference(((UnaryOperationExpression) expr).getOperand(),
                table, allTables, outputNames, groupKeyNames, groupedColumns);
            return;
        }
        if (expr instanceof CastExpression) {
            rejectUngroupedOrderReference(((CastExpression) expr).getExpression(),
                table, allTables, outputNames, groupKeyNames, groupedColumns);
        }
    }

    /** The relation part of live's bracketed rendering: the written qualifier when the reference
     *  carries one, else the name of the first in-scope relation carrying the column. */
    /** Whether {@code ref} names a column some relation in scope really declares. */
    private boolean namesAColumn(final ColumnReferenceExpression ref, final Table table,
                                 final List<Table> allTables) {
        if (allTables != null) {
            for (final Table candidate : allTables) {
                for (final TableColumn column : candidate.getColumns()) {
                    if (column.getName().equalsIgnoreCase(ref.getColumnName())) {
                        return true;
                    }
                }
            }
        }
        if (table == null) {
            return false;
        }
        for (final TableColumn column : table.getColumns()) {
            if (column.getName().equalsIgnoreCase(ref.getColumnName())) {
                return true;
            }
        }
        return false;
    }

    private String ownerQualifier(final ColumnReferenceExpression ref, final Table table,
                                  final List<Table> allTables) {
        if (ref.isQualified()) {
            return ref.getTableName().toUpperCase();
        }
        if (allTables != null) {
            for (final Table candidate : allTables) {
                for (final TableColumn column : candidate.getColumns()) {
                    if (column.getName().equalsIgnoreCase(ref.getColumnName())) {
                        return candidate.getName().toUpperCase();
                    }
                }
            }
        }
        return table != null ? table.getName().toUpperCase() : "";
    }

    /** The canonical name a key written as one identifier spells, or null for any other key text. */
    private static String outputColumnKey(final String asWritten) {
        if (!SqlIdentifiers.isIdentifierReference(asWritten)) {
            return null;
        }
        final String[] parts = SqlIdentifiers.canonicalTextParts(asWritten);
        return parts.length == 1 ? parts[0] : null;
    }

    /**
     * ORDER BY over a set operation's combined result. Live resolves these keys against the OUTPUT
     * only — an output column name (the first branch's alias, or the produced name of an un-aliased
     * item), a positional ordinal, or an expression over those output names; a branch table's
     * qualifier, a later branch's column name, an aliased item's pre-alias expression text and any
     * unknown name are all "invalid identifier", measured cell by cell. There is deliberately NO
     * fall-through to any branch's FROM scope.
     */
    List<Row> orderBySetOperation(final List<Row> rows, final FrostlakeParser.SelectStatementContext ctx,
                                  final List<ResultSetColumn> outputColumns) {
        final List<FrostlakeParser.OrderItemContext> items = ctx.orderByClause().orderItem();
        final int nKeys = items.size();
        final int[] colIndex = new int[nKeys];
        final List<Expression> keyExprs = new ArrayList<>(nKeys);
        final boolean[] ascending = new boolean[nKeys];
        final Boolean[] nullsFirst = new Boolean[nKeys];
        // Where each expression key stands, so a refusal its evaluation raises is placed in the statement.
        final SourcePosition[] keyOrigins = new SourcePosition[nKeys];

        final List<TableColumn> outputAsTableColumns = new ArrayList<>();
        final Set<String> outputNames = new HashSet<>();
        for (final ResultSetColumn column : outputColumns) {
            outputAsTableColumns.add(new TableColumn(column.getName(), column.getDataType(), true,
                null, false, false, false));
            // Exempt by its CANONICAL name, as the walk compares: folding it let x name an output column "x".
            outputNames.add(column.getName());
        }
        final Table outputTable = new Table("unioned", outputAsTableColumns, false);

        ExpressionEvaluator keyEvaluator = null;
        for (int k = 0; k < nKeys; k++) {
            final FrostlakeParser.OrderItemContext item = items.get(k);
            ascending[k] = item.DESC() == null;
            nullsFirst[k] = ValueComparisons.nullsFirstFlag(item);
            final String asWritten = ParseTreeText.getOriginalText(item.expression()).trim();
            int matched = -1;
            final long ordinal = OrdinalLiteral.positionOf(asWritten);
            if (ordinal != OrdinalLiteral.NOT_AN_ORDINAL) {
                if (ordinal >= 1 && ordinal <= outputColumns.size()) {
                    matched = (int) ordinal - 1;
                } else {
                    // A set operation's ORDER BY is refused in the same words as a plain query's — the
                    // arms' shared output width is what the position is judged against.
                    throw new RuntimeException(SqlCompilationError.of(
                        "[" + OrdinalLiteral.echo(asWritten) + "] is not a valid order by expression"));
                }
            }
            // A key written as one name matches an output column by that name's CANONICAL spelling, exactly:
            // over an output column "x", ORDER BY x names X and is refused (live-verified). Anything else is
            // compared as written, as before.
            final String outputKey = outputColumnKey(asWritten);
            for (int i = 0; matched == -1 && i < outputColumns.size(); i++) {
                final String outputName = outputColumns.get(i).getName();
                if (outputKey != null ? outputName.equals(outputKey) : outputName.equalsIgnoreCase(asWritten)) {
                    matched = i;
                }
            }
            colIndex[k] = matched;
            if (matched >= 0) {
                keyExprs.add(null);
                continue;
            }
            // An expression key: only the output relation is in scope — the walk turns anything
            // else (a branch qualifier, a non-output name) into live's invalid-identifier.
            executor.validateClauseScope(asWritten, outputTable,
                Map.of(outputTable.getName().toUpperCase(), outputTable), List.of(outputTable), outputNames,
                item.expression());
            // A subquery in the key compiles where the key stands, so what it refuses is placed in the statement.
            // It reads none of the output's names: they are no relation of its.
            final RuntimeException subqueryRefusal = executor.compileSubqueriesIn(List.of(item.expression()),
                new Table("DUMMY", new ArrayList<TableColumn>(), false), null, null, null);
            if (subqueryRefusal != null) {
                throw subqueryRefusal;
            }
            keyOrigins[k] = new SourcePosition(item.expression().getStart().getLine(),
                item.expression().getStart().getCharPositionInLine());
            if (keyEvaluator == null) {
                keyEvaluator = new ExpressionEvaluator(outputTable,
                    executor.getFunctionRegistry(), executor.getCatalog(), executor);
            }
            keyExprs.add(ExpressionEvaluator.parse(asWritten));
        }

        // An output column that carries a collation sorts under it; ties keep their order.
        final CollationSpec[] keyRules = new CollationSpec[nKeys];
        for (int k = 0; k < nKeys; k++) {
            final String spec = colIndex[k] >= 0 ? outputColumns.get(colIndex[k]).getCollation() : null;
            keyRules[k] = spec == null ? null : CollationSpec.parse(spec);
        }
        final List<Object[]> keys = new ArrayList<>(rows.size());
        for (final Row row : rows) {
            final Object[] rowKeys = new Object[nKeys];
            for (int k = 0; k < nKeys; k++) {
                if (colIndex[k] >= 0) {
                    rowKeys[k] = CollatedKey.of(DeferredFault.read(row.getValue(colIndex[k])), keyRules[k]);
                    continue;
                }
                final SourcePosition displaced = ExpressionSource.beginNested(keyOrigins[k]);
                try {
                    rowKeys[k] = CollatedKey.of(DeferredFault.read(keyEvaluator.evaluate(keyExprs.get(k), row)),
                        keyRules[k]);
                } finally {
                    ExpressionSource.end(displaced);
                }
            }
            keys.add(rowKeys);
        }

        final Integer[] order = new Integer[rows.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(final Integer a, final Integer b) {
                final Object[] ka = keys.get(a);
                final Object[] kb = keys.get(b);
                for (int k = 0; k < nKeys; k++) {
                    final int cmp = ValueComparisons.compareOrderKey(ka[k], kb[k], ascending[k], nullsFirst[k]);
                    if (cmp != 0) {
                        return cmp;
                    }
                }
                return 0;
            }
        });

        final List<Row> sorted = new ArrayList<>(rows.size());
        for (final int i : order) {
            sorted.add(rows.get(i));
        }
        return sorted;
    }

    /** The SELECT column index an ORDER BY expression matches (position, alias, or expression text), or -1. */
    /**
     * The projected-row SLOT an ORDER BY key addresses when rows are in SELECT-list shape — a star
     * item spans its expanded columns, so an item match maps to the item's first slot, an ordinal
     * counts OUTPUT columns through the expansion, and a star-expanded column is addressable by its
     * (possibly qualified) name. -1 when nothing matches.
     */
    private int matchOrderSlot(final String orderExpr, final List<FrostlakeParser.SelectItemContext> selectItems,
                               final int[] itemStartSlot, final List<List<String>> starNamesByItem,
                               final int totalSlots, final boolean setOperation) {
        if (orderExpr.matches("\\d+")) {
            final int ord = Integer.parseInt(orderExpr);
            if (ord >= 1 && ord <= totalSlots) {
                return ord - 1;
            }
        }
        final int itemMatch = matchOrderItem(orderExpr, selectItems, setOperation, false);
        if (itemMatch >= 0) {
            return itemStartSlot[itemMatch];
        }
        final int dot = orderExpr.lastIndexOf('.');
        final String bare = (dot >= 0 ? orderExpr.substring(dot + 1) : orderExpr).toUpperCase();
        for (int i = 0; i < selectItems.size(); i++) {
            final List<String> names = starNamesByItem.get(i);
            if (names == null) {
                continue;
            }
            final int at = names.indexOf(bare);
            if (at >= 0) {
                return itemStartSlot[i] + at;
            }
        }
        return -1;
    }

    private int matchOrderItem(final String orderExpr, final List<FrostlakeParser.SelectItemContext> selectItems,
                               final boolean setOperation) {
        return matchOrderItem(orderExpr, selectItems, setOperation, true);
    }

    private int matchOrderItem(final String orderExpr, final List<FrostlakeParser.SelectItemContext> selectItems,
                               final boolean setOperation, final boolean matchOrdinals) {
        // ORDER BY <n> positional reference → the N-th SELECT column.
        if (matchOrdinals && orderExpr.matches("\\d+")) {
            final int ord = Integer.parseInt(orderExpr);
            if (ord >= 1 && ord <= selectItems.size()) {
                return ord - 1;
            }
        }
        // Match by alias.
        for (int i = 0; i < selectItems.size(); i++) {
            if (SelectItemAccessors.getItemAlias(selectItems.get(i)) != null) {
                final String alias = (SelectItemAccessors.getItemAlias(selectItems.get(i)));
                if (alias.equals(SqlIdentifiers.canonicalText(orderExpr))) {
                    return i;
                }
            }
        }
        // Match by expression text (case-sensitive, then insensitive).
        for (int i = 0; i < selectItems.size(); i++) {
            final ParserRuleContext e = SelectItemAccessors.getItemExpression(selectItems.get(i));
            if (e != null && e.getText().equals(orderExpr)) {
                return i;
            }
        }
        for (int i = 0; i < selectItems.size(); i++) {
            final ParserRuleContext e = SelectItemAccessors.getItemExpression(selectItems.get(i));
            if (e != null && e.getText().equalsIgnoreCase(orderExpr)) {
                return i;
            }
        }
        // Match by the RESULT column NAME the item produces — ONLY for a set operation, whose output
        // columns take their names from the first branch and are the only thing its ORDER BY can name
        // (`… UNION ALL … ORDER BY region_id`, which matches neither an alias nor the item's
        // expression text `a.region_id`). A single SELECT keeps its existing resolution: there an
        // unmatched key is handed to the group resolver, which computes it over the row's group, and
        // hijacking that to a projected column changes the sort key of working queries.
        for (int i = 0; setOperation && i < selectItems.size(); i++) {
            final String produced = producedColumnName(selectItems.get(i));
            if (produced != null && produced.equalsIgnoreCase(orderExpr)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The result-column name an un-aliased select item produces, taken from the parse tree: the last part of
     * a (possibly qualified) column reference. Null for anything else — an expression has no natural name, and
     * {@code SELECT *} has no single one.
     */
    private String producedColumnName(final FrostlakeParser.SelectItemContext item) {
        if (!SelectItemAccessors.isExprItem(item)) {
            return null;
        }
        final FrostlakeParser.ExpressionContext valueExpr = SelectItemAccessors.getItemValueExpr(item);
        if (!(valueExpr instanceof FrostlakeParser.QualifiedNameExprContext)) {
            return null;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(
            ((FrostlakeParser.QualifiedNameExprContext) valueExpr).qualifiedName());
        return parts.length == 0 ? null : parts[parts.length - 1];
    }

    /**
     * Whether EVERY ORDER BY item matches a SELECT output column (by position, alias, or expression
     * text). When they all do, the caller sorts AFTER projection by the output values instead of
     * re-evaluating the item expressions as sort keys — live evaluates each SELECT item ONCE per
     * row, which a side-effecting item makes observable: {@code SELECT seq.nextval FROM t ORDER BY
     * 1} numbers the rows 1..n on a real account, while the re-evaluating order drew every value
     * twice.
     */
    boolean allOrderKeysMatchOutput(final FrostlakeParser.SelectStatementContext ctx) {
        if (ctx.orderByClause() == null) {
            return false;
        }
        final FrostlakeParser.SelectOperandContext firstOp = ctx.selectOperand(0);
        final FrostlakeParser.SelectClauseContext firstClause = firstOp.selectClause() != null
            ? firstOp.selectClause()
            : firstOp.selectStatement().selectOperand(0).selectClause();
        final List<FrostlakeParser.SelectItemContext> selectItems = firstClause.selectList().selectItem();
        for (final FrostlakeParser.OrderItemContext item : ctx.orderByClause().orderItem()) {
            if (matchOrderItem(item.expression().getText(), selectItems, false) < 0) {
                return false;
            }
        }
        return true;
    }

    /** The compile-time key-TYPE rejections (FILE / geospatial ORDER BY keys) for the
     *  sort-after-projection route, which skips {@link #orderBy}'s own pre-sort walk: ordinals and
     *  aliases resolve to their defining expressions and run the same key checks. */
    void validateOrderKeyTypes(final FrostlakeParser.SelectStatementContext ctx, final Table table,
                               final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final List<String> orderColumns = new ArrayList<>();
        final List<SourcePosition> keyOrigins = new ArrayList<>();
        for (final FrostlakeParser.OrderItemContext item : ctx.orderByClause().orderItem()) {
            String colName = resolveOrderOrdinal(ParseTreeText.getOriginalText(item.expression()), ctx,
                table, aliasToTable);
            colName = resolveOrderAlias(colName, ctx, table, aliasToTable);
            orderColumns.add(colName);
            keyOrigins.add(colName.equals(ParseTreeText.getOriginalText(item.expression()))
                ? new SourcePosition(item.expression().getStart().getLine(),
                    item.expression().getStart().getCharPositionInLine())
                : null);
        }
        rejectFileSortKeys(orderColumns, keyOrigins, table, aliasToTable, allTables);
    }

    /**
     * The ORDER BY items that do NOT match any SELECT column (by position, alias, or expression text). For a
     * window-function query these must be computed from the FROM columns before projection drops them — the
     * caller precomputes their values during the window projection (see the resolver in QueryExecutor).
     */
    List<FrostlakeParser.OrderItemContext> unmatchedOrderItems(final FrostlakeParser.SelectStatementContext ctx) {
        final List<FrostlakeParser.OrderItemContext> unmatched = new ArrayList<>();
        if (ctx.orderByClause() == null) {
            return unmatched;
        }
        final FrostlakeParser.SelectOperandContext firstOp = ctx.selectOperand(0);
        final FrostlakeParser.SelectClauseContext firstClause = firstOp.selectClause() != null
            ? firstOp.selectClause()
            : firstOp.selectStatement().selectOperand(0).selectClause();
        final List<FrostlakeParser.SelectItemContext> selectItems = firstClause.selectList().selectItem();
        for (final FrostlakeParser.OrderItemContext item : ctx.orderByClause().orderItem()) {
            if (matchOrderItem(item.expression().getText(), selectItems, ctx.selectOperand().size() > 1) == -1) {
                unmatched.add(item);
            }
        }
        return unmatched;
    }
}
