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

package com.snowflake.snowpark_java;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import java.sql.Connection;

/**
 * Stub implementation of Snowflake Snowpark Session for inline Java/Scala procedures.
 * Injected into the procedure handler method as the first argument.
 *
 * <p>Mirrors the subset of the real Snowpark Session surface that handler code uses, and reproduces
 * Snowflake's stored-procedure restrictions, all rejected at runtime: creating a new session, obtaining the
 * underlying JDBC connection, submitting queries from a thread other than the one running the procedure
 * (concurrency), and creating named temporary objects under owner's rights. Matches the documented
 * Java stored-procedure limitations
 * (https://docs.snowflake.com/en/developer-guide/stored-procedure/java/procedure-java-limitations).
 *
 * <p>Deliberately does <em>not</em> expose the underlying {@link DatabaseEngine}; the real Snowpark
 * Session has no such accessor, and exposing it would let handler code escape the session sandbox.
 */
public class Session {

    private final DatabaseEngine engine;
    private final Thread owningThread;
    private final boolean ownersRights;

    public Session(final DatabaseEngine engine) {
        this(engine, false);
    }

    /**
     * @param ownersRights true when the procedure runs with the owner's rights ({@code EXECUTE AS OWNER}, the
     *                     default), which forbids creating named temporary objects.
     */
    public Session(final DatabaseEngine engine, final boolean ownersRights) {
        this.engine = engine;
        this.owningThread = Thread.currentThread();
        this.ownersRights = ownersRights;
    }

    public DataFrame sql(final String sqlText) {
        checkSameThread();
        rejectTemporaryObjectUnderOwnersRights(sqlText);
        ResultSet rs = engine.executeQuery(sqlText);
        return new DataFrame(rs);
    }

    public DataFrame table(final String tableName) {
        return sql("SELECT * FROM " + tableName);
    }

    /**
     * Entry point for the {@code Session.builder()...create()} idiom. Building configuration is
     * permitted; the terminal {@link SessionBuilder#create()} call is rejected, because Snowflake
     * prohibits creating a new session from within a stored procedure.
     */
    public static SessionBuilder builder() {
        return new SessionBuilder();
    }

    /**
     * Snowflake does not allow a stored procedure to access the session's underlying JDBC connection.
     *
     * @throws UnsupportedOperationException always
     */
    public Connection jdbcConnection() {
        throw new UnsupportedOperationException(
            "session.jdbcConnection() is not supported inside a stored procedure");
    }

    /**
     * Snowflake stored procedures cannot submit queries from multiple threads — a handler may only run queries
     * on the thread executing the procedure. Enforced per invocation (each handler call gets a fresh Session),
     * so a handler that spawns threads and issues queries from them fails, matching Snowflake.
     */
    private void checkSameThread() {
        if (Thread.currentThread() != owningThread) {
            throw new UnsupportedOperationException(
                "Concurrency is not supported in stored procedures: a query was submitted from a thread other"
                + " than the one running the procedure");
        }
    }

    /**
     * An owner's rights stored procedure may not create named temporary objects (a documented limitation);
     * caller's rights procedures are unrestricted.
     */
    private void rejectTemporaryObjectUnderOwnersRights(final String sqlText) {
        if (ownersRights && createsTemporaryObject(sqlText)) {
            throw new UnsupportedOperationException(
                "Creating a named temporary object is not supported in an owner's rights stored procedure");
        }
    }

    /**
     * Heuristic recognizer for {@code CREATE [OR REPLACE] [TRANSIENT] (TEMP|TEMPORARY) <objectType> …}. Scans
     * only the DDL prefix (stopping at the object-type keyword) so a later token in the body can't false-match.
     */
    private static boolean createsTemporaryObject(final String sqlText) {
        final String[] tokens = sqlText.trim().toUpperCase().split("\\s+");
        if (tokens.length == 0 || !tokens[0].equals("CREATE")) {
            return false;
        }
        for (int i = 1; i < tokens.length && i < 6; i++) {
            final String token = tokens[i];
            if (token.equals("TEMP") || token.equals("TEMPORARY")) {
                return true;
            }
            if (token.equals("TABLE") || token.equals("STAGE") || token.equals("VIEW")
                    || token.equals("SEQUENCE") || token.equals("FILE") || token.equals("FUNCTION")
                    || token.equals("PROCEDURE")) {
                return false;
            }
        }
        return false;
    }
}
