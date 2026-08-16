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

import dev.frostlake.executor.SessionZone;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.values.BinaryValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public final class JavaScriptExecutor {

    /** Static helpers only — never instantiated. */
    private JavaScriptExecutor() {
    }

    private static final Logger logger = LoggerFactory.getLogger(JavaScriptExecutor.class);
    private static final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();

    // A GraalVM JS context is thread-confined, so keep one engine PER THREAD and session zone (created once,
    // reused across that thread's per-row calls) plus a per-thread cache of compiled scripts — so a UDF body
    // parses ONCE instead of on every row. Re-creating the engine + re-eval'ing per row made JS UDFs ~1000x a
    // built-in (see UdfQueryPerformanceTest).
    private static final ThreadLocal<Map<String, ScriptEngine>> ENGINES =
        new ThreadLocal<Map<String, ScriptEngine>>() {
            @Override
            protected Map<String, ScriptEngine> initialValue() {
                return new HashMap<>();
            }
        };

    /** The line of the evaluated source that holds the body's first line (see {@link #wrap}). */
    private static final int BODY_FIRST_LINE = 2;
    private static final ThreadLocal<Map<String, CompiledScript>> SCRIPTS =
        new ThreadLocal<Map<String, CompiledScript>>() {
            @Override
            protected Map<String, CompiledScript> initialValue() {
                return new HashMap<>();
            }
        };

    public static Object executeJavaScriptFunction(final Function function, final List<Object> arguments) {
        if (function.getUdfLanguage() != UdfLanguage.JAVASCRIPT) {
            throw new RuntimeException("Function is not a JavaScript function");
        }
        final List<Parameter> parameters = function.getParameters();
        if (parameters.size() != arguments.size()) {
            throw new RuntimeException("Function " + function.getName() + " expects "
                + parameters.size() + " arguments but got " + arguments.size());
        }

        final ZoneId zone = SessionZone.current();
        final ScriptEngine engine = threadEngine(zone);
        try {
            for (int i = 0; i < parameters.size(); i++) {
                // Each argument is exposed under its canonical name only: A for an unquoted a, x for a quoted
                // "x". On the account a body that spells an unquoted name in lower case fails with a
                // ReferenceError (live-verified).
                final String paramName = parameters.get(i).getName();
                final Object argVal = arguments.get(i) instanceof BinaryValue
                    ? ((BinaryValue) arguments.get(i)).bytes()   // BINARY surfaces as a JS byte array
                    : arguments.get(i);
                if (logger.isDebugEnabled()) {
                    final String preview = argVal == null ? "null" : argVal.toString();
                    logger.debug("JS arg {}={} ({})", paramName,
                        preview.length() > 150 ? preview.substring(0, 150) + "..." : preview,
                        argVal == null ? "-" : argVal.getClass().getName());
                }
                engine.put(paramName, argVal);
                // OBJECT / VARIANT / ARRAY args arrive as JSON text; expose them as native JS values.
                JavaScriptProcedureExecutor.reparseSemiStructured(engine, parameters.get(i), paramName, argVal);
            }
            final String wrappedCode = wrap(function.getBody());
            final Object result = engine instanceof Compilable
                ? compiledScript(engine, zone.getId(), wrappedCode).eval()
                : engine.eval(wrappedCode);
            logger.debug("JavaScript function {} executed successfully", function.getName());
            return result;
        } catch (final ScriptException e) {
            logger.error("Error executing JavaScript function: {}", function.getName(), e);
            throw new RuntimeException(JavaScriptErrorText.of(e, function.getName(), function.getBody(),
                BODY_FIRST_LINE, "Error executing JavaScript function " + function.getName() + ": " + e.getMessage()), e);
        } finally {
            // The arguments are bound for this call only: a later call must not read them, as it could not
            // on the account, where they are the handler's own parameters.
            final Bindings bound = engine.getBindings(ScriptContext.ENGINE_SCOPE);
            for (final Parameter parameter : parameters) {
                bound.remove(parameter.getName());
            }
        }
    }

    private static ScriptEngine threadEngine(final ZoneId zone) {
        final Map<String, ScriptEngine> engines = ENGINES.get();
        ScriptEngine engine = engines.get(zone.getId());
        if (engine == null) {
            // Via the shared engine so Truffle logging + warning options apply (see GraalJsEngine).
            engine = GraalJsEngine.newScriptEngine(zone);
            if (engine == null) {
                engine = scriptEngineManager.getEngineByName("nashorn");
            }
            if (engine == null) {
                engine = scriptEngineManager.getEngineByName("javascript");
            }
            if (engine == null) {
                throw new RuntimeException("No JavaScript engine available. Please ensure GraalVM "
                    + "JavaScript or a compatible engine is installed.");
            }
            engines.put(zone.getId(), engine);
        }
        return engine;
    }

    private static CompiledScript compiledScript(final ScriptEngine engine, final String zone, final String code)
            throws ScriptException {
        final Map<String, CompiledScript> cache = SCRIPTS.get();
        // A compiled script belongs to the engine that compiled it, and there is one engine per zone.
        final String key = zone + '\n' + code;
        CompiledScript cs = cache.get(key);
        if (cs == null) {
            cs = ((Compilable) engine).compile(code);
            cache.put(key, cs);
        }
        return cs;
    }

    private static String wrap(final String body) {
        // The body keeps its own text and starts a line of its own, so a failure's line and column are the
        // body's (see BODY_FIRST_LINE).
        // A body is always a function's statements, as on the account: one that opens with a function
        // declaration declares it and runs on (live-verified), never runs the declared function itself.
        final String invoked = "(function() {\n" + body + "\n})()";
        // A JS object/array return must come back as JSON text so it round-trips as a Frostlake OBJECT/ARRAY
        // (a raw JS Value stringifies to "{a: 1}", not valid JSON). Scalars and null pass through unchanged.
        return "(function() { var __r = (" + invoked + ");\n"
            + "  return (__r !== null && __r !== undefined && typeof __r === 'object') ? JSON.stringify(__r) : __r; })()";
    }
}
