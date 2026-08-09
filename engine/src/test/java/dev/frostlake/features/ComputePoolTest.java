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
import dev.frostlake.storage.ResultSetColumn;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compute pools, live-verified end to end: the CREATE option set with MIN_NODES / MAX_NODES /
 * INSTANCE_FAMILY required, SHOW COMPUTE POOLS' 21 columns, DESCRIBE's 23 (error_code and
 * status_message added), the instance-family catalog, and every rejection's exact wording.
 */
public class ComputePoolTest extends BaseDatabaseTest {

    private static final String HARNESS_ERASES_COLUMN_TYPES =
        "the comparison harness declares every live column VARCHAR";

    private static final String CREATE_SUSPENDED = "CREATE COMPUTE POOL P1"
        + " MIN_NODES = 1 MAX_NODES = 1 INSTANCE_FAMILY = CPU_X64_XS"
        + " INITIALLY_SUSPENDED = TRUE AUTO_SUSPEND_SECS = 60 COMMENT = 'probe pool'";

    /**
     * Compute pools are ACCOUNT-level, so they escape the per-test {@code test_db} teardown. Left
     * behind on a live account they collide with the next run — "Object 'P1' already exists" — and
     * inflate every listing this class asserts on. Only the names this class creates are dropped:
     * an account also carries pools nobody here made, including Snowflake's own SYSTEM_COMPUTE_POOL_*.
     */
    @AfterEach
    public void dropThePoolsThisClassCreates() {
        for (final String pool : List.of("P1", "P2", "AP1", "BP1")) {
            try {
                engine.execute("DROP COMPUTE POOL IF EXISTS " + pool);
            } catch (final RuntimeException alreadyGone) {
                // Best effort: a pool this test never got as far as creating is not a failure.
            }
        }
    }

    private List<String> columnNames(final ResultSet rs) {
        final List<String> names = new ArrayList<>();
        for (final ResultSetColumn column : rs.getColumns()) {
            names.add(column.getName());
        }
        return names;
    }

    private Object cell(final ResultSet rs, final int row, final String column) {
        return rs.getRows().get(row).getValue(rs.getColumnIndex(column));
    }

    private String typeOf(final ResultSet rs, final String column) {
        return rs.getColumns().get(rs.getColumnIndex(column)).getDataType().getName();
    }

