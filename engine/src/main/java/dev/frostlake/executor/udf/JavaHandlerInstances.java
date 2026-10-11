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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

/**
 * The instance a Java function's handler method is called on when the method is not static (live-verified). The
 * account makes one with the class's zero-argument constructor when the function is created — which is when a
 * class without one, an abstract class and a constructor that throws are refused — and one for a statement that
 * calls the function: what the instance holds is there for the statement's later rows and for nothing else.
 */
public final class JavaHandlerInstances {

    private JavaHandlerInstances() {
    }

    /**
     * Makes an instance of a handler's class.
     *
     * @param handled the class
     * @param where   the sentence's end, {@code  in function F with handler H.f}
     * @return the instance
     */
    public static Object create(final Class<?> handled, final String where) {
        Constructor<?> constructor = null;
        try {
            constructor = handled.getDeclaredConstructor();
        } catch (final NoSuchMethodException none) {
            constructor = null;
        }
        if (constructor == null || Modifier.isPrivate(constructor.getModifiers())) {
            throw new RuntimeException("The provided method is non-static, but the enclosing class does not have a"
                + " public zero-argument constructor" + where);
        }
        if (Modifier.isAbstract(handled.getModifiers())) {
            throw new RuntimeException(JavaHandlerFailure.report(
                "java.lang.InstantiationException: " + handled.getName(), where));
        }
        try {
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (final InvocationTargetException thrown) {
            final Throwable cause = thrown.getCause() != null ? thrown.getCause() : thrown;
            throw new RuntimeException(JavaHandlerFailure.report(cause, handled, where), cause);
        } catch (final ReflectiveOperationException | RuntimeException failed) {
            throw new RuntimeException(JavaHandlerFailure.report(failed.toString(), where), failed);
        }
    }
}
