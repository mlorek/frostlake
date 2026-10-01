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

import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import java.util.List;

/**
 * The update count Snowflake's JDBC driver reports for one statement — what it hands back from
 * {@code getUpdateCount()} after {@code execute()} answered {@code false} — or none, when the statement
 * answers rows the client reads through a result set.
 *
 * <p>Live-verified with snowflake-jdbc, kind by kind:
 * <ul>
 *   <li>a query, SHOW, DESCRIBE, EXPLAIN, CALL, LIST, REMOVE, GET, PUT and an anonymous block answer rows;</li>
 *   <li>INSERT (a single target, several, OVERWRITE), UPDATE, DELETE and MERGE report the sum of every count
 *       their grid carries — an UPDATE's multi-joined rows and a MERGE's inserted, updated and deleted rows
 *       all add up;</li>
 *   <li>COPY INTO a table reports the rows it loaded, summed over its files, and an unload reports 0;</li>
 *   <li>every other statement reports 0: DDL, USE, SET and UNSET, ALTER SESSION, BEGIN / COMMIT / ROLLBACK,
 *       GRANT and REVOKE, TRUNCATE, a CTAS or a CLONE;</li>
 *   <li>EXECUTE IMMEDIATE reports what the statement its text ran reports.</li>
 * </ul>
 */
public final class JdbcUpdateCounts {

    private JdbcUpdateCounts() {
    }

    /**
     * The update count a JDBC client reports for a statement.
     *
     * @param statement the statement the result answers — for an EXECUTE IMMEDIATE, the statement its
     *                  text ran — or null when it is not known
     * @param result    the statement's result
     * @return the update count, or null when the statement answers rows
     */
    public static Long of(final FrostlakeParser.StatementContext statement, final ResultSet result) {
        if (statement == null || result == null || answersRows(statement)) {
            return null;
        }
        final FrostlakeParser.DmlStatementContext dml = statement.dmlStatement();
        if (dml == null) {
            return Long.valueOf(0L);
        }
        return Long.valueOf(dml.copyIntoStatement() != null ? columnSum(result, "rows_loaded") : cellSum(result));
    }

    /** Whether a statement answers rows rather than an update count. */
    private static boolean answersRows(final FrostlakeParser.StatementContext statement) {
        if (statement.queryStatement() != null || statement.explainStatement() != null
                || statement.listStatement() != null || statement.getStatement() != null
                || statement.putStatement() != null || statement.removeStatement() != null
                || statement.describeStatement() != null || statement.showStatement() != null
                || statement.showClassStatement() != null || statement.securityObjectListing() != null
                || AnonymousBlockResult.isBlock(statement)) {
            return true;
        }
        if (statement.proceduralStatement() != null && statement.proceduralStatement().callStatement() != null) {
            return true;
        }
        // The listings and descriptions other statement families parse (SHOW SERVICES, DESCRIBE TASK, …)
        // answer rows as well: the leading word decides.
        final int leading = statement.getStart().getType();
        return leading == FrostlakeParser.SHOW || leading == FrostlakeParser.DESCRIBE
            || leading == FrostlakeParser.DESC;
    }

    /** Every numeric cell of every row, added up — how the driver counts a DML statement's grid. */
    private static long cellSum(final ResultSet result) {
        long total = 0;
        for (final Row row : result.getRows()) {
            for (final Object cell : row.getValues()) {
                total += countOf(cell);
            }
        }
        return total;
    }

    /** One named column's cells added up over every row, or 0 when the grid has no such column. */
    private static long columnSum(final ResultSet result, final String column) {
        int index = -1;
        for (int i = 0; i < result.getColumns().size(); i++) {
            if (column.equalsIgnoreCase(result.getColumns().get(i).getName())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return 0;
        }
        long total = 0;
        for (final Row row : result.getRows()) {
            final List<Object> values = row.getValues();
            if (index < values.size()) {
                total += countOf(values.get(index));
            }
        }
        return total;
    }

    private static long countOf(final Object cell) {
        if (cell instanceof Number) {
            return ((Number) cell).longValue();
        }
        return 0;
    }
}
