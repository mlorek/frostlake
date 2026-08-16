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

import com.snowflake.snowpark_java.Session;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.executor.udf.JarHandlerLoader;
import dev.frostlake.executor.udf.UdfConsoleCapture;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Executes inline Scala stored procedures by compiling them at runtime via scalac.
 *
 * The HANDLER clause specifies 'ClassName.methodName'; the method must accept
 * a {@link Session} as first argument followed by the procedure parameters.
 *
 * Requires scalac to be available on PATH, or the scala.home system property set.
 */
public final class ScalaProcedureExecutor {

    /** Static helpers only — never instantiated. */
    private ScalaProcedureExecutor() {
    }

    private static final Logger logger = LoggerFactory.getLogger(ScalaProcedureExecutor.class);

    public static Object executeScalaProcedure(final Procedure procedure,
                                                final List<Object> arguments,
                                                final DatabaseEngine engine) {
        final String handler = procedure.getHandler();
        if (handler == null || !handler.contains(".")) {
            throw new RuntimeException(
                "HANDLER must be 'ClassName.methodName' for LANGUAGE SCALA procedures");
        }

        final int dot = handler.lastIndexOf('.');
        final String className = handler.substring(0, dot);
        final String methodName = handler.substring(dot + 1);

        try {
            // Inline bodies compile ONCE (cached); jar handlers load from IMPORTS. Avoids per-row recompiles.
            final Class<?> compiled = procedure.getImports().isEmpty()
                ? ScalaCompiler.compileCached(procedure.getBody(), className)
                : JarHandlerLoader.load(procedure.getImports(), className,
                    engine != null ? engine.getCatalog() : null,
                    engine != null ? engine.getS3PathResolver() : null);
            return invokeHandler(compiled, methodName, arguments, procedure.getParameters(), engine,
                "OWNER".equalsIgnoreCase(procedure.getExecuteAs()));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("Failed to execute Scala procedure "
                + procedure.getName() + ": " + e.getMessage(), e);
        }
    }

    private static Object invokeHandler(final Class<?> clazz, final String methodName,
                                         final List<Object> arguments,
                                         final List<Parameter> parameters,
                                         final DatabaseEngine engine, final boolean ownersRights) throws Exception {
        final Session session = new Session(engine, ownersRights);

        final Object[] args = new Object[arguments.size() + 1];
        args[0] = session;
        for (int i = 0; i < arguments.size(); i++) args[i + 1] = arguments.get(i);

        // Scala objects have a MODULE$ singleton field
        Object instance;
        try {
            final Field moduleField = clazz.getField("MODULE$");
            instance = moduleField.get(null);
        } catch (final NoSuchFieldException e) {
            instance = clazz.getDeclaredConstructor().newInstance();
        }

        final Method target = findMethod(clazz, methodName, args.length);
        UdfConsoleCapture.enter();
        try {
            return target.invoke(instance, args);
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

    private static Method findMethod(final Class<?> clazz, final String methodName,
                                      final int argCount) {
        for (final Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == argCount) {
                return m;
            }
        }
        throw new RuntimeException("Method " + methodName + " with " + argCount
            + " args not found in " + clazz.getName());
    }
}
