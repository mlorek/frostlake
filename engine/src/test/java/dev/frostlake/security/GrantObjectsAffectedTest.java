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

package dev.frostlake.security;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How GRANT and REVOKE count the objects they reach — {@code Statement executed successfully. N objects affected.}
 * — and which objects a bulk grant over a kind reaches: an event table is no TABLE, a temporary table or view is
 * never reached, the multi-word kinds have bulk forms of their own, and pipes are refused in bulk.
 */
public class GrantObjectsAffectedTest extends BaseDatabaseTest {

    private static final String ROLE = "GOA_ROLE";
    private static final String EXECUTED = "Statement executed successfully.";

    @Override
    protected void setupTest() {
        engine.executeQuery("CREATE ROLE " + ROLE);
        engine.executeQuery("CREATE SCHEMA goa");
        engine.executeQuery("CREATE TABLE goa.t1 (a INT)");
        engine.executeQuery("CREATE TRANSIENT TABLE goa.t2 (a INT)");
        engine.executeQuery("CREATE EVENT TABLE goa.et");
        engine.executeQuery("CREATE FILE FORMAT goa.ff TYPE = CSV");
        engine.executeQuery("CREATE STAGE goa.st");
        engine.executeQuery("CREATE PIPE goa.p AS COPY INTO goa.t1 FROM @goa.st");
        engine.executeQuery("USE SCHEMA test_db.test_schema");
    }

    @Override
    protected void teardownTest() {
        try {
            engine.executeQuery("DROP ROLE IF EXISTS " + ROLE);
        } catch (final RuntimeException ignored) {
            // cleanup only
        }
    }

    private String status(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private List<String> grants() {
        final ResultSet rs = engine.executeQuery("SHOW GRANTS TO ROLE " + ROLE);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(row.getValue(rs.getColumnIndex("privilege")) + "|" + row.getValue(rs.getColumnIndex("granted_on"))
                + "|" + row.getValue(rs.getColumnIndex("name")));
        }
        return out;
    }

    @Test
    public void aBulkGrantCountsTheObjectsInItsScope() {
        // ALL TABLES reaches the permanent and the transient table, not the event table; a repeat counts again.
        assertEquals(EXECUTED + " 2 objects affected.",
            status("GRANT SELECT ON ALL TABLES IN SCHEMA goa TO ROLE " + ROLE));
        assertEquals(EXECUTED + " 2 objects affected.",
            status("GRANT SELECT ON ALL TABLES IN SCHEMA goa TO ROLE " + ROLE));
        assertEquals(EXECUTED + " 1 objects affected.",
            status("GRANT SELECT ON ALL EVENT TABLES IN SCHEMA goa TO ROLE " + ROLE));
        assertEquals(EXECUTED + " 1 objects affected.",
            status("GRANT USAGE ON ALL FILE FORMATS IN SCHEMA goa TO ROLE " + ROLE));
        assertEquals(EXECUTED + " 0 objects affected.",
            status("GRANT SELECT ON ALL MATERIALIZED VIEWS IN SCHEMA goa TO ROLE " + ROLE));
        assertEquals(EXECUTED + " 0 objects affected.",
            status("GRANT SELECT ON ALL DYNAMIC TABLES IN SCHEMA goa TO ROLE " + ROLE));
        assertEquals(EXECUTED + " 0 objects affected.",
            status("GRANT SELECT ON ALL EXTERNAL TABLES IN SCHEMA goa TO ROLE " + ROLE));
        assertEquals(EXECUTED + " 0 objects affected.",
            status("GRANT MONITOR ON ALL ALERTS IN SCHEMA goa TO ROLE " + ROLE));
        assertEquals(List.of("SELECT|EVENT_TABLE|TEST_DB.GOA.ET", "USAGE|FILE_FORMAT|TEST_DB.GOA.FF",
            "SELECT|TABLE|TEST_DB.GOA.T1", "SELECT|TABLE|TEST_DB.GOA.T2"), grants());
    }

    @Test
    public void aRevokeCountsTheObjectsItTookAPrivilegeFrom() {
        engine.executeQuery("GRANT SELECT ON ALL TABLES IN SCHEMA goa TO ROLE " + ROLE);
        assertEquals(EXECUTED + " 1 objects affected.",
            status("REVOKE SELECT, INSERT ON TABLE test_db.goa.t1 FROM ROLE " + ROLE));
        assertEquals(EXECUTED + " 0 objects affected.",
            status("REVOKE SELECT ON TABLE test_db.goa.t1 FROM ROLE " + ROLE));
        assertEquals(EXECUTED + " 1 objects affected.",
            status("REVOKE SELECT ON ALL TABLES IN SCHEMA goa FROM ROLE " + ROLE));
        engine.executeQuery("GRANT USAGE ON SCHEMA test_db.goa TO ROLE " + ROLE);
        assertEquals(EXECUTED + " 0 objects affected.",
            status("REVOKE GRANT OPTION FOR USAGE ON SCHEMA test_db.goa FROM ROLE " + ROLE));
        assertEquals(EXECUTED + " 1 objects affected.",
            status("REVOKE USAGE ON SCHEMA test_db.goa FROM ROLE " + ROLE + " RESTRICT"));
        engine.executeQuery("GRANT CREATE DATABASE ON ACCOUNT TO ROLE " + ROLE);
        assertEquals(EXECUTED + " 1 objects affected.",
            status("REVOKE CREATE DATABASE ON ACCOUNT FROM ROLE " + ROLE));
        // Future grants and single-object grants carry no count.
        assertEquals(EXECUTED, status("GRANT SELECT ON FUTURE TABLES IN SCHEMA goa TO ROLE " + ROLE));
        assertEquals(EXECUTED, status("REVOKE SELECT ON FUTURE TABLES IN SCHEMA goa FROM ROLE " + ROLE));
        assertEquals(EXECUTED, status("GRANT SELECT ON TABLE test_db.goa.t2 TO ROLE " + ROLE));
        assertEquals(List.of("SELECT|TABLE|TEST_DB.GOA.T2"), grants());
    }

