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

import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Base class for all Frostlake engine tests providing common setup/teardown.
 *
 * <p>With {@code SF_LIVE=1} in the environment (see {@link LiveSnowflake}) the SQL these tests
 * submit runs against a real Snowflake account instead of the embedded engine — the same suite,
 * flipped by a switch, to confirm Frostlake and Snowflake agree. In live mode {@code test_db} is
 * recreated on the account before each test for isolation.
 */
public abstract class BaseDatabaseTest {

    protected DatabaseEngine engine;

    @BeforeEach
    public void baseSetup() {
        if (LiveSnowflake.enabled()) {
            engine = new LiveSnowflakeEngine();
            // A previous test may have left the shared session in an open transaction or with
            // autocommit/role/warehouse moved — restore the baseline before touching test_db.
            LiveSnowflake.resetSharedIfDirty();
            // Record which account-level objects pre-date the run, before the suite creates any.
            LiveAccountObjects.captureBaseline(LiveSnowflake.shared());
            try {
                createTestContext("CREATE OR REPLACE DATABASE test_db");
            } catch (final RuntimeException storm) {
                // A NETWORK RULE a NETWORK POLICY still names keeps the database alive whatever the
                // drop says: the association is the POLICY's. Clear it and the replace goes through —
                // without this, one test's leftovers stop every test after it.
                if (String.valueOf(storm.getMessage()).contains(LiveNetworkRuleAssociations.REFUSAL)) {
                    LiveNetworkRuleAssociations.clearFor(LiveSnowflake.shared(), "test_db");
                    createTestContext("CREATE OR REPLACE DATABASE test_db");
                    LiveAccountObjects.beginTest();
                    setupTest();
                    return;
                }
                // A long shared session can hit a storm — the driver executing a statement late
                // or twice under load, so the sequence fails with a temporally impossible error
                // (USE SCHEMA missing right after its CREATE, CREATE SCHEMA 'already exists'
                // right after the database was replaced). One retry on a FRESH connection
                // recovers it; a second failure is a real refusal and propagates.
                LiveSnowflake.forceReconnect();
                createTestContext("CREATE OR REPLACE DATABASE test_db");
            }
            // Everything the TEST creates from here on is the test's to clean up, not the harness's.
            LiveAccountObjects.beginTest();
        } else if (FrostlakeJdbc.enabled()) {
            // FL_JDBC=1: same suite, but every statement detours through the direct JDBC driver.
            engine = new FrostlakeJdbcEngine();
            enableLocalStageUrls(engine);
            createTestContext("CREATE DATABASE test_db");
        } else {
            engine = new DatabaseEngine();
            enableLocalStageUrls(engine);
            createTestContext("CREATE DATABASE test_db");
        }

        // Allow subclasses to add their own setup
        setupTest();
    }

    /** The per-test working context, from the database statement given to the final USE SCHEMA. */
    private void createTestContext(final String createDatabase) {
        engine.execute(createDatabase);
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("USE SCHEMA test_schema");
    }

    /** Whether this run targets live Snowflake — for tests that must skip engine-internal checks there. */
    protected static boolean isLiveSnowflake() {
        return LiveSnowflake.enabled();
    }

    /**
     * The suite's stages point at local {@code file://} directories the tests write, so the harness
     * opts in to the local-URL affordance the DEFAULT config refuses (a real account refuses those
     * URLs — see StageUrlPolicyTest for the default surface). REMOVE is enabled the same way: the
     * default config keeps it off as a destructive-command guard (RemoveCommandConfigTest pins
     * that), while a real account always allows it.
     */
    protected static void enableLocalStageUrls(final DatabaseEngine target) {
        target.getConfig().setProperty(EngineConfig.PROP_STAGE_FILE_URL_ENABLED, "true");
        target.getConfig().setProperty(EngineConfig.PROP_COMMAND_REMOVE_ENABLED, "true");
    }

