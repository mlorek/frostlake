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
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.jdbc.JdbcMarshaling;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.TypeCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JavaScriptProcedureExecutor {

    static {
        // GraalVM's polyglot and Truffle artifacts can resolve to mismatched patch versions on a downstream
        // classpath (a host application that also depends on GraalVM), which trips the strict engine version
        // check and fails all JavaScript execution. Patch-level differences are compatible, so disable the
        // check before the polyglot engine first initializes (this static block runs before the
        // ScriptEngineManager below, and well before graal.js is requested).
        if (System.getProperty("polyglotimpl.DisableVersionChecks") == null) {
            System.setProperty("polyglotimpl.DisableVersionChecks", "true");
        }
    }

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
                reparseSemiStructured(scriptEngine, parameters.get(i), paramName, value);
            }

            SnowflakeAPIWrapper apiWrapper = new SnowflakeAPIWrapper(engine);
            scriptEngine.put("snowflake", apiWrapper);

            String body = procedure.getBody().trim();
            // A JS object/array return value must come back as JSON text so it round-trips as a Frostlake
            // OBJECT/ARRAY (a raw JS Value stringifies to "{a: 1}", which isn't valid JSON and breaks
            // downstream variant-path access). Scalars (string/number/boolean) and null pass through.
            String wrappedCode = "(function() {\n"
                + "  var __result = (function() {\n" + body + "\n  })();\n"
                + "  return (__result !== null && __result !== undefined && typeof __result === 'object')\n"
                + "      ? JSON.stringify(__result) : __result;\n"
                + "})()";

            Object result = scriptEngine.eval(wrappedCode);

            logger.debug("JavaScript procedure {} executed successfully", procedure.getName());
            return result;
        } catch (final ScriptException e) {
            logger.error("Error executing JavaScript procedure: {}", procedure.getName(), e);
            throw new RuntimeException("Error executing JavaScript procedure " + procedure.getName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Snowflake exposes OBJECT / VARIANT / ARRAY arguments to JavaScript as native JS values, not JSON text.
     * Frostlake carries them as JSON strings, so when a semi-structured parameter was bound as a string,
     * reparse it in place into a JS object/array — so a body can do {@code so.prop = x}, {@code arr.push(…)},
     * read {@code so.field}, etc. Non-JSON strings (or non-string values) are left untouched.
     */
    static void reparseSemiStructured(final ScriptEngine engine, final Parameter param, final String name, final Object value) {
        final DataType dt = param.getDataType();
        if (dt == null || dt.getCategory() != TypeCategory.SEMI_STRUCTURED) {
            return;
        }
        if (value instanceof String) {
            try {
                engine.eval(name + " = JSON.parse(" + name + ");");
            } catch (final ScriptException nonJson) {
                logger.debug("semi-structured JS parameter {} was not valid JSON; left as string", name);
            }
        } else if (value != null) {
            // A host container (List/Map/JsonNode/...) has no JS Array/Object protocol (no forEach,
            // Array.isArray false), so a body like `SRC.forEach(...)` threw TypeError when a variant path
            // handed the argument over as a Java value instead of JSON text. Round-trip anything non-null
            // through JSON for native JS values; numbers and booleans survive the trip unchanged.
            final String holder = "__frostlake_json_" + name;
            try {
                engine.put(holder, ArrayFunctionHelper.MAPPER.writeValueAsString(value));
                engine.eval(name + " = JSON.parse(" + holder + "); " + holder + " = undefined;");
            } catch (final ScriptException | RuntimeException e) {
                logger.debug("semi-structured JS parameter {} of type {} could not be converted to JSON; left as host object",
                    name, value.getClass().getName());
            }
        }
        // Snowflake's implicit conversion for an ARRAY-declared parameter follows TO_ARRAY: a non-array
        // value arrives wrapped as a one-element array (vendor code passes an OBJECT to `SRC ARRAY` and
        // indexes the result with ::array[0]). NULL stays NULL.
        if (value != null && dt instanceof ArrayType) {
            try {
                engine.eval("if (" + name + " !== null && " + name + " !== undefined && !Array.isArray(" + name + ")) { "
                    + name + " = [" + name + "]; }");
            } catch (final ScriptException ignored) {
                logger.debug("semi-structured JS parameter {} could not be array-wrapped", name);
            }
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

    /**
     * A prepared statement created by {@code snowflake.createStatement}. Its {@code execute()} substitutes any
     * positional {@code ?} binds and runs the SQL; column metadata (count/name) reflects the last execution,
     * as the Snowflake API exposes it on the statement.
     */
    public static class SnowflakeStatement {
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
