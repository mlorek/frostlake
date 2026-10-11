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

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * An inline Java source is compiled once, whoever asks and however often: a later request for the same class
 * name and the same text is answered with what the first compilation produced, and each load of it is a class
 * of its own. Two sources that merely share a hash are two compilations.
 */
public class JavaCompiledSourcesTest {

    private static final String COUNTER = """
        class CompiledSourcesCounter {
            static int n = 0;
            public static int next() {
                n = n + 1;
                return n;
            }
        }
        """;

    private static final String FIRST_OF_A_HASH = """
        class CompiledSourcesTwin { public static String f() { return "Aa"; } }
        """;

    private static final String SECOND_OF_A_HASH = """
        class CompiledSourcesTwin { public static String f() { return "BB"; } }
        """;

    @Test
    public void aSourceIsCompiledOnce() {
        final Map<String, byte[]> first = new JavaFunctionCompiler().compileClasses(COUNTER,
            "CompiledSourcesCounter");
        assertSame(first, new JavaFunctionCompiler().compileClasses(COUNTER, "CompiledSourcesCounter"));
    }

    @Test
    public void everyLoadIsAClassOfItsOwn() throws ReflectiveOperationException {
        final JavaFunctionCompiler compiler = new JavaFunctionCompiler();
        final Class<?> one = compiler.load(COUNTER, "CompiledSourcesCounter", null);
        final Class<?> other = compiler.load(COUNTER, "CompiledSourcesCounter", null);
        assertNotSame(one, other);
        assertEquals(1, invoke(one, "next"));
        assertEquals(2, invoke(one, "next"));
        assertEquals(1, invoke(other, "next"));
    }

    @Test
    public void compileHandsEveryCallerTheSameClass() {
        final JavaFunctionCompiler compiler = new JavaFunctionCompiler();
        assertSame(compiler.compile(COUNTER, "CompiledSourcesCounter"),
            compiler.compile(COUNTER, "CompiledSourcesCounter"));
    }

    @Test
    public void twoSourcesThatShareAHashAreTwoCompilations() throws ReflectiveOperationException {
        assertEquals(FIRST_OF_A_HASH.hashCode(), SECOND_OF_A_HASH.hashCode());
        assertNotEquals(new JavaSourceKey("CompiledSourcesTwin", FIRST_OF_A_HASH),
            new JavaSourceKey("CompiledSourcesTwin", SECOND_OF_A_HASH));
        final JavaFunctionCompiler compiler = new JavaFunctionCompiler();
        assertEquals("Aa", invoke(compiler.compile(FIRST_OF_A_HASH, "CompiledSourcesTwin"), "f"));
        assertEquals("BB", invoke(compiler.compile(SECOND_OF_A_HASH, "CompiledSourcesTwin"), "f"));
        assertEquals("Aa", invoke(compiler.load(FIRST_OF_A_HASH, "CompiledSourcesTwin", null), "f"));
        assertEquals("BB", invoke(compiler.load(SECOND_OF_A_HASH, "CompiledSourcesTwin", null), "f"));
    }

    @Test
    public void aSourceIsCompiledOncePerLevel() {
        final JavaFunctionCompiler compiler = new JavaFunctionCompiler();
        final Map<String, byte[]> eleven = compiler.compileClasses(COUNTER, "CompiledSourcesCounter", 11, "");
        assertSame(eleven, compiler.compileClasses(COUNTER, "CompiledSourcesCounter", 11, ""));
        assertNotSame(eleven, compiler.compileClasses(COUNTER, "CompiledSourcesCounter", 17, ""));
        assertNotEquals(new JavaSourceKey("CompiledSourcesCounter", COUNTER, 11),
            new JavaSourceKey("CompiledSourcesCounter", COUNTER, 17));
    }

    private static Object invoke(final Class<?> handled, final String name) throws ReflectiveOperationException {
        final Method method = handled.getMethod(name);
        method.setAccessible(true);
        return method.invoke(null);
    }
}