    private void rejected(final String sql, final String message) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(e.getMessage().contains(message),
            "expected \"" + message + "\" in: " + e.getMessage());
    }

    @Test
    public void showCarriesLiveColumnLayoutAndValues() {
        engine.execute(CREATE_SUSPENDED);
        final ResultSet rs = engine.executeQuery("SHOW COMPUTE POOLS");
        assertEquals(List.of(
            "name", "state", "min_nodes", "max_nodes", "instance_family", "num_services",
            "num_jobs", "auto_suspend_secs", "auto_resume", "active_nodes", "idle_nodes",
            "target_nodes", "created_on", "resumed_on", "updated_on", "owner", "comment",
            "is_exclusive", "application", "placement_group", "backup_instance_families"),
            columnNames(rs));
        assertEquals(1, rs.getRowCount());
        assertEquals("P1", cell(rs, 0, "name"));
        assertEquals("SUSPENDED", cell(rs, 0, "state"));
        assertEquals(1L, cell(rs, 0, "min_nodes"));
        assertEquals("CPU_X64_XS", cell(rs, 0, "instance_family"));
        assertEquals(60L, cell(rs, 0, "auto_suspend_secs"));
        assertEquals("true", cell(rs, 0, "auto_resume"));
        assertEquals(0L, cell(rs, 0, "target_nodes"));
        assertEquals("probe pool", cell(rs, 0, "comment"));
        assertEquals("false", cell(rs, 0, "is_exclusive"));
        assertNull(cell(rs, 0, "application"));
        assertNull(cell(rs, 0, "placement_group"));
        assertEquals("", cell(rs, 0, "backup_instance_families"));
        // Never resumed: the epoch, exactly as a real account reports it.
        assertTrue(String.valueOf(cell(rs, 0, "resumed_on")).startsWith("19"),
            "got: " + cell(rs, 0, "resumed_on"));
    }

    @Test
    public void describeAddsErrorCodeAndStatusMessage() {
        engine.execute(CREATE_SUSPENDED);
        final ResultSet rs = engine.executeQuery("DESCRIBE COMPUTE POOL P1");
        assertEquals(List.of(
            "name", "state", "min_nodes", "max_nodes", "instance_family", "num_services",
            "num_jobs", "auto_suspend_secs", "auto_resume", "active_nodes", "idle_nodes",
            "target_nodes", "created_on", "resumed_on", "updated_on", "owner", "comment",
            "is_exclusive", "application", "error_code", "status_message", "placement_group",
            "backup_instance_families"),
            columnNames(rs));
        assertEquals(1, rs.getRowCount());
        assertEquals("", cell(rs, 0, "error_code"));
        assertEquals("", cell(rs, 0, "status_message"));
    }

    /**
     * The column TYPES, which the value assertions only imply. Live reports the node and job counters
     * as NUMBER; everything beside them is text, including {@code auto_resume}, {@code is_exclusive}
     * and — despite its 93.13 — the family catalog's {@code storage_gib}.
     *
     * <p>{@code created_on} / {@code resumed_on} / {@code updated_on} are TIMESTAMP_LTZ, which is the
     * whole of the SHOW family and not a compute-pool quirk — see {@code ShowTimestampTypeTest}.
     *
     * <p>Not asserted live either: the comparison harness declares every column VARCHAR whatever the
     * account said, so live's signal for this fact is the value's Java type, which the assertions
     * elsewhere in this class compare.
     */
    @Test
    public void theCountersAreDeclaredNumeric() {
        Assumptions.assumeFalse(isLiveSnowflake(), HARNESS_ERASES_COLUMN_TYPES);
        engine.execute(CREATE_SUSPENDED);
        for (final String listing : List.of("SHOW COMPUTE POOLS", "DESCRIBE COMPUTE POOL P1")) {
            final ResultSet rs = engine.executeQuery(listing);
            for (final String counter : List.of("min_nodes", "max_nodes", "num_services", "num_jobs",
                "auto_suspend_secs", "active_nodes", "idle_nodes", "target_nodes")) {
                assertEquals("NUMBER", typeOf(rs, counter), listing + " . " + counter);
            }
            assertEquals("VARCHAR", typeOf(rs, "auto_resume"));
            assertEquals("VARCHAR", typeOf(rs, "is_exclusive"));
            for (final String moment : List.of("created_on", "resumed_on", "updated_on")) {
                assertEquals("TIMESTAMP_LTZ", typeOf(rs, moment), listing + " . " + moment);
            }
        }
        final ResultSet families = engine.executeQuery("SHOW COMPUTE POOL INSTANCE FAMILIES");
        for (final String numeric : List.of("vcpu", "memory_gib", "gpu_count", "gpu_memory_gib",
            "current_node_usage")) {
            assertEquals("NUMBER", typeOf(families, numeric), numeric);
        }
        assertEquals("VARCHAR", typeOf(families, "storage_gib"));
        assertEquals(1L, cell(families, 0, "vcpu"));
        assertEquals("93.13", cell(families, 0, "storage_gib"));
    }

    /** The optional properties default exactly as a real account's do. */
    @Test
    public void optionalPropertiesDefault() {
        engine.execute("CREATE COMPUTE POOL P1 MIN_NODES = 1 MAX_NODES = 2 INSTANCE_FAMILY = GPU_NV_S");
        final ResultSet rs = engine.executeQuery("SHOW COMPUTE POOLS");
        assertEquals("true", cell(rs, 0, "auto_resume"));
        assertEquals(3600L, cell(rs, 0, "auto_suspend_secs"));
        assertNull(cell(rs, 0, "comment"));
        // INITIALLY_SUSPENDED defaults false, so the pool is asking for its nodes.
        assertEquals("STARTING", cell(rs, 0, "state"));
        assertEquals(1L, cell(rs, 0, "target_nodes"));
    }

    @Test
    public void aDuplicateIsAGenericObjectClash() {
        engine.execute(CREATE_SUSPENDED);
        rejected("CREATE COMPUTE POOL P1 MIN_NODES = 1 MAX_NODES = 1 INSTANCE_FAMILY = CPU_X64_XS",
            "Object 'P1' already exists.");
        // IF NOT EXISTS: a silent no-op.
        engine.execute("CREATE COMPUTE POOL IF NOT EXISTS P1"
            + " MIN_NODES = 1 MAX_NODES = 1 INSTANCE_FAMILY = CPU_X64_XS");
    }

    /** The required options are reported missing together, in canonical order. */
    @Test
    public void missingRequiredOptionsAreListed() {
        rejected("CREATE COMPUTE POOL P2 MIN_NODES = 1 MAX_NODES = 1",
            "Missing option(s): [INSTANCE_FAMILY]");
        rejected("CREATE COMPUTE POOL P2 INSTANCE_FAMILY = CPU_X64_XS",
            "Missing option(s): [MIN_NODES, MAX_NODES]");
    }

    @Test
    public void nodeRangeIsValidated() {
        rejected("CREATE COMPUTE POOL P2 MIN_NODES = 0 MAX_NODES = 1 INSTANCE_FAMILY = CPU_X64_XS",
            "invalid value '0' for property 'MIN_NODES'");
        rejected("CREATE COMPUTE POOL P2 MIN_NODES = 3 MAX_NODES = 1 INSTANCE_FAMILY = CPU_X64_XS",
            "invalid property combination 'MIN_NODES'='3' and 'MAX_NODES'='1'");
    }

    @Test
    public void instanceFamilyIsValidatedAgainstTheCatalog() {
        rejected("CREATE COMPUTE POOL P2 MIN_NODES = 1 MAX_NODES = 1 INSTANCE_FAMILY = ROBOT",
            "Invalid instance family ROBOT. Please refer to Snowflake documentation for supported"
            + " instance families.");
    }

    /** A quoted lower-case name is refused outright — pool names must be upper case. */
    @Test
    public void theNameMustBeUpperCase() {
        rejected("CREATE COMPUTE POOL \"lower_pool\""
            + " MIN_NODES = 1 MAX_NODES = 1 INSTANCE_FAMILY = CPU_X64_XS",
            "Invalid compute pool name: 'lower_pool'. Name must be uppercase.");
    }

    @Test
    public void alterSetAppliesAndUnsetRestoresDefaults() {
        engine.execute(CREATE_SUSPENDED);
        engine.execute("ALTER COMPUTE POOL P1 SET MAX_NODES = 2 AUTO_SUSPEND_SECS = 120 COMMENT = 'changed'");
        ResultSet rs = engine.executeQuery("SHOW COMPUTE POOLS");
        assertEquals(2L, cell(rs, 0, "max_nodes"));
        assertEquals(120L, cell(rs, 0, "auto_suspend_secs"));
        assertEquals("changed", cell(rs, 0, "comment"));

        engine.execute("ALTER COMPUTE POOL P1 UNSET COMMENT");
        engine.execute("ALTER COMPUTE POOL P1 UNSET AUTO_SUSPEND_SECS");
        rs = engine.executeQuery("SHOW COMPUTE POOLS");
        assertNull(cell(rs, 0, "comment"));
        assertEquals(3600L, cell(rs, 0, "auto_suspend_secs"));
    }

    @Test
    public void alterSetValidatesTheResultingNodeRange() {
        engine.execute(CREATE_SUSPENDED);
        rejected("ALTER COMPUTE POOL P1 SET MIN_NODES = 5",
            "invalid property combination 'MIN_NODES'='5' and 'MAX_NODES'='1'");
    }

    @Test
    public void suspendResumeAndStopAll() {
        engine.execute(CREATE_SUSPENDED);
        // Suspending a suspended pool succeeds.
        engine.execute("ALTER COMPUTE POOL P1 SUSPEND");
        engine.execute("ALTER COMPUTE POOL P1 RESUME");
        final ResultSet rs = engine.executeQuery("SHOW COMPUTE POOLS");
        assertEquals("STARTING", cell(rs, 0, "state"));
        assertEquals(1L, cell(rs, 0, "target_nodes"));
        // resumed_on now carries the resume time, not the epoch.
        assertTrue(String.valueOf(cell(rs, 0, "resumed_on")).startsWith("20"),
            "got: " + cell(rs, 0, "resumed_on"));
        engine.execute("ALTER COMPUTE POOL P1 STOP ALL");
        engine.execute("ALTER COMPUTE POOL P1 SUSPEND");
        assertEquals("SUSPENDED",
            cell(engine.executeQuery("SHOW COMPUTE POOLS"), 0, "state"));
    }

    /** A missing pool reports the same wording from DESCRIBE, DROP and ALTER alike. */
    @Test
    public void aMissingPoolIsReportedUniformly() {
        rejected("DESCRIBE COMPUTE POOL NO_SUCH_POOL",
            "Compute pool 'NO_SUCH_POOL' does not exist or not authorized.");
        rejected("DROP COMPUTE POOL NO_SUCH_POOL",
            "Compute pool 'NO_SUCH_POOL' does not exist or not authorized.");
        rejected("ALTER COMPUTE POOL NO_SUCH_POOL SUSPEND",
            "Compute pool 'NO_SUCH_POOL' does not exist or not authorized.");
        // The IF EXISTS spellings are clean no-ops.
        engine.execute("DROP COMPUTE POOL IF EXISTS NO_SUCH_POOL");
        engine.execute("ALTER COMPUTE POOL IF EXISTS NO_SUCH_POOL SUSPEND");
    }

    @Test
    public void showSupportsLikeStartsWithAndLimit() {
        engine.execute("CREATE COMPUTE POOL AP1 MIN_NODES = 1 MAX_NODES = 1"
            + " INSTANCE_FAMILY = CPU_X64_XS INITIALLY_SUSPENDED = TRUE");
        engine.execute("CREATE COMPUTE POOL BP1 MIN_NODES = 1 MAX_NODES = 1"
            + " INSTANCE_FAMILY = CPU_X64_XS INITIALLY_SUSPENDED = TRUE");
        assertEquals(1, engine.executeQuery("SHOW COMPUTE POOLS LIKE 'AP%'").getRowCount());
        assertEquals(1, engine.executeQuery("SHOW COMPUTE POOLS STARTS WITH 'BP'").getRowCount());
        assertEquals(1, engine.executeQuery("SHOW COMPUTE POOLS LIMIT 1").getRowCount());
        // Both of this class's pools are listed. NOT an exact global count: a real account carries
        // pools this test never made (Snowflake's own SYSTEM_COMPUTE_POOL_*, and anything the user
        // has), so asserting "exactly 2" measures the account rather than the modifier.
        final ResultSet all = engine.executeQuery("SHOW COMPUTE POOLS");
        final List<String> listed = new ArrayList<>();
        for (int row = 0; row < all.getRowCount(); row++) {
            listed.add(String.valueOf(cell(all, row, "name")));
        }
        assertTrue(listed.contains("AP1"), "AP1 should be listed, got " + listed);
        assertTrue(listed.contains("BP1"), "BP1 should be listed, got " + listed);
    }

    /** The family catalog: 10 columns, the account's own order, reservation notes included. */
    @Test
    public void instanceFamilyCatalogIsListed() {
        final ResultSet rs = engine.executeQuery("SHOW COMPUTE POOL INSTANCE FAMILIES");
        assertEquals(List.of(
            "name", "description", "vcpu", "memory_gib", "storage_gib", "gpu", "gpu_count",
            "gpu_memory_gib", "current_node_usage", "message"),
            columnNames(rs));
        assertEquals(26, rs.getRowCount());
        // First row is the smallest family, not the alphabetical minimum — the order is the catalog's.
        assertEquals("CPU_X64_XS", cell(rs, 0, "name"));
        assertEquals(1L, cell(rs, 0, "vcpu"));
        assertEquals("93.13", cell(rs, 0, "storage_gib"));
        // A GPU row names its hardware.
        int gpuRow = -1;
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("GPU_NV_S".equals(cell(rs, i, "name"))) {
                gpuRow = i;
            }
        }
        assertEquals("NVIDIA A10G", cell(rs, gpuRow, "gpu"));
        // The reservation-only families carry the note in message.
        int xlRow = -1;
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("GPU_NV_XL".equals(cell(rs, i, "name"))) {
                xlRow = i;
            }
        }
        assertEquals("Instance family available by reservation only.", cell(rs, xlRow, "message"));
    }

    @Test
    public void dropRemovesThePool() {
        engine.execute(CREATE_SUSPENDED);
        engine.execute("DROP COMPUTE POOL P1");
        assertEquals(0, engine.executeQuery("SHOW COMPUTE POOLS").getRowCount());
    }

    // ── The ALTER surface beyond SET/UNSET of the simple properties, live-verified. ──────────────

    @Test
    public void unsetTakesACommaSeparatedList() {
        engine.execute(CREATE_SUSPENDED);
        engine.execute("ALTER COMPUTE POOL P1 UNSET COMMENT, AUTO_SUSPEND_SECS");
        final ResultSet rs = engine.executeQuery("SHOW COMPUTE POOLS");
        assertNull(cell(rs, 0, "comment"));
        assertEquals(3600L, cell(rs, 0, "auto_suspend_secs"));
    }

    /** INSTANCE_FAMILY changes only on a suspended pool, with live's wording when it is not. */
    @Test
    public void instanceFamilyChangesOnlyWhenSuspended() {
        engine.execute(CREATE_SUSPENDED);
        engine.execute("ALTER COMPUTE POOL P1 SET INSTANCE_FAMILY = CPU_X64_S");
        assertEquals("CPU_X64_S",
            cell(engine.executeQuery("SHOW COMPUTE POOLS"), 0, "instance_family"));

        rejected("ALTER COMPUTE POOL P1 SET INSTANCE_FAMILY = ROBOT",
            "Invalid instance family ROBOT.");

        engine.execute("ALTER COMPUTE POOL P1 RESUME");
        rejected("ALTER COMPUTE POOL P1 SET INSTANCE_FAMILY = CPU_X64_XS",
            "Invalid property 'INSTANCE_FAMILY' for 'COMPUTE_POOL':\nCannot set INSTANCE_FAMILY for"
            + " compute pool that is not suspended. Please suspend the compute pool first.");
    }

    /** Placement groups are a region registry this engine has none of — SET always fails its lookup. */
    @Test
    public void placementGroupsAreARegionRegistry() {
        engine.execute(CREATE_SUSPENDED);
        rejected("ALTER COMPUTE POOL P1 SET PLACEMENT_GROUP = 'pg1'",
            "Invalid value 'pg1' for property 'PLACEMENT_GROUP':\nPlacement group 'pg1' does not"
            + " exist in this region.");
        rejected("CREATE COMPUTE POOL P2 MIN_NODES = 1 MAX_NODES = 1 INSTANCE_FAMILY = CPU_X64_XS"
            + " PLACEMENT_GROUP = 'pg2'",
            "Invalid value 'pg2' for property 'PLACEMENT_GROUP':\nPlacement group 'pg2' does not"
            + " exist in this region.");
        // UNSET succeeds on a suspended pool, and refuses — with the same "set" wording — otherwise.
        engine.execute("ALTER COMPUTE POOL P1 UNSET PLACEMENT_GROUP");
        engine.execute("ALTER COMPUTE POOL P1 RESUME");
        rejected("ALTER COMPUTE POOL P1 UNSET PLACEMENT_GROUP",
            "Invalid property 'PLACEMENT_GROUP' for 'COMPUTE_POOL':\nCannot set PLACEMENT_GROUP for"
            + " compute pool that is not suspended. Please suspend the compute pool first.");
    }

    /** Backup families: catalog-checked, distinct from the primary, displayed comma-joined. */
    @Test
    public void backupFamiliesAreValidatedAndDisplayed() {
        engine.execute(CREATE_SUSPENDED);
        engine.execute("ALTER COMPUTE POOL P1"
            + " SET BACKUP_INSTANCE_FAMILIES = ('GEN_X64_G2_4', 'CPU_X64_S')");
        assertEquals("GEN_X64_G2_4,CPU_X64_S",
            cell(engine.executeQuery("SHOW COMPUTE POOLS"), 0, "backup_instance_families"));

        engine.execute("ALTER COMPUTE POOL P1 UNSET BACKUP_INSTANCE_FAMILIES");
        assertEquals("",
            cell(engine.executeQuery("SHOW COMPUTE POOLS"), 0, "backup_instance_families"));

        rejected("ALTER COMPUTE POOL P1 SET BACKUP_INSTANCE_FAMILIES = ('ROBOT')",
            "Invalid instance family ROBOT.");
        rejected("ALTER COMPUTE POOL P1 SET BACKUP_INSTANCE_FAMILIES = ('CPU_X64_XS')",
            "Invalid BACKUP_INSTANCE_FAMILIES for compute pool: BACKUP_INSTANCE_FAMILIES contains"
            + " 'CPU_X64_XS' which matches the primary INSTANCE_FAMILY. Each backup must be"
            + " different from the primary.");
        // The same clash is refused at CREATE.
        rejected("CREATE COMPUTE POOL P2 MIN_NODES = 1 MAX_NODES = 1 INSTANCE_FAMILY = CPU_X64_XS"
            + " BACKUP_INSTANCE_FAMILIES = ('CPU_X64_XS')",
            "matches the primary INSTANCE_FAMILY");
    }

    /** STOP ALL's OF TYPE list knows six types, and ALL must stand alone. */
    @Test
    public void stopAllValidatesItsWorkloadTypes() {
        engine.execute(CREATE_SUSPENDED);
        engine.execute("ALTER COMPUTE POOL P1 STOP ALL OF TYPE USER");
        engine.execute("ALTER COMPUTE POOL P1 STOP ALL OF TYPE NOTEBOOK, ML_JOB");
        rejected("ALTER COMPUTE POOL P1 STOP ALL OF TYPE ROBOT",
            "Invalid input for OF TYPE option: Invalid workload type 'ROBOT'. Expected comma"
            + " separated list like: 'type1, type2'. Valid types are ALL, USER, NOTEBOOK,"
            + " MODEL_SERVING, STREAMLIT, ML_JOB.");
        rejected("ALTER COMPUTE POOL P1 STOP ALL OF TYPE ALL, ML_JOB",
            "Invalid input for OF TYPE option: When 'ALL' is specified in 'ALL,ML_JOB', no other"
            + " workload types should be included. Use 'ALL' alone or specify individual types.");
    }

    /** SET TAG and UNSET TAG are their own actions — mixing TAG into a property SET is a syntax error. */
    @Test
    public void tagsAreTheirOwnAlterActions() {
        engine.execute(CREATE_SUSPENDED);
        engine.execute("CREATE TAG pool_tag");
        engine.execute("ALTER COMPUTE POOL P1 SET TAG pool_tag = 'v1'");
        assertEquals("v1", engine.getCatalog().getComputePool("P1").getTagValue("POOL_TAG"));
        engine.execute("ALTER COMPUTE POOL P1 UNSET TAG pool_tag");
        assertNull(engine.getCatalog().getComputePool("P1").getTagValue("POOL_TAG"));

        rejected("ALTER COMPUTE POOL P1 SET MAX_NODES = 3 TAG pool_tag = 'v2'", "syntax error");
    }
}