    @Test
    public void pipesAreRefusedInBulkButNotInTheFuture() {
        assertTrue(refusal("GRANT MONITOR ON ALL PIPES IN SCHEMA goa TO ROLE " + ROLE)
            .contains("Bulk grant on objects of type PIPE to ROLE is restricted."));
        assertTrue(refusal("REVOKE MONITOR ON ALL PIPES IN SCHEMA goa FROM ROLE " + ROLE)
            .contains("Bulk revoke on objects of type PIPE from ROLE is restricted."));
        engine.executeQuery("CREATE DATABASE ROLE goa_dr");
        assertTrue(refusal("GRANT MONITOR ON ALL PIPES IN SCHEMA goa TO DATABASE ROLE goa_dr")
            .contains("Bulk grant on objects of type PIPE to DATABASE ROLE is restricted."));
        assertTrue(refusal("REVOKE MONITOR ON ALL PIPES IN SCHEMA goa FROM DATABASE ROLE goa_dr")
            .contains("Bulk revoke on objects of type PIPE from DATABASE ROLE is restricted."));
        assertEquals(EXECUTED, status("GRANT MONITOR ON FUTURE PIPES IN SCHEMA goa TO ROLE " + ROLE));
    }

    @Test
    public void aBulkStatementPassesTemporaryObjectsBy() {
        engine.executeQuery("CREATE TEMPORARY TABLE goa.tmp (a INT)");
        engine.executeQuery("CREATE VIEW goa.v AS SELECT 1 AS x");
        engine.executeQuery("CREATE TEMPORARY VIEW goa.tv AS SELECT 1 AS x");
        assertEquals(EXECUTED + " 2 objects affected.",
            status("GRANT SELECT ON ALL TABLES IN SCHEMA goa TO ROLE " + ROLE));
        assertEquals(EXECUTED + " 1 objects affected.",
            status("GRANT SELECT ON ALL VIEWS IN SCHEMA goa TO ROLE " + ROLE));
        // A temporary table takes a grant of its own, which a bulk revoke then passes by as well.
        assertEquals(EXECUTED, status("GRANT SELECT ON TABLE test_db.goa.tmp TO ROLE " + ROLE));
        assertEquals(EXECUTED + " 2 objects affected.",
            status("REVOKE SELECT ON ALL TABLES IN SCHEMA goa FROM ROLE " + ROLE));
        assertEquals(List.of("SELECT|TABLE|TEST_DB.GOA.TMP", "SELECT|VIEW|TEST_DB.GOA.V"), grants());
        // Ownership moves the same way, counted.
        assertEquals(EXECUTED + " 2 objects affected.",
            status("GRANT OWNERSHIP ON ALL TABLES IN SCHEMA goa TO ROLE " + ROLE));
        final ResultSet tables = engine.executeQuery("SHOW TABLES IN SCHEMA test_db.goa");
        assertEquals(ROLE, cell(tables, soleRowWhere(tables, "name", "T1"), "owner"));
        assertEquals(ROLE, cell(tables, soleRowWhere(tables, "name", "T2"), "owner"));
        assertTrue(!ROLE.equals(cell(tables, soleRowWhere(tables, "name", "TMP"), "owner")),
            "a temporary table keeps its owner");
    }

    @Test
    public void theMultiWordKindsHaveFutureGrantsOfTheirOwn() {
        for (final String grant : new String[] {"SELECT ON FUTURE DYNAMIC TABLES", "SELECT ON FUTURE EVENT TABLES",
            "SELECT ON FUTURE MATERIALIZED VIEWS", "USAGE ON FUTURE FILE FORMATS", "SELECT ON FUTURE EXTERNAL TABLES",
            "MONITOR ON FUTURE ALERTS"}) {
            assertEquals(EXECUTED, status("GRANT " + grant + " IN SCHEMA goa TO ROLE " + ROLE));
        }
        final ResultSet rs = engine.executeQuery("SHOW FUTURE GRANTS IN SCHEMA goa");
        final List<String> future = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            future.add(row.getValue(rs.getColumnIndex("grant_on")) + "|" + row.getValue(rs.getColumnIndex("name")));
        }
        assertEquals(List.of("ALERT|TEST_DB.GOA.<ALERT>", "DYNAMIC_TABLE|TEST_DB.GOA.<DYNAMIC_TABLE>",
            "EVENT_TABLE|TEST_DB.GOA.<EVENT_TABLE>", "EXTERNAL_TABLE|TEST_DB.GOA.<EXTERNAL_TABLE>",
            "FILE_FORMAT|TEST_DB.GOA.<FILE_FORMAT>", "MATERIALIZED_VIEW|TEST_DB.GOA.<MATERIALIZED_VIEW>"), future);
    }
}
