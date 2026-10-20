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

    /** The global an aggregate's argument is bound to for its {@code accumulate} call, numbered from 0. */
    private static final String AGGREGATE_ARGUMENT = "__fl_aggregate_argument";

    /**
     * The global a scalar function's argument is bound to for its handler's call, numbered from 0: the arguments are
     * the handler's parameters alone (live-verified), so a parameter named like a built-in - {@code type},
     * {@code len}, {@code str}, {@code id} - shadows nothing beyond the handler, and a helper of the body that reads
     * the parameter's name finds no such global.
     */
    private static final String FUNCTION_ARGUMENT = "__fl_argument";

    public static Object executePythonFunction(final Function function, final List<Object> arguments) {
        if (function.getUdfLanguage() != UdfLanguage.PYTHON) {
            throw new RuntimeException("Function is not a Python function");
        }

        final List<Parameter> parameters = function.getParameters();
        if (parameters.size() != arguments.size()) {
            throw new RuntimeException("Function " + function.getName() + " expects "
                + parameters.size() + " arguments but got " + arguments.size());
        }

        Object frame = null;
        try {
            frame = PythonInterpreter.enterModule();
            for (int i = 0; i < parameters.size(); i++) {
                // Each argument as the Python value its parameter's type maps to (see PythonInterpreter.bindArgument),
                // under a name of the runtime's own, which the handler's call passes on.
                final Parameter parameter = parameters.get(i);
                PythonInterpreter.bindArgument(parameter, FUNCTION_ARGUMENT + i, arguments.get(i));
                if (function.getHandler() == null || function.getHandler().isEmpty()) {
                    // A body with no handler reads its parameters by their names.
                    PythonInterpreter.bindArgument(parameter, parameter.getName(), arguments.get(i));
                }
            }

            // A FUNCTION body may import snowflake.snowpark exactly like a procedure body; the
            // shim install is idempotent per thread.
            PythonProcedureExecutor.installSnowparkShim();
            if (PythonHandlerCheck.isDotted(function.getHandler())) {
                PythonInterpreter.bind("__fl_handler_name", function.getHandler());
            }
            PythonInterpreter.eval(buildCode(function, parameters));

            final Object answer = answered(function);
            logger.debug("Python function {} executed successfully", function.getName());
            return answer;
        } catch (final PythonReturnRefusal refused) {
            throw refused;
        } catch (final Exception e) {
            // Worded before the context is settled (see PythonInterpreter.settleAfter): a discarded context answers
            // nothing more of its failure, so the refusal then travels as text alone.
            final String refusal = PythonRuntimeDiagnostics.describeFailure(
                "function", function.getName(), function.getRuntimeVersion(), function.getBody(), e);
            logger.error("Error executing Python function: {}", function.getName(), e);
            if (!PythonInterpreter.settleAfter(e)) {
                throw new RuntimeException(refusal);
            }
            throw new RuntimeException(refusal, e);
        } finally {
            PythonInterpreter.leaveModule(frame);
        }
    }

    /**
     * What the handler left in {@code __result}, as the function's RETURNS type holds it: converted as Python
     * converts and judged as the account judges it (see {@link PythonReturn}), and a type that takes the answer as
     * the runtime holds it reading a dict or a list as a VARIANT.
     */
    private static Object answered(final Function function) {
        final Object declared = PythonReturn.declaredGlobal("__result", function.getReturnType(), function.getName(),
            function.getHandler());
        return declared != PythonReturn.AS_IS ? declared : sqlResult(PythonInterpreter.global("__result"));
    }

    /**
     * What a handler answered, as the engine holds a value: a dict or a list becomes a VARIANT, rendered as JSON like
     * every other semi-structured value in the engine.
     */
    private static Object sqlResult(final Object javaResult) {
        if (javaResult == null) {
            return null;
        }
        final JsonNode semiStructured = toJsonNode(javaResult);
        if (semiStructured != null) {
            // Canonical form (sorted object keys, whole-valued decimals descaled) — the same
            // normalization PARSE_JSON and OBJECT_CONSTRUCT apply. The engine compares VARIANTs by
            // their JSON TEXT, so a dict emitted in insertion order compared unequal to a structurally
            // identical PARSE_JSON'd object even when the data was the same.
            return ArrayFunctionHelper.toCanonicalVariant(semiStructured);
        }
        return javaResult;
    }

    /**
     * Run a Python aggregate function's handler over one group (live-verified): an instance of the handler class
     * reads each of the group's rows with {@code accumulate}, a NULL argument as None, and {@code finish} answers
     * the group's value. A failure anywhere is the call's, worded as a scalar function's is.
     *
     * @param function the function
     * @param rows     the group's rows, each the function's arguments
     * @return the group's value
     */
    static Object executePythonAggregate(final Function function, final List<List<Object>> rows) {
        final List<Parameter> parameters = function.getParameters();
        final String handlerName = function.getHandler();
        Object frame = null;
        try {
            frame = PythonInterpreter.enterModule();
            PythonProcedureExecutor.installSnowparkShim();
            PythonInterpreter.eval(dedent(function.getBody()));
            if (PythonHandlerCheck.isDotted(handlerName)) {
                PythonInterpreter.bind("__fl_handler_name", handlerName);
                PythonInterpreter.eval(PythonHandlerCheck.RESOLVE_DOTTED + "__fl_aggregate = __fl_handler()");
            } else {
                PythonInterpreter.eval("__fl_aggregate = " + handlerName + "()");
            }
            final StringBuilder call = new StringBuilder("__fl_aggregate.accumulate(");
            for (int i = 0; i < parameters.size(); i++) {
                call.append(i == 0 ? "" : ", ").append(AGGREGATE_ARGUMENT).append(i);
            }
            final String accumulate = call.append(')').toString();
            for (final List<Object> row : rows) {
                for (int i = 0; i < parameters.size(); i++) {
                    PythonInterpreter.bindArgument(parameters.get(i), AGGREGATE_ARGUMENT + i, i < row.size() ? row.get(i) : null);
                }
                PythonInterpreter.eval(accumulate);
            }
            PythonInterpreter.eval("__result = __fl_aggregate.finish()");
            return answered(function);
        } catch (final PythonReturnRefusal refused) {
            throw refused;
        } catch (final Exception e) {
            final String refusal = PythonRuntimeDiagnostics.describeFailure(
                "function", function.getName(), function.getRuntimeVersion(), function.getBody(), e);
            if (!PythonInterpreter.settleAfter(e)) {
                throw new RuntimeException(refusal);
            }
            throw new RuntimeException(refusal, e);
        } finally {
            PythonInterpreter.leaveModule(frame);
        }
    }

    /**
     * Compile a Python FUNCTION's body at CREATE time. Live refuses these here, and this refuses the same: a
     * body that is not Python, a body whose module-level code fails (an import of something that is not
     * installed is the usual one), and a HANDLER that cannot take the function's arguments (see
     * {@link PythonHandlerCheck}).
     *
     * <p>A Python PROCEDURE is deliberately NOT put through this. Measured on the same account: the
     * identical nonsense body is refused for a function and accepted for a procedure.
     */
    static void compilePythonFunction(final Function function) {
        Object frame = null;
        try {
            try {
                frame = PythonInterpreter.enterModule();
                // The body's module-level snowpark imports must resolve at CREATE exactly as they do at
                // execution; without the shim the compile refused every function importing snowpark.
                PythonProcedureExecutor.installSnowparkShim();
                PythonHandlerCheck.forget(function.getHandler());
                PythonInterpreter.eval(dedent(function.getBody()));
            } catch (final Exception e) {
                // Same discipline as execution (see PythonInterpreter.settleAfter).
                final String refusal = PythonRuntimeDiagnostics.describeFailure(
                    "function", function.getName(), function.getRuntimeVersion(), function.getBody(), e);
                if (!PythonInterpreter.settleAfter(e)) {
                    throw new RuntimeException(refusal);
                }
                throw new RuntimeException(refusal, e);
            }
            final String handlerName = function.getHandler();
            if (handlerName == null) {
                return;
            }
            final String refusal = PythonHandlerCheck.refusal(handlerName, function.isTableFunction(),
                function.isAggregate(), function.getParameters() == null ? 0 : function.getParameters().size());
            if (refusal != null) {
                throw new RuntimeException(refusal + (handlerName.isEmpty() ? " in function " + function.getName()
                    : " in function " + function.getName() + " with handler " + handlerName));
            }
        } finally {
            PythonInterpreter.leaveModule(frame);
        }
    }

    /**
     * Build the runnable Python source: the body plus an {@code __result = handler(params)} call. A dotted handler
     * names an attribute of a module, which is imported and read as CREATE resolved it.
     */
    private static String buildCode(final Function function, final List<Parameter> parameters) {
        final String body = dedent(function.getBody());
        final String handlerName = function.getHandler();
        final StringBuilder code = new StringBuilder();
        if (PythonHandlerCheck.isDotted(handlerName)) {
            code.append(body).append('\n').append(PythonHandlerCheck.RESOLVE_DOTTED).append("__result = __fl_handler(");
            appendArgs(code, parameters);
            code.append(')');
        } else if (handlerName != null && !handlerName.isEmpty()) {
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
            code.append(FUNCTION_ARGUMENT).append(i);
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
        return PythonValueText.temporalInContainer(value);
    }

}
