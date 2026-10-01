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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The INFORMATION_SCHEMA catalog matches a real account's, view for view. Every name a real
 * account exposes is queryable here — populated where this engine models the objects, empty with
 * the live column shape where it does not, because a query that runs on Snowflake and fails here
 * is a fidelity bug regardless of whether it would have returned rows. Names Snowflake does NOT
 * expose are equally absent: answering those accepts a query a real account refuses.
 */
public class InformationSchemaCatalogTest extends BaseDatabaseTest {

    /** Every INFORMATION_SCHEMA view a real account exposes (measured on the account). */
    private static final List<String> LIVE_VIEWS = Arrays.asList(
        "APPLICABLE_ROLES", "APPLICATION_CONFIGURATIONS", "APPLICATION_SERVICES", "APPLICATION_SPECIFICATIONS",
        "BACKUPS", "BACKUP_POLICIES", "BACKUP_SETS", "CHECK_CONSTRAINTS", "CLASSES", "CLASS_INSTANCES",
        "CLASS_INSTANCE_FUNCTIONS", "CLASS_INSTANCE_PROCEDURES", "COLUMNS",
        "CORTEX_SEARCH_SERVICES", "CORTEX_SEARCH_SERVICE_SCORING_PROFILES",
        "CURRENT_PACKAGES_POLICY", "DATABASES", "ELEMENT_TYPES", "ENABLED_ROLES", "EVENT_TABLES",
        "EXTERNAL_TABLES", "FIELDS", "FILE_FORMATS", "FUNCTIONS", "GIT_REPOSITORIES",
        "HYBRID_TABLES", "INDEXES", "INDEX_COLUMNS", "INFORMATION_SCHEMA_CATALOG_NAME", "LISTINGS",
        "LOAD_HISTORY", "MODEL_VERSIONS", "NOTEBOOKS", "OBJECT_PRIVILEGES", "PACKAGES", "PIPES",
        "PROCEDURES", "REFERENTIAL_CONSTRAINTS", "REPLICATION_DATABASES", "REPLICATION_GROUPS",
        "SCHEMATA", "SEMANTIC_DIMENSIONS", "SEMANTIC_FACTS", "SEMANTIC_METRICS",
        "SEMANTIC_RELATIONSHIPS", "SEMANTIC_TABLES", "SEMANTIC_VARIABLES", "SEMANTIC_VIEWS",
        "SEQUENCES", "SERVICES", "SHARES", "SNAPSHOTS", "SNAPSHOT_POLICIES", "SNAPSHOT_SETS",
        "STAGES", "STREAMLITS", "TABLES", "TABLE_CONSTRAINTS", "TABLE_PRIVILEGES",
        "TABLE_STORAGE_METRICS", "TYPES", "USAGE_PRIVILEGES", "VIEWS");

    /** Names a real account does NOT expose as INFORMATION_SCHEMA views. */
    private static final List<String> NOT_LIVE_VIEWS = Arrays.asList(
        "STREAMS", "TASKS", "TAGS", "TAG_REFERENCES", "DYNAMIC_TABLES");

    @Test
    public void everyLiveViewIsQueryable() {
        for (final String view : LIVE_VIEWS) {
            final ResultSet rs = engine.executeQuery("SELECT * FROM INFORMATION_SCHEMA." + view);
            assertTrue(rs.getColumns().size() > 0, view + " must expose its columns");
        }
    }

