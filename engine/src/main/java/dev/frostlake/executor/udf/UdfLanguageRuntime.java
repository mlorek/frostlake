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
}
