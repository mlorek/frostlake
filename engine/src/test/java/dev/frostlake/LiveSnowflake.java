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
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * The live-Snowflake test switch. With {@code SF_LIVE=1} in the environment, the test base classes
 * route their SQL to a real Snowflake account instead of the embedded engine, so the same suite can
 * be flipped between Frostlake and Snowflake to confirm the two agree. Credentials come from
 * {@code SF_USER} / {@code SF_PASS} / {@code SF_URL} (the account host, e.g.
 * {@code myorg-myaccount.snowflakecomputing.com}); {@code SF_WAREHOUSE} optionally names the
 * warehouse to activate (otherwise the user's default warehouse must be set server-side).
 *
 * <p>Statements are submitted one at a time ({@link LiveSnowflakeEngine} splits engine-style
 * multi-statement scripts with the engine's own parser), so Snowflake's server-side multi-statement
 * splitter — which mis-splits unquoted procedure bodies at their internal semicolons — never runs.
 * Because the engine path shares one session across tests, statements that mutate session state
 * (transactions, autocommit, role, warehouse) mark it dirty and {@link #resetSharedIfDirty()}
 * restores the baseline before the next test. Secrets are never logged.
 */
public final class LiveSnowflake {

    private static Connection shared;
    private static String initialRole;
    private static String initialWarehouse;
    private static boolean sharedDirty;

    private LiveSnowflake() {
    }

    /** Whether live-Snowflake mode is on ({@code SF_LIVE=1}). */
    public static boolean enabled() {
        return "1".equals(System.getenv("SF_LIVE"));
    }

    /** A new prepared connection (caller closes) — used where each test wants its own session. */
    public static Connection open() {
        final Properties props = new Properties();
        props.put("user", requiredEnv("SF_USER"));
        props.put("password", requiredEnv("SF_PASS"));
        try {
            final Connection connection =
                DriverManager.getConnection("jdbc:snowflake://" + requiredEnv("SF_URL") + "/", props);
            prepareSession(connection);
            return connection;
        } catch (final SQLException e) {
            throw new IllegalStateException("Cannot connect to live Snowflake: " + e.getMessage(), e);
        }
    }

    /**
     * The JVM-wide shared connection used by {@link LiveSnowflakeEngine}. Opened lazily; a shutdown
     * hook drops the {@code test_db} working database and closes the session at JVM exit.
     */
    public static synchronized Connection shared() {
        if (shared == null) {
            shared = open();
            captureBaseline();
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    closeShared();
                }
            }, "live-snowflake-cleanup"));
        }
        return shared;
    }

    /**
     * Marks the shared session's state as needing a reset — called for statements that can leave a
     * transaction open or move autocommit/role/warehouse, and for any statement that errors (a
     * failed script may abandon a transaction mid-flight).
     */
    public static synchronized void markSharedDirty() {
        sharedDirty = true;
    }

    /**
     * Restores the shared session to its baseline (no open transaction, autocommit on, the initial
     * role and warehouse) — a no-op unless a previous test marked the session dirty, so the common
     * case costs nothing. Without this, one test's dangling {@code BEGIN} or
     * {@code ALTER SESSION SET AUTOCOMMIT = FALSE} silently poisons every later test on the shared
     * session (stream reads pinned at transaction start were the visible symptom).
     */
    public static synchronized void resetSharedIfDirty() {
        if (shared == null || !sharedDirty) {
            return;
        }
        try (final Statement statement = shared.createStatement()) {
            statement.execute("ROLLBACK");
            statement.execute("ALTER SESSION SET AUTOCOMMIT = TRUE");
            // Restore the session-parameter baseline too — a dirty session may have run its own
            // ALTER SESSION (JSON_INDENT, QUERY_TAG, …), and those changes poison every later
            // semi-structured comparison if left standing.
            applySessionParameters(statement);
            if (initialRole != null) {
                statement.execute("USE ROLE " + initialRole);
            }
            if (initialWarehouse != null) {
                statement.execute("USE WAREHOUSE " + initialWarehouse);
            }
            sharedDirty = false;
        } catch (final SQLException e) {
            throw new IllegalStateException("Cannot reset the live Snowflake session: " + e.getMessage(), e);
        }
    }

    private static void captureBaseline() {
        try (final Statement statement = shared.createStatement();
             final ResultSet rs = statement.executeQuery("SELECT CURRENT_ROLE(), CURRENT_WAREHOUSE()")) {
            if (rs.next()) {
                initialRole = rs.getString(1);
                initialWarehouse = rs.getString(2);
            }
        } catch (final SQLException e) {
            throw new IllegalStateException("Cannot capture the live session baseline: " + e.getMessage(), e);
        }
    }

    private static synchronized void closeShared() {
        if (shared == null) {
            return;
        }
        try (final Statement statement = shared.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS test_db");
        } catch (final Exception ignored) {
            // Best-effort cleanup only.
        }
        try {
            shared.close();
        } catch (final Exception ignored) {
            // Best-effort cleanup only.
        }
        shared = null;
    }

    private static void prepareSession(final Connection connection) throws SQLException {
        try (final Statement statement = connection.createStatement()) {
            applySessionParameters(statement);
            final String warehouse = System.getenv("SF_WAREHOUSE");
            if (warehouse != null && !warehouse.isEmpty()) {
                statement.execute("USE WAREHOUSE " + warehouse);
            }
        }
    }

    /**
     * The session-parameter baseline every live statement depends on. Applied at session OPEN and
     * RE-APPLIED by {@link #resetSharedIfDirty}: a test that runs its own {@code ALTER SESSION}
     * (SyntaxBreadthSingletonsTest sets {@code JSON_INDENT = 1}) really changes the shared session,
     * and the showed the cost of not restoring it — every semi-structured
     * assertion after that test received PRETTY-printed text and ~20 tests failed on rendering alone.
     */
    private static void applySessionParameters(final Statement statement) throws SQLException {
        // The driver's default Arrow result format needs --add-opens JVM flags under Java 17;
        // the JSON format works in any JVM the suite runs in.
        statement.execute("ALTER SESSION SET JDBC_QUERY_RESULT_FORMAT = 'JSON'");
        // Snowflake's findColumn is case-SENSITIVE by default (getString("db_name") misses the
        // DB_NAME label); virtually every other JDBC driver — including Frostlake's — matches
        // case-insensitively, so align the client setting rather than fail on ergonomics.
        statement.execute("ALTER SESSION SET CLIENT_RESULT_COLUMN_CASE_INSENSITIVE = TRUE");
        // Snowflake pretty-prints semi-structured output with two-space indent by default; the
        // engine's canonical text is compact. JSON_INDENT = 0 makes the SERVER emit the compact
        // form (live-verified), so raw-JDBC tests compare identically without re-serializing.
        statement.execute("ALTER SESSION SET JSON_INDENT = 0");
        // No suite statement legitimately runs longer than seconds; Snowflake's DEFAULT statement
        // timeout is TWO DAYS. Without this cap, one unbounded query wedges the whole run — a
        // cyclic CONNECT BY sat executing for ten hours on (the test now skips live,
        // but the cap protects the suite from the next such statement, burning at most 5 minutes
        // of warehouse time instead of days).
        statement.execute("ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 300");
        // A test's own QUERY_TAG must not outlive it either.
        statement.execute("ALTER SESSION UNSET QUERY_TAG");
    }

    private static String requiredEnv(final String name) {
        final String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(
                "SF_LIVE=1 requires the " + name + " environment variable (SF_USER, SF_PASS, SF_URL)");
        }
        return value;
    }
}
