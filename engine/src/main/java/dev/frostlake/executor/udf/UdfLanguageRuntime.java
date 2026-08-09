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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.config.S3PathResolver;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.storage.ResultSet;

import java.util.List;

/**
 * Executes UDFs and stored procedures of one {@link UdfLanguage}. Runtimes are discovered as a
 * {@link java.util.ServiceLoader} SPI (see {@link UdfRuntimes}): Java ships in the engine, while the
 * heavyweight languages live in optional modules — {@code frostlake-rt-js} (GraalJS JavaScript),
 * {@code frostlake-rt-py} (GraalPy Python) and {@code frostlake-rt-scala} (in-process scala-compiler)
 * — and light up only when their module is on the classpath, so the core engine carries no GraalVM
 * language or Scala compiler dependencies. Adding a language is "implement this interface and
 * register the provider in {@code META-INF/services/dev.frostlake.executor.udf.UdfLanguageRuntime}".
 */
public interface UdfLanguageRuntime {

    /** The language this runtime executes. */
    UdfLanguage language();

    /**
     * Engine-construction hook: receives every new engine's configuration (e.g. {@code python.venv}).
     * Called once per {@link DatabaseEngine} instance for every discovered runtime — implementations
     * with JVM-wide state must be idempotent; runtimes without configuration make this a no-op.
     */
    void configure(EngineConfig config);

    /**
     * Evaluate a scalar UDF call and return its SQL value. The catalog and S3 resolver (either may be
     * null) let runtimes whose handlers can live in a JAR resolve {@code IMPORTS} stage references —
     * runtimes that only execute inline bodies ignore them.
     */
    Object executeFunction(Function function, List<Object> arguments, Catalog catalog, S3PathResolver s3PathResolver);

    /** Execute a stored procedure call and return its result value. */
    Object executeProcedure(Procedure procedure, List<Object> arguments, DatabaseEngine engine);

    /** Execute a table function (UDTF) call; languages without table functions throw. */
    ResultSet executeTableFunction(Function function, List<Object> arguments);

    /**
     * Compile a FUNCTION's body at CREATE time, throwing if the routine could never run — live-verified,
     * a Python, Java or Scala function whose body will not compile, or whose HANDLER is not in it, is
     * refused by CREATE rather than by the first call.
     *
     * <p>The default is a deliberate no-op, and it is what JavaScript wants: live compiles no JavaScript
     * body at all, so {@code AS '}{@code '} is created happily and only fails when called. It is also
     * the honest answer for a language whose module is absent — an engine that cannot judge a body must
     * accept it, exactly as the SQL-body check fails open on a construct it cannot parse.
     */
    default void compileFunction(final Function function) {
    }

    /**
     * Compile a PROCEDURE's body at CREATE time, as {@link #compileFunction} does for a function.
     *
     * <p>Not simply the same rule: a PYTHON procedure is NOT compiled at CREATE though a Python function
     * is — measured both ways on the same account — so this defaults to a no-op separately rather than
     * sharing the function's implementation.
     */
    default void compileProcedure(final Procedure procedure) {
    }
}
