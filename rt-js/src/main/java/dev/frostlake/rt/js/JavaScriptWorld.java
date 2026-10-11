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

package dev.frostlake.rt.js;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * The world one call site of a JavaScript function runs in while one statement runs: a script engine of its own
 * and the invokers evaluated in it. On an account the globals a function's handler sets are there for the later
 * rows of the statement and for nothing else (live-verified) — not for another function, another call of the same
 * function written in the statement, a later statement or another session — so a world is made for one call site
 * of one statement and closed with the statement.
 */
final class JavaScriptWorld implements AutoCloseable {

    private static final ScriptEngineManager MANAGER = new ScriptEngineManager();

    private final ScriptEngine engine;

    /** The invoker each handler's script evaluated to here, so a body is compiled once for the statement's rows. */
    private final Map<String, Object> invokers = new HashMap<>();

    /** The script of the function this world was made for, written once for the statement's rows. */
    private String source;

    JavaScriptWorld(final ZoneId zone) {
        // Via the shared engine so Truffle logging + warning options apply (see GraalJsEngine).
        ScriptEngine made = GraalJsEngine.newScriptEngine(zone);
        if (made == null) {
            made = MANAGER.getEngineByName("nashorn");
        }
        if (made == null) {
            made = MANAGER.getEngineByName("javascript");
        }
        if (made == null) {
            throw new RuntimeException("No JavaScript engine available. Please ensure GraalVM "
                + "JavaScript or a compatible engine is installed.");
        }
        this.engine = made;
    }

    ScriptEngine engine() {
        return engine;
    }

    /** The script kept by {@link #keep}, or null before the first call. */
    String source() {
        return source;
    }

    void keep(final String written) {
        this.source = written;
    }

    /** The invoker a handler's script evaluates to in this world, evaluated once. */
    Object invoker(final String source) throws ScriptException {
        Object invoker = invokers.get(source);
        if (invoker == null) {
            invoker = engine.eval(source);
            invokers.put(source, invoker);
        }
        return invoker;
    }

    @Override
    public void close() {
        invokers.clear();
        if (engine instanceof AutoCloseable) {
            try {
                ((AutoCloseable) engine).close();
            } catch (final Exception alreadyClosed) {
                // A world that will not close is left to the collector.
            }
        }
    }
}
