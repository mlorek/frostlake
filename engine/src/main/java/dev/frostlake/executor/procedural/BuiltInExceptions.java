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

package dev.frostlake.executor.procedural;

/**
 * The two exceptions every script has without declaring them, STATEMENT_ERROR and EXPRESSION_ERROR, as a script
 * raises them itself: {@code RAISE STATEMENT_ERROR} raises an exception of that name with the code -20000, the
 * state P0001 and no message, which {@code WHEN STATEMENT_ERROR} catches and {@code WHEN EXPRESSION_ERROR} does
 * not, and which reads {@code Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6} when nothing
 * catches it (all live-verified). A statement or an expression the engine refuses is no exception of these
 * declarations: it carries the refusal's own code and text.
 */
public final class BuiltInExceptions {

    /** The code an exception raised with no number of its own carries. */
    private static final int RAISED_CODE = -20000;

    /** STATEMENT_ERROR as a script raises it. */
    public static final UserDefinedException STATEMENT_ERROR =
        new UserDefinedException("STATEMENT_ERROR", RAISED_CODE, null);

    /** EXPRESSION_ERROR as a script raises it. */
    public static final UserDefinedException EXPRESSION_ERROR =
        new UserDefinedException("EXPRESSION_ERROR", RAISED_CODE, null);

    private BuiltInExceptions() {
    }

    /**
     * @param canonical an exception's name, upper-cased unless it was written in quotes
     * @return the built-in exception of that name, or null
     */
    public static UserDefinedException of(final String canonical) {
        if ("STATEMENT_ERROR".equals(canonical)) {
            return STATEMENT_ERROR;
        }
        return "EXPRESSION_ERROR".equals(canonical) ? EXPRESSION_ERROR : null;
    }
}
