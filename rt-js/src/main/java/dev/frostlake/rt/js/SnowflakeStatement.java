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
            return new JavaScriptResultSet(rs, failures);
        } catch (final RuntimeException failed) {
            return failures.statement(failed);
        }
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
            ? lastResult.getColumns().get(columnIndex - 1).getName() : null;
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
