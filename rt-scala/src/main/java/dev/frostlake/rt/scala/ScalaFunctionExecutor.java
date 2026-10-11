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

package dev.frostlake.rt.scala;

import dev.frostlake.config.S3PathResolver;
import dev.frostlake.executor.udf.HandlerResults;
import dev.frostlake.executor.udf.JarHandlerLoader;
import dev.frostlake.executor.udf.JavaHandlerArguments;
import dev.frostlake.executor.udf.JavaHandlerFailure;
import dev.frostlake.executor.udf.UdfConsoleCapture;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.UdfLanguage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.JavaConverters;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Executes inline Scala scalar UDFs by compiling the body in-process via {@link ScalaCompiler} (the
 * bundled scala-compiler, no external {@code scalac}), then loading and invoking the HANDLER method.
 * Mirrors {@link ScalaProcedureExecutor} but for scalar functions: the handler takes the function's
 * declared parameters directly (no Snowpark {@code Session}) and returns the value.
 *
 * <p>HANDLER is {@code 'ClassName.methodName'}; a Scala {@code object} is invoked through its
 * {@code MODULE$} singleton, a {@code class} via its no-arg constructor.
 */
public final class ScalaFunctionExecutor {

    /** Static helpers only — never instantiated. */
    private ScalaFunctionExecutor() {
    }

    private static final Logger logger = LoggerFactory.getLogger(ScalaFunctionExecutor.class);

    public static Object executeScalaFunction(final Function function, final List<Object> arguments,
                                              final Catalog catalog, final S3PathResolver s3Resolver) {
        if (function.getUdfLanguage() != UdfLanguage.SCALA) {
            throw new RuntimeException("Function is not a Scala function");
        }

        final String handler = function.getHandler();
        if (handler == null || !handler.contains(".")) {
            throw new RuntimeException("HANDLER must be 'ClassName.methodName' for LANGUAGE SCALA functions");
        }

        final List<Parameter> parameters = function.getParameters();
        if (parameters.size() != arguments.size()) {
            throw new RuntimeException("Function " + function.getName() + " expects "
                + parameters.size() + " arguments but got " + arguments.size());
        }

        final int dot = handler.lastIndexOf('.');
        final String className = handler.substring(0, dot);
        final String methodName = handler.substring(dot + 1);

        try {
            // Inline bodies compile ONCE (cached); jar handlers load from IMPORTS. Avoids per-row recompiles.
            final String where = " in function " + function.getName() + " with handler " + handler;
            final Class<?> compiled = function.getImports().isEmpty()
                ? ScalaCompiler.compileCached(function.getBody(), className, where)
                : JarHandlerLoader.load(function.getImports(), className, catalog, s3Resolver);
            final Object answered = invokeHandler(compiled, methodName, arguments, parameters, where);
            // What the handler hands back becomes the declared RETURNS type's value as a Java handler's does:
            // an Array[String] an ARRAY, a Map an OBJECT, a text read as the type reads one.
            final Object result = HandlerResults.fromJava(answered instanceof scala.collection.Map
                ? JavaConverters.mapAsJavaMap((scala.collection.Map<?, ?>) answered) : answered,
                function.getReturnType(), function.getName(), handler);
            logger.debug("Scala function {} executed successfully", function.getName());
            return result;
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("Failed to execute Scala function "
                + function.getName() + ": " + e.getMessage(), e);
        }
    }

    private static Object invokeHandler(final Class<?> clazz, final String methodName,
                                        final List<Object> arguments, final List<Parameter> parameters,
                                        final String where) throws Exception {
        Object instance;
        try {
            final Field moduleField = clazz.getField("MODULE$");
            instance = moduleField.get(null);
        } catch (final NoSuchFieldException e) {
            instance = clazz.getDeclaredConstructor().newInstance();
        }
        final Method target = findMethod(clazz, methodName, arguments.size());
        // A Scala handler's parameters are the JVM's: each argument becomes its type as a Java handler's does,
        // so a number outside an Int's or a Long's range is refused, never wrapped.
        final Object[] callArgs = JavaHandlerArguments.of(target.getParameterTypes(), parameters, arguments, where);
        UdfConsoleCapture.enter();
        try {
            return target.invoke(instance, callArgs);
        } catch (final InvocationTargetException thrown) {
            // What the handler raised reaches SQL as its report, as a Java handler's does (live-verified): the
            // exception and its frames in the handler's source, an object's method under its forwarder.
            final Throwable cause = thrown.getCause() != null ? thrown.getCause() : thrown;
            final String name = clazz.getName();
            throw new RuntimeException(JavaHandlerFailure.report(cause, clazz, where, ScalaCompiler.SOURCE_NAME,
                name.endsWith("$") ? "function_handler_0//" + name.substring(0, name.length() - 1) + '.'
                    + methodName + '(' + ScalaCompiler.SOURCE_NAME + ')' : null), cause);
        } finally {
            UdfConsoleCapture.exit();
        }
    }

    private static Method findMethod(final Class<?> clazz, final String methodName, final int argCount) {
        for (final Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == argCount) {
                return m;
            }
        }
        throw new RuntimeException("Method " + methodName + " with " + argCount
            + " args not found in " + clazz.getName());
    }
}
