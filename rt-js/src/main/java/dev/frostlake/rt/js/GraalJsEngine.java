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

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import javax.script.Invocable;
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
     * Run once in every fresh context, with the context's {@link JavaScriptHost}. The {@code Java} global
     * survives the options below, so it is removed there, as are the {@code context}, {@code engine} and
     * {@code arguments} globals the script-engine bridge adds, which a handler on the account never sees.
     * {@code eval} and the {@code Function} constructor refuse in the account's own words; the engine-level
     * {@code js.disable-eval} still stands behind every other route to dynamic code.
     *
     * <p>The account's own globals are declared as it declares them for a function and a procedure alike: the
     * {@code Snowflake} and {@code SnowflakeLogger} constructors, the {@code snowflakeLogger} and
     * {@code _c_snowflake_logger} objects with their members, and {@code snowflake}, an object with no properties
     * of its own whose prototype logs, builds a user exception and refuses to read an account parameter
     * (live-verified). A procedure's handler adds the statement methods to that prototype (see
     * {@link JavaScriptHandler}). A date names its zone in full, and {@code JSON.parse} refuses a text in the
     * account's words.
     */
    private static final String SANDBOX_PRELUDE = prelude();

    /**
     * The host object of each engine made here, for the sentence an uncaught error reaches SQL with. The host is
     * kept by the world it serves, whose own functions call it, and held weakly here: it knows that world, so a
     * strong hold would keep every engine ever made.
     */
    private static final Map<ScriptEngine, WeakReference<JavaScriptHost>> HOSTS =
        Collections.synchronizedMap(new WeakHashMap<ScriptEngine, WeakReference<JavaScriptHost>>());

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
     * {@code eval} and {@code new Function} throw, as they do on the account (live-verified). Host access is
     * explicit: only the members the engine's own objects export can be called, by the script the engine wraps a
     * handler in, and no host object is handed to the handler itself — its arguments are JavaScript values and
     * the procedure API's objects are made in its own world.
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
                .allowHostAccess(HostAccess.EXPLICIT)
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
                // The account's engine speaks US English whatever the host's locale is, reads a date written
                // without a zone in the session's, and has the builtins this engine still stages.
                .option("js.locale", "en-US")
                .option("js.allow-narrow-spaces-in-date-format", "false")
                .option("js.use-utc-for-legacy-dates", "false")
                .option("js.ecmascript-version", "staging")
                .option("js.timezone", JavaScriptZones.option(zone));
            final ScriptEngine engine = GraalJSScriptEngine.create(SHARED_ENGINE, contextConfig);
            final JavaScriptHost host = new JavaScriptHost(zone);
            ((Invocable) engine).invokeMethod(engine.eval(SANDBOX_PRELUDE), "call", null, host);
            HOSTS.put(engine, new WeakReference<JavaScriptHost>(host));
            return engine;
        } catch (final ScriptException | NoSuchMethodException e) {
            throw new IllegalStateException("the JavaScript sandbox prelude failed", e);
        } catch (final RuntimeException | NoClassDefFoundError e) {
            logger.debug("Graal JS unavailable: {}", e.getMessage());
            return null;
        }
    }

    /**
     * The host object of an engine made here.
     *
     * @param engine the engine
     * @return its host, or null for an engine made elsewhere
     */
    static JavaScriptHost hostOf(final ScriptEngine engine) {
        final WeakReference<JavaScriptHost> held = HOSTS.get(engine);
        return held == null ? null : held.get();
    }

    private static String prelude() {
        try (InputStream in = GraalJsEngine.class.getResourceAsStream("prelude.js")) {
            if (in == null) {
                throw new IllegalStateException("the JavaScript sandbox prelude is missing");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new IllegalStateException("the JavaScript sandbox prelude cannot be read", e);
        }
    }
}
