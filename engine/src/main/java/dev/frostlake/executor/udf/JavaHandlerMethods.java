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

import java.lang.reflect.Method;

/**
 * The one public method of a Java routine's handler: named as the handler names it and taking the routine's
 * arguments, a procedure's Session first. None is {@code Failed to find a public method named "f" with 1 arguments
 * in function F with handler H.f}, several {@code Cannot determine which implementation of handler "f" to invoke
 * since there are multiple definitions with 1 arguments in function F with handler H.f} - as the routine is created,
 * from an inline body or an IMPORTS JAR alike, and as it is called once its JAR no longer holds the one it was
 * created with (live-verified).
 */
public final class JavaHandlerMethods {

    private JavaHandlerMethods() {
    }

    /**
     * The handler method, or the refusal of a class that holds none or several.
     *
     * @param handled     the handler's class
     * @param methodName  the method the handler names
     * @param arity       the arguments it takes: the routine's, and a procedure's Session
     * @param routineName the routine's name, as the refusal prints it
     * @param handler     the routine's HANDLER
     * @return the method
     */
    public static Method resolve(final Class<?> handled, final String methodName, final int arity,
                                 final String routineName, final String handler) {
        Method found = null;
        int definitions = 0;
        for (final Method method : handled.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == arity) {
                found = method;
                definitions++;
            }
        }
        if (found == null) {
            throw new RuntimeException("Failed to find a public method named \"" + methodName + "\" with " + arity
                + " arguments in function " + routineName + " with handler " + handler);
        }
        if (definitions > 1) {
            throw new RuntimeException("Cannot determine which implementation of handler \"" + methodName
                + "\" to invoke since there are multiple definitions with " + arity + " arguments in function "
                + routineName + " with handler " + handler);
        }
        return found;
    }
}
