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
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * dependency JARs beside the handler JAR and they link. The parent classloader is the engine's, so
 * handlers can reference engine/Snowpark classes. See docs/functions.md.
 */
public final class JarHandlerLoader {

    private static final Logger logger = LoggerFactory.getLogger(JarHandlerLoader.class);

    /** Load {@code className} (trying the Scala {@code object} singleton {@code className$} first) from the IMPORTS jar(s). */
    public static Class<?> load(final List<String> imports, final String className,
                                final Catalog catalog, final S3PathResolver s3Resolver) {
        if (imports == null || imports.isEmpty()) {
            throw new RuntimeException("No IMPORTS jar specified for handler class " + className);
        }
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
                if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
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
        final List<URL> urls = new ArrayList<>();
        for (final String localPath : localPaths) {
            try {
                urls.add(new File(localPath).toURI().toURL());
            } catch (final MalformedURLException e) {
                throw new RuntimeException("Invalid IMPORTS path: " + localPath, e);
            }
        }
        final URLClassLoader loader = new URLClassLoader(
            urls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
        try {
            return loader.loadClass(className + "$");   // Scala object singleton, if present
        } catch (final ClassNotFoundException e) {
            try {
                return loader.loadClass(className);      // plain Java/Scala class
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
                throw new RuntimeException(
                    "Handler class '" + className + "' not found in IMPORTS jar(s) " + imports
                    + " (resolved to: " + resolved + ")", e2);
            }
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
            stage = catalog.getStage(stageName);
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

    private JarHandlerLoader() {
    }
}
