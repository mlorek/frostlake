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

package dev.frostlake.metastore;

/**
 * Live's refusal of a statement that needs a current database the session does not have, naming what the
 * statement was doing, with no compilation-error prefix:
 *
 * <pre>
 *   Cannot perform CREATE TABLE. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.
 * </pre>
 *
 * <p>A session gets there by dropping its current database, which leaves CURRENT_DATABASE() and
 * CURRENT_SCHEMA() both NULL. The refusal usually names the statement's own kind, which the command visitor
 * enters as each statement starts, so a refusal raised deep inside name resolution still names it. A few
 * lookups name themselves instead, whatever the statement: a FROM clause is a SELECT, a CLONE source a
 * CLONE. They pass that name to {@link #naming}.
 *
 * <p>The missing prefix matters beyond the text. The refusal is raised while the statement compiles, so a
 * DML write must never wrap it in its row-time envelope, and this type is how the envelope tells.
 */
public final class NoCurrentDatabaseRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private static final ThreadLocal<String> KIND = new ThreadLocal<String>();

    private NoCurrentDatabaseRefusal(final String kind) {
        super("Cannot perform " + kind + ". This session does not have a current database."
            + " Call 'USE DATABASE', or use a qualified name.");
    }

    /**
     * The statement about to run, by the name live gives it.
     *
     * @param kind the statement's name
     * @return the enclosing statement's name, for {@link #exit}
     */
    public static String enter(final String kind) {
        final String enclosing = KIND.get();
        KIND.set(kind);
        return enclosing;
    }

    /**
     * The statement has ended, and the enclosing one's name applies again.
     *
     * @param enclosing what {@link #enter} returned
     */
    public static void exit(final String enclosing) {
        if (enclosing == null) {
            KIND.remove();
        } else {
            KIND.set(enclosing);
        }
    }

    /**
     * The refusal, naming the statement running now.
     *
     * @return the refusal to throw
     */
    public static RuntimeException forStatement() {
        final String kind = KIND.get();
        return kind == null ? new RuntimeException("No database selected") : new NoCurrentDatabaseRefusal(kind);
    }

    /**
     * The refusal, naming an operation of its own.
     *
     * @param kind what the refusal names: SELECT for a FROM clause, CLONE for a CLONE source
     * @return the refusal to throw
     */
    public static NoCurrentDatabaseRefusal naming(final String kind) {
        return new NoCurrentDatabaseRefusal(kind);
    }
}
