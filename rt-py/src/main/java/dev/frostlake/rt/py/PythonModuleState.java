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

import dev.frostlake.executor.udf.HandlerStateFactory;
import dev.frostlake.executor.udf.StatementHandlerStates;
import dev.frostlake.metastore.model.Function;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The module a call site of a Python function keeps for the statement that runs it (see StatementHandlerStates).
 * An account imports a function's module once for a call of it and calls its handler for every row the call reaches,
 * so what the module's code set up - a counter, a cache, a loaded model - is there for the next row; each call of a
 * function in a query keeps its own, and none outlives the query (live-verified). A table function's partitions and
 * an aggregate's groups are read by that one module too, each by an instance of the handler class of its own.
 * Closing the state forgets the module.
 */
final class PythonModuleState implements AutoCloseable {

    /** Tells the keys of the module states made in this JVM apart. */
    private static final AtomicLong SEQUENCE = new AtomicLong();

    /** What a call site's kept module is told apart by among the states the site keeps. */
    private static final String KIND = "python-module";

    /** The backend the module lives in. */
    private final PythonBackend backend;

    /** The key the backend keeps the module under. */
    private final String key;

    /**
     * @param backend the backend the module lives in
     */
    PythonModuleState(final PythonBackend backend) {
        this.backend = backend;
        this.key = "__fl_kept_module_" + SEQUENCE.incrementAndGet();
    }

    /** The key the backend keeps the module under. */
    String key() {
        return key;
    }

    /**
     * The key of the module the running statement keeps for the call of a function being evaluated, made on the
     * call's first use.
     *
     * @param function the function
     * @return the key, or null outside every statement, where a call has a module of its own
     */
    static String keptFor(final Function function) {
        final AutoCloseable kept = StatementHandlerStates.state(function, KIND, new HandlerStateFactory() {
            @Override
            public AutoCloseable create() {
                return new PythonModuleState(PythonInterpreter.running());
            }
        });
        return kept instanceof PythonModuleState ? ((PythonModuleState) kept).key() : null;
    }

    @Override
    public void close() {
        backend.forgetModule(key);
    }
}