    /**
     * Stage a small file on a named internal stage through the SAME SQL both transports run: the
     * content is written to a fresh local temp file and PUT with {@code AUTO_COMPRESS=FALSE}, so
     * the staged name is deterministic. Embedded, the file lands in the stage's engine-managed
     * directory; live, the JDBC driver uploads it.
     */
    protected void stageLocalFile(final String stage, final String fileName, final String content) {
        try {
            final Path dir = Files.createTempDirectory("fl_stage_put");
            final Path file = dir.resolve(fileName);
            Files.writeString(file, content);
            engine.execute("PUT file://" + file.toAbsolutePath() + " @" + stage
                + " AUTO_COMPRESS=FALSE");
        } catch (final IOException e) {
            throw new RuntimeException("could not stage " + fileName, e);
        }
    }

    @AfterEach
    public void baseTeardown() {
        // Allow subclasses to add their own teardown
        teardownTest();

        // Recreating test_db isolates everything INSIDE a database; roles, users, warehouses and
        // sibling databases outlive it and would collide with the next test that uses the same name.
        if (LiveSnowflake.enabled() && LiveAccountObjects.sawAccountObjectStatement()) {
            // Restore the session's role/warehouse first: dropping the role a test switched TO would
            // otherwise leave the shared session without a usable one.
            LiveSnowflake.resetSharedIfDirty();
            LiveAccountObjects.dropNewAccountObjects(LiveSnowflake.shared());
        }

        if (engine != null) {
            engine.shutdown();
        }
    }

    // ---- SHOW/DESCRIBE cell helpers -------------------------------------------------------------
    // Assertions over metadata go through the SQL surface (SHOW …, DESCRIBE …) so they hold against
    // whichever engine executed the DDL — embedded or live. Cells are addressed by COLUMN NAME, not
    // position, because live may append columns.

    /** The value of {@code column} in one SHOW/DESCRIBE result row, as text (null-safe). */
    protected final String cell(final ResultSet rs, final Row row, final String column) {
        final Object value = row.getValue(rs.getColumnIndex(column));
        return value == null ? null : value.toString();
    }

    /** The rows whose {@code column} cell equals {@code value}, compared case-insensitively. */
    protected final List<Row> rowsWhere(final ResultSet rs, final String column, final String value) {
        final List<Row> matches = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final String cell = cell(rs, row, column);
            if (cell != null && cell.equalsIgnoreCase(value)) {
                matches.add(row);
            }
        }
        return matches;
    }

    /** The single row whose {@code column} cell equals {@code value} — asserts exactly one match. */
    protected final Row soleRowWhere(final ResultSet rs, final String column, final String value) {
        final List<Row> matches = rowsWhere(rs, column, value);
        assertEquals(1, matches.size(), "expected exactly one row with " + column + "=" + value);
        return matches.get(0);
    }

    /** One DESCRIBE TABLE cell: the {@code header} column of {@code columnName}'s row. */
    protected final String describeCell(final String table, final String columnName, final String header) {
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE " + table);
        return cell(rs, soleRowWhere(rs, "name", columnName), header);
    }

    /**
     * An expected missing-object refusal with the privilege hint that ends it, addressed to the role this session
     * runs as (see {@link MissingObjectHint}), so one expectation holds embedded and live.
     */
    protected final String hinted(final String expected) {
        final String[] principal = sessionPrincipal();
        return MissingObjectHint.of(expected, principal[0], false, principal[1]);
    }

    /** The session's current role and account: asked of the account live, read from the engine embedded. */
    private String[] sessionPrincipal() {
        if (isLiveSnowflake()) {
            final Row row = engine.executeQuery("SELECT CURRENT_ROLE(), CURRENT_ACCOUNT()").getRows().get(0);
            return new String[] {String.valueOf(row.getValue(0)), String.valueOf(row.getValue(1))};
        }
        return new String[] {engine.getCurrentRole(), engine.getConfig().getAccountId()};
    }

    /**
     * Override this method to add test-specific setup
     */
    protected void setupTest() {
        // Default: no additional setup
    }

    /**
     * Override this method to add test-specific teardown
     */
    protected void teardownTest() {
        // Default: no additional teardown
    }
}
