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
 * A thread-local mode in which a stored definition — a view's body, a routine's — is compiled to be kept, not
 * run. The account checks such a body's names and types and stops there: the steps that fold a call over the
 * catalog while a statement compiles, as GET_DDL finds the object it names, wait for the statement that reads
 * the view or calls the routine (live-verified):
 *
 * <pre>
 *   CREATE VIEW v AS SELECT GET_DDL('table', 'missing') AS d    a view; DESCRIBE knows D
 *   CREATE VIEW v AS SELECT GET_DDL('nokind', 't') AS d         a view
 *   CREATE VIEW v AS SELECT GET_DDL('table', c) AS d FROM t     a view
 *   SELECT * FROM v     SELECT COUNT(*) FROM v                  the refusal, to whoever reads it
 * </pre>
 */
public final class DefinitionCompile {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<Boolean>();

    private DefinitionCompile() {
    }

    /** Whether the current thread is compiling a definition to keep. */
    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    /** Enter the mode; hands back the state to put back with {@link #end}. */
    public static boolean begin() {
        final boolean previous = isActive();
        ACTIVE.set(Boolean.TRUE);
        return previous;
    }

    public static void end(final boolean previous) {
        if (previous) {
            ACTIVE.set(Boolean.TRUE);
        } else {
            ACTIVE.remove();
        }
    }
}
