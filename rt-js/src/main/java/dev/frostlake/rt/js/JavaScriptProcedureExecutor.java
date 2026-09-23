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
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.UdfLanguage;
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
            final Object invoker = scriptEngine.eval(
                JavaScriptHandler.source(procedure.getName(), parameters, procedure.getBody(), true));
            final Object result = JavaScriptHandler.invoke(scriptEngine, invoker, new SnowflakeAPIWrapper(engine),
                parameters, arguments);
            logger.debug("JavaScript procedure {} executed successfully", procedure.getName());
            return result;
        } catch (final ScriptException e) {
            logger.error("Error executing JavaScript procedure: {}", procedure.getName(), e);
            throw new RuntimeException(JavaScriptErrorText.of(e, procedure.getName(), procedure.getBody(),
                JavaScriptHandler.BODY_FIRST_LINE, "Error executing JavaScript procedure " + procedure.getName() + ": "
                    + e.getMessage()), e);
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException("Error executing JavaScript procedure " + procedure.getName() + ": "
                + e.getMessage(), e);
        }
    }
}
