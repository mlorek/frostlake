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

import com.snowflake.snowpark_java.Session;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

/**
 * Executes inline Java stored procedures by compiling them at runtime.
 * The HANDLER clause specifies 'ClassName.methodName'; the method must accept
 * a {@link Session} as first argument followed by the procedure parameters.
 */
public final class JavaProcedureExecutor {

    /** Static helpers only — never instantiated. */
    private JavaProcedureExecutor() {
    }

    private static final Logger logger = LoggerFactory.getLogger(JavaProcedureExecutor.class);

    public static Object executeJavaProcedure(final Procedure procedure, final List<Object> arguments,
                                              final DatabaseEngine engine) {
        final String handler = procedure.getHandler();
        if (handler == null || !handler.contains(".")) {
            throw new RuntimeException(
                "HANDLER must be specified as 'ClassName.methodName' for LANGUAGE JAVA procedures");
        }

        final int dot = handler.lastIndexOf('.');
        final String className = handler.substring(0, dot);
        final String methodName = handler.substring(dot + 1);

        final String body = procedure.getBody();

        Path tempDir = null;
        try {
            final Class<?> compiledClass;
            if (!procedure.getImports().isEmpty()) {
                compiledClass = JarHandlerLoader.load(procedure.getImports(), className,
                    engine != null ? engine.getCatalog() : null,
                    engine != null ? engine.getS3PathResolver() : null);
            } else {
                tempDir = Files.createTempDirectory("java_proc_");
                compiledClass = compileAndLoad(body, className, tempDir);
            }
            return invokeHandler(compiledClass, methodName, arguments, procedure.getParameters(), engine,
                "OWNER".equalsIgnoreCase(procedure.getExecuteAs()));
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("Failed to execute Java procedure " + procedure.getName() + ": " + e.getMessage(), e);
        } finally {
            if (tempDir != null) {
                deleteTempDir(tempDir);
            }
        }
    }

    private static Class<?> compileAndLoad(final String sourceCode, final String className,
                                           final Path tempDir) throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new RuntimeException(
                "Java compiler not available. Make sure you are running on a JDK (not JRE).");
        }

        // Write source file
        final String simpleClassName = className.contains(".") ? className.substring(className.lastIndexOf('.') + 1) : className;
        final Path sourceFile = tempDir.resolve(simpleClassName + ".java");
        Files.writeString(sourceFile, sourceCode);

        // Build classpath from current classloader
        final String classpath = buildClasspath();

        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        final List<String> options = Arrays.asList("-classpath", classpath, "-d", tempDir.toString());

        try (var fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            final var compilationUnits = fileManager.getJavaFileObjects(sourceFile.toFile());
            final JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options,
                null, compilationUnits);

            if (!task.call()) {
                final StringBuilder errors = new StringBuilder("Compilation failed:\n");
                for (final var d : diagnostics.getDiagnostics()) {
                    errors.append(d.toString()).append("\n");
                }
                throw new RuntimeException(errors.toString());
            }
        }

        // Load compiled class
        final URLClassLoader classLoader = new URLClassLoader(
            new URL[]{tempDir.toUri().toURL()},
            Thread.currentThread().getContextClassLoader()
        );
        return classLoader.loadClass(className);
    }

    private static Object invokeHandler(final Class<?> clazz, final String methodName,
                                        final List<Object> arguments, final List<Parameter> parameters,
                                        final DatabaseEngine engine, final boolean ownersRights) throws Exception {
        final Session session = new Session(engine, ownersRights);

        // Build argument array: session + procedure params
        final Object[] args = new Object[arguments.size() + 1];
        args[0] = session;
        for (int i = 0; i < arguments.size(); i++) {
            args[i + 1] = arguments.get(i);
        }

        // Find matching method: first parameter must be Session
        final Method target = findMethod(clazz, methodName, args.length);

        // The handler class does not have to be public — Snowflake accepts a package-private one, and a
        // body written as a bare "class H { … }" is the common shape in its own examples. Reflection from
        // this package cannot touch such a class's members without being told to.
        final Constructor<?> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        final Object instance = constructor.newInstance();
        target.setAccessible(true);
        UdfConsoleCapture.enter();
        try {
            return target.invoke(instance, args);
        } catch (final InvocationTargetException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause != null) {
                throw new RuntimeException(cause.getMessage(), cause);
            }
            throw e;
        } finally {
            UdfConsoleCapture.exit();
        }
    }

    private static Method findMethod(final Class<?> clazz, final String methodName, final int argCount) {
        for (final Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == argCount) {
                return m;
            }
        }
        throw new RuntimeException("Method " + methodName + " with " + argCount
            + " parameters not found in class " + clazz.getName());
    }

    private static String buildClasspath() {
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        final StringBuilder cp = new StringBuilder(System.getProperty("java.class.path", ""));

        if (cl instanceof URLClassLoader urlCl) {
            for (final URL url : urlCl.getURLs()) {
                cp.append(File.pathSeparator).append(url.getPath());
            }
        }
        return cp.toString();
    }

    private static void deleteTempDir(final Path dir) {
        try {
            // Deepest paths first, so a directory is only removed once it is empty.
            final List<Path> paths = new ArrayList<>();
            collectPaths(dir, paths);
            Collections.sort(paths, Collections.reverseOrder());
            for (final Path p : paths) {
                try {
                    Files.deleteIfExists(p);
                } catch (final Exception ignored) {
                }
            }
        } catch (final Exception ignored) {
        }
    }

    /** Every path under {@code dir}, the directory itself included, in no particular order. */
    private static void collectPaths(final Path dir, final List<Path> out) throws IOException {
        out.add(dir);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> children = Files.newDirectoryStream(dir)) {
            for (final Path child : children) {
                collectPaths(child, out);
            }
        }
    }
}
