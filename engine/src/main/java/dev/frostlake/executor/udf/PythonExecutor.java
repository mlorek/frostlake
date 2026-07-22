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

import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.UdfLanguage;
import org.python.core.PyBoolean;
import org.python.core.PyCode;
import org.python.core.PyFloat;
import org.python.core.PyInteger;
import org.python.core.PyLong;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PythonExecutor {

    private static final Logger logger = LoggerFactory.getLogger(PythonExecutor.class);

    // Reuse one Jython interpreter PER THREAD plus a per-thread cache of compiled code, so a Python UDF body
    // compiles ONCE instead of spinning up a new PythonInterpreter and re-parsing on every row (per-row
    // interpreter creation made Python UDFs ~1000x a built-in — see UdfQueryPerformanceTest). The interpreter
    // is thread-confined, so no cross-thread sharing.
    private static final ThreadLocal<PythonInterpreter> INTERP = new ThreadLocal<PythonInterpreter>() {
        @Override
        protected PythonInterpreter initialValue() {
            return new PythonInterpreter();
        }
    };
    private static final ThreadLocal<Map<String, PyCode>> CODE = new ThreadLocal<Map<String, PyCode>>() {
        @Override
        protected Map<String, PyCode> initialValue() {
            return new HashMap<>();
        }
    };

    public static Object executePythonFunction(final Function function, final List<Object> arguments) {
        if (function.getUdfLanguage() != UdfLanguage.PYTHON) {
            throw new RuntimeException("Function is not a Python function");
        }

        final List<Parameter> parameters = function.getParameters();
        if (parameters.size() != arguments.size()) {
            throw new RuntimeException("Function " + function.getName() + " expects "
                + parameters.size() + " arguments but got " + arguments.size());
        }

        try {
            final PythonInterpreter interp = INTERP.get();
            for (int i = 0; i < parameters.size(); i++) {
                // Bind each argument under both its canonical (upper-cased) name and its lower-cased form:
                // the generated def uses the canonical name, a lower-case body reference finds the global.
                final String pName = parameters.get(i).getName();
                interp.set(pName, arguments.get(i));
                interp.set(pName.toLowerCase(), arguments.get(i));
            }

            final String pythonCode = buildCode(function, parameters);
            final Map<String, PyCode> cache = CODE.get();
            PyCode code = cache.get(pythonCode);
            if (code == null) {
                code = interp.compile(pythonCode);
                cache.put(pythonCode, code);
            }
            interp.exec(code);

            final PyObject result = interp.get("__result");
            logger.debug("Python function {} executed successfully", function.getName());
            if (result == null) {
                return null;
            }

            final Object javaResult = result.__tojava__(Object.class);
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
            return javaResult;
        } catch (final Exception e) {
            logger.error("Error executing Python function: {}", function.getName(), e);
            throw new RuntimeException("Error executing Python function "
                + function.getName() + ": " + e.getMessage(), e);
        }
    }

    /** Build the runnable Python source: the body plus an {@code __result = handler(params)} call. */
    private static String buildCode(final Function function, final List<Parameter> parameters) {
        final String body = dedent(function.getBody());
        final String handlerName = function.getHandler();
        final StringBuilder code = new StringBuilder();
        if (handlerName != null && !handlerName.isEmpty()) {
            code.append(body).append("\n__result = ").append(handlerName).append('(');
            appendArgs(code, parameters);
            code.append(')');
        } else if (body.contains("def ") && body.contains("return")) {
            code.append(body).append("\n__result = ").append(extractFunctionName(body)).append('(');
            appendArgs(code, parameters);
            code.append(')');
        } else {
            code.append("def __temp_func():\n");
            for (final String line : body.split("\n")) {
                if (!line.isEmpty()) {
                    code.append("    ").append(line).append('\n');
                }
            }
            code.append("__result = __temp_func()");
        }
        return code.toString();
    }

    private static void appendArgs(final StringBuilder code, final List<Parameter> parameters) {
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                code.append(", ");
            }
            code.append(parameters.get(i).getName());
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
        if (lines.length == 0) {
            return text;
        }

        int minIndent = Integer.MAX_VALUE;
        for (final String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            int indent = 0;
            for (final char c : line.toCharArray()) {
                if (c == ' ') {
                    indent++;
                } else if (c == '\t') {
                    indent += 4;
                } else {
                    break;
                }
            }
            minIndent = Math.min(minIndent, indent);
        }

        if (minIndent == 0 || minIndent == Integer.MAX_VALUE) {
            return text;
        }

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
