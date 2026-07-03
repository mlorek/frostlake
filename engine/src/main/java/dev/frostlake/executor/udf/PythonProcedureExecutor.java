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
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.python.core.PyBoolean;
import org.python.core.PyDictionary;
import org.python.core.PyFloat;
import org.python.core.PyInteger;
import org.python.core.PyLong;
import org.python.core.PyString;

public class PythonProcedureExecutor {

    private static final Logger logger = LoggerFactory.getLogger(PythonProcedureExecutor.class);

    public static Object executePythonProcedure(final Procedure procedure, final List<Object> arguments, final DatabaseEngine engine) {
        if (procedure.getUdfLanguage() != UdfLanguage.PYTHON) {
            throw new RuntimeException("Procedure is not a Python procedure");
        }

        List<Parameter> parameters = procedure.getParameters();
        if (parameters.size() != arguments.size()) {
            throw new RuntimeException("Procedure " + procedure.getName() + " expects " +
                parameters.size() + " arguments but got " + arguments.size());
        }

        try (PythonInterpreter interp = new PythonInterpreter()) {

            for (int i = 0; i < parameters.size(); i++) {
                String paramName = parameters.get(i).getName();
                Object value = arguments.get(i);
                interp.set(paramName, value);
            }

            SnowparkSession session = new SnowparkSession(engine);
            interp.set("session", session);

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

            interp.exec(pythonCode);

            PyObject result = interp.get("__result");

            logger.debug("Python procedure {} executed successfully", procedure.getName());

            if (result == null) {
                return null;
            }

            Object javaResult = result.__tojava__(Object.class);

            if (javaResult instanceof PyInteger) {
                return ((PyInteger) javaResult).getValue();
            }
            if (javaResult instanceof PyLong) {
                return ((PyLong) javaResult).getValue().longValue();
            }
            if (javaResult instanceof PyFloat) {
                return ((PyFloat) javaResult).getValue();
            }
            if (javaResult instanceof PyString) {
                return javaResult.toString();
            }
            if (javaResult instanceof PyBoolean) {
                return ((PyBoolean) javaResult).getBooleanValue();
            }
            if (javaResult instanceof PyDictionary) {
                Map<String, Object> map = new HashMap<>();
                PyDictionary dict = (PyDictionary) javaResult;
                for (final Object key : dict.keys()) {
                    String keyStr = key.toString();
                    Object value = dict.get(key);
                    if (value != null) {
                        map.put(keyStr, value.toString());
                    } else {
                        map.put(keyStr, null);
                    }
                }
                return map;
            }

            return javaResult;

        } catch (final Exception e) {
            logger.error("Error executing Python procedure: {}", procedure.getName(), e);
            throw new RuntimeException("Error executing Python procedure " + procedure.getName() + ": " + e.getMessage(), e);
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
                ResultSet rs = engine.executeQuery(sqlText);
                return new PythonResultSet(rs);
            } catch (final Exception e) {
                throw new RuntimeException("Error executing SQL: " + e.getMessage(), e);
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
    }
}
