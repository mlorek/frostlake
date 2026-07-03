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

import scala.tools.nsc.Global;
import scala.tools.nsc.Settings;

import java.io.File;
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

    // key -> kept-alive temp dir (the URLClassLoader resolves classes from it); only touched under CACHE's lock.
    private static final Map<String, Path> CACHE_DIRS = new HashMap<>();

    // Compiled handler classes keyed by class name + source hash, so a Scala UDF/procedure compiles ONCE
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

    /** Compile (or reuse a cached) Scala handler class — reused across invocations to avoid per-row recompiles. */
    static Class<?> compileCached(final String sourceCode, final String className) throws Exception {
        final String key = className + ":" + (sourceCode == null ? 0 : sourceCode.hashCode());
        synchronized (CACHE) {
            final Class<?> hit = CACHE.get(key);
            if (hit != null) {
                return hit;
            }
        }
        final Path dir = Files.createTempDirectory("scala_udf_");
        final Class<?> compiled = compile(sourceCode, className, dir);
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

        final Global global = new Global(settings);
        final Global.Run run = global.new Run();
        run.compile(scalaSingletonList(sourceFile.toString()));
        if (global.reporter().hasErrors()) {
            throw new RuntimeException("Scala compilation failed for " + className);
        }

        // A Scala `object` compiles to ClassName$ (the singleton, holding MODULE$ and the methods) plus a
        // static-forwarder ClassName. Load the `$` singleton first so the handler runs on MODULE$; fall
        // back to the plain name for a regular `class`.
        final URLClassLoader classLoader = new URLClassLoader(
            new URL[]{ tempDir.toUri().toURL() },
            Thread.currentThread().getContextClassLoader());
        try {
            return classLoader.loadClass(className + "$");
        } catch (final ClassNotFoundException e) {
            return classLoader.loadClass(className);
        }
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
