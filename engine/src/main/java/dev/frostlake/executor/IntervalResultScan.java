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

import dev.frostlake.executor.expressions.IntervalCasts;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.DataType;

import org.antlr.v4.runtime.ParserRuleContext;

/**
 * RESULT_SCAN over a result holding an interval column. The scan reads each cached column back through a
 * conversion to its type, and the interval conversions take no cached value, so any query over such a
 * scan is refused while it compiles, naming the first interval column whatever the query reads —
 * {@code SELECT n}, {@code COUNT(*)}, a WHERE, an ORDER BY, a LIMIT, {@code SELECT r.*}, a CTAS and a UNION
 * alike: "invalid type [CAST(STRIP_NULL_VALUE(GET("RESULT_SCAN_&lt;id&gt;_RESULT_SCAN".$1, 'IV')) AS
 * INTERVAL DAY(9) TO SECOND(9))] for parameter 'TO_INTERVAL_DAY_TIME'". The relation is named by its alias
 * when it has one, and a column that cannot hold NULL is read without STRIP_NULL_VALUE. Only the bare
 * {@code SELECT * FROM TABLE(RESULT_SCAN(…))}, with or without an alias, hands the result back as it is
 * (live-verified).
 */
final class IntervalResultScan {

    private IntervalResultScan() {
    }

    /**
     * Refuses a scan of a result with an interval column unless the query is the bare star over it.
     *
     * @param cached  the scanned result, or null when there is none (its own refusal follows)
     * @param queryId the statement the scan reads
     * @param call    the RESULT_SCAN call inside TABLE(...)
     * @param alias   the relation's alias by its canonical name, or null
     */
    static void refuse(final ResultSet cached, final String queryId, final ParserRuleContext call,
                       final String alias) {
        if (cached == null) {
            return;
        }
        ResultSetColumn interval = null;
        for (final ResultSetColumn column : cached.getColumns()) {
            if (IntervalCasts.isIntervalType(declared(column))) {
                interval = column;
                break;
            }
        }
        if (interval == null || bareStar(call)) {
            return;
        }
        final boolean nullable = interval.isNullable() || !interval.isNullabilityKnown();
        final String read = "GET(\"RESULT_SCAN_" + queryId + "_" + (alias == null ? "RESULT_SCAN" : alias) + "\".$1, '"
            + interval.getName() + "')";
        final DataType type = declared(interval);
        throw new RuntimeException(SqlCompilationError.of("invalid type [CAST("
            + (nullable ? "STRIP_NULL_VALUE(" + read + ")" : read) + " AS " + type.getName()
            + ")] for parameter '" + IntervalCasts.conversionName(type) + "'"));
    }

    private static DataType declared(final ResultSetColumn column) {
        return column.getStaticType() != null ? column.getStaticType() : column.getDataType();
    }

    /** Whether the scan is the whole statement's only source under a bare {@code SELECT *} and nothing else. */
    private static boolean bareStar(final ParserRuleContext call) {
        if (!(call.getParent() instanceof FrostlakeParser.TableSourceContext)
                || !(call.getParent().getParent() instanceof FrostlakeParser.TableReferenceContext)) {
            return false;
        }
        final FrostlakeParser.TableReferenceContext reference =
            (FrostlakeParser.TableReferenceContext) call.getParent().getParent();
        if (reference.LATERAL() != null || reference.pivotClause() != null || reference.unpivotClause() != null
                || reference.sampleClause() != null
                || !(reference.getParent() instanceof FrostlakeParser.TableExpressionContext)) {
            return false;
        }
        final FrostlakeParser.TableExpressionContext from = (FrostlakeParser.TableExpressionContext) reference.getParent();
        if (from.tableReference().size() != 1 || !from.joinClause().isEmpty()
                || !(from.getParent() instanceof FrostlakeParser.SelectClauseContext)) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext select = (FrostlakeParser.SelectClauseContext) from.getParent();
        if (select.DISTINCT() != null || select.topClause() != null || select.whereClause() != null
                || select.connectByClause() != null || select.groupByClause() != null || select.havingClause() != null
                || select.qualifyClause() != null || select.selectList().selectItem().size() != 1
                || !(select.selectList().selectItem(0) instanceof FrostlakeParser.StarItemContext)
                || !((FrostlakeParser.StarItemContext) select.selectList().selectItem(0)).starModifier().isEmpty()
                || !(select.getParent() instanceof FrostlakeParser.SelectOperandContext)
                || !(select.getParent().getParent() instanceof FrostlakeParser.SelectStatementContext)) {
            return false;
        }
        final FrostlakeParser.SelectStatementContext statement =
            (FrostlakeParser.SelectStatementContext) select.getParent().getParent();
        return statement.withClause() == null && statement.selectOperand().size() == 1
            && statement.orderByClause() == null && statement.limitClause() == null && statement.fetchClause() == null
            && statement.getParent() instanceof FrostlakeParser.QueryStatementContext;
    }
}
