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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.executor.SessionZone;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.TypeCategory;
import dev.frostlake.values.VariantValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public final class JavaScriptProcedureExecutor {

    /** Static helpers only — never instantiated. */
    private JavaScriptProcedureExecutor() {
    }

    static {
        // GraalVM's polyglot and Truffle artifacts can resolve to mismatched patch versions on a downstream
        // classpath (a host application that also depends on GraalVM), which trips the strict engine version
        // check and fails all JavaScript execution. Patch-level differences are compatible, so disable the
        // check before the polyglot engine first initializes (this static block runs before the
        // ScriptEngineManager below, and well before graal.js is requested).
        if (System.getProperty("polyglotimpl.DisableVersionChecks") == null) {
            System.setProperty("polyglotimpl.DisableVersionChecks", "true");
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(JavaScriptProcedureExecutor.class);
    private static final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();

    public static Object executeJavaScriptProcedure(final Procedure procedure, final List<Object> arguments, final DatabaseEngine engine) {
        if (procedure.getUdfLanguage() != UdfLanguage.JAVASCRIPT) {
            throw new RuntimeException("Procedure is not a JavaScript procedure");
        }

        ScriptEngine scriptEngine = null;

        try {
            // Via the shared engine so Truffle logging + warning options apply; host access and
            // class lookup are already fixed in its context configuration (see GraalJsEngine).
            scriptEngine = GraalJsEngine.newScriptEngine(SessionZone.current());
        } catch (final Exception e) {
            logger.debug("GraalVM JavaScript not available: {}", e.getMessage());
        }

        if (scriptEngine == null) {
            scriptEngine = scriptEngineManager.getEngineByName("nashorn");
        }
        if (scriptEngine == null) {
            scriptEngine = scriptEngineManager.getEngineByName("javascript");
        }
        if (scriptEngine == null) {
            throw new RuntimeException("No JavaScript engine available. Please ensure GraalVM JavaScript or a compatible engine is installed.");
        }

        try {
            final List<Parameter> parameters = procedure.getParameters();
            if (parameters.size() != arguments.size()) {
                throw new RuntimeException("Procedure " + procedure.getName() + " expects " +
                    parameters.size() + " arguments but got " + arguments.size());
            }

            for (int i = 0; i < parameters.size(); i++) {
                final String paramName = parameters.get(i).getName();
                final Object value = arguments.get(i);
                scriptEngine.put(paramName, value);
                reparseSemiStructured(scriptEngine, parameters.get(i), paramName, value);
            }

            final SnowflakeAPIWrapper apiWrapper = new SnowflakeAPIWrapper(engine);
            scriptEngine.put("snowflake", apiWrapper);

            // The body keeps its own text and starts the third line, so a failure's line and column are the body's.
            final String body = procedure.getBody();
            // A JS object/array return value must come back as JSON text so it round-trips as a Frostlake
            // OBJECT/ARRAY (a raw JS Value stringifies to "{a: 1}", which isn't valid JSON and breaks
            // downstream variant-path access). Scalars (string/number/boolean) and null pass through.
            final String wrappedCode = "(function() {\n"
                + "  var __result = (function() {\n" + body + "\n  })();\n"
                + "  return (__result !== null && __result !== undefined && typeof __result === 'object')\n"
                + "      ? JSON.stringify(__result) : __result;\n"
                + "})()";

            final Object result = scriptEngine.eval(wrappedCode);

            logger.debug("JavaScript procedure {} executed successfully", procedure.getName());
            return result;
        } catch (final ScriptException e) {
            logger.error("Error executing JavaScript procedure: {}", procedure.getName(), e);
            throw new RuntimeException(JavaScriptErrorText.of(e, procedure.getName(), procedure.getBody(), 3,
                "Error executing JavaScript procedure " + procedure.getName() + ": " + e.getMessage()), e);
        }
    }

    /**
     * Snowflake exposes OBJECT / VARIANT / ARRAY arguments to JavaScript as native JS values, not JSON text.
     * Frostlake carries them as JSON strings, so when a semi-structured parameter was bound as a string,
     * reparse it in place into a JS object/array — so a body can do {@code so.prop = x}, {@code arr.push(…)},
     * read {@code so.field}, etc. Non-JSON strings (or non-string values) are left untouched.
     */
    static void reparseSemiStructured(final ScriptEngine engine, final Parameter param, final String name, final Object value) {
        final DataType dt = param.getDataType();
        if (dt == null || dt.getCategory() != TypeCategory.SEMI_STRUCTURED) {
            return;
        }
        if (value instanceof String) {
            try {
                engine.eval(name + " = JSON.parse(" + name + ");");
            } catch (final ScriptException nonJson) {
                logger.debug("semi-structured JS parameter {} was not valid JSON; left as string", name);
            }
        } else if (value instanceof VariantValue) {
            // A semi-structured runtime value carries its canonical JSON text — parse THAT, not the
            // display form, where a variant string shows its content unquoted and an XML variant shows
            // as XML; JSON.parse can read neither.
            final String holder = "__frostlake_json_" + name;
            try {
                engine.put(holder, ((VariantValue) value).text());
                engine.eval(name + " = JSON.parse(" + holder + "); " + holder + " = undefined;");
            } catch (final ScriptException e) {
                logger.debug("semi-structured JS parameter {} could not be parsed; left as text", name);
            }
        } else if (value != null) {
            // A host container (List/Map/JsonNode/...) has no JS Array/Object protocol (no forEach,
            // Array.isArray false), so a body like `SRC.forEach(...)` threw TypeError when a variant path
            // handed the argument over as a Java value instead of JSON text. Round-trip anything non-null
            // through JSON for native JS values; numbers and booleans survive the trip unchanged.
            final String holder = "__frostlake_json_" + name;
            try {
                engine.put(holder, ArrayFunctionHelper.MAPPER.writeValueAsString(value));
                engine.eval(name + " = JSON.parse(" + holder + "); " + holder + " = undefined;");
            } catch (final ScriptException | RuntimeException e) {
                logger.debug("semi-structured JS parameter {} of type {} could not be converted to JSON; left as host object",
                    name, value.getClass().getName());
            }
        }
        // Snowflake's implicit conversion for an ARRAY-declared parameter follows TO_ARRAY: a non-array
        // value arrives wrapped as a one-element array (vendor code passes an OBJECT to `SRC ARRAY` and
        // indexes the result with ::array[0]). NULL stays NULL.
        if (value != null && dt instanceof ArrayType) {
            try {
                engine.eval("if (" + name + " !== null && " + name + " !== undefined && !Array.isArray(" + name + ")) { "
                    + name + " = [" + name + "]; }");
            } catch (final ScriptException ignored) {
                logger.debug("semi-structured JS parameter {} could not be array-wrapped", name);
            }
        }
    }

}
