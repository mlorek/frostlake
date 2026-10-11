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
import dev.frostlake.executor.udf.HandlerStateFactory;
import dev.frostlake.executor.udf.StatementHandlerStates;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.TypeCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.util.List;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

public final class JavaScriptExecutor {

    /** Static helpers only — never instantiated. */
    private JavaScriptExecutor() {
    }

    private static final Logger logger = LoggerFactory.getLogger(JavaScriptExecutor.class);

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
        // The world of this call site for the statement that is running; a call outside any statement runs in a
        // world of its own, which ends with the call.
        final AutoCloseable kept = StatementHandlerStates.state(function, zone.getId(), new HandlerStateFactory() {
            @Override
            public AutoCloseable create() {
                return new JavaScriptWorld(zone);
            }
        });
        final JavaScriptWorld world = kept != null ? (JavaScriptWorld) kept : new JavaScriptWorld(zone);
        try {
            return call(function, arguments, world);
        } finally {
            if (kept == null) {
                world.close();
            }
        }
    }

    private static Object call(final Function function, final List<Object> arguments, final JavaScriptWorld world) {
        final List<Parameter> parameters = function.getParameters();
        final ScriptEngine engine = world.engine();
        // The handler's value comes back as the declared type takes it: its text for a VARCHAR, its number for a
        // FLOAT, its truthiness for a BOOLEAN - the number 1 a BOOLEAN argument arrived as included - a Date for a
        // DATE or a TIMESTAMP, a Uint8Array for a BINARY, and a member-by-member copy for a VARIANT, an ARRAY or
        // an OBJECT. An answer of any other kind is the engine's to convert.
        final DataType declared = function.getReturnType();
        final DataType returns = declared instanceof DateTimeType || declared instanceof BinaryType
            || declared instanceof BooleanType || declared instanceof StringType || declared instanceof NumericType
            || declared != null && declared.getCategory() == TypeCategory.SEMI_STRUCTURED ? declared : null;
        final String source;
        if (world.source() == null) {
            source = JavaScriptHandler.source(function.getName(), parameters, function.getBody(), false, returns);
            world.keep(source);
        } else {
            source = world.source();
        }
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
            final Object result = JavaScriptHandler.invoke(engine, world.invoker(source), null,
                function.getName(), parameters, arguments);
            logger.debug("JavaScript function {} executed successfully", function.getName());
            return JavaScriptReturn.toSql(result, returns, false);
        } catch (final ScriptException e) {
            logger.error("Error executing JavaScript function: {}", function.getName(), e);
            throw new RuntimeException(JavaScriptErrorText.of(e, function.getName(), function.getBody(),
                JavaScriptHandler.BODY_FIRST_LINE, source, GraalJsEngine.hostOf(engine),
                "Error executing JavaScript function " + function.getName() + ": " + e.getMessage()), e);
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException("Error executing JavaScript function " + function.getName() + ": "
                + e.getMessage(), e);
        }
    }
}
