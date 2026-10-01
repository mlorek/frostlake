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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    // reused across that thread's per-row calls) plus a per-thread cache of the invokers the handlers evaluate
    // to — so a UDF body parses ONCE instead of on every row. Re-creating the engine + re-eval'ing per row made JS UDFs ~1000x a
    // built-in (see UdfQueryPerformanceTest).
    private static final ThreadLocal<Map<String, ScriptEngine>> ENGINES =
        new ThreadLocal<Map<String, ScriptEngine>>() {
            @Override
            protected Map<String, ScriptEngine> initialValue() {
                return new HashMap<>();
            }
        };

    /** Per thread and engine, the invoker each handler's script evaluated to, so a body is compiled once. */
    private static final ThreadLocal<Map<String, Object>> INVOKERS =
        new ThreadLocal<Map<String, Object>>() {
            @Override
            protected Map<String, Object> initialValue() {
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
            if (logger.isDebugEnabled()) {
                for (int i = 0; i < parameters.size(); i++) {
                    final Object argVal = arguments.get(i);
                    final String preview = argVal == null ? "null" : argVal.toString();
                    logger.debug("JS arg {}={} ({})", parameters.get(i).getName(),
                        preview.length() > 150 ? preview.substring(0, 150) + "..." : preview,
                        argVal == null ? "-" : argVal.getClass().getName());
                }
            }
            final String source = JavaScriptHandler.source(function.getName(), parameters, function.getBody(), false);
            final Object result = JavaScriptHandler.invoke(engine, invoker(engine, zone.getId(), source), null,
                parameters, arguments);
            logger.debug("JavaScript function {} executed successfully", function.getName());
            return result;
        } catch (final ScriptException e) {
            logger.error("Error executing JavaScript function: {}", function.getName(), e);
            throw new RuntimeException(JavaScriptErrorText.of(e, function.getName(), function.getBody(),
                JavaScriptHandler.BODY_FIRST_LINE, "Error executing JavaScript function " + function.getName() + ": "
                    + e.getMessage()), e);
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException("Error executing JavaScript function " + function.getName() + ": "
                + e.getMessage(), e);
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

    /** The invoker a handler's script evaluates to in this engine, evaluated once. */
    private static Object invoker(final ScriptEngine engine, final String zone, final String source)
            throws ScriptException {
        final Map<String, Object> cache = INVOKERS.get();
        // An invoker belongs to the engine that evaluated it, and there is one engine per zone.
        final String key = zone + '\n' + source;
        Object invoker = cache.get(key);
        if (invoker == null) {
            invoker = engine.eval(source);
            cache.put(key, invoker);
        }
        return invoker;
    }
}
