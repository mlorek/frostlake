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

import java.io.File;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Compiles and executes inline Java user-defined functions
 */
public class JavaFunctionCompiler {

    private static final int MAX_COMPILED_CLASSES = 256;

    private final Map<JavaSourceKey, Class<?>> compiledClasses;

    public JavaFunctionCompiler() {
        // Bounded, synchronized LRU: this compiler is held in a static field (shared across sessions),
        // so an unbounded map would retain every distinct compiled UDF class indefinitely — and each
        // class pins its InMemoryClassLoader, leaking metaspace. The cap + LRU eviction lets stale
        // classes (and their loaders) be collected; the wrapper makes concurrent access safe.
        this.compiledClasses = Collections.synchronizedMap(
            new LinkedHashMap<JavaSourceKey, Class<?>>(MAX_COMPILED_CLASSES + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<JavaSourceKey, Class<?>> eldest) {
                    return size() > MAX_COMPILED_CLASSES;
                }
            });
    }

    /**
     * Compiles Java source code at the JDK's own level and caches the resulting class.
     *
     * @param sourceCode the Java source code
     * @param className the fully qualified class name to compile
     * @return the compiled Class object
     */
    public Class<?> compile(final String sourceCode, final String className) {
        return compile(sourceCode, className, 0, "");
    }

    /**
     * Compiles Java source code and caches the resulting class: every caller of one source is handed the same
     * class, static state included. A caller that wants the class as a statement or a CALL of its own sees it
     * asks {@link #load(String, String, ClassLoader, int, String)}.
     *
     * @param sourceCode the Java source code
     * @param className the fully qualified class name to compile
     * @param release   the language level (see {@link JavaRuntimeLevel}), or 0 for the JDK's own
     * @param where     the end of a refusal's sentence, {@code  in function F with handler H.f}
     * @return the compiled Class object
     */
    public Class<?> compile(final String sourceCode, final String className, final int release,
                            final String where) {
        // The whole source names the entry, so the same class name may have different implementations and two
        // sources that share a hash stay apart.
        final JavaSourceKey cacheKey = new JavaSourceKey(className, sourceCode, release);
        final Class<?> cached = compiledClasses.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        final Class<?> compiledClass = load(sourceCode, className, null, release, where);
        compiledClasses.put(cacheKey, compiledClass);
        return compiledClass;
    }

    /**
     * Loads a source's classes anew at the JDK's own level.
     *
     * @param sourceCode the Java source code
     * @param className  the class the handler names
     * @param parent     the loader every other class comes from, or null for the system's
     * @return the handler's class
     */
    public Class<?> load(final String sourceCode, final String className, final ClassLoader parent) {
        return load(sourceCode, className, parent, 0, "");
    }

    /**
     * Loads a source's classes anew, compiling it only when it has not been compiled before: the class handed
     * back shares nothing with one loaded earlier, so its static fields start as the source writes them.
     *
     * @param sourceCode the Java source code
     * @param className  the class the handler names
     * @param parent     the loader every other class comes from, or null for the system's
     * @param release    the language level (see {@link JavaRuntimeLevel}), or 0 for the JDK's own
     * @param where      the end of a refusal's sentence, {@code  in function F with handler H.f}
     * @return the handler's class
     */
    public Class<?> load(final String sourceCode, final String className, final ClassLoader parent,
                         final int release, final String where) {
        return load(sourceCode, className, parent, release, where, true);
    }

    /**
     * Loads a source's classes anew (see {@link #load(String, String, ClassLoader, int, String)}), compiled with or
     * without Snowpark's classes on the class path.
     *
     * @param sourceCode the Java source code
     * @param className  the class the handler names
     * @param parent     the loader every other class comes from, or null for the system's
     * @param release    the language level (see {@link JavaRuntimeLevel}), or 0 for the JDK's own
     * @param where      the end of a refusal's sentence, {@code  in function F with handler H.f}
     * @param snowpark   whether the routine names Snowpark (see {@link SnowparkPackage})
     * @return the handler's class
     */
    public Class<?> load(final String sourceCode, final String className, final ClassLoader parent,
                         final int release, final String where, final boolean snowpark) {
        // Every class the source declares is loaded with the handler's: a nested class, an enum, an
        // anonymous class and another top-level class are part of the body the handler runs.
        final Map<String, byte[]> classes = compileClasses(sourceCode, className, release, where, snowpark);
        if (!classes.containsKey(className)) {
            throw new RuntimeException("No class bytes generated for: " + className);
        }
        final InMemoryClassLoader classLoader = parent == null ? new InMemoryClassLoader(classes)
            : new InMemoryClassLoader(classes, parent);
        try {
            return classLoader.loadClass(className);
        } catch (final ClassNotFoundException | LinkageError e) {
            throw new RuntimeException("Failed to compile Java function: " + e.getMessage(), e);
        }
    }

    /**
     * Compiles Java source code at the JDK's own level and hands back every class it produced.
     *
     * @param sourceCode the Java source code
     * @param className the class the source declares
     * @return the classes' bytes by binary name, in name order
     */
    public Map<String, byte[]> compileClasses(final String sourceCode, final String className) {
        return compileClasses(sourceCode, className, 0, "");
    }

    /**
     * Compiles Java source code and hands back every class it produced. A source is compiled once: a later
     * request for the same class name, the same text and the same level is answered from what the first
     * produced. A body that does not compile is refused with the compiler's own report, as an account's is
     * (live-verified): each fault's place in {@code InlineCode.java}, its line and a mark under the place, the
     * compiler's notes and its count of errors.
     *
     * @param sourceCode the Java source code
     * @param className the class the source declares
     * @param release   the language level (see {@link JavaRuntimeLevel}), or 0 for the JDK's own
     * @param where     the end of the refusal's sentence, {@code  in function F with handler H.f}
     * @return the classes' bytes by binary name, in name order
     */
    public Map<String, byte[]> compileClasses(final String sourceCode, final String className, final int release,
                                              final String where) {
        return compileClasses(sourceCode, className, release, where, true);
    }

    /**
     * Compiles Java source code and hands back every class it produced (see
     * {@link #compileClasses(String, String, int, String)}), with or without Snowpark's classes on the class path.
     *
     * @param sourceCode the Java source code
     * @param className  the class the source declares
     * @param release    the language level (see {@link JavaRuntimeLevel}), or 0 for the JDK's own
     * @param where      the end of the refusal's sentence, {@code  in function F with handler H.f}
     * @param snowpark   whether the routine names Snowpark (see {@link SnowparkPackage})
     * @return the classes' bytes by binary name, in name order
     */
    public Map<String, byte[]> compileClasses(final String sourceCode, final String className, final int release,
                                              final String where, final boolean snowpark) {
        final JavaSourceKey key = new JavaSourceKey(className, sourceCode, release, snowpark);
        final Map<String, byte[]> kept = JavaCompiledSources.get(key);
        if (kept != null) {
            return kept;
        }
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new RuntimeException("Java compiler not available. Make sure you are running with JDK, not JRE.");
        }
        final InMemoryJavaFileManager fileManager = new InMemoryJavaFileManager(
            compiler.getStandardFileManager(null, null, null), snowpark);
        // The compiler's report is KEPT rather than left to go to stderr: a real account puts it in the error
        // it raises ("Error while compiling source: …"), and a body rejected at CREATE is useless to the caller
        // without knowing which line broke it.
        final StringWriter report = new StringWriter();
        final List<String> options = new ArrayList<>(Arrays.asList("-classpath", classpath()));
        if (release > 0) {
            options.add("--release");
            options.add(String.valueOf(release));
        }
        final JavaCompiler.CompilationTask task = compiler.getTask(report, fileManager, null, options, null,
            Arrays.asList(new InMemoryJavaFile(sourceCode)));
        if (!task.call()) {
            final String printed = report.toString();
            throw new RuntimeException("Error while compiling source: " + printed
                + (printed.endsWith("\n") || where.isEmpty() ? "" : "\n") + where);
        }
        final Map<String, byte[]> classes = Collections.unmodifiableMap(fileManager.getAllClassBytes());
        JavaCompiledSources.put(key, classes);
        return classes;
    }

    /**
     * The classes a source compiles against: the JVM's class path and, where the thread's loader reads further
     * places, those too.
     */
    private static String classpath() {
        final StringBuilder path = new StringBuilder(System.getProperty("java.class.path", ""));
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader instanceof URLClassLoader) {
            for (final URL url : ((URLClassLoader) loader).getURLs()) {
                path.append(File.pathSeparator).append(url.getPath());
            }
        }
        return path.toString();
    }

    /**
     * The handler method of a class: its one public method of a name that takes a number of arguments, inherited
     * ones included.
     *
     * @param clazz      the handler's class
     * @param methodName the handler method's name
     * @param arity      how many arguments the routine declares
     * @return the method, or null when the class has none or several
     */
    public Method handlerMethod(final Class<?> clazz, final String methodName, final int arity) {
        Method found = null;
        for (final Method method : clazz.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == arity) {
                if (found != null) {
                    return null;
                }
                found = method;
            }
        }
        return found;
    }

    /**
     * Calls a handler method with arguments already of its parameter types. What the handler raises reaches SQL
     * as its report (see {@link JavaHandlerFailure}).
     *
     * @param handled  the handler's class
     * @param method   the handler method
     * @param receiver the instance to call it on, or null for a static method
     * @param args     the arguments
     * @param where    the end of the report's sentence, {@code  in function F with handler H.f}
     * @return what the handler answers
     */
    public Object invokeHandler(final Class<?> handled, final Method method, final Object receiver,
                                final Object[] args, final String where) {
        try {
            method.setAccessible(true);
            UdfConsoleCapture.enter();
            try {
                return method.invoke(receiver, args);
            } finally {
                UdfConsoleCapture.exit();
            }
        } catch (final InvocationTargetException thrown) {
            final Throwable cause = thrown.getCause() != null ? thrown.getCause() : thrown;
            throw new RuntimeException(JavaHandlerFailure.report(cause, handled, where), cause);
        } catch (final IllegalAccessException | IllegalArgumentException failed) {
            throw new RuntimeException("Failed to invoke Java function method: " + failed.getMessage(), failed);
        }
    }
}
