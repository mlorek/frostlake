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

package dev.frostlake.rt.py;

import dev.frostlake.config.EngineConfig;
import dev.frostlake.executor.udf.RunningEngine;
import dev.frostlake.metastore.model.Parameter;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Python the Python executors run on, chosen by {@code python.runtime}: {@code graalpy} (the default) embeds
 * GraalPy in the JVM with packages from {@code python.venv}; {@code cpython} runs a CPython interpreter —
 * {@code python.executable}, typically a venv's {@code bin/python}, else {@code python3} on the PATH — in worker
 * processes, so packages with native extensions (numpy, pandas, scikit-learn) work as on Snowflake on any platform.
 * Each engine runs on the interpreter its own configuration chose, whatever another engine chose since.
 */
public final class PythonInterpreter {

    /** {@code python.runtime}: {@code graalpy} or {@code cpython}. */
    public static final String PROP_RUNTIME = "python.runtime";

    /** {@code python.executable}: the CPython interpreter for {@code python.runtime=cpython}. */
    public static final String PROP_EXECUTABLE = "python.executable";

    /** The settings GraalPy is chosen by: one embedded interpreter serves every engine that chooses it. */
    private static final String GRAALPY = "graalpy";

    /** Each backend an engine chose, by its settings: engines configured alike share one. */
    private static final Map<String, PythonBackend> BACKENDS = new HashMap<>();
    /** The backend each engine chose, by the engine's configuration. */
    private static final Map<EngineConfig, PythonBackend> ENGINES = new WeakHashMap<>();
    /** Every backend in {@link #BACKENDS}, for the end of a statement. */
    private static volatile PythonBackend[] chosen = new PythonBackend[0];
    /** The backend the engine built last chose, for a call no engine's statement makes. */
    private static volatile PythonBackend latest;

    private PythonInterpreter() {
    }

    /**
     * Choose an engine's backend from its configuration. Called for every engine built: the engine's statements run
     * on it whatever another engine chooses later, and engines configured alike share it, so the interpreters
     * already running on each thread survive.
     */
    public static synchronized void configure(final EngineConfig config) {
        final String runtime = config.getProperty(PROP_RUNTIME, GRAALPY).trim().toLowerCase(Locale.ROOT);
        final String executable = resolveExecutable(config.getProperty(PROP_EXECUTABLE, "").trim());
        final String settings;
        if ("cpython".equals(runtime)) {
            settings = runtime + '|' + executable;
        } else if (runtime.isEmpty() || GRAALPY.equals(runtime)) {
            // GraalPy runs in the JVM, one context per thread whichever engine calls it: a context starts with the
            // packages of the venv the engine built last names.
            PythonRuntime.configureVenv(config.getPythonVenv());
            settings = GRAALPY;
        } else {
            throw new IllegalArgumentException(PROP_RUNTIME + " must be graalpy or cpython, not '" + runtime + "'");
        }
        final PythonBackend backend = backendFor(settings, executable);
        ENGINES.put(config, backend);
        latest = backend;
    }

    /** The backend of {@code settings}, made the first time they are chosen. */
    private static PythonBackend backendFor(final String settings, final String executable) {
        PythonBackend backend = BACKENDS.get(settings);
        if (backend == null) {
            backend = GRAALPY.equals(settings) ? new GraalPyBackend() : new CPythonBackend(executable);
            BACKENDS.put(settings, backend);
            chosen = BACKENDS.values().toArray(new PythonBackend[0]);
        }
        return backend;
    }

    /** The backend {@link #configure} chose for an engine's configuration, or null when it chose none. */
    static synchronized PythonBackend backendOf(final EngineConfig config) {
        return ENGINES.get(config);
    }

    /** A relative interpreter path is the working directory's; a bare command name is looked up on the PATH. */
    private static String resolveExecutable(final String executable) {
        if (executable.isEmpty()) {
            return "python3";
        }
        if (executable.indexOf('/') < 0 && executable.indexOf('\\') < 0) {
            return executable;
        }
        return Paths.get(executable).toAbsolutePath().normalize().toString();
    }

    /** The backend of the engine whose statement runs on this thread, else the one the engine built last chose. */
    private static PythonBackend backend() {
        final EngineConfig running = RunningEngine.config();
        if (running != null) {
            final PythonBackend own = backendOf(running);
            if (own != null) {
                return own;
            }
        }
        final PythonBackend current = latest;
        if (current != null) {
            return current;
        }
        synchronized (PythonInterpreter.class) {
            if (latest == null) {
                latest = backendFor(GRAALPY, null);
            }
            return latest;
        }
    }

    /** The interpreter, for messages: {@code 3.12 (GraalPy)}, {@code 3.12.4 (CPython)}. */
    public static String description() {
        return backend().description();
    }

    /** The interpreter's Python minor version (3.x gives x). */
    public static int minorVersion() {
        return backend().minorVersion();
    }

    public static void bind(final String name, final Object value) {
        backend().bind(name, value);
    }

    public static void bindArgument(final Parameter parameter, final String name, final Object value) {
        backend().bindArgument(parameter, name, value);
    }

    public static void bindProcedureArgument(final Parameter parameter, final String name, final Object value) {
        backend().bindProcedureArgument(parameter, name, value);
    }

    public static void eval(final String code) {
        backend().eval(code);
    }

    /** A global as a plain Java value (Map, List, scalars, java.time), or null when unset or None. */
    public static Object global(final String name) {
        return backend().global(name);
    }

    public static void discard() {
        backend().discard();
    }

    /**
     * Settle this thread's interpreter after a failed execution (see {@link PythonBackend#settleAfter}).
     *
     * @param failure what the execution threw
     * @return whether the interpreter was kept
     */
    public static boolean settleAfter(final Throwable failure) {
        return backend().settleAfter(failure);
    }

    /** Enter a routine call's own module namespace (see {@link PythonBackend#enterModule()}). */
    static Object enterModule() {
        return backend().enterModule();
    }

    /** Enter the module namespace a call site keeps (see {@link PythonBackend#resumeModule}). */
    static Object resumeModule(final String key) {
        return backend().resumeModule(key);
    }

    /** Whether a frame holds the namespace its call site kept (see {@link PythonBackend#resumed}). */
    static boolean resumed(final Object frame) {
        return frame != null && backend().resumed(frame);
    }

    /** Keep the running call's namespace for its call site (see {@link PythonBackend#keepModule}). */
    static void keepModule(final String key) {
        backend().keepModule(key);
    }

    /** The backend this thread's statement runs Python on, which a call site's kept module lives in. */
    static PythonBackend running() {
        return backend();
    }

    /** Leave a frame {@link #enterModule()} entered; null leaves nothing. */
    static void leaveModule(final Object frame) {
        if (frame != null) {
            backend().leaveModule(frame);
        }
    }

    /** Make a helper module of the runtime's, once per interpreter (see {@link PythonBackend#installModule}). */
    static void installModule(final String name, final String source) {
        backend().installModule(name, source);
    }

    /**
     * An outermost statement on this thread has ended (see {@link PythonBackend#restoreShared()}): each backend puts
     * back what the statement's routines changed in this thread's interpreter, when they ran on it.
     */
    static void restoreShared() {
        for (final PythonBackend each : chosen) {
            each.restoreShared();
        }
    }

    /** Whether the backend is GraalPy, whose globals {@link PythonRuntime} reads as polyglot values. */
    static boolean holdsPolyglotValues() {
        return backend().holdsPolyglotValues();
    }

}
