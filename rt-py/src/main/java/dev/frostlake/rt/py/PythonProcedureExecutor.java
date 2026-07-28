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

package dev.frostlake.rt.py;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.ExecutionResult;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PythonProcedureExecutor {

    private static final Logger logger = LoggerFactory.getLogger(PythonProcedureExecutor.class);

    /**
     * The pure-Python {@code snowflake.snowpark} emulation, evaluated into each context before a
     * procedure body so the body's snowpark imports resolve locally. Idempotent (guarded on
     * {@code sys.modules}), and the per-thread {@link PythonRuntime} source cache makes re-evaluation
     * per call cheap.
     */
    private static final String SNOWPARK_SHIM = loadSnowparkShim();

    private static String loadSnowparkShim() {
        try (final InputStream in = PythonProcedureExecutor.class.getResourceAsStream("snowpark_shim.py")) {
            if (in == null) {
                throw new IllegalStateException("snowpark_shim.py resource not found");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to load snowpark_shim.py", e);
        }
    }

    public static Object executePythonProcedure(final Procedure procedure, final List<Object> arguments, final DatabaseEngine engine) {
        if (procedure.getUdfLanguage() != UdfLanguage.PYTHON) {
            throw new RuntimeException("Procedure is not a Python procedure");
        }

        List<Parameter> parameters = procedure.getParameters();
        if (parameters.size() != arguments.size()) {
            throw new RuntimeException("Procedure " + procedure.getName() + " expects " +
                parameters.size() + " arguments but got " + arguments.size());
        }

        try {
            for (int i = 0; i < parameters.size(); i++) {
                PythonRuntime.bind(parameters.get(i).getName(), arguments.get(i));
            }

            SnowparkSession session = new SnowparkSession(engine);
            // Handlers receive a PYTHON Session (the snowpark emulation) wrapping the Java facade, so
            // snowpark DataFrame code (session.table(...).select(...), df.write, udtf) works; plain
            // session.sql(...).collect()/count() callers see the same surface snowpark itself has.
            PythonRuntime.bind("__frostlake_java_session", session);
            PythonRuntime.eval(SNOWPARK_SHIM);
            PythonRuntime.eval("import snowflake.snowpark as __frostlake_sp\n"
                + "session = __frostlake_sp.Session(__frostlake_java_session)\n");

            String body = procedure.getBody();
            body = dedent(body);

            String pythonCode;
            String handlerName = procedure.getHandler();

            if (handlerName != null && !handlerName.isEmpty()) {
                pythonCode = body + "\n__result = " + handlerName + "(session";
                for (int i = 0; i < parameters.size(); i++) {
                    pythonCode += ", ";
                    pythonCode += parameters.get(i).getName();
                }
                pythonCode += ")";
            } else if (body.contains("def ") && body.contains("return")) {
                pythonCode = body + "\n__result = " + extractFunctionName(body) + "(session";
                for (int i = 0; i < parameters.size(); i++) {
                    pythonCode += ", ";
                    pythonCode += parameters.get(i).getName();
                }
                pythonCode += ")";
            } else {
                pythonCode = "def __temp_func():\n";
                for (final String line : body.split("\n")) {
                    if (!line.isEmpty()) {
                        pythonCode += "    " + line + "\n";
                    }
                }
                pythonCode += "__result = __temp_func()";
            }

            PythonRuntime.eval(pythonCode);

            final Object javaResult = PythonRuntime.toJava(PythonRuntime.global("__result"));

            logger.debug("Python procedure {} executed successfully", procedure.getName());

            if (javaResult == null) {
                return null;
            }

            if (javaResult instanceof Map) {
                // Parity with the previous behaviour: a returned dict becomes a Map of stringified values.
                final Map<String, Object> map = new HashMap<>();
                for (final Map.Entry<?, ?> entry : ((Map<?, ?>) javaResult).entrySet()) {
                    final Object value = entry.getValue();
                    map.put(String.valueOf(entry.getKey()), value == null ? null : value.toString());
                }
                return map;
            }

            return javaResult;

        } catch (final Exception e) {
            PythonRuntime.discardContext();
            logger.error("Error executing Python procedure: {}", procedure.getName(), e);
            throw new RuntimeException(PythonRuntimeDiagnostics.describeFailure(
                "procedure", procedure.getName(), procedure.getRuntimeVersion(), procedure.getBody(), e), e);
        }
    }

    private static String extractFunctionName(final String body) {
        String[] lines = body.split("\n");
        for (final String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("def ")) {
                int parenIndex = trimmed.indexOf('(');
                if (parenIndex > 4) {
                    return trimmed.substring(4, parenIndex).trim();
                }
            }
        }
        return null;
    }

    private static String dedent(final String text) {
        String[] lines = text.split("\n");
        if (lines.length == 0) return text;

        int minIndent = Integer.MAX_VALUE;
        for (final String line : lines) {
            if (line.trim().isEmpty()) continue;

            int indent = 0;
            for (final char c : line.toCharArray()) {
                if (c == ' ') indent++;
                else if (c == '\t') indent += 4;
                else break;
            }
            minIndent = Math.min(minIndent, indent);
        }

        if (minIndent == 0 || minIndent == Integer.MAX_VALUE) return text;

        StringBuilder result = new StringBuilder();
        for (final String line : lines) {
            if (line.trim().isEmpty()) {
                result.append("\n");
            } else {
                int toRemove = Math.min(minIndent, line.length());
                result.append(line.substring(toRemove)).append("\n");
            }
        }

        return result.toString().trim();
    }

    public static class SnowparkSession {
        private final DatabaseEngine engine;

        public SnowparkSession(final DatabaseEngine engine) {
            this.engine = engine;
        }

        public PythonResultSet sql(final String sqlText) {
            try {
                // General execute, not executeQuery: handler code runs DDL and DML (CREATE OR REPLACE
                // TABLE, TRUNCATE, MERGE, DELETE, ...) through session.sql exactly as it does queries.
                final ExecutionResult result = engine.execute(sqlText);
                if (!result.isSuccess()) {
                    throw new RuntimeException(result.getErrorMessage());
                }
                final List<ResultSet> resultSets = result.getResultSets();
                final ResultSet rs = resultSets == null || resultSets.isEmpty()
                    ? new ResultSet(new ArrayList<>(), new ArrayList<>())
                    : resultSets.get(resultSets.size() - 1);
                return new PythonResultSet(rs);
            } catch (final Exception e) {
                throw new RuntimeException("Error executing SQL: " + e.getMessage(), e);
            }
        }

        /**
         * Quiet existence probe for the shim's save_as_table paths: resolves through the catalog without
         * executing a statement, so a missing table does not produce an engine ERROR log entry the way a
         * failing probe query would.
         */
        public boolean tableExists(final String tableName) {
            try {
                return engine.getCatalog().resolveTable(tableName) != null;
            } catch (final RuntimeException e) {
                return false;
            }
        }

        public List<Row> table(final String tableName) {
            try {
                ResultSet rs = engine.executeQuery("SELECT * FROM " + tableName);
                return rs.getRows();
            } catch (final Exception e) {
                throw new RuntimeException("Error reading table: " + e.getMessage(), e);
            }
        }
    }

    public static class PythonResultSet {
        private final ResultSet resultSet;
        private final List<Row> rows;
        private int currentRow = -1;

        public PythonResultSet(final ResultSet resultSet) {
            this.resultSet = resultSet;
            this.rows = resultSet.getRows();
        }

        public boolean next() {
            currentRow++;
            return currentRow < rows.size();
        }

        public Object get(final int columnIndex) {
            if (currentRow < 0 || currentRow >= rows.size()) {
                throw new RuntimeException("No current row. Call next() first.");
            }

            Row row = rows.get(currentRow);
            int index = columnIndex;

            if (index < 0 || index >= row.size()) {
                throw new RuntimeException("Column index " + columnIndex + " is out of range");
            }

            return row.getValue(index);
        }

        public Object get(final String columnName) {
            if (currentRow < 0 || currentRow >= rows.size()) {
                throw new RuntimeException("No current row. Call next() first.");
            }

            Row row = rows.get(currentRow);

            for (int i = 0; i < resultSet.getColumns().size(); i++) {
                if (resultSet.getColumns().get(i).getName().equalsIgnoreCase(columnName)) {
                    return row.getValue(i);
                }
            }

            throw new RuntimeException("Column not found: " + columnName);
        }

        public List<Row> collect() {
            return new ArrayList<>(rows);
        }

        public int count() {
            return rows.size();
        }

        /** Column names in result order, for the Python snowpark emulation. */
        public List<String> columnNames() {
            final List<String> names = new ArrayList<>();
            for (int i = 0; i < resultSet.getColumns().size(); i++) {
                names.add(resultSet.getColumns().get(i).getName());
            }
            return names;
        }

        /** SQL type names ({@code VARCHAR}, {@code VARIANT}, ...) per column, aligned with {@link #columnNames()}. */
        public List<String> columnTypes() {
            final List<String> types = new ArrayList<>();
            for (int i = 0; i < resultSet.getColumns().size(); i++) {
                types.add(resultSet.getColumns().get(i).getDataType() == null
                    ? "VARCHAR"
                    : resultSet.getColumns().get(i).getDataType().getName());
            }
            return types;
        }

        /**
         * All row values, normalized to types GraalPy maps onto native Python values (str, int, float,
         * bool, None). Temporals cross as ISO strings and BigDecimal as long/double — the Python side
         * re-types them from {@link #columnTypes()}; host objects would otherwise surface as opaque
         * foreign values that break {@code json.dumps} and pandas.
         */
        public List<List<Object>> data() {
            final List<List<Object>> out = new ArrayList<>();
            for (final Row row : rows) {
                final List<Object> values = new ArrayList<>();
                for (int i = 0; i < row.size(); i++) {
                    values.add(toPythonFriendly(row.getValue(i)));
                }
                out.add(values);
            }
            return out;
        }

        private Object toPythonFriendly(final Object value) {
            if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Long || value instanceof Integer || value instanceof Short
                || value instanceof Byte || value instanceof Double || value instanceof Float) {
                return value;
            }
            if (value instanceof BigDecimal) {
                final BigDecimal decimal = ((BigDecimal) value).stripTrailingZeros();
                if (decimal.scale() <= 0) {
                    try {
                        return decimal.longValueExact();
                    } catch (final ArithmeticException tooBig) {
                        return decimal.doubleValue();
                    }
                }
                return decimal.doubleValue();
            }
            if (value instanceof BigInteger) {
                try {
                    return ((BigInteger) value).longValueExact();
                } catch (final ArithmeticException tooBig) {
                    return ((BigInteger) value).doubleValue();
                }
            }
            if (value instanceof LocalDateTime || value instanceof LocalDate || value instanceof LocalTime) {
                return value.toString();
            }
            return value;
        }
    }
}
