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
import dev.frostlake.executor.udf.TemporaryObjectStatements;
import dev.frostlake.storage.ResultSet;

import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stub implementation of Snowflake Snowpark Session for inline Java/Scala procedures.
 * Injected into the procedure handler method as the first argument.
 *
 * <p>Mirrors the subset of the real Snowpark Session surface that handler code uses, and reproduces
 * Snowflake's stored-procedure restrictions, all rejected at runtime: creating a new session, obtaining the
 * underlying JDBC connection, running two queries at the same time (concurrency — see
 * {@link #beginQuery()} for why that is not the thread check the documentation's wording suggests), and
 * creating named temporary objects under owner's rights. Matches the documented Java stored-procedure
 * limitations
 * (https://docs.snowflake.com/en/developer-guide/stored-procedure/java/procedure-java-limitations).
 *
 * <p>Deliberately does <em>not</em> expose the underlying {@link DatabaseEngine}; the real Snowpark
 * Session has no such accessor, and exposing it would let handler code escape the session sandbox.
 */
public class Session {

    private final DatabaseEngine engine;
    /**
     * Whether a query is in flight on this session. One permit, not a thread identity: see
     * {@link #beginQuery()}.
     */
    private final AtomicBoolean queryInFlight = new AtomicBoolean(false);
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
        this.ownersRights = ownersRights;
    }

    /**
     * Build a plan over {@code sqlText}. Nothing is submitted here — see {@link DataFrame}.
     *
     * <p>The refusals that used to live in this method moved to {@link #runPlan}, because that is where
     * live raises them: a handler that calls {@code session.sql("CREATE TEMPORARY TABLE …")} under
     * owner's rights and never collects returns normally on Snowflake, and only the added
     * {@code .collect()} produces "Unsupported statement type 'temporary TABLE'".
     */
    public DataFrame sql(final String sqlText) {
        return new DataFrame(this, sqlText);
    }

    /**
     * Submit one statement — the action end of a {@link DataFrame}, and the point every per-statement
     * rule applies at: the owner's-rights temporary-object refusal, and the one-query-in-flight permit
     * (a permit about queries being RUN, which under a lazy plan is here rather than at sql()).
     */
    ResultSet runPlan(final String sqlText) {
        rejectTemporaryObjectUnderOwnersRights(sqlText);
        beginQuery();
        try {
            return engine.executeQuery(sqlText);
        } finally {
            queryInFlight.set(false);
        }
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
     * Take the session's single query permit, or refuse: a stored procedure may have only ONE query in
     * flight at a time.
     *
     * <p><b>Not a thread-identity check</b>, though the documented limitation ("you can't submit queries
     * from multiple threads") reads like one and this class used to enforce it that way. Measured on a
     * real account, all three cases:
     *
     * <pre>
     *   session.sql() on the handler's own thread              accepted
     *   session.sql() from ANOTHER thread, sequentially        ACCEPTED
     *   several session.sql() calls released at the SAME time  the first wins, the rest fail
     * </pre>
     *
     * <p>So the constraint is simultaneity, not which thread asks. Rejecting on thread identity refused
     * handler code that a real account runs happily — a false rejection, which is the more expensive
     * direction to be wrong in.
     *
     * <p>Live raises a {@code JVMStoredProcUserError} from its native statement layer with a null
     * message, surfacing to the caller as a "User Error Report" carrying the Java stack. That is a
     * Snowflake internal type, so the refusal here is Frostlake's own and says why in its message —
     * what a handler needs locally is to be stopped, and told the reason.
     */
    private void beginQuery() {
        if (!queryInFlight.compareAndSet(false, true)) {
            throw new UnsupportedOperationException(
                "Concurrency is not supported in stored procedures: a query was submitted while another"
                + " was already running on this session");
        }
    }

    /**
     * Refuse a temporary object under owner's rights, in Snowflake's own words.
     *
     * <p>The documentation says "cannot create named temporary objects", but measured, the rule is
     * narrower and the message names the kind: {@code CREATE TRANSIENT TABLE} is ACCEPTED, while
     * TEMPORARY / TEMP / LOCAL TEMPORARY / VOLATILE tables, temporary stages and temporary file formats
     * each come back as {@code Unsupported statement type 'temporary <KIND>'}.
     *
     * <p>Caller's rights procedures are unrestricted, and so — measured — are LANGUAGE SQL procedures of
     * either rights mode: the restriction belongs to the handler languages, which is why it lives on this
     * session rather than in the DDL path. A handler that CALLs a SQL procedure which creates a temporary
     * table is likewise allowed, because the statement never passes through here.
     */
    private void rejectTemporaryObjectUnderOwnersRights(final String sqlText) {
        if (!ownersRights) {
            return;
        }
        final String kind = TemporaryObjectStatements.temporaryObjectKind(sqlText);
        if (kind != null) {
            throw new UnsupportedOperationException(
                "Stored procedure execution error: Unsupported statement type 'temporary " + kind + "'.");
        }
    }
}
