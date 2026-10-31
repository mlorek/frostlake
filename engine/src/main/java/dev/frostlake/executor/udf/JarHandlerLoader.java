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

import dev.frostlake.config.S3PathResolver;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads a pre-compiled handler class from the JAR(s) named in a UDF/procedure's {@code IMPORTS} clause — the
 * alternative to an inline {@code AS $$ … $$} body. Language-agnostic: Java and Scala both compile to JVM
 * classes, so a Scala {@code object}/{@code class} in a JAR loads exactly like a Java class (the per-language
 * executors keep their own handler-invocation logic, e.g. a Scala {@code object}'s {@code MODULE$} or a
 * procedure's Snowpark {@code Session} arg).
 *
 * <p>Each IMPORTS entry is resolved to a local file:
 * <ul>
 *   <li>{@code s3://bucket/key.jar} — mapped to a local path by the engine's {@link S3PathResolver}
 *       (configured via {@code stage.s3.localMappings} / {@code stage.s3.localRoot}).</li>
 *   <li>{@code @stage/path.jar} — the named stage is looked up in the catalog; an S3-backed stage maps
 *       through the resolver and a {@code file://} stage uses its local directory. Unknown stages (or
 *       {@code @~}/{@code @%} user/table stages) fall back to treating the reference as a local path with a
 *       leading {@code @} stripped, preserving prior behavior.</li>
 *   <li>a bare path — used as-is.</li>
 * </ul>
 * A stage-resolved import additionally puts every other {@code *.jar} in the same stage directory on the
 * classpath (imports first, then siblings sorted by name). Handler JARs are usually thin — on Snowflake
 * their dependencies arrive via the {@code PACKAGES} transitive closure, which the engine cannot resolve
 * from Maven — so the stage directory doubles as the function's dependency set: drop the missing
 * dependency JARs beside the handler JAR and they link. Loading is child-first (see
 * {@link ChildFirstUdfClassLoader}): the jar's own dependency versions win over the engine's classpath,
 * while JDK/engine/Snowpark/SLF4J classes stay shared so handlers can reference them. See docs/functions.md.
 */
public final class JarHandlerLoader {

    private static final Logger logger = LoggerFactory.getLogger(JarHandlerLoader.class);

    /**
     * Loaded handler classes keyed by class name + each jar's path/size/mtime. A UDF handler is
     * invoked once per ROW, and building a fresh classloader per call re-scanned the jars and
     * re-defined every class each time — the vendor ANTLR-parser UDFs spent more time class-loading
     * than parsing. A replaced jar (new size/mtime) naturally misses the cache and reloads.
     */
    private static final Map<String, Class<?>> HANDLER_CACHE = new ConcurrentHashMap<>();

    /** What every class file opens with. */
    private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;

    /** Load {@code className} (trying the Scala {@code object} singleton {@code className$} first) from the IMPORTS jar(s). */
    public static Class<?> load(final List<String> written, final String className,
                                final Catalog catalog, final S3PathResolver s3Resolver) {
        return load(written, className, catalog, s3Resolver, true);
    }

    /**
     * Load {@code className} from the IMPORTS jar(s) in a loader of its own, sharing nothing with a class loaded
     * before: its static fields, and those of every class of the jars it reads, start as the jars write them.
     *
     * @param written    the IMPORTS entries
     * @param className  the handler's class
     * @param catalog    the catalog the stages are named in
     * @param s3Resolver maps an S3 location to a local path, or null
     * @return the handler's class
     */
    public static Class<?> loadAnew(final List<String> written, final String className,
                                    final Catalog catalog, final S3PathResolver s3Resolver) {
        return load(written, className, catalog, s3Resolver, false);
    }

    /**
     * The files an IMPORTS list reads on this machine, in the order a class path takes them: each import as it
     * resolves, then the other jars of a stage directory an import lies in, which are the routine's dependency set.
     *
     * @param written    the IMPORTS entries
     * @param catalog    the catalog the stages are named in
     * @param s3Resolver maps an S3 location to a local path, or null
     * @return the files, each once
     */
    public static List<File> localJars(final List<String> written, final Catalog catalog,
                                       final S3PathResolver s3Resolver) {
        final List<File> jars = new ArrayList<>();
        for (final String localPath : localPaths(written, catalog, s3Resolver)) {
            jars.add(new File(localPath));
        }
        return jars;
    }

    /**
     * A loader of the classes jars hold, ahead of its parent's.
     *
     * @param jars   the jars
     * @param parent the loader every other class comes from
     * @return the loader
     */
    public static ClassLoader jarLoader(final List<File> jars, final ClassLoader parent) {
        final List<URL> urls = new ArrayList<>();
        for (final File jar : jars) {
            try {
                urls.add(jar.toURI().toURL());
            } catch (final MalformedURLException e) {
                throw new RuntimeException("Invalid IMPORTS path: " + jar.getPath(), e);
            }
        }
        return new ChildFirstUdfClassLoader(urls.toArray(new URL[0]), parent);
    }

    private static List<String> localPaths(final List<String> written, final Catalog catalog,
                                           final S3PathResolver s3Resolver) {
        // A file an internal named stage keeps is read from the engine's own directory behind the stage.
        final ImportLocalizer localizer = LOCALIZER.get();
        final List<String> imports = localizer != null ? localizer.localImports(written) : written;
        final List<String> localPaths = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        final List<File> stageDirs = new ArrayList<>();
        for (final String entry : imports) {
            final String localPath = resolveToLocalPath(entry, catalog, s3Resolver);
            if (seen.add(localPath)) {
                localPaths.add(localPath);
            }
            if (entry.trim().startsWith("@")) {
                final File parent = new File(localPath).getParentFile();
                if (parent != null) {
                    stageDirs.add(parent);
                }
            }
        }
        // Sibling JARs of a stage-resolved import: the stage directory is the function's dependency set.
        for (final File dir : stageDirs) {
            final File[] entries = dir.listFiles();
            if (entries == null) {
                continue;
            }
            final List<File> siblings = new ArrayList<>();
            for (final File f : entries) {
                if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    siblings.add(f);
                }
            }
            Collections.sort(siblings);
            for (final File sibling : siblings) {
                if (seen.add(sibling.getPath())) {
                    localPaths.add(sibling.getPath());
                }
            }
        }
        return localPaths;
    }

    private static Class<?> load(final List<String> written, final String className,
                                 final Catalog catalog, final S3PathResolver s3Resolver, final boolean cached) {
        if (written == null || written.isEmpty()) {
            throw new RuntimeException("No IMPORTS jar specified for handler class " + className);
        }
        final ImportLocalizer localizer = LOCALIZER.get();
        final List<String> imports = localizer != null ? localizer.localImports(written) : written;
        final List<String> localPaths = localPaths(written, catalog, s3Resolver);
        final StringBuilder cacheKey = new StringBuilder(className);
        for (final String localPath : localPaths) {
            final File jar = new File(localPath);
            cacheKey.append('|').append(localPath).append(':').append(jar.length()).append(':').append(jar.lastModified());
        }
        final Class<?> loaded = cached ? HANDLER_CACHE.get(cacheKey.toString()) : null;
        if (loaded != null) {
            return loaded;
        }

        final List<URL> urls = new ArrayList<>();
        for (final String localPath : localPaths) {
            try {
                urls.add(new File(localPath).toURI().toURL());
            } catch (final MalformedURLException e) {
                throw new RuntimeException("Invalid IMPORTS path: " + localPath, e);
            }
        }
        final URLClassLoader loader = new ChildFirstUdfClassLoader(
            urls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
        try {
            final Class<?> scalaObject = loader.loadClass(className + "$");   // Scala object singleton, if present
            if (cached) {
                HANDLER_CACHE.put(cacheKey.toString(), scalaObject);
            }
            return scalaObject;
        } catch (final UnsupportedClassVersionError newer) {
            throw tooNewForThisRuntime(className, newer);
        } catch (final ClassNotFoundException e) {
            try {
                final Class<?> plainClass = loader.loadClass(className);      // plain Java/Scala class
                if (cached) {
                    HANDLER_CACHE.put(cacheKey.toString(), plainClass);
                }
                return plainClass;
            } catch (final UnsupportedClassVersionError newer) {
                throw tooNewForThisRuntime(className, newer);
            } catch (final ClassNotFoundException e2) {
                // Spell out how each entry resolved — a stage lookup that silently fell back to a
                // relative local path is otherwise indistinguishable from a jar that lacks the class.
                final StringBuilder resolved = new StringBuilder();
                for (final String localPath : localPaths) {
                    if (resolved.length() > 0) {
                        resolved.append(", ");
                    }
                    final File f = new File(localPath);
                    resolved.append(localPath).append(f.isFile() ? "" : " [MISSING FILE]");
                }
                throw new HandlerClassNotFound(
                    "Handler class '" + className + "' not found in IMPORTS jar(s) " + imports
                    + " (resolved to: " + resolved + ")", className, e2);
            }
        }
    }

    /**
     * A handler class compiled for a newer Java than the one this engine runs on: the class cannot be loaded here,
     * whatever runtime its routine names, and the call is refused rather than left to fail inside the JVM.
     */
    private static RuntimeException tooNewForThisRuntime(final String className,
                                                         final UnsupportedClassVersionError newer) {
        return new RuntimeException("Handler class '" + className + "' was compiled for a newer Java than this"
            + " engine runs on (Java " + Runtime.version().feature() + "): " + newer.getMessage(), newer);
    }

    /**
     * The class-file version a handler class was compiled to, as the IMPORTS jar(s) hold it: 55 for Java 11, 61
     * for Java 17, 65 for Java 21. The first import that holds the class answers.
     *
     * @param written    the routine's imports, as it keeps them
     * @param className  the handler's class
     * @param catalog    the catalog the imports' stages are looked up in
     * @param s3Resolver the resolver of {@code s3://} imports, or null
     * @return the version, or -1 when no import holds the class or it cannot be read
     */
    public static int classFileVersion(final List<String> written, final String className, final Catalog catalog,
                                       final S3PathResolver s3Resolver) {
        if (written == null) {
            return -1;
        }
        final ImportLocalizer localizer = LOCALIZER.get();
        final List<String> imports = localizer != null ? localizer.localImports(written) : written;
        final String held = className.replace('.', '/') + ".class";
        for (final String entry : imports) {
            final File jar;
            try {
                jar = new File(resolveToLocalPath(entry, catalog, s3Resolver));
            } catch (final RuntimeException unresolved) {
                continue;
            }
            if (!jar.isFile()) {
                continue;
            }
            try (JarFile file = new JarFile(jar)) {
                final JarEntry found = file.getJarEntry(held);
                if (found == null) {
                    continue;
                }
                try (DataInputStream in = new DataInputStream(file.getInputStream(found))) {
                    if (in.readInt() != CLASS_FILE_MAGIC) {
                        return -1;
                    }
                    in.readUnsignedShort();
                    return in.readUnsignedShort();
                }
            } catch (final IOException unreadable) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Whether a routine's imports hold its handler class, as the account looks for it when the routine is created: a
     * JAR that holds the class file, or a stage directory that does (live-verified: a directory that holds nothing
     * holds no class).
     *
     * @param written    the routine's imports, as it keeps them
     * @param className  the handler's class
     * @param catalog    the catalog the imports' stages are looked up in
     * @param s3Resolver the resolver of {@code s3://} imports, or null
     * @return TRUE or FALSE, or null when an import cannot be read here and nothing can be told
     */
    public static Boolean holdsClass(final List<String> written, final String className, final Catalog catalog,
                                     final S3PathResolver s3Resolver) {
        if (written == null || written.isEmpty()) {
            return null;
        }
        final String held = className.replace('.', '/') + ".class";
        for (final String entry : written) {
            final String trimmed = entry == null ? "" : entry.trim();
            final File file = localFile(trimmed, catalog, s3Resolver);
            if (file == null) {
                return null;
            }
            if (trimmed.endsWith("/")) {
                if (new File(file, held).isFile()) {
                    return Boolean.TRUE;
                }
                continue;
            }
            if (!file.isFile()) {
                return null;
            }
            try (JarFile jar = new JarFile(file)) {
                if (jar.getJarEntry(held) != null) {
                    return Boolean.TRUE;
                }
            } catch (final IOException unreadable) {
                return null;
            }
        }
        return Boolean.FALSE;
    }

    /** Where an import lies on this machine, a stage's file or directory included, or null where it cannot be told. */
    private static File localFile(final String entry, final Catalog catalog, final S3PathResolver s3Resolver) {
        final ImportLocalizer localizer = LOCALIZER.get();
        if (localizer != null) {
            final Path kept = localizer.keptStageFile(entry);
            if (kept != null) {
                return kept.toFile();
            }
        }
        if (entry.startsWith("@")) {
            final String resolved = resolveStageReference(entry.substring(1), catalog, s3Resolver);
            return resolved == null ? null : new File(resolved);
        }
        try {
            return new File(resolveToLocalPath(entry, catalog, s3Resolver));
        } catch (final RuntimeException unresolved) {
            return null;
        }
    }

    /**
     * Resolve an IMPORTS entry to a local filesystem path: {@code s3://…} via the resolver, {@code @stage/…}
     * via the catalog (S3 stages map through the resolver, {@code file://} stages use their local dir), and a
     * bare path as-is. A non-resolvable {@code @ref} falls back to its literal text minus the leading {@code @}.
     */
    static String resolveToLocalPath(final String entry, final Catalog catalog, final S3PathResolver s3Resolver) {
        final String ref = entry.trim();
        if (S3PathResolver.isS3Url(ref)) {
            if (s3Resolver == null) {
                throw new RuntimeException("Cannot resolve s3:// IMPORTS path without an S3 path resolver: " + entry);
            }
            return s3Resolver.toLocalPath(ref).toString();
        }
        if (ref.startsWith("@")) {
            final String resolved = resolveStageReference(ref.substring(1), catalog, s3Resolver);
            return resolved != null ? resolved : ref.substring(1);   // best-effort local-path fallback
        }
        return ref;
    }

    /** Resolve {@code stageName/relativePath} (the IMPORTS text after {@code @}) to a local path, or null if it can't be. */
    private static String resolveStageReference(final String stageRef, final Catalog catalog,
                                                final S3PathResolver s3Resolver) {
        if (catalog == null) {
            return null;
        }
        final int slash = stageRef.indexOf('/');
        final String stageName = slash >= 0 ? stageRef.substring(0, slash) : stageRef;
        final String relative = slash >= 0 ? stageRef.substring(slash + 1) : "";
        // User (@~) and table (@%name) stages are not modelled here; fall back to a local path.
        if (stageName.isEmpty() || stageName.startsWith("~") || stageName.startsWith("%")) {
            return null;
        }

        final Stage stage;
        try {
            // The entry names the stage as an identifier does: quoted parts kept, the rest folded.
            stage = catalog.getStage(SqlIdentifiers.canonicalText(stageName));
        } catch (final RuntimeException e) {
            logger.warn("IMPORTS stage lookup failed for '{}' ({}) — treating the reference as a local path",
                stageName, e.getMessage());
            return null;
        }
        if (stage == null) {
            logger.warn("IMPORTS stage '{}' not found in the catalog — treating the reference as a local path",
                stageName);
            return null;
        }

        final String url = stage.getUrl();
        final Path base;
        if (S3PathResolver.isS3Url(url)) {
            if (s3Resolver == null) {
                return null;
            }
            base = s3Resolver.toLocalPath(url);
        } else if (stage.getLocalPath() != null) {
            base = stage.getLocalPath();   // file:// (or otherwise local) stage
        } else {
            return null;
        }
        return relative.isEmpty() ? base.toString() : base.resolve(relative).toString();
    }

    /** What turns the imports of the routine running on this thread into local paths, or none. */
    private static final ThreadLocal<ImportLocalizer> LOCALIZER = new ThreadLocal<ImportLocalizer>();

    /**
     * The engine running a handler on this thread says how its imports are found.
     *
     * @param localizer the engine's localizer
     * @return the localizer in force until now, to hand to {@link #restore}
     */
    public static ImportLocalizer localizeWith(final ImportLocalizer localizer) {
        final ImportLocalizer outer = LOCALIZER.get();
        LOCALIZER.set(localizer);
        return outer;
    }

    /**
     * @param outer what {@link #localizeWith} answered
     */
    public static void restore(final ImportLocalizer outer) {
        if (outer == null) {
            LOCALIZER.remove();
        } else {
            LOCALIZER.set(outer);
        }
    }

    private JarHandlerLoader() {
    }
}
