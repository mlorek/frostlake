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

import dev.frostlake.executor.expressions.AstPrinterVisitor;
import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlSyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

/**
 * An ASOF join's conditions, compiled where the join is reached. The MATCH_CONDITION and the ON are compiled like any
 * join condition, in the scope of every relation joined so far and the one being joined — the ON first — so a name
 * nothing resolves is an invalid identifier at its place, over empty inputs too, ranked with the other join
 * conditions' names (see {@link JoinConditionCompilation}). A USING column either side lacks is refused as a USING
 * join's is. The ON must be a predicate, as any join condition must; the MATCH_CONDITION is judged by its shape
 * instead, so {@code MATCH_CONDITION (f.a)} is refused for its operator, not for its type.
 *
 * <p>The MATCH_CONDITION's own shape is judged once its names resolve, and that refusal waits for every clause's
 * names, the ORDER BY position, an unknown function and the clauses' types, ahead of the grouped select list
 * (live-verified): the condition must be one of {@code >=, >, <=, <}, and each side may name columns of its own side
 * only, at least one of them — a column of a query around the join is neither side's.
 *
 * <p>A MATCH_CONDITION belongs to an ASOF join alone: after any other join it is a syntax error at the keyword.
 */
final class AsofJoinCompilation {

    /** The sentence for a MATCH_CONDITION that is not one of the four comparisons. */
    static final String OPERATORS = "MATCH_CONDITION clause is invalid: Only comparison operators '>=', '>', '<=' "
        + "and '<' are allowed. Keywords such as AND and OR are not allowed.";

    /** The sentence for a MATCH_CONDITION side naming anything but its own side's columns. */
    static final String SIDES = "MATCH_CONDITION clause is invalid: The left side allows only column references "
        + "from the left side table, and the right side allows only column references from the right side table.";

    private AsofJoinCompilation() {
    }

    /**
     * Refuse a MATCH_CONDITION written after a join that is not an ASOF join, as the syntax error it is: {@code JOIN r
     * MATCH_CONDITION (…)} is "unexpected 'MATCH_CONDITION'" at the keyword, ahead of anything the statement's names
     * would raise (live-verified).
     *
     * @param joins the joins of one FROM clause, in written order
     */
    static void rejectMatchConditionWithoutAsof(final List<FrostlakeParser.JoinClauseContext> joins) {
        for (final FrostlakeParser.JoinClauseContext join : joins) {
            if (join.ASOF() != null || join.asofMatchCondition() == null) {
                continue;
            }
            final Token keyword = join.asofMatchCondition().MATCH_CONDITION().getSymbol();
            // A nested query's keyword is placed in the statement it stands in.
            final SourcePosition at = ExpressionSource.place(keyword.getLine(), keyword.getCharPositionInLine());
            final int[] shown = LeadingCommentOffset.rebase(at.getLine(), at.getCharPositionInLine());
            final String line = "syntax error line " + shown[0] + " at position " + shown[1] + " unexpected '"
                + keyword.getText() + "'.";
            final CharStream text = keyword.getInputStream();
            throw new SqlSyntaxException(SqlCompilationError.of(line), Collections.singletonList(line),
                text.getText(Interval.of(0, text.size() - 1)));
        }
    }

    /**
     * Compile the join's ON, then its MATCH_CONDITION; a refusal is held by {@code onCompile} for its rank.
     *
     * @param executor     the executor planning the join
     * @param joinCtx      the ASOF join
     * @param leftTable    the relation joined so far
     * @param aliasToTable every relation in scope, the right one included
     * @param allTables    every relation in scope, the right one last
     * @param onCompile    the block's join-condition compilation, or null when the block is not compiled
     * @return true when both compile, so the join may be planned
     */
    static boolean conditionsCompile(final QueryExecutor executor, final FrostlakeParser.JoinClauseContext joinCtx,
                                     final Table leftTable, final Map<String, Table> aliasToTable,
                                     final List<Table> allTables, final JoinConditionCompilation onCompile) {
        if (onCompile == null) {
            return true;
        }
        boolean compiles = true;
        if (joinCtx.ON() != null && joinCtx.booleanExpr() != null) {
            compiles = onCompile.compiles(joinCtx.booleanExpr(), leftTable, aliasToTable, allTables);
        }
        if (joinCtx.asofMatchCondition() != null) {
            // Judged by its operator rather than its type: see shapeRefusal.
            compiles = onCompile.compiles(joinCtx.asofMatchCondition().booleanExpr(), leftTable, aliasToTable,
                allTables, false) && compiles;
        }
        return compiles;
    }

