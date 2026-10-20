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
import dev.frostlake.metastore.model.Parameter;

import java.nio.file.Paths;
import java.util.Locale;

/**
 * The Python the Python executors run on, chosen by {@code python.runtime}: {@code graalpy} (the default) embeds
 * GraalPy in the JVM with packages from {@code python.venv}; {@code cpython} runs a CPython interpreter —
 * {@code python.executable}, typically a venv's {@code bin/python}, else {@code python3} on the PATH — in worker
 * processes, so packages with native extensions (numpy, pandas, scikit-learn) work as on Snowflake on any platform.
 */
public final class PythonInterpreter {

    /** {@code python.runtime}: {@code graalpy} or {@code cpython}. */
    public static final String PROP_RUNTIME = "python.runtime";

    /** {@code python.executable}: the CPython interpreter for {@code python.runtime=cpython}. */
    public static final String PROP_EXECUTABLE = "python.executable";

    private static volatile PythonBackend backend;
    private static String configured;

    private PythonInterpreter() {
    }

    /**
     * Choose the backend from an engine's configuration. Called for every engine built; the backend is kept while
     * the settings stay the same, so the interpreters already running on each thread survive.
     */
    public static synchronized void configure(final EngineConfig config) {
        final String runtime = config.getProperty(PROP_RUNTIME, "graalpy").trim().toLowerCase(Locale.ROOT);
        final String venv = config.getPythonVenv();
        final String executable = resolveExecutable(config.getProperty(PROP_EXECUTABLE, "").trim());
        final String settings = runtime + '|' + venv + '|' + executable;
        if (backend != null && settings.equals(configured)) {
            return;
        }
        if ("cpython".equals(runtime)) {
            backend = new CPythonBackend(executable);
        } else if (runtime.isEmpty() || "graalpy".equals(runtime)) {
            backend = new GraalPyBackend(venv);
        } else {
            throw new IllegalArgumentException(PROP_RUNTIME + " must be graalpy or cpython, not '" + runtime + "'");
        }
        configured = settings;
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

    private static PythonBackend backend() {
        final PythonBackend current = backend;
        if (current != null) {
            return current;
        }
        synchronized (PythonInterpreter.class) {
            if (backend == null) {
                backend = new GraalPyBackend(null);
            }
            return backend;
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

    /** An outermost statement on this thread has ended (see {@link PythonBackend#restoreShared()}). */
    static void restoreShared() {
        final PythonBackend current = backend;
        if (current != null) {
            current.restoreShared();
        }
    }

    /** Whether the backend is GraalPy, whose globals {@link PythonRuntime} reads as polyglot values. */
    static boolean holdsPolyglotValues() {
        return backend().holdsPolyglotValues();
    }

}
