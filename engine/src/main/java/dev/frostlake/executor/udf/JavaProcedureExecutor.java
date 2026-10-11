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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
                compiledClass = JarHandlerLoader.load(engine != null
                        ? engine.localImports(procedure.getImports()) : procedure.getImports(), className,
                    engine != null ? engine.getCatalog() : null,
                    engine != null ? engine.getS3PathResolver() : null);
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

        // Find matching method: first parameter must be Session
        final Method target = findMethod(clazz, methodName, args.length);

        // The handler class does not have to be public — Snowflake accepts a package-private one, and a
        // body written as a bare "class H { … }" is the common shape in its own examples. Reflection from
        // this package cannot touch such a class's members without being told to.
        final Constructor<?> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        final Object instance = constructor.newInstance();
        target.setAccessible(true);
        // Each argument as the class the handler declares for it; one the class cannot hold is refused before
        // the handler runs.
        final Object[] adapted = HandlerArguments.adapt(target.getParameterTypes(), args, procedure.getName(),
            procedure.getHandler());
        UdfConsoleCapture.enter();
        try {
            return target.invoke(instance, adapted);
        } catch (final InvocationTargetException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause != null) {
                throw new RuntimeException(cause.getMessage(), cause);
            }
            throw e;
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
            + " parameters not found in class " + clazz.getName());
    }
}
