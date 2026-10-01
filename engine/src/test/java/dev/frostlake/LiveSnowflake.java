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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import net.snowflake.client.api.statement.SnowflakeStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *
 * <p>One run at a time: the first connection a JVM opens claims the account ({@link LiveAccountClaim}), and a
 * run that finds the account claimed by another live run fails every live test at once, naming the holder,
 * instead of letting the two runs drop each other's objects. The claim is refreshed every
 * {@link LiveAccountClaim#HEARTBEAT_SECONDS} seconds on a session of its own and released at JVM exit, after the
 * run's {@code test_db} is dropped; a run that dies without releasing it blocks the account for at most
 * {@link LiveAccountClaim#STALE_AFTER_SECONDS} seconds. A run whose heartbeat finds its claim gone stops using the
 * account: every live test after that fails at once, and the harness's cleanups drop nothing.
 */
public final class LiveSnowflake {

    private static final Logger logger = LoggerFactory.getLogger(LiveSnowflake.class);

    private static Connection shared;
    private static String initialRole;
    private static String initialWarehouse;
    private static boolean sharedDirty;

    /** Guards the account claim's state; never held while waiting for the {@code LiveSnowflake} class lock. */
    private static final Object CLAIM_LOCK = new Object();
    /** How many times a run tries to take the claim, each on a new session, before it gives up. */
    private static final int CLAIM_ATTEMPTS = 3;
    /** This run's claim once taken, its dedicated session, and the timer refreshing its heartbeat. */
    private static LiveAccountClaim accountClaim;
    private static Connection claimConnection;
    private static Timer heartbeat;
    /** Whether this run holds the account's claim now. */
    private static volatile boolean claimHeld;
    /** Why this run may not use the account — the claim was refused or lost — or null. */
    private static volatile String claimProblem;

    /** The session parameters the harness itself sets, which a reset must leave standing. */
    private static final Set<String> HARNESS_PARAMETERS = new HashSet<String>(Arrays.asList(
        "CLIENT_RESULT_COLUMN_CASE_INSENSITIVE", "JSON_INDENT", "STATEMENT_TIMEOUT_IN_SECONDS",
        "MULTI_STATEMENT_COUNT"));

    private LiveSnowflake() {
    }

    /** Whether live-Snowflake mode is on ({@code SF_LIVE=1}). */
    public static boolean enabled() {
        return "1".equals(System.getenv("SF_LIVE"));
    }

    /**
     * A new prepared connection (caller closes) — used where each test wants its own session. The first one a
     * JVM opens claims the account for the run; it fails when another live run holds the claim.
     */
    public static Connection open() {
        claimAccount();
        return connect();
    }

    /**
     * Fails unless nothing stops this run from using the account: the check every live statement path makes, so
     * a run whose claim was refused, or lost to another run, stops at once instead of disturbing the holder.
     *
     * @throws IllegalStateException naming the run that holds the account
     */
    public static void checkAccountClaim() {
        final String problem = claimProblem;
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
    }

    /**
     * Whether this run holds the account's claim now. The cleanup paths drop nothing on an account this run no
     * longer holds: after a lost claim, {@code test_db} and the new account objects belong to the holder.
     *
     * @return whether the claim is this run's
     */
    public static boolean holdsAccountClaim() {
        return claimHeld;
    }

    /**
     * Claims the account for this run, once per JVM: prepares the claim table, takes the claim, starts its
     * heartbeat and registers the exit hook that drops {@code test_db} and then releases the claim. A refusal, or a
     * claim that cannot be taken in {@link #CLAIM_ATTEMPTS} tries, is kept, so every later live test fails at once
     * with the same sentence.
     */
    private static void claimAccount() {
        synchronized (CLAIM_LOCK) {
            checkAccountClaim();
            if (claimHeld) {
                return;
            }
            final String runId = UUID.randomUUID().toString();
            final String holder = describeThisRun();
            String refusal = null;
            IllegalStateException failure = null;
            for (int attempt = 0; attempt < CLAIM_ATTEMPTS; attempt++) {
                Connection connection = null;
                try {
                    connection = connect();
                    final LiveAccountClaim claim = LiveAccountClaim.onAccount(connection, runId, holder);
                    claim.prepare();
                    refusal = claim.acquire();
                    if (refusal == null) {
                        accountClaim = claim;
                        claimConnection = connection;
                        claimHeld = true;
                    } else {
                        closeQuietly(connection);
                    }
                    break;
                } catch (final SQLException | IllegalStateException unreachable) {
                    closeQuietly(connection);
                    failure = new IllegalStateException("Cannot claim the live account in "
                        + LiveAccountClaim.DATABASE + ": " + unreachable.getMessage(), unreachable);
                }
            }
            if (!claimHeld) {
                final IllegalStateException stop = refusal != null ? new IllegalStateException(refusal) : failure;
                claimProblem = stop.getMessage();
                throw stop;
            }
            final LiveAccountClaim claim = accountClaim;
            logger.info("Live account claimed by this run: {} (run {})", claim.holder(), claim.runId());
            heartbeat = new Timer("live-account-claim", true);
            final long period = LiveAccountClaim.HEARTBEAT_SECONDS * 1000L;
            heartbeat.schedule(new TimerTask() {
                @Override
                public void run() {
                    beat();
                }
            }, period, period);
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    closeShared();
                    releaseAccountClaim();
                }
            }, "live-snowflake-cleanup"));
        }
    }

    /**
     * Refreshes the claim's heartbeat. A claim that is no longer this run's — another run holds it, it was
     * released, or its table is gone — marks this run as stopped; a broken claim session is reopened for the next
     * beat, since the claim itself lives in the row, not in the session.
     */
    private static void beat() {
        synchronized (CLAIM_LOCK) {
            if (!claimHeld) {
                return;
            }
            try {
                final String lost = accountClaim.heartbeat();
                if (lost != null) {
                    claimProblem = lost;
                    claimHeld = false;
                    heartbeat.cancel();
                    logger.error(lost);
                }
            } catch (final SQLException | RuntimeException broken) {
                logger.warn("Live account claim: heartbeat failed ({}); reconnecting for the next one",
                    broken.getMessage());
                closeQuietly(claimConnection);
                try {
                    claimConnection = connect();
                    accountClaim = LiveAccountClaim.onAccount(claimConnection, accountClaim.runId(),
                        accountClaim.holder());
                } catch (final IllegalStateException unreachable) {
                    logger.warn("Live account claim: cannot reconnect ({})", unreachable.getMessage());
                }
            }
        }
    }

    /** Gives this run's claim up at JVM exit — only while it is still this run's — and closes its session. */
    private static void releaseAccountClaim() {
        synchronized (CLAIM_LOCK) {
            if (heartbeat != null) {
                heartbeat.cancel();
            }
            if (claimHeld) {
                claimHeld = false;
                try {
                    accountClaim.release();
                } catch (final SQLException e) {
                    logger.warn("Live account claim: cannot release it ({}); it goes stale in {} s",
                        e.getMessage(), LiveAccountClaim.STALE_AFTER_SECONDS);
                }
            }
            closeQuietly(claimConnection);
            claimConnection = null;
        }
    }

    /** Who this run is, for the refusal another run reads: user, host, process and working directory. */
    private static String describeThisRun() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (final UnknownHostException unknown) {
            host = "an unknown host";
        }
        return System.getProperty("user.name") + "@" + host + " pid " + ProcessHandle.current().pid() + " in "
            + System.getProperty("user.dir");
    }

    private static void closeQuietly(final Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (final SQLException ignored) {
            // Best-effort close only.
        }
    }

    /** A new prepared connection to the account, outside the claim. */
    private static Connection connect() {
        final Properties props = new Properties();
        props.put("user", requiredEnv("SF_USER"));
        props.put("password", requiredEnv("SF_PASS"));
        try {
            // JSON results, not the driver's Arrow default: the Arrow path of driver 4.x renders
            // a FILE value's SIZE as the ENCRYPTED object size (24 bytes read back as 32) where
            // the JSON path carries the true content size — measured by bisecting the two formats
            // over the same SSE stage.
            final Connection connection = DriverManager.getConnection(
                "jdbc:snowflake://" + requiredEnv("SF_URL") + "/?JDBC_QUERY_RESULT_FORMAT=JSON", props);
            prepareSession(connection);
            return connection;
        } catch (final SQLException e) {
            throw new IllegalStateException("Cannot connect to live Snowflake: " + e.getMessage(), e);
        }
    }

    /**
     * The JVM-wide shared connection used by {@link LiveSnowflakeEngine}. Opened lazily; the exit hook the
     * account claim registers drops the {@code test_db} working database and closes the session at JVM exit.
     * Fails, like every live statement path, once this run's claim on the account is refused or lost.
     */
    public static synchronized Connection shared() {
        checkAccountClaim();
        if (shared == null) {
            shared = open();
            captureBaseline();
        }
        return shared;
    }

    /**
     * Drops the shared connection so the next statement opens a FRESH session. The recovery for a
     * shared-session storm: under load the driver can execute a statement late or twice, leaving
     * the session in a temporally impossible state (USE SCHEMA failing right after its CREATE
     * succeeded). Closing quietly is fine — every caller reaches the connection through
     * {@link #shared()}, which reopens on demand with the full session prep.
     */
    public static synchronized void forceReconnect() {
        if (shared != null) {
            try {
                shared.close();
            } catch (final Exception ignored) {
                // A storm-hit session may fail its own close; the reopen is what matters.
            }
            shared = null;
            sharedDirty = false;
        }
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
            pinSingleStatement(statement);
            statement.execute("ROLLBACK");
            statement.execute("ALTER SESSION SET AUTOCOMMIT = TRUE");
            // Restore the session-parameter baseline too — a dirty session may have run its own
            // ALTER SESSION (JSON_INDENT, QUERY_TAG, …), and those changes poison every later
            // semi-structured comparison if left standing.
            applySessionParameters(statement);
            clearSessionVariables(statement);
            clearSessionParameters(statement);
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

    /**
     * Pins the per-statement MULTI_STATEMENT_COUNT override to 1 — the account default — so a
     * test's {@code ALTER SESSION SET MULTI_STATEMENT_COUNT} cannot break the transport: the
     * harness splits every script into single-statement submissions, and the session-level count
     * must never apply to them (it otherwise fails every later submission, the reset included,
     * with "Actual statement count 1 did not match the desired statement count N").
     */
    static void pinSingleStatement(final Statement statement) {
        try {
            if (statement.isWrapperFor(SnowflakeStatement.class)) {
                statement.unwrap(SnowflakeStatement.class).setParameter("MULTI_STATEMENT_COUNT", 1);
            }
        } catch (final SQLException ignored) {
            // Transport hardening only — a clean session works without the override.
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
        if (claimHeld) {
            // Only while the claim is this run's: after a lost claim, test_db is the holder's.
            try (final Statement statement = shared.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS test_db");
            } catch (final Exception ignored) {
                // Best-effort cleanup only.
            }
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
    /**
     * Unset every session VARIABLE a test left behind. They are invisible to the parameter baseline —
     * nothing in {@code ALTER SESSION} touches them — so without this a later test's SHOW VARIABLES
     * reads the whole run's accumulation rather than its own.
     *
     * @param statement the session's statement
     */
    private static void clearSessionVariables(final Statement statement) {
        final List<String> names = new ArrayList<String>();
        try (final ResultSet rs = statement.executeQuery("SHOW VARIABLES")) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        } catch (final SQLException noVariables) {
            return;
        }
        for (final String name : names) {
            try {
                statement.execute("UNSET " + name);
            } catch (final SQLException alreadyGone) {
                continue;
            }
        }
    }

    /**
     * Unset every session-LEVEL parameter a test set, leaving the four the harness sets deliberately.
     * A parameter the suite never names — TIMESTAMP_TYPE_MAPPING, say — otherwise survives the test
     * that set it and silently retypes every later column.
     *
     * @param statement the session's statement
     */
    private static void clearSessionParameters(final Statement statement) {
        final List<String> keys = new ArrayList<String>();
        try (final ResultSet rs = statement.executeQuery("SHOW PARAMETERS IN SESSION")) {
            while (rs.next()) {
                final String key = rs.getString("key");
                if ("SESSION".equalsIgnoreCase(rs.getString("level")) && !HARNESS_PARAMETERS.contains(key)) {
                    keys.add(key);
                }
            }
        } catch (final SQLException noParameters) {
            return;
        }
        for (final String key : keys) {
            try {
                statement.execute("ALTER SESSION UNSET " + key);
            } catch (final SQLException notUnsettable) {
                continue;
            }
        }
    }

    private static void applySessionParameters(final Statement statement) throws SQLException {
        // The result format is left at the driver's default (ARROW). It used to be forced to JSON
        // because Arrow needs the add-opens flag under Java 17 — the surefire argLine supplies it.
        // Forcing JSON was not free: the JSON payload TRUNCATES every DOUBLE to 10 significant
        // digits, so the account answered PI() as 3.141592654 where Arrow answers
        // 3.141592653589793. Frostlake was reported wrong on seven correct trigonometric results
        // because of it, and any genuine difference below the 10th digit was invisible.
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
        // The engine models no secondary roles (CURRENT_SECONDARY_ROLES() is empty), while the account's
        // users default to ALL, which lengthens every missing-object refusal's privilege hint to "Your
        // primary role R or one of your secondary roles must have ...". NONE gives the short form the
        // engine speaks. USE ROLE leaves the secondary roles as they were, so a reset re-applies this too.
        statement.execute("USE SECONDARY ROLES NONE");
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
