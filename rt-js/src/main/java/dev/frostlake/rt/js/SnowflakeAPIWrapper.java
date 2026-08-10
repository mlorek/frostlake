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
import dev.frostlake.storage.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SnowflakeAPIWrapper {
    private final DatabaseEngine engine;

    public SnowflakeAPIWrapper(final DatabaseEngine engine) {
        this.engine = engine;
    }

    public JavaScriptResultSet execute(final Object options) {
        String sqlText = null;

        if (options instanceof Map) {
            final Map<?, ?> optMap = (Map<?, ?>) options;
            Object sqlObj = optMap.get("sqlText");
            if (sqlObj == null) {
                sqlObj = optMap.get("sql");
            }
            if (sqlObj != null) {
                sqlText = String.valueOf(sqlObj);
            }
        }

        if (sqlText == null) {
            throw new RuntimeException("sqlText is required in snowflake.execute() options");
        }

        try {
            final ResultSet rs = engine.executeQuery(sqlText);
            return new JavaScriptResultSet(rs);
        } catch (final Exception e) {
            throw new RuntimeException("Error executing SQL: " + e.getMessage(), e);
        }
    }

    /**
     * The standard Snowflake stored-procedure API: {@code snowflake.createStatement({sqlText, binds})}
     * returns a Statement whose {@code execute()} runs the SQL. Real Snowflake JS procedures use this
     * two-step form (createStatement → execute), not the one-step {@code snowflake.execute(...)} above.
     */
    public SnowflakeStatement createStatement(final Object options) {
        String sqlText = null;
        final List<Object> binds = new ArrayList<>();

        if (options instanceof Map) {
            final Map<?, ?> optMap = (Map<?, ?>) options;
            Object sqlObj = optMap.get("sqlText");
            if (sqlObj == null) {
                sqlObj = optMap.get("sql");
            }
            if (sqlObj != null) {
                sqlText = String.valueOf(sqlObj);
            }
            final Object bindsObj = optMap.get("binds");
            if (bindsObj instanceof List) {
                binds.addAll((List<?>) bindsObj);
            }
        }

        if (sqlText == null) {
            throw new RuntimeException("sqlText is required in snowflake.createStatement() options");
        }

        return new SnowflakeStatement(engine, sqlText, binds);
    }
}
