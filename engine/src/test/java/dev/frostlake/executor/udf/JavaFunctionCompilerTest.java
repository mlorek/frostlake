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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The compiler's own contract, below the SQL surface: what it answers for a handler it cannot pick, an argument
 * the handler cannot take, a class that compiles against a loader the JVM's class path does not hold, and a
 * cache that has to let go of old classes.
 *
 * <p>None of these is reachable through CREATE FUNCTION and a call. CREATE refuses an inline body whose handler
 * names zero or several methods of one arity, the call path converts every argument to the handler's own
 * parameter types first, and a test JVM's context loader is never a {@link URLClassLoader} — which is exactly
 * the loader an application server or a build tool's test worker hands the engine.
 *
 * <p>Every class name here is unique to this file: the compiled bytes of a source are kept JVM-wide, and a
 * test fork runs many classes in one JVM.
 */
public class JavaFunctionCompilerTest {

    /** How many compiled classes the compiler keeps before it evicts the least recently used. */
    private static final int CACHE_CAPACITY = 256;

    private final JavaFunctionCompiler compiler = new JavaFunctionCompiler();

    /** Two public methods of one name and one arity: the handler is ambiguous, so there is none to hand back. */
    @Test
    public void twoMethodsOfOneArityLeaveNoHandler() {
        final Class<?> overloaded = compiler.compile("""
            public class JfcOverloaded {
                public static int f(final int x) { return x; }
                public static String f(final String x) { return x; }
                public static int f(final int x, final int y) { return x + y; }
            }
            """, "JfcOverloaded");

        assertNull(compiler.handlerMethod(overloaded, "f", 1), "two candidates take one argument");
    }

    /** The arity picks among methods of one name, so another arity's overloads do not count against it. */
    @Test
    public void theArityPicksAmongOverloadsOfOneName() {
        final Class<?> overloaded = compiler.compile("""
            public class JfcByArity {
                public static int f(final int x) { return x; }
                public static String f(final String x) { return x; }
                public static int f(final int x, final int y) { return x + y; }
            }
            """, "JfcByArity");

        final Method pair = compiler.handlerMethod(overloaded, "f", 2);
        assertNotNull(pair, "exactly one method takes two arguments");
        assertEquals(2, pair.getParameterCount());
        assertNull(compiler.handlerMethod(overloaded, "f", 3), "none takes three");
        assertNull(compiler.handlerMethod(overloaded, "g", 1), "none has that name");
    }

    /** An argument the handler's parameter cannot take is the caller's fault, reported as such — not the handler's. */
    @Test
    public void anArgumentOfTheWrongTypeIsAnInvocationFailure() throws NoSuchMethodException {
        final Class<?> typed = compiler.compile("""
            public class JfcTyped {
                public static int twice(final int x) { return 2 * x; }
            }
            """, "JfcTyped");
        final Method twice = typed.getMethod("twice", int.class);

        final RuntimeException mismatched = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                compiler.invokeHandler(typed, twice, null, new Object[] {"seven"}, "");
            }
        });
        assertTrue(mismatched.getMessage().startsWith("Failed to invoke Java function method: "),
            mismatched.getMessage());
        assertInstanceOf(IllegalArgumentException.class, mismatched.getCause());

        final RuntimeException missing = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                compiler.invokeHandler(typed, twice, null, new Object[0], "");
            }
        });
        assertInstanceOf(IllegalArgumentException.class, missing.getCause(), "a missing argument fails the same way");

        assertEquals(14, compiler.invokeHandler(typed, twice, null, new Object[] {7}, ""),
            "the right argument still answers");
    }

    /**
     * A source compiles against the classes the thread's loader reads, not only the JVM's class path, so a handler
     * may extend a class only that loader holds. Loaded under the system loader, the class cannot find its
     * superclass, and that is a refusal of its own; loaded under the loader that holds the superclass, it runs.
     */
    @Test
    public void aContextLoadersClassesAreOnTheCompilersClassPath() throws Exception {
        final Path dir = Files.createTempDirectory("jfc_context_loader");
        try {
            writeClasses(dir, compiler.compileClasses("""
                public class JfcContextBase {
                    public static String tag() { return "base"; }
                }
                """, "JfcContextBase"));
            final String child = """
                public class JfcContextChild extends JfcContextBase {
                    public static String both() { return tag() + "+child"; }
                }
                """;
            final Thread thread = Thread.currentThread();
            final ClassLoader previous = thread.getContextClassLoader();
            try (URLClassLoader holder = new URLClassLoader(new URL[] {dir.toUri().toURL()},
                    JavaFunctionCompilerTest.class.getClassLoader())) {
                thread.setContextClassLoader(holder);

                final RuntimeException unlinked = assertThrows(RuntimeException.class, new Executable() {
                    @Override
                    public void execute() {
                        compiler.load(child, "JfcContextChild", null);
                    }
                });
                assertTrue(unlinked.getMessage().startsWith("Failed to compile Java function: "),
                    unlinked.getMessage());
                assertInstanceOf(LinkageError.class, unlinked.getCause());
                assertTrue(unlinked.getMessage().contains("JfcContextBase"), unlinked.getMessage());

                final Class<?> linked = compiler.load(child, "JfcContextChild", holder);
                assertEquals("base+child", linked.getMethod("both").invoke(null));
            } finally {
                thread.setContextClassLoader(previous);
            }
        } finally {
            deleteRecursively(dir.toFile());
        }
    }

    /**
     * The compiler hands every caller of one source the same class, but keeps only so many: past its capacity the
     * least recently used class is let go, and asking for its source again compiles a class of its own.
     */
    @Test
    public void theClassCacheEvictsTheLeastRecentlyUsedSource() {
        final String first = "public class JfcEvicted { public static int v() { return 0; } }";
        final Class<?> original = compiler.compile(first, "JfcEvicted");
        assertSame(original, compiler.compile(first, "JfcEvicted"), "a second request is answered from the cache");

        for (int i = 1; i <= CACHE_CAPACITY; i++) {
            compiler.compile("public class JfcFiller" + i + " { public static int v() { return " + i + "; } }",
                "JfcFiller" + i);
        }

        final Class<?> again = compiler.compile(first, "JfcEvicted");
        assertNotSame(original, again, "the eldest class was evicted, so the source yields a new class");
        assertEquals("JfcEvicted", again.getName());
    }

    /** Writes compiled classes under their binary names, as a class-path directory holds them. */
    private static void writeClasses(final Path dir, final Map<String, byte[]> classes) throws IOException {
        for (final Map.Entry<String, byte[]> entry : classes.entrySet()) {
            final Path file = dir.resolve(entry.getKey().replace('.', File.separatorChar) + ".class");
            Files.createDirectories(file.getParent());
            Files.write(file, entry.getValue());
        }
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
