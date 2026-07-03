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

package dev.frostlake.executor.udf;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.List;
import java.util.Map;

public class JavaScriptProcedureExecutor {

    private static final Logger logger = LoggerFactory.getLogger(JavaScriptProcedureExecutor.class);
    private static final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();

    public static Object executeJavaScriptProcedure(final Procedure procedure, final List<Object> arguments, final DatabaseEngine engine) {
        if (procedure.getUdfLanguage() != UdfLanguage.JAVASCRIPT) {
            throw new RuntimeException("Procedure is not a JavaScript procedure");
        }

        ScriptEngine scriptEngine = null;

        try {
            scriptEngine = scriptEngineManager.getEngineByName("graal.js");
            if (scriptEngine != null) {
                Bindings bindings = scriptEngine.getBindings(ScriptContext.ENGINE_SCOPE);
                bindings.put("polyglot.js.allowHostAccess", true);
                bindings.put("polyglot.js.allowHostClassLookup", true);
            }
        } catch (final Exception e) {
            logger.debug("GraalVM JavaScript not available: {}", e.getMessage());
        }

        if (scriptEngine == null) {
            scriptEngine = scriptEngineManager.getEngineByName("nashorn");
        }
        if (scriptEngine == null) {
            scriptEngine = scriptEngineManager.getEngineByName("javascript");
        }
        if (scriptEngine == null) {
            throw new RuntimeException("No JavaScript engine available. Please ensure GraalVM JavaScript or a compatible engine is installed.");
        }

        try {
            List<Parameter> parameters = procedure.getParameters();
            if (parameters.size() != arguments.size()) {
                throw new RuntimeException("Procedure " + procedure.getName() + " expects " +
                    parameters.size() + " arguments but got " + arguments.size());
            }

            for (int i = 0; i < parameters.size(); i++) {
                String paramName = parameters.get(i).getName();
                Object value = arguments.get(i);
                scriptEngine.put(paramName, value);
            }

            SnowflakeAPIWrapper apiWrapper = new SnowflakeAPIWrapper(engine);
            scriptEngine.put("snowflake", apiWrapper);

            String body = procedure.getBody().trim();
            String wrappedCode = "(function() {\n" + body + "\n})()";

            Object result = scriptEngine.eval(wrappedCode);

            logger.debug("JavaScript procedure {} executed successfully", procedure.getName());
            return result;
        } catch (final ScriptException e) {
            logger.error("Error executing JavaScript procedure: {}", procedure.getName(), e);
            throw new RuntimeException("Error executing JavaScript procedure " + procedure.getName() + ": " + e.getMessage(), e);
        }
    }

    public static class SnowflakeAPIWrapper {
        private final DatabaseEngine engine;

        public SnowflakeAPIWrapper(final DatabaseEngine engine) {
            this.engine = engine;
        }

        public JavaScriptResultSet execute(final Object options) {
            String sqlText = null;

            if (options instanceof Map) {
                Map<?, ?> optMap = (Map<?, ?>) options;
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
                ResultSet rs = engine.executeQuery(sqlText);
                return new JavaScriptResultSet(rs);
            } catch (final Exception e) {
                throw new RuntimeException("Error executing SQL: " + e.getMessage(), e);
            }
        }
    }

    public static class JavaScriptResultSet {
        private final ResultSet resultSet;
        private int currentRow = -1;

        public JavaScriptResultSet(final ResultSet resultSet) {
            this.resultSet = resultSet;
        }

        public boolean next() {
            currentRow++;
            return currentRow < resultSet.getRowCount();
        }

        public Object getColumnValue(final int columnIndex) {
            if (currentRow < 0 || currentRow >= resultSet.getRowCount()) {
                throw new RuntimeException("No current row. Call next() first.");
            }

            Row row = resultSet.getRows().get(currentRow);
            int index = columnIndex - 1;

            if (index < 0 || index >= row.size()) {
                throw new RuntimeException("Column index " + columnIndex + " is out of range");
            }

            return row.getValue(index);
        }

        public Object getColumnValue(final String columnName) {
            if (currentRow < 0 || currentRow >= resultSet.getRowCount()) {
                throw new RuntimeException("No current row. Call next() first.");
            }

            Row row = resultSet.getRows().get(currentRow);
            for (int i = 0; i < resultSet.getColumns().size(); i++) {
                if (resultSet.getColumns().get(i).getName().equalsIgnoreCase(columnName)) {
                    return row.getValue(i);
                }
            }

            throw new RuntimeException("Column " + columnName + " not found");
        }

        public int getRowCount() {
            return resultSet.getRowCount();
        }

        public int getColumnCount() {
            return resultSet.getColumnCount();
        }
    }
}
