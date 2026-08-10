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
import java.util.List;

/**
 * A prepared statement created by {@code snowflake.createStatement}. Its {@code execute()} substitutes any
 * positional {@code ?} binds and runs the SQL; column metadata (count/name) reflects the last execution,
 * as the Snowflake API exposes it on the statement.
 */
public class SnowflakeStatement {
    private final DatabaseEngine engine;
    private final String sqlText;
    private final List<Object> binds;
    private ResultSet lastResult;

    SnowflakeStatement(final DatabaseEngine engine, final String sqlText, final List<Object> binds) {
        this.engine = engine;
        this.sqlText = sqlText;
        this.binds = binds;
    }

    public JavaScriptResultSet execute() {
        final String sql = (binds == null || binds.isEmpty())
            ? sqlText : JdbcMarshaling.substitutePlaceholders(sqlText, binds);
        try {
            final ResultSet rs = engine.executeQuery(sql);
            this.lastResult = rs;
            return new JavaScriptResultSet(rs);
        } catch (final Exception e) {
            throw new RuntimeException("Error executing SQL: " + e.getMessage(), e);
        }
    }

    public int getColumnCount() {
        return lastResult == null ? 0 : lastResult.getColumnCount();
    }

    public String getColumnName(final int columnIndex) {
        return lastResult == null ? null : lastResult.getColumns().get(columnIndex - 1).getName();
    }

    public int getRowCount() {
        return lastResult == null ? 0 : lastResult.getRowCount();
    }
}
