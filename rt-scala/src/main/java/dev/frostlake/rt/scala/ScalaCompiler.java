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

package dev.frostlake.rt.scala;

import dev.frostlake.executor.udf.HandlerStateFactory;
import dev.frostlake.executor.udf.JavaHandlerClass;
import dev.frostlake.executor.udf.StatementHandlerStates;
import scala.collection.Iterator;
import scala.reflect.internal.util.Position;
import scala.tools.nsc.Global;
import scala.tools.nsc.Settings;
import scala.tools.nsc.reporters.StoreReporter;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compiles inline Scala source in-process via the bundled scala-compiler (no external {@code scalac}),
 * the way {@code JavaFunctionCompiler} uses {@code javac}. Writes the source into {@code tempDir},
 * compiles it there, and returns the loaded class — the caller owns {@code tempDir} cleanup (so the
 * compiled .class files survive until the handler has been invoked). Shared by
 * {@link ScalaFunctionExecutor} and {@link ScalaProcedureExecutor}.
 */
final class ScalaCompiler {

    private static final int MAX_CACHED = 256;

    /** The name a compile error and a handler's frame place the body in. */
    static final String SOURCE_NAME = "ScalaUDF";

    /** What the class a statement's call site loads is kept under among its function's states. */
    private static final String STATEMENT_CLASS = "scala-handler-class";

    /**
     * The Java level a Scala handler runs at: the class loader's frames of a class a body does not hold are
     * numbered as Java 11 numbers them (live-verified).
     */
    static final int JAVA_LEVEL = 11;

    // key -> kept-alive temp dir (the URLClassLoader resolves classes from it); only touched under CACHE's lock.
    private static final Map<String, Path> CACHE_DIRS = new HashMap<>();

