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

import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import dev.frostlake.executor.SessionZone;
import dev.frostlake.executor.udf.TruffleLogBridge;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.function.Predicate;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * One shared polyglot {@link Engine} behind every Graal JavaScript {@code ScriptEngine} the executors
 * create. Going through {@link GraalJSScriptEngine#create} instead of {@code ScriptEngineManager}
 * lets the engine carry the {@link TruffleLogBridge} (Truffle logs → SLF4J) and the
 * interpreter-only-warning suppression, which the manager-created engines cannot be given.
 */
public final class GraalJsEngine {

    private static final Logger logger = LoggerFactory.getLogger(GraalJsEngine.class);

    private static final Engine SHARED_ENGINE = buildEngine();

    /**
     * Run once in every fresh context. The {@code Java} global survives the options above, so it is removed
     * here, as are the {@code context} and {@code engine} globals the script-engine bridge adds, which a
     * handler on the account never sees. {@code eval} and the {@code Function} constructor refuse in the account's own words; the
     * engine-level {@code js.disable-eval} still stands behind every other route to dynamic code. The
     * {@code Function} wrapper keeps its prototype, so {@code f instanceof Function} holds.
     */
    private static final String SANDBOX_PRELUDE = """
        (function () {
            delete globalThis.Java;
            delete globalThis.context;
            delete globalThis.engine;
            var refuse = function () {
                throw new EvalError('Dynamic code evaluation disallowed');
            };
            globalThis.eval = function eval() {
                return refuse();
            };
            globalThis.Function = new Proxy(globalThis.Function, { apply: refuse, construct: refuse });
        })();
        """;

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
     * <p>Every context of a shared engine must use the SAME configuration, so it is fixed here instead
     * of being toggled per call site via the {@code polyglot.js.*} magic bindings. It is the account's
     * locked-down handler sandbox: no class lookup and none of the JVM-flavoured globals ({@code Java},
     * {@code Packages}, {@code java}, {@code Graal}, {@code print}, {@code load}), and no dynamic code —
     * {@code eval} and {@code new Function} throw, as they do on the account (live-verified). Host access
     * stays, since the handler's arguments and the procedure's {@code snowflake} object are host values.
     */
    public static ScriptEngine newScriptEngine() {
        return newScriptEngine(SessionZone.current());
    }

    /**
     * {@link #newScriptEngine()} in a context whose {@code Intl} and {@code Date} read the given zone, as a
     * handler on the account reads the session's TIMEZONE, and which offers V8's stack-trace API
     * ({@code Error.captureStackTrace}, {@code stackTraceLimit}).
     *
     * @param zone the session's zone
     * @return the engine, or null when Graal JS is not on the classpath
     */
    public static ScriptEngine newScriptEngine(final ZoneId zone) {
        try {
            final Context.Builder contextConfig = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(new Predicate<String>() {
                    @Override
                    public boolean test(final String className) {
                        return false;
                    }
                })
                .allowExperimentalOptions(true)
                .option("js.java-package-globals", "false")
                .option("js.graal-builtin", "false")
                .option("js.print", "false")
                .option("js.load", "false")
                .option("js.disable-eval", "true")
                .option("js.stack-trace-api", "true")
                .option("js.timezone", timeZoneOption(zone));
            final ScriptEngine engine = GraalJSScriptEngine.create(SHARED_ENGINE, contextConfig);
            engine.eval(SANDBOX_PRELUDE);
            return engine;
        } catch (final ScriptException e) {
            throw new IllegalStateException("the JavaScript sandbox prelude failed", e);
        } catch (final RuntimeException | NoClassDefFoundError e) {
            logger.debug("Graal JS unavailable: {}", e.getMessage());
            return null;
        }
    }

    /**
     * The zone as the JavaScript engine names one: a region by its own name, a whole-hour offset as its
     * {@code Etc/GMT} zone (whose sign runs the other way), and anything else as UTC.
     */
    static String timeZoneOption(final ZoneId zone) {
        if (!(zone instanceof ZoneOffset)) {
            return zone.getId();
        }
        final int seconds = ((ZoneOffset) zone).getTotalSeconds();
        if (seconds == 0 || seconds % 3600 != 0) {
            return "UTC";
        }
        final int hours = seconds / 3600;
        return "Etc/GMT" + (hours > 0 ? "-" + hours : "+" + (-hours));
    }
}
