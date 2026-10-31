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

import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import java.util.List;
import java.util.Locale;

/**
 * A plain star over a CALL's scan, {@code SELECT * FROM TABLE(RESULT_SCAN(…))} and nothing more, hands out the answer
 * the CALL handed out where a cell of the scan holds one its RETURNS type cannot (see
 * {@link DeferredFault#heldAnswer}): a text wider than {@code RETURNS VARCHAR(2)}, what is no object for
 * {@code RETURNS OBJECT}, the scan named by an alias or not. Every other statement over that scan is refused as it
 * reads the cell — a named column,
 * {@code $1}, an alias's star, a star beside another item, a WHERE, an ORDER BY, a LIMIT, a DISTINCT, the star of a
 * subquery over the scan (live-verified). Over the scan of a Snowflake Scripting answer, a CALL's or a block's, the
 * plain star hands the answer out laid out across lines as the CALL did, and is itself such an answer to a scan of
 * it, where every other statement over the scan reads its values compact (live-verified).
 */
final class ScanStar {

    /** Whether the scan the running statement read holds a Snowflake Scripting answer. */
    private static final ThreadLocal<Boolean> SCANNED_ANSWER = new ThreadLocal<>();

    private ScanStar() {
    }

    /** Forget what an earlier statement's scan read. */
    static void clearScan() {
        SCANNED_ANSWER.remove();
    }

    /**
     * Note the result the running statement's scan reads.
     *
     * @param scanned the scanned result, or null
     */
    static void noteScan(final ResultSet scanned) {
        SCANNED_ANSWER.set(Boolean.valueOf(scanned != null && scanned.isScriptingAnswer()));
    }

    /**
     * Whether a statement is a plain star over a scan: one SELECT, its list a lone unqualified star with no
     * modifier, its FROM one {@code TABLE(RESULT_SCAN(…))}, with an alias or none, and no other clause.
     *
     * @param statement the statement as parsed
     * @return whether it is
     */
    static boolean isPlainStarOverScan(final FrostlakeParser.StatementContext statement) {
        final FrostlakeParser.QueryStatementContext query = statement == null ? null : statement.queryStatement();
        final FrostlakeParser.SelectStatementContext select = query == null ? null : query.selectStatement();
        if (select == null || query.forUpdateClause() != null || select.withClause() != null
                || select.selectOperand().size() != 1 || select.orderByClause() != null || select.limitClause() != null
                || select.fetchClause() != null) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext clause = select.selectOperand(0).selectClause();
        if (clause == null || clause.DISTINCT() != null || clause.ALL() != null || clause.topClause() != null
                || clause.intoClause() != null || clause.whereClause() != null || clause.connectByClause() != null
                || clause.groupByClause() != null || clause.havingClause() != null || clause.qualifyClause() != null
                || clause.tableExpression() == null || !isLoneStar(clause.selectList())) {
            return false;
        }
        final FrostlakeParser.TableExpressionContext from = clause.tableExpression();
        if (from.tableReference().size() != 1 || !from.joinClause().isEmpty() || !from.COMMA().isEmpty()) {
            return false;
        }
        final FrostlakeParser.TableReferenceContext reference = from.tableReference(0);
        if (reference.LATERAL() != null || reference.pivotClause() != null || reference.unpivotClause() != null
                || reference.matchRecognizeClause() != null || reference.sampleClause() != null) {
            return false;
        }
        final FrostlakeParser.TableSourceContext source = reference.tableSource();
        return source != null && source.TABLE() != null && source.expression() != null
            && isResultScanCall(source.expression());
    }

    /** Whether a select list is one unqualified star with no modifier and no trailing comma. */
    private static boolean isLoneStar(final FrostlakeParser.SelectListContext list) {
        return list != null && list.selectItem().size() == 1 && list.COMMA().isEmpty()
            && list.selectItem(0) instanceof FrostlakeParser.StarItemContext
            && ((FrostlakeParser.StarItemContext) list.selectItem(0)).starModifier().isEmpty();
    }

    /** Whether a table function's operand is an unqualified call of RESULT_SCAN. */
    private static boolean isResultScanCall(final FrostlakeParser.ExpressionContext operand) {
        final Expression call;
        try {
            call = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(operand));
        } catch (final RuntimeException unreadable) {
            return false;
        }
        if (!(call instanceof FunctionCallExpression)) {
            return false;
        }
        final FunctionCallExpression scan = (FunctionCallExpression) call;
        return (scan.getNameParts() == null || scan.getNameParts().size() == 1) && scan.getFunctionName() != null
            && "RESULT_SCAN".equals(scan.getFunctionName().toUpperCase(Locale.ROOT));
    }

    /**
     * Put the answer back in each cell of the result that holds one a CALL handed out.
     *
     * @param result the plain star's result
     */
    static void handOut(final ResultSet result) {
        if (result != null && Boolean.TRUE.equals(SCANNED_ANSWER.get())) {
            result.markScriptingAnswer();
        }
        final List<Row> rows = result == null ? null : result.getRows();
        if (rows == null) {
            return;
        }
        for (final Row row : rows) {
            if (row == null) {
                continue;
            }
            final List<Object> values = row.getValues();
            for (int column = 0; column < values.size(); column++) {
                final Object value = values.get(column);
                if (value instanceof DeferredFault && ((DeferredFault) value).holdsAnswer()) {
                    row.setValue(column, ((DeferredFault) value).getAnswered());
                }
            }
        }
    }
}
