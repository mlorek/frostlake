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

package dev.frostlake.rt.js;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.jdbc.JdbcMarshaling;
import dev.frostlake.storage.ResultSet;
import org.graalvm.polyglot.HostAccess;

import java.util.List;

/**
 * The statement behind the one {@code snowflake.createStatement} answers. The handler holds the script-side
 * {@code Statement} (see {@code JavaScriptHandler}), which calls only the members exported here. Its
 * {@code execute()} substitutes any positional {@code ?} binds and runs the SQL; the column metadata is the last
 * execution's, and asking for it before there is one is refused: {@code Statement is not executed yet.}
 * (live-verified).
 */
public class SnowflakeStatement {

    private static final String NOT_EXECUTED = "Statement is not executed yet.";

    private final DatabaseEngine engine;
    private final String sqlText;
    private final List<Object> binds;
    private final JavaScriptApiFailures failures;
    private ResultSet lastResult;
    private String lastQueryId;
    /** {@code initialized} until the statement runs, then {@code success} or {@code error} (live-verified). */
    private String status = "initialized";

    SnowflakeStatement(final DatabaseEngine engine, final String sqlText, final List<Object> binds,
                       final JavaScriptApiFailures failures) {
        this.engine = engine;
        this.sqlText = sqlText;
        this.binds = binds;
        this.failures = failures;
    }

    /**
     * @return the rows, a {@link JavaScriptResultSet}, or a led failure
     */
    @HostAccess.Export
    public Object execute() {
        final String sql = (binds == null || binds.isEmpty())
            ? sqlText : JdbcMarshaling.substitutePlaceholders(sqlText, binds);
        try {
            final ResultSet rs = engine.executeQuery(sql);
            this.lastResult = rs;
            this.lastQueryId = engine.getExecutor().getResultCache().getLastQueryId();
            this.status = "success";
            return new JavaScriptResultSet(rs, failures, lastQueryId);
        } catch (final RuntimeException failed) {
            this.status = "error";
            return failures.statement(failed);
        }
    }

    /**
     * What a column of the last execution's answer is asked for (see {@link JavaScriptColumns#about}).
     *
     * @param column  the column, counted from 1, or null for anything else
     * @param spelled the column as the handler's language prints it, for the refusal
     * @param what    the member asked
     * @return the answer, or a led failure
     */
    @HostAccess.Export
    public Object column(final Object column, final String spelled, final String what) {
        if (lastResult == null) {
            return failures.misuse(NOT_EXECUTED);
        }
        final int count = lastResult.getColumns().size();
        final double asked = column instanceof Number ? ((Number) column).doubleValue() : 0;
        final int index = (int) asked;
        if (index != asked || index < 1 || index > count) {
            return failures.misuse("Given column name/index does not exist: " + spelled);
        }
        return JavaScriptColumns.about(lastResult.getColumns().get(index - 1), what);
    }

    /** {@code initialized} until the statement runs, then {@code success}, or {@code error} once it failed. */
    @HostAccess.Export
    public String status() {
        return status;
    }

    /**
     * The id of the last execution's query.
     *
     * @return the id, or a led failure before the statement ran
     */
    @HostAccess.Export
    public String queryId() {
        return lastResult == null ? failures.misuse(NOT_EXECUTED) : lastQueryId;
    }

    /**
     * What the last execution wrote: {@code affected} (every row it inserted, updated and deleted), {@code inserted},
     * {@code updated}, {@code deleted}, {@code duplicate} (rows a join matched more than once) or {@code dml}
     * (whether it wrote rows at all).
     *
     * @param what the count asked
     * @return the count, the flag, or a led failure before the statement ran
     */
    @HostAccess.Export
    public Object written(final String what) {
        if (lastResult == null) {
            return failures.misuse(NOT_EXECUTED);
        }
        switch (what) {
            case "dml":
                return Boolean.valueOf(JavaScriptColumns.isDml(lastResult));
            case "inserted":
                return Long.valueOf(JavaScriptColumns.count(lastResult, JavaScriptColumns.INSERTED));
            case "updated":
                return Long.valueOf(JavaScriptColumns.count(lastResult, JavaScriptColumns.UPDATED));
            case "deleted":
                return Long.valueOf(JavaScriptColumns.count(lastResult, JavaScriptColumns.DELETED));
            case "duplicate":
                return Long.valueOf(JavaScriptColumns.count(lastResult, JavaScriptColumns.MULTI_JOINED));
            default:
                return Long.valueOf(JavaScriptColumns.count(lastResult, JavaScriptColumns.INSERTED)
                    + JavaScriptColumns.count(lastResult, JavaScriptColumns.UPDATED)
                    + JavaScriptColumns.count(lastResult, JavaScriptColumns.DELETED));
        }
    }

    /**
     * Whether the statement ran: the members that tell of its execution refuse before it did.
     *
     * @return null once it ran, else a led failure
     */
    @HostAccess.Export
    public String ran() {
        return lastResult == null ? failures.misuse(NOT_EXECUTED) : null;
    }

    @HostAccess.Export
    public Object getColumnCount() {
        return lastResult == null ? (Object) failures.misuse(NOT_EXECUTED)
            : (Object) Integer.valueOf(lastResult.getColumnCount());
    }

    @HostAccess.Export
    public Object getColumnName(final int columnIndex) {
        if (lastResult == null) {
            return failures.misuse(NOT_EXECUTED);
        }
        return columnIndex >= 1 && columnIndex <= lastResult.getColumns().size()
            ? lastResult.getColumns().get(columnIndex - 1).getName()
            : failures.misuse("Given column name/index does not exist: " + columnIndex);
    }

    @HostAccess.Export
    public Object getRowCount() {
        return lastResult == null ? (Object) failures.misuse(NOT_EXECUTED)
            : (Object) Integer.valueOf(lastResult.getRowCount());
    }

    @HostAccess.Export
    public String getSqlText() {
        return sqlText;
    }
}
