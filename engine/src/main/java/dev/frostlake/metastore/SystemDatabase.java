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
 * The SNOWFLAKE database, which every account has and nobody changes: it cannot be cloned, renamed, altered,
 * commented on, dropped or replaced, and its schemas are its own — none is created in it, dropped from it, altered,
 * or moved into or out of it.
 *
 * <p>Its PUBLIC schema is another matter. A real SNOWFLAKE database has none (live-verified, see
 * {@code SnowflakeSchemas}); here it is the engine's default working schema ({@code database.default} and
 * {@code schema.default}), where a session that names no database creates its tables and writes its rows. What is
 * created in SNOWFLAKE.PUBLIC therefore stays writable, which is also why the DML read-only check passes this
 * database by. Snowflake's own sentences for these refusals are not known; the engine's read-only sentence stands in.
 */
public final class SystemDatabase {

    /** The database's name. */
    public static final String NAME = "SNOWFLAKE";

    private SystemDatabase() {
    }

    /**
     * Whether a database name is the SNOWFLAKE database's, as the catalog stores it.
     *
     * @param databaseName the stored (canonical) name, or null
     * @return whether it is
     */
    public static boolean is(final String databaseName) {
        return NAME.equals(databaseName);
    }

    /**
     * Refuses a statement that would change the SNOWFLAKE database itself or the set of its schemas.
     *
     * @param databaseName the stored name of the database the statement changes
     */
    public static void refuseChange(final String databaseName) {
        if (is(databaseName)) {
            throw new RuntimeException(readOnly());
        }
    }

    /**
     * The engine's read-only sentence for the SNOWFLAKE database.
     *
     * @return the sentence
     */
    public static String readOnly() {
        return "Database '" + NAME + "' is read-only";
    }
}
