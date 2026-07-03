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

import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.UdfLanguage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JavaScriptExecutor {

    private static final Logger logger = LoggerFactory.getLogger(JavaScriptExecutor.class);
    private static final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();

    // A GraalVM JS context is thread-confined, so keep one engine PER THREAD (created once, reused across
    // that thread's per-row calls) plus a per-thread cache of compiled scripts — so a UDF body parses ONCE
    // instead of on every row. Re-creating the engine + re-eval'ing per row made JS UDFs ~1000x a built-in
    // (see UdfQueryPerformanceTest).
    private static final ThreadLocal<ScriptEngine> ENGINE = new ThreadLocal<>();
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

        final ScriptEngine engine = threadEngine();
        try {
            for (int i = 0; i < parameters.size(); i++) {
                engine.put(parameters.get(i).getName(), arguments.get(i));
            }
            final String wrappedCode = wrap(function.getBody().trim());
            final Object result = engine instanceof Compilable
                ? compiledScript(engine, wrappedCode).eval()
                : engine.eval(wrappedCode);
            logger.debug("JavaScript function {} executed successfully", function.getName());
            return result;
        } catch (final ScriptException e) {
            logger.error("Error executing JavaScript function: {}", function.getName(), e);
            throw new RuntimeException("Error executing JavaScript function "
                + function.getName() + ": " + e.getMessage(), e);
        }
    }

    private static ScriptEngine threadEngine() {
        ScriptEngine engine = ENGINE.get();
        if (engine == null) {
            engine = scriptEngineManager.getEngineByName("graal.js");
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
            ENGINE.set(engine);
        }
        return engine;
    }

    private static CompiledScript compiledScript(final ScriptEngine engine, final String code)
            throws ScriptException {
        final Map<String, CompiledScript> cache = SCRIPTS.get();
        CompiledScript cs = cache.get(code);
        if (cs == null) {
            cs = ((Compilable) engine).compile(code);
            cache.put(code, cs);
        }
        return cs;
    }

    private static String wrap(final String body) {
        if (body.startsWith("function")) {
            // The body IS a function expression — invoke it. (Must START with `function`; a body that merely
            // CONTAINS `function(` in an inner callback is a normal statement body, wrapped below.)
            return "(" + body + ")()";
        }
        if (body.startsWith("return ") && !body.contains(";") && !body.contains("\n")) {
            return "(function() { " + body + "; })()";
        }
        return "(function() {\n" + body + "\n})()";
    }
}
