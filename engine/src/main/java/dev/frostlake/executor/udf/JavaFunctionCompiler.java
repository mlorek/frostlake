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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

/**
 * Compiles and executes inline Java user-defined functions
 */
public class JavaFunctionCompiler {

    private static final int MAX_COMPILED_CLASSES = 256;

    private final Map<String, Class<?>> compiledClasses;

    public JavaFunctionCompiler() {
        // Bounded, synchronized LRU: this compiler is held in a static field (shared across sessions),
        // so an unbounded map would retain every distinct compiled UDF class indefinitely — and each
        // class pins its InMemoryClassLoader, leaking metaspace. The cap + LRU eviction lets stale
        // classes (and their loaders) be collected; the wrapper makes concurrent access safe.
        this.compiledClasses = Collections.synchronizedMap(
            new LinkedHashMap<String, Class<?>>(MAX_COMPILED_CLASSES + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, Class<?>> eldest) {
                    return size() > MAX_COMPILED_CLASSES;
                }
            });
    }

    /**
     * Compiles Java source code and caches the resulting class
     *
     * @param sourceCode the Java source code
     * @param className the fully qualified class name to compile
     * @return the compiled Class object
     */
    public Class<?> compile(final String sourceCode, final String className) {
        // Use source code hash in cache key to allow same class name with different implementations
        final String cacheKey = className + "_" + sourceCode.hashCode();
        final Class<?> cached = compiledClasses.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new RuntimeException("Java compiler not available. Make sure you are running with JDK, not JRE.");
            }

            final InMemoryJavaFileManager fileManager = new InMemoryJavaFileManager(
                compiler.getStandardFileManager(null, null, null)
            );

            // Diagnostics are COLLECTED rather than left to go to stderr: a real account puts the
            // compiler's own complaint in the error it raises ("Error while compiling source: …"), and
            // a body rejected at CREATE is useless to the caller without knowing which line broke it.
            final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            final JavaFileObject javaFile = new InMemoryJavaFile(className, sourceCode);
            final JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                null,
                null,
                Arrays.asList(javaFile)
            );

            final boolean success = task.call();
            if (!success) {
                throw new RuntimeException("Error while compiling source: " + describe(diagnostics));
            }

            final byte[] classBytes = fileManager.getClassBytes(className);
            if (classBytes == null) {
                throw new RuntimeException("No class bytes generated for: " + className);
            }

            final InMemoryClassLoader classLoader = new InMemoryClassLoader(classBytes, className);
            final Class<?> compiledClass = classLoader.loadClass(className);

            compiledClasses.put(cacheKey, compiledClass);
            return compiledClass;

        } catch (final RuntimeException alreadyDescribed) {
            // A compile diagnostic already reads the way a real account's does; wrapping it a second
            // time ("Failed to compile Java function: Error while compiling source: …") only doubles it.
            throw alreadyDescribed;
        } catch (final Exception e) {
            throw new RuntimeException("Failed to compile Java function: " + e.getMessage(), e);
        }
    }

    /** The compiler's own complaints, one per line, as the raised error will quote them. */
    private static String describe(final DiagnosticCollector<JavaFileObject> diagnostics) {
        final StringBuilder text = new StringBuilder();
        for (final Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(diagnostic.getMessage(null));
        }
        return text.length() == 0 ? "no diagnostic reported" : text.toString();
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
            }
        }
        return adapted;
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
