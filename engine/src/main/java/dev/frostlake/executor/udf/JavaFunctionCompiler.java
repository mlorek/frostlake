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

import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.io.File;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
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

    /**
     * Invokes a static method on a compiled class
     *
     * @param clazz the compiled class
     * @param methodName the name of the static method to invoke
     * @param args the arguments to pass to the method
     * @return the result of the method invocation
     */
    public Object invokeMethod(final Class<?> clazz, final String methodName, final Object... args) {
        Method method = null;
        try {
            final Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                // A null argument matches any reference parameter type (checked in the compatibility pass).
                paramTypes[i] = args[i] != null ? args[i].getClass() : null;
            }

            method = findMethod(clazz, methodName, paramTypes);
            if (method == null) {
                throw new RuntimeException("Method not found: " + methodName + " in class " + clazz.getName());
            }

            method.setAccessible(true);
            UdfConsoleCapture.enter();
            try {
                return method.invoke(null, adaptArgs(method.getParameterTypes(), args));
            } finally {
                UdfConsoleCapture.exit();
            }

        } catch (final InvocationTargetException e) {
            // Surface the handler's own exception, not the reflection wrapper (whose message is null).
            final Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("Java function handler "
                + (method != null ? method.getName() : methodName) + " threw: " + cause, cause);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("Failed to invoke Java function method: " + e.getMessage(), e);
        }
    }

    /**
     * Adapt argument values to the resolved method's parameter types where the SQL and JVM representations
     * differ: an ARRAY value (its JSON text, e.g. {@code ["a","b"]}) becomes the {@code String[]} that
     * Snowflake's Java UDF type mapping passes for ARRAY parameters.
     */
    private Object[] adaptArgs(final Class<?>[] paramTypes, final Object[] args) {
        Object[] adapted = args;
        for (int i = 0; i < args.length; i++) {
            if (paramTypes[i] == String[].class && args[i] instanceof String) {
                if (adapted == args) {
                    adapted = args.clone();
                }
                adapted[i] = jsonArrayToStringArray((String) args[i]);
            } else if (args[i] instanceof Number && isNumeric(paramTypes[i])) {
                if (adapted == args) {
                    adapted = args.clone();
                }
                adapted[i] = toNumber((Number) args[i], paramTypes[i]);
            }
        }
        return adapted;
    }

    /** Whether a parameter takes a number: a numeric primitive, its box, or an arbitrary-precision number. */
    private static boolean isNumeric(final Class<?> type) {
        return type == int.class || type == Integer.class || type == long.class || type == Long.class
            || type == short.class || type == Short.class || type == byte.class || type == Byte.class
            || type == double.class || type == Double.class || type == float.class || type == Float.class
            || type == BigDecimal.class || type == BigInteger.class;
    }

    /** A SQL number as the Java number a handler's parameter takes: a NUMBER is an int, a long or a BigDecimal alike. */
    private static Object toNumber(final Number value, final Class<?> type) {
        if (type == int.class || type == Integer.class) {
            return Integer.valueOf(value.intValue());
        }
        if (type == long.class || type == Long.class) {
            return Long.valueOf(value.longValue());
        }
        if (type == short.class || type == Short.class) {
            return Short.valueOf(value.shortValue());
        }
        if (type == byte.class || type == Byte.class) {
            return Byte.valueOf(value.byteValue());
        }
        if (type == double.class || type == Double.class) {
            return Double.valueOf(value.doubleValue());
        }
        if (type == float.class || type == Float.class) {
            return Float.valueOf(value.floatValue());
        }
        if (type == BigDecimal.class) {
            return value instanceof BigDecimal ? value : new BigDecimal(value.toString());
        }
        return value instanceof BigInteger ? value : new BigDecimal(value.toString()).toBigInteger();
    }

    /** Parse a JSON array text into its element strings (textual elements unquoted, others as JSON text). */
    private String[] jsonArrayToStringArray(final String jsonArrayText) {
        final ArrayNode array = ArrayFunctionHelper.parseArray(jsonArrayText);
        if (array == null) {
            throw new RuntimeException("Cannot pass a non-array value to a String[] parameter: " + jsonArrayText);
        }
        final String[] out = new String[array.size()];
        for (int i = 0; i < array.size(); i++) {
            final JsonNode element = array.get(i);
            out[i] = element.isNull() ? null : element.isTextual() ? element.asText() : element.toString();
        }
        return out;
    }

    private Method findMethod(final Class<?> clazz, final String methodName, final Class<?>[] paramTypes) {
        try {
            // The exact lookup cannot express "null argument" — fall through to the compatibility scan.
            for (final Class<?> paramType : paramTypes) {
                if (paramType == null) {
                    throw new NoSuchMethodException();
                }
            }
            return clazz.getMethod(methodName, paramTypes);
        } catch (final NoSuchMethodException e) {
            // Try to find a compatible method considering primitive/boxed type conversions
            for (final Method method : clazz.getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == paramTypes.length) {
                    final Class<?>[] methodParamTypes = method.getParameterTypes();
                    boolean compatible = true;
                    for (int i = 0; i < paramTypes.length; i++) {
                        if (!isCompatibleType(paramTypes[i], methodParamTypes[i])) {
                            compatible = false;
                            break;
                        }
                    }
                    if (compatible) {
                        return method;
                    }
                }
            }
            return null;
        }
    }

    private boolean isCompatibleType(final Class<?> argType, final Class<?> paramType) {
        if (argType == null) {
            return !paramType.isPrimitive();
        }
        if (paramType.isAssignableFrom(argType)) {
            return true;
        }
        // A SQL number is handed to whichever Java number the handler declares - see adaptArgs.
        if (Number.class.isAssignableFrom(argType) && isNumeric(paramType)) {
            return true;
        }
        // A SQL ARRAY value (JSON text) is adaptable to a String[] parameter — see adaptArgs.
        if (paramType == String[].class && argType == String.class) {
            return true;
        }
        // Check primitive/boxed type conversions
        if (paramType == int.class && argType == Integer.class) {
            return true;
        }
        if (paramType == long.class && argType == Long.class) {
            return true;
        }
        if (paramType == double.class && argType == Double.class) {
            return true;
        }
        if (paramType == float.class && argType == Float.class) {
            return true;
        }
        if (paramType == boolean.class && argType == Boolean.class) {
            return true;
        }
        if (paramType == byte.class && argType == Byte.class) {
            return true;
        }
        if (paramType == short.class && argType == Short.class) {
            return true;
        }
        if (paramType == char.class && argType == Character.class) {
            return true;
        }
        return false;
    }

}
