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

import dev.frostlake.config.EngineConfig;

/**
 * The engine whose statement runs on this thread, by its configuration: what a language runtime keeps for each
 * engine, such as the interpreter {@code python.runtime} chooses, is found by it. An engine binds itself for every
 * statement it runs, a task's statements included, and a statement another engine runs inside one binds that
 * engine until it ends.
 */
public final class RunningEngine {

    private static final ThreadLocal<EngineConfig> CONFIG = new ThreadLocal<EngineConfig>();

    private RunningEngine() {
    }

    /**
     * Bind the engine about to run a statement on this thread.
     *
     * @param config the engine's configuration
     * @return what was bound before, to be put back with {@link #leave}
     */
    public static EngineConfig enter(final EngineConfig config) {
        final EngineConfig displaced = CONFIG.get();
        CONFIG.set(config);
        return displaced;
    }

    /**
     * Put back what {@link #enter} displaced.
     *
     * @param displaced what {@link #enter} returned, or null when no engine was bound
     */
    public static void leave(final EngineConfig displaced) {
        if (displaced == null) {
            CONFIG.remove();
        } else {
            CONFIG.set(displaced);
        }
    }

    /** The configuration of the engine running a statement on this thread, or null when none is. */
    public static EngineConfig config() {
        return CONFIG.get();
    }
}
