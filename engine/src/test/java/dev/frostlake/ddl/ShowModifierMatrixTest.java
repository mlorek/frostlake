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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which SHOW listings actually act on TERSE / STARTS WITH / LIMIT, and which merely accept them.
 *
 * <p>A real account does not apply these three uniformly, and the split is not guessable, so every
 * assertion here is a transcription of what one answered (a scratch database with two of each
 * object in one schema). The three shapes being pinned:
 *
 * <ul>
 *   <li>listings that <b>filter</b> — TABLES, VIEWS, SCHEMAS, STREAMS, TASKS, the policies;</li>
 *   <li>listings that <b>parse the modifier and drop it</b> — STAGES, SEQUENCES, FILE FORMATS,
 *       WAREHOUSES, COLUMNS and the routine listings, plus TAGS for STARTS WITH only;</li>
 *   <li>the per-listing TERSE column shapes, which range from five columns to fourteen to none at all.</li>
 * </ul>
 *
 * <p>Each "ignored" case asserts against a prefix or page size that would visibly bite if it were
 * honoured, and pins the unmodified listing beside it, so neither a filter that appeared nor one that
 * vanished could slip through.
 */
public class ShowModifierMatrixTest extends BaseDatabaseTest {

    private void createPair() {
        engine.execute("CREATE TABLE m_a (id INTEGER)");
        engine.execute("CREATE TABLE m_b (id INTEGER)");
        engine.execute("CREATE VIEW v_a AS SELECT 1 AS c");
        engine.execute("CREATE VIEW v_b AS SELECT 2 AS c");
        engine.execute("CREATE SEQUENCE q_a");
        engine.execute("CREATE SEQUENCE q_b");
        engine.execute("CREATE STAGE g_a");
        engine.execute("CREATE STAGE g_b");
        engine.execute("CREATE FILE FORMAT ff_a TYPE = CSV");
        engine.execute("CREATE FILE FORMAT ff_b TYPE = JSON");
        engine.execute("CREATE TAG tg_a");
        engine.execute("CREATE TAG tg_b");
        engine.execute("CREATE STREAM s_a ON TABLE m_a");
        engine.execute("CREATE STREAM s_b ON TABLE m_b");
    }

    private List<String> columnNames(final ResultSet rs) {
        final List<String> names = new ArrayList<>();
        for (final ResultSetColumn col : rs.getColumns()) {
            names.add(col.getName());
        }
        return names;
    }

