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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.config.S3PathResolver;
import dev.frostlake.executor.udf.JavaHandlerFailure;
import dev.frostlake.executor.udf.JavaTableFunctions;
import dev.frostlake.executor.udf.TablePartitionRows;
import dev.frostlake.executor.udf.UdfLanguageRuntime;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.storage.ResultSet;

import java.lang.reflect.Method;
import java.util.List;

/** The in-process-scala-compiler-backed {@link UdfLanguageRuntime} provider for LANGUAGE SCALA. */
public final class ScalaRuntime implements UdfLanguageRuntime {

    @Override
    public UdfLanguage language() {
        return UdfLanguage.SCALA;
    }

    @Override
    public void configure(final EngineConfig config) {
        // No Scala-specific engine configuration.
    }

    @Override
    public Object executeFunction(final Function function, final List<Object> arguments,
                                  final Catalog catalog, final S3PathResolver s3PathResolver) {
        return ScalaFunctionExecutor.executeScalaFunction(function, arguments, catalog, s3PathResolver);
    }

    @Override
    public Object executeProcedure(final Procedure procedure, final List<Object> arguments, final DatabaseEngine engine) {
        return ScalaProcedureExecutor.executeScalaProcedure(procedure, arguments, engine);
    }

    @Override
    public ResultSet executeTableFunction(final Function function, final List<Object> arguments) {
        throw new RuntimeException("Scala table functions are not supported");
    }

    /**
     * Scala is compiled at CREATE, functions and procedures alike — live-verified, a body that is not
     * Scala is refused by the CREATE statement rather than by the first call. The compile is cached,
     * so the class the first call loads is the one built here.
     */
    @Override
    public void compileFunction(final Function function) {
        if (function.isTableFunction()) {
            // A table function's handler is a class, held to the rules of a Java one's; one that keeps them is
            // refused for its language, so no Scala table function is ever created (live-verified).
            JavaTableFunctions.check(function, tableHandler(function));
            throw new RuntimeException("Table functions not supported for language 'SCALA'.");
        }
        compileBody(function.getBody(), function.getHandler(), function.getName(),
            function.getParameters() == null ? 0 : function.getParameters().size());
    }

    @Override
    public TablePartitionRows executeTablePartition(final Function function, final List<List<Object>> rows) {
        return JavaTableFunctions.run(function, tableHandler(function), rows);
    }

    /**
     * The class a table function's HANDLER names, compiled from the body: the class of that name itself, whose
     * methods an {@code object} of the name gives it as static ones, never the object's own class.
     */
    private static Class<?> tableHandler(final Function function) {
        final String where = " in function " + function.getName() + " with handler " + function.getHandler();
        try {
            final Class<?> compiled = ScalaCompiler.compileCached(function.getBody(), function.getHandler(), where);
            return compiled.getName().endsWith("$")
                ? compiled.getClassLoader().loadClass(function.getHandler()) : compiled;
        } catch (final ClassNotFoundException missing) {
            throw new RuntimeException(JavaHandlerFailure.missingClass(function.getHandler(), ScalaCompiler.JAVA_LEVEL,
                where), missing);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("Failed to compile Scala routine " + function.getName() + ": "
                + e.getMessage(), e);
        }
    }

    @Override
    public void compileProcedure(final Procedure procedure) {
        // A procedure's handler takes a leading Session the declared parameter list does not mention.
        compileBody(procedure.getBody(), procedure.getHandler(), procedure.getName(),
            procedure.getParameters().size() + 1);
    }

    /**
     * Compiles a body and confirms its handler is in it: a class of that name, reported as the class loader
     * reports one it cannot find, and a method of that name taking {@code arity} arguments, refused when the
     * routine is created otherwise (live-verified).
     */
    private void compileBody(final String body, final String handler, final String routineName, final int arity) {
        final String where = " in function " + routineName + " with handler " + handler;
        final Class<?> compiled;
        try {
            compiled = ScalaCompiler.compileCached(body, handlerClass(handler), where);
        } catch (final ClassNotFoundException missing) {
            throw new RuntimeException(JavaHandlerFailure.missingClass(handlerClass(handler),
                ScalaCompiler.JAVA_LEVEL, where), missing);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("Failed to compile Scala routine " + routineName + ": "
                + e.getMessage(), e);
        }
        final String methodName = handler.substring(handler.lastIndexOf('.') + 1);
        for (final Method method : compiled.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == arity) {
                return;
            }
        }
        throw new RuntimeException("Failed to find a public method named \"" + methodName + "\" with " + arity
            + " arguments" + where);
    }

    private String handlerClass(final String handler) {
        final int dot = handler == null ? -1 : handler.lastIndexOf('.');
        if (dot < 0) {
            throw new RuntimeException(
                "HANDLER must be 'ClassName.methodName' for LANGUAGE SCALA routines");
        }
        return handler.substring(0, dot);
    }
}
