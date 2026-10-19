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

import dev.frostlake.config.S3PathResolver;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantValue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.List;

/**
 * Runs an aggregate function written in Java (live-verified). Its HANDLER names a class whose public methods carry
 * a group's state from one call to the next: {@code initialize()} makes the empty state, {@code accumulate(state,
 * <the function's arguments>)} answers the state with one row folded in, {@code merge(state, state)} answers two
 * partial states as one, and {@code finish(state)} answers the group's result. Each is required, and looked for in
 * that order, as the function is created. Every row of a group reaches {@code accumulate}, a NULL argument as
 * {@code null}; a group is finished from the state its rows left, and an aggregation over no rows at all is NULL
 * without the handler being asked.
 */
public final class JavaAggregateFunctions {

    private static final JavaFunctionCompiler COMPILER = new JavaFunctionCompiler();

    /** Static helpers only — never instantiated. */
    private JavaAggregateFunctions() {
    }

    /**
     * Judges an aggregate function's handler as creating the function does.
     *
     * @param function   the function
     * @param catalog    the catalog an IMPORTS reference is resolved in, or null
     * @param s3Resolver resolves an IMPORTS reference in external storage, or null
     */
    public static void check(final Function function, final Catalog catalog, final S3PathResolver s3Resolver) {
        handlerOf(function, JavaTableFunctions.loaded(function, catalog, s3Resolver));
    }

    /**
     * Runs the handler over one group's rows.
     *
     * @param function   the function
     * @param rows       the group's rows, each the function's arguments; at least one
     * @param catalog    the catalog an IMPORTS reference is resolved in, or null
     * @param s3Resolver resolves an IMPORTS reference in external storage, or null
     * @return the group's result
     */
    public static Object run(final Function function, final List<List<Object>> rows, final Catalog catalog,
                             final S3PathResolver s3Resolver) {
        final String where = JavaTableFunctions.where(function);
        final JavaAggregateHandler handler = handlerOf(function,
            JavaTableFunctions.loaded(function, catalog, s3Resolver));
        final boolean needsInstance = !Modifier.isStatic(handler.initialize().getModifiers())
            || !Modifier.isStatic(handler.accumulate().getModifiers())
            || !Modifier.isStatic(handler.finish().getModifiers());
        final Object instance = needsInstance ? JavaHandlerInstances.create(handler.handled(), where) : null;
        Object state = invoke(handler, handler.initialize(), instance, new Object[0], where);
        final Class<?>[] declared = handler.accumulate().getParameterTypes();
        final Class<?>[] argumentTypes = Arrays.copyOfRange(declared, 1, declared.length);
        for (final List<Object> arguments : rows) {
            final Object[] converted = JavaHandlerArguments.of(argumentTypes, function.getParameters(), arguments,
                where);
            final Object[] call = new Object[converted.length + 1];
            call[0] = state;
            System.arraycopy(converted, 0, call, 1, converted.length);
            state = invoke(handler, handler.accumulate(), instance, call, where);
        }
        final Object answered = invoke(handler, handler.finish(), instance, new Object[] {state}, where);
        return sqlValue(HandlerResults.fromJava(answered, function.getReturnType(), function.getName(),
            function.getHandler()));
    }

    private static Object invoke(final JavaAggregateHandler handler, final Method method, final Object instance,
                                 final Object[] arguments, final String where) {
        return COMPILER.invokeHandler(handler.handled(), method,
            Modifier.isStatic(method.getModifiers()) ? null : instance, arguments, where);
    }

    /** The handler's methods, or the refusal of the first one the class lacks. */
    private static JavaAggregateHandler handlerOf(final Function function, final Class<?> handled) {
        final String where = JavaTableFunctions.where(function);
        final int arity = function.getParameters() == null ? 0 : function.getParameters().size();
        final Method initialize = required(handled, "initialize", 0, where);
        final Method accumulate = required(handled, "accumulate", arity + 1, where);
        required(handled, "merge", 2, where);
        final Method finish = required(handled, "finish", 1, where);
        return new JavaAggregateHandler(handled, initialize, accumulate, finish);
    }

    private static Method required(final Class<?> handled, final String name, final int arity, final String where) {
        final Method method = JavaTableFunctions.publicMethod(handled, name, arity);
        if (method == null) {
            throw new RuntimeException("Failed to find a public method named \"" + name + "\" with " + arity
                + " arguments\nThe " + name + "() method is required for Java UDAFs" + where);
        }
        return method;
    }

    /** What the handler answered as the engine holds a value: an int as a long, anything else unknown as text. */
    private static Object sqlValue(final Object value) {
        if (value == null || value instanceof BinaryValue || value instanceof VariantValue
                || value instanceof TemporalAccessor || value instanceof Number && !(value instanceof Integer)
                || value instanceof String || value instanceof Boolean) {
            return value;
        }
        return value instanceof Integer ? Long.valueOf(((Integer) value).longValue()) : value.toString();
    }
}
