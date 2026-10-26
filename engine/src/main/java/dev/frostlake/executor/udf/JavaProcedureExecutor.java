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

import com.snowflake.snowpark_java.Session;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Procedure;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Executes inline Java stored procedures by compiling them at runtime.
 * The HANDLER clause specifies 'ClassName.methodName'; the method must accept
 * a {@link Session} as first argument followed by the procedure parameters.
 */
public final class JavaProcedureExecutor {

    /** Static helpers only — never instantiated. */
    private JavaProcedureExecutor() {
    }

    private static final JavaFunctionCompiler COMPILER = new JavaFunctionCompiler();

    public static Object executeJavaProcedure(final Procedure procedure, final List<Object> arguments,
                                              final DatabaseEngine engine) {
        final String handler = procedure.getHandler();
        if (handler == null || !handler.contains(".")) {
            throw new RuntimeException(
                "HANDLER must be specified as 'ClassName.methodName' for LANGUAGE JAVA procedures");
        }

        final int dot = handler.lastIndexOf('.');
        final String className = handler.substring(0, dot);
        final String methodName = handler.substring(dot + 1);

        final String body = procedure.getBody();

        try {
            final Class<?> compiledClass;
            if (!procedure.getImports().isEmpty()) {
                // Every CALL loads the jar's classes anew, as it loads an inline body's (live-verified for a
                // function's jar); a class the jar no longer holds is refused as the account's class loader refuses
                // one, as the procedure's CREATE would refuse it now (live-verified).
                try {
                    compiledClass = JarHandlerLoader.loadAnew(engine != null
                            ? engine.localImports(procedure.getImports()) : procedure.getImports(), className,
                        engine != null ? engine.getCatalog() : null,
                        engine != null ? engine.getS3PathResolver() : null);
                } catch (final HandlerClassNotFound missing) {
                    throw new RuntimeException(JavaHandlerFailure.missingClass(className,
                        JavaRuntimeLevel.declared(procedure.getRuntimeVersion()),
                        " in function " + procedure.getName() + " with handler " + handler), missing);
                }
            } else {
                // The body is compiled once - creating the procedure did it - and every CALL loads the classes
                // anew, so what a class's static fields hold is the CALL's own (live-verified).
                compiledClass = COMPILER.load(body, className, Thread.currentThread().getContextClassLoader(),
                    JavaRuntimeLevel.of(procedure.getRuntimeVersion()),
                    " in function " + procedure.getName() + " with handler " + handler);
            }
            return invokeHandler(compiledClass, methodName, arguments, procedure, engine,
                "OWNER".equalsIgnoreCase(procedure.getExecuteAs()));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("Failed to execute Java procedure " + procedure.getName() + ": " + e.getMessage(), e);
        }
    }

    private static Object invokeHandler(final Class<?> clazz, final String methodName,
                                        final List<Object> arguments, final Procedure procedure,
                                        final DatabaseEngine engine, final boolean ownersRights) throws Exception {
        final Session session = new Session(engine, ownersRights);

        // Build argument array: session + procedure params
        final Object[] args = new Object[arguments.size() + 1];
        args[0] = session;
        for (int i = 0; i < arguments.size(); i++) {
            args[i + 1] = arguments.get(i);
        }

        // The one public method of the handler's name taking the Session and the procedure's arguments, as its
        // CREATE judged it - refused as that CREATE would refuse it when the jar changed since (live-verified).
        final Method target = JavaHandlerMethods.resolve(clazz, methodName, args.length, procedure.getName(),
            procedure.getHandler());

        // The handler class does not have to be public — Snowflake accepts a package-private one, and a
        // body written as a bare "class H { … }" is the common shape in its own examples. Reflection from
        // this package cannot touch such a class's members without being told to. A static handler is
        // called on no instance; any other on one made as a function's handler's is.
        final String where = " in function " + procedure.getName() + " with handler " + procedure.getHandler();
        final Object instance = Modifier.isStatic(target.getModifiers()) ? null
            : JavaHandlerInstances.create(clazz, where);
        target.setAccessible(true);
        // Each argument as the class the handler declares for it; one the class cannot hold is refused before
        // the handler runs.
        final Object[] adapted = HandlerArguments.adapt(target.getParameterTypes(), args, procedure.getName(),
            procedure.getHandler());
        UdfConsoleCapture.enter();
        try {
            return target.invoke(instance, adapted);
        } catch (final InvocationTargetException e) {
            // What the handler raised reaches SQL as its report, as a function's handler's does (live-verified),
            // a statement it ran through its session that failed included.
            final Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException(JavaHandlerFailure.report(cause, clazz, where), cause);
        } finally {
            UdfConsoleCapture.exit();
        }
    }
}
