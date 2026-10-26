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

import java.util.concurrent.atomic.AtomicLong;

/**
 * The module a call site of a Python function keeps for the statement that runs it (see StatementHandlerStates).
 * An account imports a function's module once for a call of it and calls its handler for every row the call reaches,
 * so what the module's code set up - a counter, a cache, a loaded model - is there for the next row; each call of a
 * function in a query keeps its own, and none outlives the query (live-verified). Closing the state forgets the
 * module.
 */
final class PythonModuleState implements AutoCloseable {

    /** Tells the keys of the module states made in this JVM apart. */
    private static final AtomicLong SEQUENCE = new AtomicLong();

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

    @Override
    public void close() {
        backend.forgetModule(key);
    }
}
