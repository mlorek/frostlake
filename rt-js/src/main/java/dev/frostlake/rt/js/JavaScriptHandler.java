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

import dev.frostlake.executor.udf.JavaScriptIdentifiers;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.TypeCategory;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantValue;

import java.util.List;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * A JavaScript handler as the account runs one: a function under the routine's own name, taking the routine's
 * arguments as its parameters. Inside the body the arguments are parameters and nothing else — {@code arguments}
 * holds them, {@code typeof globalThis.X} is undefined — the handler's name is the handler itself, {@code this}
 * is the global object, or undefined under {@code 'use strict'}, and while it runs the handler is a global of
 * that name (live-verified).
 *
 * <p>The script evaluates to an invoker, which the executors call with the argument values. The invoker turns a
 * semi-structured argument's JSON text into a native value, wraps a non-array given for an ARRAY parameter in
 * one, binds the global name for the length of the call, and hands an object result back as JSON text.
 */
final class JavaScriptHandler {

    /** The line of the evaluated source that holds the body's first line. */
    static final int BODY_FIRST_LINE = 3;

    private JavaScriptHandler() {
    }

    /**
     * The script whose value is the handler's invoker. A procedure's invoker takes the procedure API object
     * first, and gives the {@code snowflake} object its statement methods from it.
     *
     * @param name       the routine's name as the catalog holds it
     * @param parameters the routine's parameters
     * @param body       the handler body as written
     * @param procedure  whether the handler is a procedure's
     * @return the script
     */
    static String source(final String name, final List<Parameter> parameters, final String body,
                         final boolean procedure) {
        final boolean named = JavaScriptIdentifiers.isDeclarable(name);
        final StringBuilder declared = new StringBuilder();
        final StringBuilder passed = new StringBuilder();
        final StringBuilder converted = new StringBuilder();
        for (int i = 0; i < parameters.size(); i++) {
            final String parameterName = parameters.get(i).getName();
            declared.append(i > 0 ? ", " : "")
                .append(JavaScriptIdentifiers.isDeclarable(parameterName) ? parameterName : "__fl_p" + i);
            passed.append(", a").append(i);
            final DataType type = parameters.get(i).getDataType();
            if (type != null && type.getCategory() == TypeCategory.SEMI_STRUCTURED) {
                converted.append("if (typeof a").append(i).append(" === 'string') { try { a").append(i)
                    .append(" = parse(a").append(i).append("); } catch (notJson) { } }\n");
                if (type instanceof ArrayType) {
                    // An ARRAY parameter converts as TO_ARRAY does: a non-array arrives as a one-element array.
                    converted.append("if (a").append(i).append(" !== null && a").append(i)
                        .append(" !== undefined && !isArray(a").append(i).append(")) { a").append(i)
                        .append(" = [a").append(i).append("]; }\n");
                }
            }
        }
        final String arguments = passed.length() > 0 ? passed.substring(2) : "";
        final String global = "'" + name.replace("\\", "\\\\").replace("'", "\\'") + "'";
        final StringBuilder source = new StringBuilder()
            .append("(function () { var parse = JSON.parse, toJson = JSON.stringify, isArray = Array.isArray;\n")
            .append("var handler = function ").append(named ? name : "").append('(').append(declared).append(") {\n")
            .append(body)
            .append("\n};\n")
            .append("return function (").append(procedure ? "api" : "").append(procedure && arguments.length() > 0 ? ", " : "")
            .append(arguments).append(") {\n")
            .append(converted);
        if (procedure) {
            source.append("Snowflake.prototype.createStatement = function createStatement(options) {")
                .append(" return api.createStatement(options); };\n")
                .append("Snowflake.prototype.execute = function execute(options) { return api.execute(options); };\n");
        }
        if (named) {
            source.append("var had = Object.prototype.hasOwnProperty.call(globalThis, ").append(global)
                .append("), saved = globalThis[").append(global).append("];\n")
                .append("globalThis[").append(global).append("] = handler;\n")
                .append("try {\n");
        }
        source.append("var result = handler.call(undefined").append(arguments.length() > 0 ? ", " : "")
            .append(arguments).append(");\n")
            .append("return result !== null && typeof result === 'object' ? toJson(result) : result;\n");
        if (named) {
            source.append("} finally { if (had) { globalThis[").append(global).append("] = saved; } else { delete globalThis[")
                .append(global).append("]; } }\n");
        }
        return source.append("};\n})()").toString();
    }

    /**
     * Calls a handler's invoker with the argument values: a BINARY as a byte array, and a semi-structured
     * value as the JSON text the invoker parses.
     *
     * @param engine     the engine that evaluated the invoker
     * @param invoker    the invoker
     * @param api        the procedure API object, or null for a function
     * @param parameters the routine's parameters
     * @param arguments  the argument values
     * @return the handler's result
     * @throws ScriptException when the handler fails
     * @throws NoSuchMethodException when the invoker is not callable
     */
    static Object invoke(final ScriptEngine engine, final Object invoker, final Object api,
                         final List<Parameter> parameters, final List<Object> arguments)
            throws ScriptException, NoSuchMethodException {
        final Object[] values = new Object[(api != null ? 2 : 1) + arguments.size()];
        int at = 0;
        values[at++] = null;
        if (api != null) {
            values[at++] = api;
        }
        for (int i = 0; i < arguments.size(); i++) {
            values[at++] = argumentValue(parameters.get(i), arguments.get(i));
        }
        return ((Invocable) engine).invokeMethod(invoker, "call", values);
    }

    /** One argument as the invoker takes it. */
    private static Object argumentValue(final Parameter parameter, final Object value) {
        if (value instanceof BinaryValue) {
            return ((BinaryValue) value).bytes();
        }
        final DataType type = parameter.getDataType();
        if (value == null || value instanceof String || type == null || type.getCategory() != TypeCategory.SEMI_STRUCTURED) {
            return value;
        }
        if (value instanceof VariantValue) {
            // The canonical JSON text, not the display form, where a string shows unquoted and XML as XML.
            return ((VariantValue) value).text();
        }
        try {
            // A host container has no JavaScript Array or Object protocol, so it crosses as JSON text too.
            return ArrayFunctionHelper.MAPPER.writeValueAsString(value);
        } catch (final Exception notJson) {
            return value;
        }
    }
}