    /**
     * Refuse a USING column either side lacks by its exact name, in USING's own words.
     *
     * @param joinCtx    the ASOF join
     * @param leftTable  the relation joined so far
     * @param rightTable the relation being joined
     */
    static void rejectMissingUsingColumns(final FrostlakeParser.JoinClauseContext joinCtx, final Table leftTable,
                                          final Table rightTable) {
        if (joinCtx.USING() == null) {
            return;
        }
        for (final FrostlakeParser.QualifiedNameContext name : joinCtx.usingColumnList().qualifiedName()) {
            final String[] parts = ParseTreeText.qualifiedNameParts(name);
            final String column = parts[parts.length - 1];
            if (!leftTable.hasColumnExactly(column) || !rightTable.hasColumnExactly(column)) {
                throw new RuntimeException(SqlCompilationError.of("Invalid identifier " + column));
            }
        }
    }

    /**
     * The MATCH_CONDITION's shape refusal, or null when its shape is sound: a condition other than the four
     * comparisons, or a side naming a column of the other side, or none of its own.
     *
     * @param executor    the executor planning the join
     * @param condition   the MATCH_CONDITION as parsed
     * @param leftTable   the relation joined so far
     * @param leftAliases the relations joined so far, by name
     * @param leftTables  the relations joined so far
     * @param rightAlias  the name the joined relation answers to
     * @param rightTable  the joined relation
     * @return the refusal, or null
     */
    static RuntimeException shapeRefusal(final QueryExecutor executor, final Expression condition,
                                         final Table leftTable, final Map<String, Table> leftAliases,
                                         final List<Table> leftTables, final String rightAlias,
                                         final Table rightTable) {
        if (!(condition instanceof BinaryOperationExpression)) {
            return new RuntimeException(SqlCompilationError.of(OPERATORS));
        }
        final BinaryOperationExpression comparison = (BinaryOperationExpression) condition;
        final BinaryOperator op = comparison.getOperator();
        if (op != BinaryOperator.GREATER_THAN && op != BinaryOperator.GREATER_THAN_OR_EQUAL
                && op != BinaryOperator.LESS_THAN && op != BinaryOperator.LESS_THAN_OR_EQUAL) {
            return new RuntimeException(SqlCompilationError.of(OPERATORS));
        }
        final ExpressionEvaluator leftSide = new ExpressionEvaluator(leftTable, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        leftSide.setMultiTableContext(leftAliases, leftTables);
        final Map<String, Table> rightOnly = new LinkedHashMap<>();
        rightOnly.put(rightAlias, rightTable);
        final ExpressionEvaluator rightSide = new ExpressionEvaluator(rightTable, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        rightSide.setMultiTableContext(rightOnly, Collections.singletonList(rightTable));
        if (!namesOnlyItsOwnSide(comparison.getLeft(), leftSide)
                || !namesOnlyItsOwnSide(comparison.getRight(), rightSide)) {
            return new RuntimeException(SqlCompilationError.of(SIDES));
        }
        return null;
    }

    /**
     * Whether an operand names at least one column, and each column it names resolves in its side's scope. The side
     * is judged as a closed scope — compiled with no enclosing names — so a column of a query around the join (a
     * correlation, or a name an outer row would supply while the join is planned for it) belongs to neither side.
     */
    private static boolean namesOnlyItsOwnSide(final Expression operand, final ExpressionEvaluator side) {
        final int[] columns = new int[1];
        operand.accept(new AstPrinterVisitor() {
            @Override
            public String visitColumnReference(final ColumnReferenceExpression expr) {
                columns[0]++;
                return super.visitColumnReference(expr);
            }

            @Override
            public String visitSubquery(final SubqueryExpression expr) {
                return "";
            }
        });
        if (columns[0] == 0) {
            return false;
        }
        final Map<String, Object> enclosing = SubqueryCompilation.begin(Collections.<String, Object>emptyMap());
        try {
            side.validateColumnScope(operand);
            return true;
        } catch (final RuntimeException otherSide) {
            return false;
        } finally {
            SubqueryCompilation.end(enclosing);
        }
    }
}