    private List<String> names(final ResultSet rs) {
        final int idx = rs.getColumnIndex("name");
        final List<String> result = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            result.add(String.valueOf(row.getValue(idx)));
        }
        return result;
    }

    // ---------- STARTS WITH: honoured here ----------

    @Test
    public void startsWithFiltersTablesViewsAndStreams() {
        createPair();
        assertEquals(List.of("M_A", "M_B"), names(engine.executeQuery("SHOW TABLES STARTS WITH 'M'")));
        assertEquals(0, engine.executeQuery("SHOW TABLES STARTS WITH 'ZZZ'").getRowCount());
        assertEquals(List.of("V_A", "V_B"), names(engine.executeQuery("SHOW VIEWS STARTS WITH 'V'")));
        assertEquals(0, engine.executeQuery("SHOW STREAMS STARTS WITH 'ZZZ'").getRowCount());
        assertEquals(2, engine.executeQuery("SHOW STREAMS").getRowCount());
    }

    // ---------- STARTS WITH: parsed and dropped ----------

    /**
     * {@code SHOW STAGES STARTS WITH 'ZZZ'} returned both stages live, and {@code SHOW SEQUENCES},
     * {@code SHOW FILE FORMATS}, {@code SHOW TAGS} and {@code SHOW WAREHOUSES} did the same. LIKE still
     * filters on those listings — live {@code SHOW WAREHOUSES LIKE 'ZZZ%'} answered nothing — so it is
     * these two modifiers that are dropped, not filtering in general.
     */
    @Test
    public void startsWithIsAcceptedAndIgnoredOnStagesSequencesFileFormatsAndTags() {
        createPair();
        assertEquals(2, engine.executeQuery("SHOW STAGES STARTS WITH 'ZZZ'").getRowCount());
        assertEquals(2, engine.executeQuery("SHOW STAGES").getRowCount());
        assertEquals(2, engine.executeQuery("SHOW SEQUENCES STARTS WITH 'ZZZ'").getRowCount());
        assertEquals(2, engine.executeQuery("SHOW FILE FORMATS STARTS WITH 'ZZZ'").getRowCount());
        assertEquals(2, engine.executeQuery("SHOW TAGS STARTS WITH 'ZZZ'").getRowCount());
        assertTrue(engine.executeQuery("SHOW WAREHOUSES STARTS WITH 'ZZZ'").getRowCount() >= 1);
        // LIKE is unaffected and still filters these same listings.
        assertEquals(0, engine.executeQuery("SHOW STAGES LIKE 'ZZZ%'").getRowCount());
    }

    /** The routine listings drop both modifiers: live, STARTS WITH 'FN' still returned all 1136 rows. */
    @Test
    public void routineListingsAcceptAndIgnoreStartsWithAndLimit() {
        engine.execute("CREATE FUNCTION rf(x INTEGER) RETURNS INTEGER AS 'x + 1'");
        final int all = engine.executeQuery("SHOW FUNCTIONS").getRowCount();
        assertTrue(all > 2, "the built-in catalog is listed");
        assertEquals(all, engine.executeQuery("SHOW FUNCTIONS STARTS WITH 'ZZZ'").getRowCount());
        assertEquals(all, engine.executeQuery("SHOW FUNCTIONS LIMIT 2").getRowCount());
        final int procs = engine.executeQuery("SHOW PROCEDURES").getRowCount();
        assertEquals(procs, engine.executeQuery("SHOW PROCEDURES LIMIT 1").getRowCount());
    }

    /** SHOW COLUMNS drops both: live, STARTS WITH 'I' returned all 10 rows though only 9 are named ID. */
    @Test
    public void showColumnsAcceptsAndIgnoresStartsWithAndLimit() {
        engine.execute("CREATE TABLE c_one (id INTEGER, txt VARCHAR)");
        final int all = engine.executeQuery("SHOW COLUMNS").getRowCount();
        assertEquals(2, all);
        assertEquals(all, engine.executeQuery("SHOW COLUMNS STARTS WITH 'I'").getRowCount());
        assertEquals(all, engine.executeQuery("SHOW COLUMNS LIMIT 1").getRowCount());
    }

    // ---------- LIMIT: the honour set differs from STARTS WITH's ----------

    /**
     * TAGS is the asymmetric listing: live it honoured {@code LIMIT 1} (1 row of 2) while ignoring
     * {@code STARTS WITH 'ZZZ'} (2 rows of 2). Nothing about the object type predicts it, which is
     * exactly why both directions are pinned here.
     */
    @Test
    public void tagsHonourLimitButIgnoreStartsWith() {
        createPair();
        assertEquals(2, engine.executeQuery("SHOW TAGS").getRowCount());
        assertEquals(1, engine.executeQuery("SHOW TAGS LIMIT 1").getRowCount());
        assertEquals(2, engine.executeQuery("SHOW TAGS STARTS WITH 'ZZZ'").getRowCount());
    }

    /** Live: {@code SHOW SEQUENCES LIMIT 1} returned both sequences, and stages/file formats likewise. */
    @Test
    public void limitIsAcceptedAndIgnoredOnStagesSequencesAndFileFormats() {
        createPair();
        assertEquals(2, engine.executeQuery("SHOW SEQUENCES LIMIT 1").getRowCount());
        assertEquals(2, engine.executeQuery("SHOW STAGES LIMIT 1").getRowCount());
        assertEquals(2, engine.executeQuery("SHOW FILE FORMATS LIMIT 1").getRowCount());
        assertTrue(engine.executeQuery("SHOW WAREHOUSES LIMIT 1").getRowCount() >= 1);
    }

    /**
     * PIPES drops both modifiers — and this one had to be measured rather than reasoned about. Two pipes
     * over internal stages, live: {@code LIMIT 1} returned both and {@code STARTS WITH 'ZZZ'} returned
     * both, though every other schema-level object listing of that shape (STREAMS, TASKS) filters.
     */
    @Test
    public void pipesAcceptAndIgnoreBothModifiers() {
        engine.execute("CREATE TABLE pt (id INTEGER)");
        engine.execute("CREATE STAGE ps");
        engine.execute("CREATE PIPE pipe_a AS COPY INTO pt FROM @ps");
        engine.execute("CREATE PIPE pipe_b AS COPY INTO pt FROM @ps");
        assertEquals(2, engine.executeQuery("SHOW PIPES").getRowCount());
        assertEquals(2, engine.executeQuery("SHOW PIPES LIMIT 1").getRowCount());
        assertEquals(2, engine.executeQuery("SHOW PIPES STARTS WITH 'ZZZ'").getRowCount());
    }

    /** Streams and tasks paginate, and the retained rows are the first by name. */
    @Test
    public void streamsAndTasksHonourLimit() {
        createPair();
        assertEquals(List.of("S_A"), names(engine.executeQuery("SHOW STREAMS LIMIT 1")));
        assertEquals(List.of("S_A", "S_B"), names(engine.executeQuery("SHOW STREAMS")));
        engine.execute("CREATE TASK tk_a SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK tk_b SCHEDULE = '60 MINUTE' AS SELECT 2");
        assertEquals(List.of("TK_A"), names(engine.executeQuery("SHOW TASKS LIMIT 1")));
    }

    // ---------- TERSE column shapes ----------

    /**
     * TERSE reports {@code kind} even where the untrimmed listing has no such column, and reports
     * MATERIALIZED_VIEW for a materialized view — live, {@code SHOW VIEWS} answers 12 columns none of
     * which is kind, while {@code SHOW TERSE VIEWS} answers the standard five with kind filled in.
     */
    @Test
    public void terseViewsSynthesisesKind() {
        engine.execute("CREATE VIEW tv AS SELECT 1 AS c");
        // A materialized view needs a real table source — live rejects a FROM-less definition
        // ("'VALUES' should be a table"), so the fixture selects from one.
        engine.execute("CREATE TABLE tmv_src (c INTEGER)");
        engine.execute("CREATE MATERIALIZED VIEW tmv AS SELECT c FROM tmv_src");
        final ResultSet rs = engine.executeQuery("SHOW TERSE VIEWS");
        assertEquals(List.of("created_on", "name", "kind", "database_name", "schema_name"), columnNames(rs));
        final int kind = rs.getColumnIndex("kind");
        final int name = rs.getColumnIndex("name");
        for (final Row row : rs.getRows()) {
            final String expected = "TMV".equals(String.valueOf(row.getValue(name)))
                ? "MATERIALIZED_VIEW" : "VIEW";
            assertEquals(expected, String.valueOf(row.getValue(kind)));
        }
    }

    /** TERSE SCHEMAS / DATABASES keep the five-column shape, nulling what the listing cannot answer. */
    @Test
    public void terseSchemasAndDatabasesKeepTheFiveColumnShape() {
        final List<String> standard =
            List.of("created_on", "name", "kind", "database_name", "schema_name");
        final ResultSet schemas = engine.executeQuery("SHOW TERSE SCHEMAS");
        assertEquals(standard, columnNames(schemas));
        for (final Row row : schemas.getRows()) {
            assertEquals("null", String.valueOf(row.getValue(schemas.getColumnIndex("kind"))),
                "live reports a null kind for schemas");
        }
        final ResultSet databases = engine.executeQuery("SHOW TERSE DATABASES");
        assertEquals(standard, columnNames(databases));
        assertTrue(databases.getRowCount() >= 1);
        // The row for THIS test's database, not row 0 — the listing is account-wide and its first row
        // is whichever name sorts first, whose kind is its own business (live has STANDARD,
        // APPLICATION, IMPORTED DATABASE and PERSONAL DATABASE in one listing).
        String ownKind = null;
        for (final Row row : databases.getRows()) {
            if ("TEST_DB".equalsIgnoreCase(
                    String.valueOf(row.getValue(databases.getColumnIndex("name"))))) {
                ownKind = String.valueOf(row.getValue(databases.getColumnIndex("kind")));
            }
        }
        assertEquals("STANDARD", ownKind);
    }

    /** STREAMS and TASKS take a sixth TERSE column apiece — tableOn and schedule. */
    @Test
    public void terseStreamsAndTasksCarryASixthColumn() {
        createPair();
        final ResultSet streams = engine.executeQuery("SHOW TERSE STREAMS");
        assertEquals(List.of("created_on", "name", "kind", "database_name", "schema_name", "tableOn"),
            columnNames(streams));
        final Row first = streams.getRows().get(0);
        assertEquals("DELTA", String.valueOf(first.getValue(streams.getColumnIndex("kind"))));
        assertEquals("M_A", String.valueOf(first.getValue(streams.getColumnIndex("tableOn"))));

        engine.execute("CREATE TASK tk SCHEDULE = '60 MINUTE' AS SELECT 1");
        final ResultSet tasks = engine.executeQuery("SHOW TERSE TASKS");
        assertEquals(List.of("created_on", "name", "kind", "database_name", "schema_name", "schedule"),
            columnNames(tasks));
    }

    /** ROLES and USERS take their own TERSE subsets, neither of which is the standard five. */
    @Test
    public void terseRolesAndUsersHaveTheirOwnSubsets() {
        assertEquals(List.of("name", "is_default", "is_current", "is_inherited",
                "is_from_organization_user_group"),
            columnNames(engine.executeQuery("SHOW TERSE ROLES")));
        assertEquals(List.of("name", "created_on", "display_name", "first_name", "last_name", "email",
                "comment", "has_password", "has_rsa_public_key", "type", "has_mfa", "has_pat",
                "has_workload_identity", "is_from_organization_user"),
            columnNames(engine.executeQuery("SHOW TERSE USERS")));
    }

    /**
     * On the dozen listings below TERSE is accepted and inert — the listing comes back whole. Frostlake
     * used to trim them to whichever of five columns they happened to have, which left
     * {@code SHOW TERSE COLUMNS} with 3 columns where a real account answers all 13.
     */
    @Test
    public void terseIsInertOnTheListingsThatDoNotTrim() {
        createPair();
        assertUntrimmed("SHOW COLUMNS", "SHOW TERSE COLUMNS");
        assertUntrimmed("SHOW SEQUENCES", "SHOW TERSE SEQUENCES");
        assertUntrimmed("SHOW STAGES", "SHOW TERSE STAGES");
        assertUntrimmed("SHOW FILE FORMATS", "SHOW TERSE FILE FORMATS");
        assertUntrimmed("SHOW TAGS", "SHOW TERSE TAGS");
        assertUntrimmed("SHOW WAREHOUSES", "SHOW TERSE WAREHOUSES");
        assertUntrimmed("SHOW PIPES", "SHOW TERSE PIPES");
        assertUntrimmed("SHOW MASKING POLICIES", "SHOW TERSE MASKING POLICIES");
        assertUntrimmed("SHOW ROW ACCESS POLICIES", "SHOW TERSE ROW ACCESS POLICIES");
        assertUntrimmed("SHOW MATERIALIZED VIEWS", "SHOW TERSE MATERIALIZED VIEWS");
        assertUntrimmed("SHOW DYNAMIC TABLES", "SHOW TERSE DYNAMIC TABLES");
        assertUntrimmed("SHOW PRIMARY KEYS", "SHOW TERSE PRIMARY KEYS");
        assertUntrimmed("SHOW FUNCTIONS", "SHOW TERSE FUNCTIONS");
        assertUntrimmed("SHOW PROCEDURES", "SHOW TERSE PROCEDURES");
    }

    private void assertUntrimmed(final String plain, final String terse) {
        final ResultSet bare = engine.executeQuery(plain);
        final ResultSet trimmed = engine.executeQuery(terse);
        assertNotNull(trimmed, terse);
        assertEquals(columnNames(bare), columnNames(trimmed), terse + " must not trim the column set");
        assertEquals(bare.getRowCount(), trimmed.getRowCount(), terse);
    }
}
