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

package dev.frostlake.executor;

/**
 * Letting a statement's failure out with the message it already has.
 *
 * <p>Snowflake reports what went wrong and nothing else — {@code DROP VIEW nosuch} answers exactly
 * {@code SQL compilation error:\nView '…' does not exist or not authorized.} with no preamble naming the
 * statement. Frostlake used to add one at every layer it passed through, and because several layers wrap,
 * the same phrase appeared more than once: a missing table in a query came back as {@code Failed to
 * execute SQL: Failed to execute SELECT: Failed to execute SELECT: Table does not exist: T}. None of that
 * is in the real message, and the repetition was not even intentional.
 *
 * <p>So a handler that has nothing to add re-throws instead of re-describing. Wrappers that name a
 * specific object — which table function, which Java or Scala routine — are NOT this: they say something
 * the inner message does not, and they stay.
 */
public final class StatementErrors {

    private StatementErrors() {
    }

    /**
     * The failure to re-throw, unchanged where it already is unchecked. Written as
     * {@code throw StatementErrors.propagate(e);} so the compiler still sees the method exit.
     */
    public static RuntimeException propagate(final Exception cause) {
        return cause instanceof RuntimeException ? (RuntimeException) cause
            : new RuntimeException(cause.getMessage(), cause);
    }
}
