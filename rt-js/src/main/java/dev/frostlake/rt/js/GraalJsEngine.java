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

import dev.frostlake.executor.udf.TruffleLogBridge;
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import java.util.function.Predicate;

/**
 * One shared polyglot {@link Engine} behind every Graal JavaScript {@code ScriptEngine} the executors
 * create. Going through {@link GraalJSScriptEngine#create} instead of {@code ScriptEngineManager}
 * lets the engine carry the {@link TruffleLogBridge} (Truffle logs → SLF4J) and the
 * interpreter-only-warning suppression, which the manager-created engines cannot be given.
 */
public final class GraalJsEngine {

    private static final Logger logger = LoggerFactory.getLogger(GraalJsEngine.class);

    private static final Engine SHARED_ENGINE = buildEngine();

    private GraalJsEngine() {
    }

    private static Engine buildEngine() {
        final Engine.Builder builder = Engine.newBuilder()
            .option("engine.WarnInterpreterOnly", "false");
        TruffleLogBridge.installOn(builder);
        return builder.build();
    }

    /**
     * A fresh Graal JS {@code ScriptEngine} over the shared engine, or null when Graal JS is not on
     * the classpath — callers keep their Nashorn/javascript fallback chain.
     *
     * <p>Every context of a shared engine must use the SAME host-access configuration, so it is
     * fixed here (full host access + class lookup, what UDF/procedure handlers need) instead of
     * being toggled per call site via the {@code polyglot.js.*} magic bindings.
     */
    public static ScriptEngine newScriptEngine() {
        try {
            final Context.Builder contextConfig = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(new Predicate<String>() {
                    @Override
                    public boolean test(final String className) {
                        return true;
                    }
                });
            return GraalJSScriptEngine.create(SHARED_ENGINE, contextConfig);
        } catch (final RuntimeException | NoClassDefFoundError e) {
            logger.debug("Graal JS unavailable: {}", e.getMessage());
            return null;
        }
    }
}
