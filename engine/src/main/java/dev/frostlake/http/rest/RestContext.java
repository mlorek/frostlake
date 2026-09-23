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

package dev.frostlake.http.rest;

import dev.frostlake.ConcurrentDatabaseEngine;

/** What every call of the REST surface shares: the engine, and the store of results answered {@code 202}. */
public final class RestContext {

    private final ConcurrentDatabaseEngine engine;
    private final RestResults results;

    /**
     * @param engine the engine the statements run on
     * @param results the asynchronous result store
     */
    public RestContext(final ConcurrentDatabaseEngine engine, final RestResults results) {
        this.engine = engine;
        this.results = results;
    }

    /** The engine. */
    public ConcurrentDatabaseEngine engine() {
        return engine;
    }

    /** The asynchronous result store. */
    public RestResults results() {
        return results;
    }
}
