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

package dev.frostlake.rt.py;

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

/** The GraalPy-backed {@link UdfLanguageRuntime} provider for LANGUAGE PYTHON (UDF, UDTF, procedures). */
public final class PythonLanguageRuntime implements UdfLanguageRuntime {

    @Override
    public UdfLanguage language() {
        return UdfLanguage.PYTHON;
    }

    @Override
    public void configure(final EngineConfig config) {
        PythonRuntime.configureVenv(config.getPythonVenv());
    }

    @Override
    public Object executeFunction(final Function function, final List<Object> arguments,
                                  final Catalog catalog, final S3PathResolver s3PathResolver) {
        return PythonExecutor.executePythonFunction(function, arguments);
    }

    @Override
    public Object executeProcedure(final Procedure procedure, final List<Object> arguments, final DatabaseEngine engine) {
        return PythonProcedureExecutor.executePythonProcedure(procedure, arguments, engine);
    }

    @Override
    public ResultSet executeTableFunction(final Function function, final List<Object> arguments) {
        return PythonTableFunctionExecutor.executePythonTableFunction(function, arguments);
    }
}
