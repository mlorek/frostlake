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

package dev.frostlake;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * One {@code SF_LIVE} run's claim on the live account, so that a second run refuses to start instead of silently
 * destroying the first.
 *
 * <p>Two runs on one account destroy each other: each run recreates {@code test_db} for every test, and its teardown
 * drops every account-level object created after its own baseline, so the other run's objects vanish under it. The
 * symptoms do not look like a collision — "This session does not have a current schema", a table missing right
 * after its CREATE, classes failing and passing at random between runs.
 *
 * <p>The claim is ONE row in a small transient database that the harness never drops ({@link #DATABASE}): the
 * holding run's id, a description of the holder (user, host, process and working directory), and two stamps read
 * from the server's clock — when the run claimed the account and its last heartbeat. A run claims with one
 * conditional UPDATE that succeeds only while the row is free, already this run's, or stale. A conditional UPDATE
 * blocked behind another session's re-evaluates its condition against the row that session committed, so of two
 * runs starting together exactly one wins. The row itself comes from {@code CREATE TABLE IF NOT EXISTS … AS SELECT},
 * so two first runs racing to create the table still leave one row; the CREATE that loses answers a success-class
 * exception (SQL state {@code 00000}, "… already exists, statement succeeded."), which counts as success.
 *
 * <p>A claim goes stale {@link #STALE_AFTER_SECONDS} after its last heartbeat, so a run that died without releasing
 * blocks the account for at most that long. A heartbeat and a release touch only a row that still carries this
 * run's id: a run never refreshes, frees or overwrites a claim another run holds. A heartbeat that fails is read
 * against the account's listings: a claim table that no longer exists is a lost claim, while a failure beside an
 * existing table is the session's, rethrown so the caller can reconnect.
 */
public final class LiveAccountClaim {

    /** The database that holds the claim: transient, and kept by every teardown. */
    public static final String DATABASE = "FROSTLAKE_LIVE_CLAIM";

    /** Seconds after its last heartbeat at which a claim is stale, and another run may take the account over. */
    public static final long STALE_AFTER_SECONDS = 1800;

    /** Seconds between two heartbeats of a held claim: a sixth of the staleness window. */
    public static final long HEARTBEAT_SECONDS = 300;

    /** The table in {@link #DATABASE}'s {@code PUBLIC} schema that holds the claim row. */
    static final String TABLE = "ACCOUNT_CLAIM";

    /** The server's clock in epoch seconds: every stamp and every age comes from it, never from a client's clock. */
    private static final String NOW = "DATE_PART(EPOCH_SECOND, CURRENT_TIMESTAMP())";

    /** SQL state of a statement the account reports as succeeded, although the driver raises it as an exception. */
    private static final String SUCCEEDED = "00000";

    /** How many times a claim is retried when the row turns out free, released between the UPDATE and its read. */
    private static final int ATTEMPTS = 3;

    private final Connection connection;
    private final String database;
    private final String schema;
    private final String table;
    private final String runId;
    private final String holder;
    private final long staleAfterSeconds;

    /**
     * A claimant over one connection.
     *
     * @param connection the session every claim statement runs on
     * @param database the database holding the claim table, created when absent
     * @param schema the schema of the claim table, which must exist
     * @param runId the claiming run's id
     * @param holder who the claiming run is, for the refusal another run reads
     * @param staleAfterSeconds seconds after its last heartbeat at which a claim is stale
     */
    public LiveAccountClaim(final Connection connection, final String database, final String schema,
                            final String runId, final String holder, final long staleAfterSeconds) {
        this.connection = connection;
        this.database = database;
        this.schema = schema;
        this.table = database + "." + schema + "." + TABLE;
        this.runId = runId;
        this.holder = holder;
        this.staleAfterSeconds = staleAfterSeconds;
    }

    /**
     * The claimant of the account's own claim, {@code FROSTLAKE_LIVE_CLAIM.PUBLIC.ACCOUNT_CLAIM}.
     *
     * @param connection the session every claim statement runs on, dedicated to the claim
     * @param runId the claiming run's id
     * @param holder who the claiming run is
     * @return the claimant
     */
    public static LiveAccountClaim onAccount(final Connection connection, final String runId, final String holder) {
        return new LiveAccountClaim(connection, DATABASE, "PUBLIC", runId, holder, STALE_AFTER_SECONDS);
    }

    /** @return the claim table's qualified name */
    public String table() {
        return table;
    }

    /** @return the claiming run's id */
    public String runId() {
        return runId;
    }

    /** @return who the claiming run is */
    public String holder() {
        return holder;
    }

    /**
     * Creates the claim database and the claim table, with its one free row, where they are absent. Safe to run
     * from any number of runs at once. {@code CREATE DATABASE} makes the database current, so the connection
     * should be the claim's own.
     *
     * @throws SQLException when either cannot be created
     */
    public void prepare() throws SQLException {
        createIfAbsent("CREATE TRANSIENT DATABASE IF NOT EXISTS " + database + " DATA_RETENTION_TIME_IN_DAYS = 0"
            + " COMMENT = 'The account claim of the Frostlake SF_LIVE test harness: one run at a time.'");
        createIfAbsent("CREATE TRANSIENT TABLE IF NOT EXISTS " + table + " AS SELECT"
            + " CAST(NULL AS VARCHAR) AS RUN_ID, CAST(NULL AS VARCHAR) AS HOLDER,"
            + " CAST(NULL AS NUMBER(19, 0)) AS CLAIMED_AT, CAST(NULL AS NUMBER(19, 0)) AS HEARTBEAT_AT");
    }

    /**
     * Claims the account for this run: takes the row when it is free, already this run's, or stale.
     *
     * @return null when this run holds the claim; otherwise the refusal, naming the run that holds it
     * @throws SQLException when the claim table cannot be read or written
     */
    public String acquire() throws SQLException {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            try (final PreparedStatement update = connection.prepareStatement("UPDATE " + table
                    + " SET RUN_ID = ?, HOLDER = ?, CLAIMED_AT = " + NOW + ", HEARTBEAT_AT = " + NOW
                    + " WHERE RUN_ID IS NULL OR RUN_ID = ? OR HEARTBEAT_AT < " + NOW + " - ?")) {
                update.setString(1, runId);
                update.setString(2, holder);
                update.setString(3, runId);
                update.setLong(4, staleAfterSeconds);
                update.executeUpdate();
            }
            final String[] row = readRow();
            if (row == null) {
                return damaged();
            }
            if (runId.equals(row[0])) {
                return null;
            }
            if (row[0] != null) {
                return refusal(row);
            }
            // Free again: released between the UPDATE and its read, so the claim is worth another attempt.
        }
        return "The live account's claim row " + table + " kept being released while this run tried to take it;"
            + " rerun.";
    }

    /**
     * Refreshes this run's heartbeat.
     *
     * @return null while the claim is still this run's; otherwise the sentence saying why it no longer is: another
     *     run holds it, it was released, or the claim table is gone
     * @throws SQLException when the heartbeat fails while the claim table still exists, or when the session cannot
     *     tell whether it does: a broken session, not a lost claim
     */
    public String heartbeat() throws SQLException {
        final int refreshed;
        try (final PreparedStatement update = connection.prepareStatement("UPDATE " + table
                + " SET HEARTBEAT_AT = " + NOW + " WHERE RUN_ID = ?")) {
            update.setString(1, runId);
            refreshed = update.executeUpdate();
        } catch (final SQLException failed) {
            if (tableExists(failed)) {
                throw failed;
            }
            return "This run (" + runId + ") no longer holds the live account's claim: the claim table " + table
                + " no longer exists, and the account may hold another run by now. This run stops using the account.";
        }
        if (refreshed > 0) {
            return null;
        }
        final String[] row = readRow();
        if (row == null) {
            return damaged();
        }
        if (row[0] == null) {
            return "This run (" + runId + ") no longer holds the live account's claim: it was released, and the"
                + " account may hold another run by now. This run stops using the account.";
        }
        return "This run (" + runId + ") no longer holds the live account's claim: " + describe(row)
            + ". This run stops using the account.";
    }

    /**
     * Gives the claim up, if it is still this run's; a claim another run holds is left as it is.
     *
     * @return whether this run held the claim
     * @throws SQLException when the claim table cannot be written
     */
    public boolean release() throws SQLException {
        try (final PreparedStatement update = connection.prepareStatement("UPDATE " + table
                + " SET RUN_ID = NULL WHERE RUN_ID = ?")) {
            update.setString(1, runId);
            return update.executeUpdate() > 0;
        }
    }

    /**
     * The claim row: its run id, holder, and the seconds since its claim and since its last heartbeat, as text.
     *
     * @return the row's cells, or null when the table does not hold exactly one row
     * @throws SQLException when the table cannot be read
     */
    private String[] readRow() throws SQLException {
        try (final Statement statement = connection.createStatement();
             final ResultSet rs = statement.executeQuery("SELECT RUN_ID, HOLDER, " + NOW + " - CLAIMED_AT, "
                 + NOW + " - HEARTBEAT_AT FROM " + table)) {
            if (!rs.next()) {
                return null;
            }
            final String[] row = {rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)};
            return rs.next() ? null : row;
        }
    }

    /**
     * Whether the claim table still exists, read from the account's listings rather than from the wording of the
     * statement that failed: {@code SHOW DATABASES} lists the claim database while it exists, and
     * {@code SHOW TABLES … IN DATABASE} lists the claim table under its schema while both exist.
     *
     * @param failed the heartbeat's failure, rethrown when the session cannot read the listings either
     * @return whether the claim table exists
     * @throws SQLException the heartbeat's failure, with the listing's own attached, when a listing cannot be read
     */
    private boolean tableExists(final SQLException failed) throws SQLException {
        try (final Statement statement = connection.createStatement()) {
            return lists(statement, "SHOW DATABASES LIKE '" + database + "'", database, null)
                && lists(statement, "SHOW TABLES LIKE '" + TABLE + "' IN DATABASE " + database, TABLE, schema);
        } catch (final SQLException unanswered) {
            failed.addSuppressed(unanswered);
            throw failed;
        }
    }

    /** Whether a listing holds a row named {@code name}, and in the schema {@code schemaName} unless that is null. */
    private static boolean lists(final Statement statement, final String show, final String name,
                                 final String schemaName) throws SQLException {
        try (final ResultSet rs = statement.executeQuery(show)) {
            while (rs.next()) {
                if (name.equalsIgnoreCase(rs.getString("name"))
                    && (schemaName == null || schemaName.equalsIgnoreCase(rs.getString("schema_name")))) {
                    return true;
                }
            }
            return false;
        }
    }

    /** The refusal a run reads when another run holds the claim: who holds it, and how it is freed. */
    private String refusal(final String[] row) {
        final String staleIn = row[3] == null ? "once its heartbeat is " + staleAfterSeconds + " s old"
            : Math.max(0, staleAfterSeconds - Long.parseLong(row[3])) + " s from now";
        return "The live account is claimed by another SF_LIVE run, so this run refuses to start: two runs on one"
            + " account drop each other's objects. Holder: " + describe(row) + ". If that run has died, its claim"
            + " goes stale " + staleIn + "; to free it sooner, run UPDATE " + table + " SET RUN_ID = NULL WHERE"
            + " RUN_ID = '" + row[0] + "'.";
    }

    /** The holder of a claimed row, its run id and its two ages. */
    private static String describe(final String[] row) {
        return row[1] + " (run " + row[0] + "), claimed " + row[2] + " s ago, last heartbeat " + row[3] + " s ago";
    }

    private String damaged() {
        return "The live account's claim table " + table + " does not hold exactly one row; drop the database "
            + database + " (the next run recreates it) and rerun.";
    }

    private void createIfAbsent(final String ddl) throws SQLException {
        try (final Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        } catch (final SQLException raced) {
            if (!SUCCEEDED.equals(raced.getSQLState())) {
                throw raced;
            }
        }
    }
}
