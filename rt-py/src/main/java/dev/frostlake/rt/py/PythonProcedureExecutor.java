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
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.UdfLanguage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PythonProcedureExecutor {

    /** Static helpers only — never instantiated. */
    private PythonProcedureExecutor() {
    }

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

        final List<Parameter> parameters = procedure.getParameters();
        if (parameters.size() != arguments.size()) {
            throw new RuntimeException("Procedure " + procedure.getName() + " expects " +
                parameters.size() + " arguments but got " + arguments.size());
        }

        try {
            for (int i = 0; i < parameters.size(); i++) {
                PythonRuntime.bind(parameters.get(i).getName(), arguments.get(i));
            }

            // Measured live: an owner's rights PYTHON procedure is refused a temporary object exactly as
            // a Java one is, so the restriction belongs to the handler languages rather than to Java.
            // The rights mode rides on the Procedure, so the SPI signature does not have to grow one.
            final SnowparkSession session = new SnowparkSession(
                engine, "OWNER".equalsIgnoreCase(procedure.getExecuteAs()));
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
            final String handlerName = procedure.getHandler();

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
        final String[] lines = body.split("\n");
        for (final String line : lines) {
            final String trimmed = line.trim();
            if (trimmed.startsWith("def ")) {
                final int parenIndex = trimmed.indexOf('(');
                if (parenIndex > 4) {
                    return trimmed.substring(4, parenIndex).trim();
                }
            }
        }
        return null;
    }

    private static String dedent(final String text) {
        final String[] lines = text.split("\n");
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

        final StringBuilder result = new StringBuilder();
        for (final String line : lines) {
            if (line.trim().isEmpty()) {
                result.append("\n");
            } else {
                final int toRemove = Math.min(minIndent, line.length());
                result.append(line.substring(toRemove)).append("\n");
            }
        }

        return result.toString().trim();
    }

}
