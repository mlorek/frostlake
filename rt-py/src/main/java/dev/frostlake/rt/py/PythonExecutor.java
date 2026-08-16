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

import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.types.TypeCategory;
import dev.frostlake.values.BinaryValue;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class PythonExecutor {

    /** Static helpers only — never instantiated. */
    private PythonExecutor() {
    }

    private static final Logger logger = LoggerFactory.getLogger(PythonExecutor.class);

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
            for (int i = 0; i < parameters.size(); i++) {
                // Bind each argument under both its canonical (upper-cased) name and its lower-cased form:
                // the generated def uses the canonical name, a lower-case body reference finds the global.
                final Parameter parameter = parameters.get(i);
                final String pName = parameter.getName();
                bindArgument(parameter, pName, arguments.get(i));
                bindArgument(parameter, pName.toLowerCase(), arguments.get(i));
            }

            // A FUNCTION body may import snowflake.snowpark exactly like a procedure body; the
            // shim install is idempotent per thread.
            PythonProcedureExecutor.installSnowparkShim();
            PythonRuntime.eval(buildCode(function, parameters));

            final Object javaResult = PythonRuntime.toJava(PythonRuntime.global("__result"));
            logger.debug("Python function {} executed successfully", function.getName());
            if (javaResult == null) {
                return null;
            }
            // A dict / list return becomes a VARIANT, rendered as JSON like every other semi-structured
            // value in the engine.
            final JsonNode semiStructured = toJsonNode(javaResult);
            if (semiStructured != null) {
                // Canonical form (sorted object keys, whole-valued decimals descaled) — the same
                // normalization PARSE_JSON and OBJECT_CONSTRUCT apply. The engine compares VARIANTs by
                // their JSON TEXT, so a dict emitted in insertion order compared unequal to a structurally
                // identical PARSE_JSON'd object even when the data was the same.
                return ArrayFunctionHelper.toCanonicalVariant(semiStructured);
            }
            return javaResult;
        } catch (final Exception e) {
            // A failed execution may have left a Python-level lock acquired (see discardContext); never
            // reuse the context afterwards, or the NEXT call on this thread blocks forever.
            PythonRuntime.discardContext();
            logger.error("Error executing Python function: {}", function.getName(), e);
            throw new RuntimeException(PythonRuntimeDiagnostics.describeFailure(
                "function", function.getName(), function.getRuntimeVersion(), function.getBody(), e), e);
        }
    }

    /**
     * Compile a Python FUNCTION's body at CREATE time. Live refuses three things here and this refuses
     * the same three: a body that is not Python, a body whose module-level code fails (an import of
     * something that is not installed is the usual one), and a HANDLER the body does not define — or
     * defines with a different number of arguments than the function declares.
     *
     * <p>A Python PROCEDURE is deliberately NOT put through this. Measured on the same account: the
     * identical nonsense body is refused for a function and accepted for a procedure.
     */
    static void compilePythonFunction(final Function function) {
        try {
            // The body's module-level snowpark imports must resolve at CREATE exactly as they do at
            // execution; without the shim the compile refused every function importing snowpark.
            PythonProcedureExecutor.installSnowparkShim();
            PythonRuntime.eval(dedent(function.getBody()));
        } catch (final Exception e) {
            // Same discipline as execution: a failed eval may hold a Python-level lock, and reusing the
            // context afterwards would block this thread's NEXT call forever.
            PythonRuntime.discardContext();
            throw new RuntimeException(PythonRuntimeDiagnostics.describeFailure(
                "function", function.getName(), function.getRuntimeVersion(), function.getBody(), e), e);
        }
        final String handlerName = function.getHandler();
        if (handlerName == null || handlerName.isEmpty()) {
            return;
        }
        final Value handler = PythonRuntime.global(handlerName);
        if (handler == null || !handler.canExecute()) {
            throw new RuntimeException("Could not find handler in function " + function.getName()
                + " with handler " + handlerName);
        }
        final int declared = function.getParameters() == null ? 0 : function.getParameters().size();
        final int accepts = declaredArgumentCount(handler);
        if (accepts >= 0 && accepts != declared) {
            throw new RuntimeException("Python function is defined with " + accepts
                + " arguments, but UDF definition contains " + declared
                + " arguments in function " + function.getName() + " with handler " + handlerName);
        }
    }

    /** How many positional arguments a Python callable takes, or -1 when it will not say. */
    private static int declaredArgumentCount(final Value handler) {
        try {
            final Value code = handler.getMember("__code__");
            if (code == null || !code.hasMember("co_argcount")) {
                return -1;
            }
            return code.getMember("co_argcount").asInt();
        } catch (final RuntimeException notIntrospectable) {
            return -1;
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

    /**
     * Bind one argument. A parameter DECLARED as semi-structured (ARRAY / OBJECT / VARIANT) has its JSON
     * text turned into native Python dict/list values so the handler can index/iterate it; everything else is
     * bound as-is, so a VARCHAR that merely looks like JSON still arrives as a string.
     */
    private static void bindArgument(final Parameter parameter, final String name, final Object value) {
        if (parameter.getDataType() != null
                && parameter.getDataType().getCategory() == TypeCategory.SEMI_STRUCTURED
                && PythonRuntime.bindJson(name, value)) {
            return;
        }
        if (value instanceof BinaryValue && PythonRuntime.bindBytes(name, ((BinaryValue) value).toHex())) {
            return;
        }
        PythonRuntime.bind(name, value);
    }

    /**
     * A Python {@code dict} / {@code list} (or tuple) return value as a Jackson node, so it can be rendered as
     * the JSON text the engine uses for VARIANT. Returns null for anything that is not a container, letting the
     * caller keep its existing scalar handling. Nested values recurse, and a temporal member is rendered
     * the way Python's {@code str()} would, which is what Snowflake stores inside a VARIANT.
     */
    private static JsonNode toJsonNode(final Object value) {
        if (value instanceof Map) {
            final ObjectNode object = ArrayFunctionHelper.MAPPER.createObjectNode();
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                object.set(String.valueOf(unwrapScalar(entry.getKey())), toNode(entry.getValue()));
            }
            return object;
        }
        if (value instanceof Collection) {
            final ArrayNode array = ArrayFunctionHelper.MAPPER.createArrayNode();
            for (final Object element : (Collection<?>) value) {
                array.add(toNode(element));
            }
            return array;
        }
        if (value instanceof Object[]) {
            final ArrayNode array = ArrayFunctionHelper.MAPPER.createArrayNode();
            for (final Object element : (Object[]) value) {
                array.add(toNode(element));
            }
            return array;
        }
        return null;
    }

    /** One value inside a returned dict/list, as a node: nested dicts/lists recurse, scalars are unwrapped. */
    private static JsonNode toNode(final Object value) {
        final JsonNode container = toJsonNode(value);
        if (container != null) {
            return container;
        }
        return ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, unwrapScalar(value));
    }

    /** A value inside a returned container: temporals stringify like Python str(), others pass through. */
    private static Object unwrapScalar(final Object value) {
        return PythonRuntime.temporalInContainer(value);
    }

}
