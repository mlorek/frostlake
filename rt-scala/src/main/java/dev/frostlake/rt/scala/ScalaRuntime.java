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
import dev.frostlake.executor.udf.UdfLanguageRuntime;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.storage.ResultSet;

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
}
