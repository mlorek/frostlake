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

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;

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
        String cacheKey = className + "_" + sourceCode.hashCode();
        final Class<?> cached = compiledClasses.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new RuntimeException("Java compiler not available. Make sure you are running with JDK, not JRE.");
            }

            InMemoryJavaFileManager fileManager = new InMemoryJavaFileManager(
                compiler.getStandardFileManager(null, null, null)
            );

            JavaFileObject javaFile = new InMemoryJavaFile(className, sourceCode);
            JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                fileManager,
                null,
                null,
                null,
                Arrays.asList(javaFile)
            );

            boolean success = task.call();
            if (!success) {
                throw new RuntimeException("Compilation failed for class: " + className);
            }

            byte[] classBytes = fileManager.getClassBytes(className);
            if (classBytes == null) {
                throw new RuntimeException("No class bytes generated for: " + className);
            }

            InMemoryClassLoader classLoader = new InMemoryClassLoader(classBytes, className);
            Class<?> compiledClass = classLoader.loadClass(className);

            compiledClasses.put(cacheKey, compiledClass);
            return compiledClass;

        } catch (final Exception e) {
            throw new RuntimeException("Failed to compile Java function: " + e.getMessage(), e);
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
        try {
            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
            }

            Method method = findMethod(clazz, methodName, paramTypes);
            if (method == null) {
                throw new RuntimeException("Method not found: " + methodName + " in class " + clazz.getName());
            }

            method.setAccessible(true);
            return method.invoke(null, args);

        } catch (final Exception e) {
            throw new RuntimeException("Failed to invoke Java function method: " + e.getMessage(), e);
        }
    }

    private Method findMethod(final Class<?> clazz, final String methodName, final Class<?>[] paramTypes) {
        try {
            return clazz.getMethod(methodName, paramTypes);
        } catch (final NoSuchMethodException e) {
            // Try to find a compatible method considering primitive/boxed type conversions
            for (final Method method : clazz.getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == paramTypes.length) {
                    Class<?>[] methodParamTypes = method.getParameterTypes();
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

    /**
     * In-memory Java file object
     */
    private static class InMemoryJavaFile extends SimpleJavaFileObject {
        private final String sourceCode;

        public InMemoryJavaFile(final String className, final String sourceCode) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.sourceCode = sourceCode;
        }

        @Override
        public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
            return sourceCode;
        }
    }

    /**
     * In-memory class loader
     */
    private static class InMemoryClassLoader extends ClassLoader {
        private final byte[] classBytes;
        private final String className;

        public InMemoryClassLoader(final byte[] classBytes, final String className) {
            this.classBytes = classBytes;
            this.className = className;
        }

        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
            if (name.equals(className)) {
                return defineClass(name, classBytes, 0, classBytes.length);
            }
            return super.findClass(name);
        }
    }

    /**
     * In-memory file manager
     */
    private static class InMemoryJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, ByteArrayOutputStream> classBytes;

        public InMemoryJavaFileManager(final StandardJavaFileManager fileManager) {
            super(fileManager);
            this.classBytes = new HashMap<>();
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                final Location location,
                final String className,
                final JavaFileObject.Kind kind,
                final FileObject sibling) {

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            classBytes.put(className, baos);

            return new SimpleJavaFileObject(URI.create("string:///" + className), kind) {
                @Override
                public OutputStream openOutputStream() {
                    return baos;
                }
            };
        }

        public byte[] getClassBytes(final String className) {
            ByteArrayOutputStream baos = classBytes.get(className);
            return baos != null ? baos.toByteArray() : null;
        }
    }
}
