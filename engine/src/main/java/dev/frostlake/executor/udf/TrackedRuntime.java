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
import dev.frostlake.executor.StatementCancellation;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.storage.ResultSet;

import java.util.List;

/**
 * A discovered runtime as the engine calls it: each handler it runs is counted in {@link HandlerLanguages} for as
 * long as it runs, and everything else is the runtime's own. A handler whose statement is over - canceled, timed out
 * or its session aborted - fails as the statement does, whether it failed or answered: the account ends the
 * statement whatever the handler does meanwhile (live-verified), and a guest the statement's watch stopped (see
 * GuestWatch) fails in its own runtime's words, which are no answer of the statement's.
 */
final class TrackedRuntime implements UdfLanguageRuntime {

    private final UdfLanguageRuntime runtime;

    TrackedRuntime(final UdfLanguageRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public UdfLanguage language() {
        return runtime.language();
    }

    @Override
    public void configure(final EngineConfig config) {
        runtime.configure(config);
    }

    @Override
    public Object executeFunction(final Function function, final List<Object> arguments, final Catalog catalog,
                                  final S3PathResolver s3PathResolver) {
        HandlerLanguages.enter(language());
        final Object answer;
        try {
            answer = runtime.executeFunction(function, arguments, catalog, s3PathResolver);
        } catch (final RuntimeException failed) {
            StatementCancellation.check();
            throw failed;
        } finally {
            HandlerLanguages.exit(language());
        }
        StatementCancellation.check();
        return answer;
    }

    @Override
    public Object executeProcedure(final Procedure procedure, final List<Object> arguments,
                                   final DatabaseEngine engine) {
        HandlerLanguages.enter(language());
        final Object answer;
        try {
            answer = runtime.executeProcedure(procedure, arguments, engine);
        } catch (final RuntimeException failed) {
            StatementCancellation.check();
            throw failed;
        } finally {
            HandlerLanguages.exit(language());
        }
        StatementCancellation.check();
        return answer;
    }

    @Override
    public ResultSet executeTableFunction(final Function function, final List<Object> arguments) {
        HandlerLanguages.enter(language());
        final ResultSet answer;
        try {
            answer = runtime.executeTableFunction(function, arguments);
        } catch (final RuntimeException failed) {
            StatementCancellation.check();
            throw failed;
        } finally {
            HandlerLanguages.exit(language());
        }
        StatementCancellation.check();
        return answer;
    }

    @Override
    public TablePartitionRows executeTablePartition(final Function function, final List<List<Object>> rows) {
        HandlerLanguages.enter(language());
        final TablePartitionRows answer;
        try {
            answer = runtime.executeTablePartition(function, rows);
        } catch (final RuntimeException failed) {
            StatementCancellation.check();
            throw failed;
        } finally {
            HandlerLanguages.exit(language());
        }
        StatementCancellation.check();
        return answer;
    }

    @Override
    public Object executeAggregate(final Function function, final List<List<Object>> rows) {
        HandlerLanguages.enter(language());
        final Object answer;
        try {
            answer = runtime.executeAggregate(function, rows);
        } catch (final RuntimeException failed) {
            StatementCancellation.check();
            throw failed;
        } finally {
            HandlerLanguages.exit(language());
        }
        StatementCancellation.check();
        return answer;
    }

    @Override
    public void compileFunction(final Function function) {
        runtime.compileFunction(function);
    }

    @Override
    public void compileProcedure(final Procedure procedure) {
        runtime.compileProcedure(procedure);
    }

    @Override
    public void statementEnded() {
        runtime.statementEnded();
    }
}