    // Compiled handler classes keyed by class name + source text, so a Scala UDF/procedure compiles ONCE
    // instead of on every per-row invocation (per-row recompiles made Scala UDFs unusable in queries — see
    // UdfQueryPerformanceTest). Bounded access-order LRU; an evicted entry's temp dir is deleted.
    private static final Map<String, Class<?>> CACHE = Collections.synchronizedMap(
        new LinkedHashMap<String, Class<?>>(MAX_CACHED + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, Class<?>> eldest) {
                if (size() > MAX_CACHED) {
                    deleteRecursively(CACHE_DIRS.remove(eldest.getKey()));
                    return true;
                }
                return false;
            }
        });

    /**
     * The frame a handler's report shows after the handler's own: an {@code object}'s method is reached through
     * the forwarder its companion class holds, {@code function_handler_0//H.f(ScalaUDF)} (live-verified).
     *
     * @param handled    the handler's class, an object's ending in {@code $}
     * @param methodName the handler's method
     * @return the forwarder's frame, or null for a class's method, which none reaches
     */
    static String forwarderFrame(final Class<?> handled, final String methodName) {
        final String name = handled.getName();
        return name.endsWith("$") ? "function_handler_0//" + name.substring(0, name.length() - 1) + '.' + methodName
            + '(' + SOURCE_NAME + ')' : null;
    }

    /**
     * The class a function's handler runs in: loaded anew, from the classes its body compiled to once, for the
     * statement that is running and the place that calls the function, so what an object's fields hold is there
     * for the statement's later rows and for nothing else - not for a later statement, nor for another call of
     * the function in the same statement (live-verified). Outside a statement the one class every caller shares.
     *
     * @param routine    the function, which the class is kept for
     * @param sourceCode the body
     * @param className  the handler's class
     * @param where      the end of a compile error's report
     * @return the class
     * @throws Exception when the body does not compile or holds no such class
     */
    static Class<?> statementClass(final Object routine, final String sourceCode, final String className,
                                   final String where) throws Exception {
        final Class<?> shared = compileCached(sourceCode, className, where);
        final Path dir = compiledDir(sourceCode, className);
        if (dir == null) {
            return shared;
        }
        final AutoCloseable kept = StatementHandlerStates.state(routine, STATEMENT_CLASS, new HandlerStateFactory() {
            @Override
            public AutoCloseable create() {
                return new JavaHandlerClass(loadFrom(dir, className));
            }
        });
        return kept != null ? ((JavaHandlerClass) kept).handled() : shared;
    }

    /**
     * The class a procedure's handler runs in: loaded anew for each call, from the classes its body compiled to
     * once, so what an object's fields hold is the call's own (live-verified).
     *
     * @param sourceCode the body
     * @param className  the handler's class
     * @param where      the end of a compile error's report
     * @return the class
     * @throws Exception when the body does not compile or holds no such class
     */
    static Class<?> callClass(final String sourceCode, final String className, final String where)
            throws Exception {
        final Class<?> shared = compileCached(sourceCode, className, where);
        final Path dir = compiledDir(sourceCode, className);
        return dir == null ? shared : loadFrom(dir, className);
    }

    /** Where a body's classes were compiled to, or null when the cache no longer holds them. */
    private static Path compiledDir(final String sourceCode, final String className) {
        synchronized (CACHE) {
            return CACHE_DIRS.get(className + ":" + sourceCode);
        }
    }

    /** The handler's class, loaded from a compile's directory by a loader of its own. */
    private static Class<?> loadFrom(final Path dir, final String className) {
        try {
            return load(new URLClassLoader(new URL[]{ dir.toUri().toURL() },
                Thread.currentThread().getContextClassLoader()), className);
        } catch (final ClassNotFoundException | MalformedURLException unloadable) {
            throw new IllegalStateException("The compiled handler class " + className + " cannot be loaded",
                unloadable);
        }
    }

    /**
     * A Scala {@code object} compiles to ClassName$ (the singleton, holding MODULE$ and the methods) plus a
     * static-forwarder ClassName: the {@code $} singleton is loaded first, so the handler runs on MODULE$, and the
     * plain name for a regular {@code class}.
     */
    private static Class<?> load(final ClassLoader classLoader, final String className) throws ClassNotFoundException {
        try {
            return classLoader.loadClass(className + "$");
        } catch (final ClassNotFoundException e) {
            return classLoader.loadClass(className);
        }
    }

    /** Compile (or reuse a cached) Scala handler class — reused across invocations to avoid per-row recompiles. */
    static Class<?> compileCached(final String sourceCode, final String className, final String where)
            throws Exception {
        // The whole source names the entry: two sources that merely share a hash are two classes.
        final String key = className + ":" + sourceCode;
        synchronized (CACHE) {
            final Class<?> hit = CACHE.get(key);
            if (hit != null) {
                return hit;
            }
        }
        final Path dir = Files.createTempDirectory("scala_udf_");
        final Class<?> compiled;
        try {
            compiled = compile(sourceCode, className, dir);
        } catch (final ScalaCompileFailure failure) {
            deleteRecursively(dir);
            // The compiler's report is the refusal, as an account's is (live-verified).
            throw new RuntimeException(failure.getMessage() + "\n" + where, failure);
        }
        synchronized (CACHE) {
            final Class<?> raced = CACHE.get(key);
            if (raced != null) {
                deleteRecursively(dir);   // another thread compiled it first
                return raced;
            }
            CACHE_DIRS.put(key, dir);
            CACHE.put(key, compiled);     // may evict (and delete) the eldest entry's dir
        }
        return compiled;
    }

    static Class<?> compile(final String sourceCode, final String className, final Path tempDir) throws Exception {
        final String simpleName = className.contains(".")
            ? className.substring(className.lastIndexOf('.') + 1) : className;
        final Path sourceFile = tempDir.resolve(simpleName + ".scala");
        Files.writeString(sourceFile, sourceCode);

        final Settings settings = new Settings();
        settings.usejavacp().value_$eq(true);                 // compile against the running JVM classpath
        settings.outputDirs().setSingleOutput(tempDir.toString());

        // The compiler's complaints are KEPT rather than printed to the console, where a client never sees them.
        final StoreReporter reporter = new StoreReporter(settings);
        final Global global = new Global(settings, reporter);
        final Global.Run run = global.new Run();
        run.compile(scalaSingletonList(sourceFile.toString()));
        if (reporter.hasErrors()) {
            throw new ScalaCompileFailure(describe(reporter));
        }

        return load(new URLClassLoader(new URL[]{ tempDir.toUri().toURL() },
            Thread.currentThread().getContextClassLoader()), className);
    }

    /**
     * The compiler's errors as its console would print them, the source named {@code ScalaUDF}: the place and
     * the complaint, the line and a mark under the place.
     */
    private static String describe(final StoreReporter reporter) {
        final StringBuilder text = new StringBuilder();
        final Iterator<StoreReporter.Info> infos = reporter.infos().iterator();
        while (infos.hasNext()) {
            final StoreReporter.Info info = infos.next();
            if (info.severity() != reporter.ERROR()) {
                continue;
            }
            if (text.length() > 0) {
                text.append('\n');
            }
            final Position place = info.pos();
            if (!place.isDefined()) {
                text.append("error: ").append(info.msg());
                continue;
            }
            text.append(SOURCE_NAME).append(':').append(place.line()).append(": error: ").append(info.msg())
                .append('\n').append(place.lineContent()).append('\n');
            for (int i = 1; i < place.column(); i++) {
                text.append(' ');
            }
            text.append('^');
        }
        return text.length() == 0 ? "Scala compilation failed" : text.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static scala.collection.immutable.List<String> scalaSingletonList(final String element) {
        return new scala.collection.immutable.$colon$colon(element, scala.collection.immutable.Nil$.MODULE$);
    }

    private static void deleteRecursively(final Path dir) {
        if (dir != null) {
            deleteRecursively(dir.toFile());
        }
    }

    private static void deleteRecursively(final File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            final File[] children = f.listFiles();
            if (children != null) {
                for (final File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        f.delete();
    }

    private ScalaCompiler() {
    }
}