    @Test
    public void namesSnowflakeDoesNotExposeAreRejected() {
        for (final String view : NOT_LIVE_VIEWS) {
            final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery("SELECT * FROM INFORMATION_SCHEMA." + view);
                }
            });
            assertTrue(String.valueOf(error.getMessage()).contains("does not exist"),
                "expected a does-not-exist rejection for " + view + ", got: " + error.getMessage());
        }
    }

    /** The catalog LISTS exactly those views too — TABLES and VIEWS agree with what is queryable. */
    @Test
    public void theCatalogListsExactlyTheLiveViewSet() {
        final ResultSet listed = engine.executeQuery(
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'INFORMATION_SCHEMA'");
        final Set<String> names = new HashSet<>();
        for (final Row row : listed.getRows()) {
            names.add(String.valueOf(row.getValue(0)));
        }
        assertEquals(new HashSet<>(LIVE_VIEWS), names);
    }

    /** FILE_FORMATS reports a created format and its options. */
    @Test
    public void fileFormatsReportsTheFormatAndItsOptions() {
        engine.execute("CREATE FILE FORMAT ff TYPE = CSV SKIP_HEADER = 2 FIELD_DELIMITER = '|'");
        final ResultSet rs = engine.executeQuery(
            "SELECT FILE_FORMAT_NAME, FILE_FORMAT_TYPE FROM INFORMATION_SCHEMA.FILE_FORMATS");
        assertEquals(1, rs.getRowCount());
        assertEquals("FF", String.valueOf(rs.getRows().get(0).getValue(0)));
        assertEquals("CSV", String.valueOf(rs.getRows().get(0).getValue(1)));
    }

    /** HYBRID_TABLES lists hybrid tables only. */
    @Test
    public void hybridTablesListsOnlyHybridTables() {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "creates a HYBRID TABLE, which the verification account cannot do — a real account "
            + "answers \"Hybrid tables are currently not available to trial accounts\"; the view "
            + "itself is covered live by everyLiveViewIsQueryable");
        engine.execute("CREATE TABLE plain_t (id INTEGER)");
        engine.execute("CREATE HYBRID TABLE hybrid_t (id INTEGER PRIMARY KEY)");
        final ResultSet rs = engine.executeQuery("SELECT NAME FROM INFORMATION_SCHEMA.HYBRID_TABLES");
        assertEquals(1, rs.getRowCount());
        assertEquals("HYBRID_T", String.valueOf(rs.getRows().get(0).getValue(0)));
    }

    /** INFORMATION_SCHEMA_CATALOG_NAME names the database being read. */
    @Test
    public void catalogNameViewNamesTheDatabase() {
        final ResultSet rs = engine.executeQuery(
            "SELECT CATALOG_NAME FROM INFORMATION_SCHEMA.INFORMATION_SCHEMA_CATALOG_NAME");
        assertEquals(1, rs.getRowCount());
        assertEquals("TEST_DB", String.valueOf(rs.getRows().get(0).getValue(0)).toUpperCase());
    }

    /**
     * TABLE_STORAGE_METRICS carries a row per LIVE table; byte counts are zero for in-memory
     * storage. All three drop stamps are filtered on purpose — that is what they are for: a real
     * account keeps a table listed until it leaves fail-safe, whether it was the TABLE that was
     * dropped or the SCHEMA or CATALOG containing it, so an account that has run this test before
     * legitimately holds several rows for the same name.
     */
    @Test
    public void storageMetricsCarriesARowPerTable() {
        engine.execute("CREATE TABLE metrics_t (id INTEGER)");
        final ResultSet rs = engine.executeQuery(
            "SELECT TABLE_NAME, ACTIVE_BYTES FROM INFORMATION_SCHEMA.TABLE_STORAGE_METRICS"
            + " WHERE TABLE_NAME = 'METRICS_T' AND TABLE_DROPPED IS NULL"
            + " AND SCHEMA_DROPPED IS NULL AND CATALOG_DROPPED IS NULL");
        assertEquals(1, rs.getRowCount());
        assertEquals(0L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
    }

    /**
     * The populated views carry live's FULL column list, in live's order — measured column for
     * column on a real account. A client that selects a column Snowflake has must not fail here,
     * and one that selects a column Snowflake lacks must not succeed.
     */
    @Test
    public void populatedViewsCarryLivesColumnList() {
        assertColumns("TABLES", "TABLE_CATALOG", "TABLE_SCHEMA", "TABLE_NAME", "TABLE_OWNER",
            "TABLE_TYPE", "IS_TRANSIENT", "CLUSTERING_KEY", "ROW_COUNT", "BYTES", "RETENTION_TIME",
            "SELF_REFERENCING_COLUMN_NAME", "REFERENCE_GENERATION", "USER_DEFINED_TYPE_CATALOG",
            "USER_DEFINED_TYPE_SCHEMA", "USER_DEFINED_TYPE_NAME", "IS_INSERTABLE_INTO", "IS_TYPED",
            "COMMIT_ACTION", "CREATED", "LAST_ALTERED", "LAST_DDL", "LAST_DDL_BY",
            "AUTO_CLUSTERING_ON", "COMMENT", "IS_TEMPORARY", "IS_ICEBERG", "IS_DYNAMIC",
            "IS_IMMUTABLE", "IS_HYBRID", "ROW_TIMESTAMP_ON", "ERROR_LOGGING", "IS_INTERACTIVE");
        assertColumns("VIEWS", "TABLE_CATALOG", "TABLE_SCHEMA", "TABLE_NAME", "TABLE_OWNER",
            "VIEW_DEFINITION", "CHECK_OPTION", "IS_UPDATABLE", "INSERTABLE_INTO", "IS_SECURE",
            "CREATED", "LAST_ALTERED", "LAST_DDL", "LAST_DDL_BY", "COMMENT");
        assertColumns("DATABASES", "DATABASE_NAME", "DATABASE_OWNER", "IS_TRANSIENT", "COMMENT",
            "CREATED", "LAST_ALTERED", "RETENTION_TIME", "TYPE",
            "REPLICABLE_WITH_FAILOVER_GROUPS", "OWNER_ROLE_TYPE");
        assertColumns("STAGES", "STAGE_CATALOG", "STAGE_SCHEMA", "STAGE_NAME", "STAGE_URL",
            "STAGE_REGION", "STAGE_TYPE", "STAGE_OWNER", "COMMENT", "CREATED", "LAST_ALTERED",
            "ENDPOINT", "DIRECTORY_ENABLED");
        assertColumns("ENABLED_ROLES", "ROLE_NAME", "ROLE_OWNER");
    }

    /**
     * A routine's returned shape follows live: a scalar function reports its own type and, for the
     * exact-numeric family, its precision/radix/scale; a TABLE function has no boolean flag at all
     * — its DATA_TYPE reads {@code TABLE (COL TYPE)}, which is what identifies it.
     */
    @Test
    public void routineViewsReportLivesReturnShape() {
        engine.execute("CREATE FUNCTION scalar_fn(n INTEGER) RETURNS INTEGER AS $$ SELECT 1 $$");
        engine.execute("CREATE FUNCTION table_fn() RETURNS TABLE(v VARCHAR) AS $$ SELECT 'a' $$");
        final ResultSet scalar = engine.executeQuery(
            "SELECT DATA_TYPE, NUMERIC_PRECISION, NUMERIC_PRECISION_RADIX, NUMERIC_SCALE,"
            + " VOLATILITY, IS_SECURE, IS_EXTERNAL FROM INFORMATION_SCHEMA.FUNCTIONS"
            + " WHERE FUNCTION_NAME = 'SCALAR_FN'");
        assertEquals(1, scalar.getRowCount());
        assertEquals(10L, ((Number) scalar.getRows().get(0).getValue(2)).longValue(),
            "the exact-numeric radix is 10");
        assertEquals("NO", scalar.getRows().get(0).getValue(5));
        assertEquals("NO", scalar.getRows().get(0).getValue(6));

        final ResultSet tableFn = engine.executeQuery(
            "SELECT DATA_TYPE, NUMERIC_PRECISION FROM INFORMATION_SCHEMA.FUNCTIONS"
            + " WHERE FUNCTION_NAME = 'TABLE_FN'");
        assertTrue(String.valueOf(tableFn.getRows().get(0).getValue(0)).startsWith("TABLE ("),
            "a table function is identified by its DATA_TYPE, got: "
                + tableFn.getRows().get(0).getValue(0));
        assertEquals(null, tableFn.getRows().get(0).getValue(1),
            "a table function reports no numeric precision");
    }

    /** PROCEDURES carries live's column list, including the routine-metadata tail. */
    @Test
    public void proceduresCarryLivesColumnList() {
        assertColumns("PROCEDURES", "PROCEDURE_CATALOG", "PROCEDURE_SCHEMA", "PROCEDURE_NAME",
            "PROCEDURE_OWNER", "ARGUMENT_SIGNATURE", "DATA_TYPE", "CHARACTER_MAXIMUM_LENGTH",
            "CHARACTER_OCTET_LENGTH", "NUMERIC_PRECISION", "NUMERIC_PRECISION_RADIX",
            "NUMERIC_SCALE", "PROCEDURE_LANGUAGE", "PROCEDURE_DEFINITION", "CREATED",
            "LAST_ALTERED", "COMMENT", "EXTERNAL_ACCESS_INTEGRATIONS", "SECRETS",
            "RUNTIME_VERSION", "PACKAGES", "INSTALLED_PACKAGES", "ARTIFACT_REPOSITORY");
    }

    /** INFORMATION_SCHEMA spells booleans YES / NO — never the Y / N that SHOW output uses. */
    @Test
    public void booleanFlagsUseTheYesNoSpelling() {
        engine.execute("CREATE TRANSIENT TABLE flag_t (id INTEGER)");
        final ResultSet rs = engine.executeQuery(
            "SELECT IS_TRANSIENT, IS_INSERTABLE_INTO, IS_TYPED, IS_HYBRID FROM INFORMATION_SCHEMA.TABLES"
            + " WHERE TABLE_NAME = 'FLAG_T'");
        assertEquals(1, rs.getRowCount());
        assertEquals("YES", rs.getRows().get(0).getValue(0));
        assertEquals("YES", rs.getRows().get(0).getValue(1));
        assertEquals("YES", rs.getRows().get(0).getValue(2));
        assertEquals("NO", rs.getRows().get(0).getValue(3));
    }

    /**
     * SEQUENCES reports the value the next NEXTVAL will draw, without consuming it: on an untouched
     * sequence that is its START. After a draw it only has to have MOVED PAST the drawn value — a
     * real account hands out values in blocks, so the reported next value jumps well beyond
     * START + INCREMENT (measured), while this engine is gapless. Both satisfy the sequence
     * contract, which promises only unique, increasing values.
     */
    @Test
    public void sequencesReportsNextValue() {
        engine.execute("CREATE SEQUENCE nv_seq START = 5 INCREMENT = 2");
        assertEquals(5L, nextValueOf("NV_SEQ"), "an untouched sequence reports its START");
        engine.executeQuery("SELECT nv_seq.NEXTVAL");
        assertTrue(nextValueOf("NV_SEQ") > 5L, "the reported next value must move past the drawn one");
    }

    /** A sequence's NEXT_VALUE as a number — a real account hands it back as text. */
    private long nextValueOf(final String sequenceName) {
        final ResultSet rs = engine.executeQuery(
            "SELECT NEXT_VALUE FROM INFORMATION_SCHEMA.SEQUENCES WHERE SEQUENCE_NAME = '"
            + sequenceName + "'");
        assertEquals(1, rs.getRowCount());
        final Object value = rs.getRows().get(0).getValue(0);
        return value instanceof Number ? ((Number) value).longValue()
            : Long.parseLong(String.valueOf(value).trim());
    }

    /** The column names of an INFORMATION_SCHEMA view, in order. */
    private void assertColumns(final String view, final String... expected) {
        final ResultSet rs = engine.executeQuery("SELECT * FROM INFORMATION_SCHEMA." + view);
        final List<String> actual = new ArrayList<>();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            actual.add(rs.getColumns().get(i).getName().toUpperCase());
        }
        assertEquals(Arrays.asList(expected), actual, view + " must carry live's column list");
    }

    /** An unmodeled view answers with its live column shape and no rows. */
    @Test
    public void anUnmodeledViewIsEmptyWithItsLiveShape() {
        final ResultSet rs = engine.executeQuery("SELECT * FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS");
        assertEquals(0, rs.getRowCount());
        final Set<String> columns = new HashSet<>();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            columns.add(rs.getColumns().get(i).getName());
        }
        assertEquals(new HashSet<>(Arrays.asList("CONSTRAINT_CATALOG", "CONSTRAINT_SCHEMA",
            "CONSTRAINT_TABLE", "CONSTRAINT_NAME", "CHECK_CLAUSE")), columns);
    }
}
