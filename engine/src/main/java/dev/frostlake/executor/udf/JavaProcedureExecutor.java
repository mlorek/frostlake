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

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Comparator;

/**
 * Executes inline Java stored procedures by compiling them at runtime.
 * The HANDLER clause specifies 'ClassName.methodName'; the method must accept
 * a {@link Session} as first argument followed by the procedure parameters.
 */
public class JavaProcedureExecutor {

    private static final Logger logger = LoggerFactory.getLogger(JavaProcedureExecutor.class);

    public static Object executeJavaProcedure(final Procedure procedure, final List<Object> arguments,
                                              final DatabaseEngine engine) {
        String handler = procedure.getHandler();
        if (handler == null || !handler.contains(".")) {
            throw new RuntimeException(
                "HANDLER must be specified as 'ClassName.methodName' for LANGUAGE JAVA procedures");
        }

        int dot = handler.lastIndexOf('.');
        String className = handler.substring(0, dot);
        String methodName = handler.substring(dot + 1);

        String body = procedure.getBody();

        Path tempDir = null;
        try {
            Class<?> compiledClass;
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
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new RuntimeException(
                "Java compiler not available. Make sure you are running on a JDK (not JRE).");
        }

        // Write source file
        String simpleClassName = className.contains(".") ? className.substring(className.lastIndexOf('.') + 1) : className;
        Path sourceFile = tempDir.resolve(simpleClassName + ".java");
        Files.writeString(sourceFile, sourceCode);

        // Build classpath from current classloader
        String classpath = buildClasspath();

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<String> options = Arrays.asList("-classpath", classpath, "-d", tempDir.toString());

        try (var fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            var compilationUnits = fileManager.getJavaFileObjects(sourceFile.toFile());
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options,
                null, compilationUnits);

            if (!task.call()) {
                StringBuilder errors = new StringBuilder("Compilation failed:\n");
                for (final var d : diagnostics.getDiagnostics()) {
                    errors.append(d.toString()).append("\n");
                }
                throw new RuntimeException(errors.toString());
            }
        }

        // Load compiled class
        URLClassLoader classLoader = new URLClassLoader(
            new URL[]{tempDir.toUri().toURL()},
            Thread.currentThread().getContextClassLoader()
        );
        return classLoader.loadClass(className);
    }

    private static Object invokeHandler(final Class<?> clazz, final String methodName,
                                        final List<Object> arguments, final List<Parameter> parameters,
                                        final DatabaseEngine engine, final boolean ownersRights) throws Exception {
        Session session = new Session(engine, ownersRights);

        // Build argument array: session + procedure params
        Object[] args = new Object[arguments.size() + 1];
        args[0] = session;
        for (int i = 0; i < arguments.size(); i++) {
            args[i + 1] = arguments.get(i);
        }

        // Find matching method: first parameter must be Session
        Method target = findMethod(clazz, methodName, args.length);

        Object instance = clazz.getDeclaredConstructor().newInstance();
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
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        StringBuilder cp = new StringBuilder(System.getProperty("java.class.path", ""));

        if (cl instanceof URLClassLoader urlCl) {
            for (final URL url : urlCl.getURLs()) {
                cp.append(File.pathSeparator).append(url.getPath());
            }
        }
        return cp.toString();
    }

    private static void deleteTempDir(final Path dir) {
        try {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach((final var p) -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (final Exception ignored) {
                    }
                });
        } catch (final Exception ignored) {
        }
    }
}
