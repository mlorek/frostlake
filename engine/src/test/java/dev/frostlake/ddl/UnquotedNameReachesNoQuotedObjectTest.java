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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * An unquoted name is upper case, and a quoted lower-case object of the same letters is another object
 * (docs.snowflake.com identifier requirements: double-quoted identifiers are case-sensitive). GRANT, REVOKE, GRANT
 * OWNERSHIP and SHOW GRANTS ON a stream, a task, a pipe, an alert, a materialized view, a file format or a masking or
 * row access policy named LX_… refuse it as missing (FL-462); so do DESCRIBE, EXECUTE, ALTER and DROP of an alert, a
 * read, ALTER and DROP of a materialized view, DESCRIBE and DROP of a join policy, COMMENT ON VIEW and a query of a
 * stage's files (FL-463). The quoted name still reaches each one. The refusals are this engine's words for each kind,
 * so the engine alone is asked.
 */
public class UnquotedNameReachesNoQuotedObjectTest extends BaseDatabaseTest {

    private static final String SCHEMA = "TEST_DB.TEST_SCHEMA.";

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "the refusals are this engine's words");
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return refused.getMessage();
        }
    }

    private List<String> rows(final String sql) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            final List<String> cells = new ArrayList<>();
            for (final Object value : row.getValues()) {
                cells.add(String.valueOf(value));
            }
            out.add(String.join("|", cells));
        }
        return out;
    }

    private void assertMissing(final String sql, final String kind, final String name) {
        final String refused = refusal(sql);
        assertTrue(refused.contains(kind + " '" + name + "' does not exist or not authorized."), sql + ": " + refused);
    }

    @Test
    public void grantsRevokesAndOwnershipReachOnlyTheExactName() {
        engine.execute("CREATE TABLE ux_t (id INT)");
        engine.execute("ALTER TABLE ux_t SET CHANGE_TRACKING = TRUE");
        engine.execute("CREATE STREAM \"lx_s\" ON TABLE ux_t");
        engine.execute("CREATE TASK \"lx_t\" AS SELECT 1");
        engine.execute("CREATE ALERT \"lx_a\" IF (EXISTS (SELECT 1)) THEN SELECT 1");
        engine.execute("CREATE STAGE \"lx_st\"");
        engine.execute("CREATE PIPE \"lx_p\" AS COPY INTO ux_t FROM @\"lx_st\"");
        engine.execute("CREATE FILE FORMAT \"lx_ff\" TYPE = CSV");
        engine.execute("CREATE MASKING POLICY \"lx_mp\" AS (v STRING) RETURNS STRING -> v");
        engine.execute("CREATE ROW ACCESS POLICY \"lx_rp\" AS (id INT) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE MATERIALIZED VIEW \"lx_mv\" AS SELECT id FROM ux_t");
        engine.execute("CREATE ROLE ux_r");
        try {
            assertMissing("GRANT SELECT ON STREAM LX_S TO ROLE ux_r", "Stream", "LX_S");
            assertMissing("GRANT MONITOR ON TASK LX_T TO ROLE ux_r", "Task", SCHEMA + "LX_T");
            assertMissing("GRANT MONITOR ON ALERT LX_A TO ROLE ux_r", "Alert", SCHEMA + "LX_A");
            assertMissing("GRANT OPERATE ON PIPE LX_P TO ROLE ux_r", "Pipe", SCHEMA + "LX_P");
            assertMissing("GRANT USAGE ON FILE FORMAT LX_FF TO ROLE ux_r", "File format", "LX_FF");
            assertMissing("GRANT APPLY ON MASKING POLICY LX_MP TO ROLE ux_r", "Masking policy", SCHEMA + "LX_MP");
            assertMissing("GRANT APPLY ON ROW ACCESS POLICY LX_RP TO ROLE ux_r", "Row access policy",
                SCHEMA + "LX_RP");
            assertMissing("GRANT SELECT ON MATERIALIZED VIEW LX_MV TO ROLE ux_r", "Materialized view", "LX_MV");
            assertTrue(rows("SHOW GRANTS TO ROLE ux_r").isEmpty());
            engine.execute("GRANT SELECT ON STREAM \"lx_s\" TO ROLE ux_r");
            assertMissing("REVOKE SELECT ON STREAM LX_S FROM ROLE ux_r", "Stream", "LX_S");
            assertMissing("SHOW GRANTS ON STREAM LX_S", "Stream", "LX_S");
            assertMissing("SHOW GRANTS ON ALERT LX_A", "Alert", SCHEMA + "LX_A");
            assertMissing("GRANT OWNERSHIP ON STREAM LX_S TO ROLE ux_r", "Stream", "LX_S");
            assertMissing("GRANT OWNERSHIP ON TASK LX_T TO ROLE ux_r", "Task", SCHEMA + "LX_T");
            // The quoted name keeps its grant and its owner.
            assertEquals(1, rows("SHOW GRANTS TO ROLE ux_r").size());
            assertTrue(rows("SHOW GRANTS ON STREAM \"lx_s\"").toString().contains("|OWNERSHIP|STREAM|"));
            assertFalse(rows("SHOW STREAMS LIKE 'lx_s'").get(0).contains("|UX_R|"));
            engine.execute("REVOKE SELECT ON STREAM \"lx_s\" FROM ROLE ux_r");
            engine.execute("GRANT MONITOR ON ALERT \"lx_a\" TO ROLE ux_r");
            engine.execute("GRANT SELECT ON MATERIALIZED VIEW \"lx_mv\" TO ROLE ux_r");
            assertEquals(2, rows("SHOW GRANTS TO ROLE ux_r").size());
        } finally {
            engine.execute("DROP ROLE IF EXISTS ux_r");
        }
    }

    @Test
    public void alertsMaterializedViewsJoinPoliciesViewsAndStagesReachOnlyTheExactName() {
        engine.execute("CREATE TABLE ux_t (id INT)");
        engine.execute("INSERT INTO ux_t VALUES (1)");
        engine.execute("CREATE ALERT \"lx_a\" IF (EXISTS (SELECT 1)) THEN SELECT 1");
        engine.execute("CREATE MATERIALIZED VIEW \"lx_mv\" AS SELECT id FROM ux_t");
        engine.execute("CREATE VIEW \"lx_v\" AS SELECT 1 x");
        engine.execute("CREATE STAGE \"lx_st\"");
        engine.execute("COPY INTO @\"lx_st\"/a.csv FROM (SELECT 7) FILE_FORMAT = (TYPE = CSV COMPRESSION = NONE)"
            + " SINGLE = TRUE");
        engine.execute("CREATE JOIN POLICY \"lx_j\" AS () RETURNS JOIN_CONSTRAINT"
            + " -> JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        assertMissing("DESCRIBE ALERT LX_A", "Alert", SCHEMA + "LX_A");
        assertMissing("EXECUTE ALERT LX_A", "Alert", SCHEMA + "LX_A");
        assertMissing("ALTER ALERT LX_A SET COMMENT = 'x'", "Alert", SCHEMA + "LX_A");
        assertMissing("DROP ALERT LX_A", "Alert", SCHEMA + "LX_A");
        assertMissing("SELECT * FROM LX_MV", "Object", "LX_MV");
        assertMissing("ALTER MATERIALIZED VIEW LX_MV SET COMMENT = 'x'", "Materialized view", SCHEMA + "LX_MV");
        assertMissing("DROP MATERIALIZED VIEW LX_MV", "Materialized view", SCHEMA + "LX_MV");
        assertMissing("DESCRIBE JOIN POLICY LX_J", "Join policy", SCHEMA + "LX_J");
        assertMissing("DROP JOIN POLICY LX_J", "Join policy", SCHEMA + "LX_J");
        assertMissing("COMMENT ON VIEW LX_V IS 'x'", "View", SCHEMA + "LX_V");
        assertMissing("SELECT $1 FROM @LX_ST", "Stage", SCHEMA + "LX_ST");
        // The quoted names still reach every one.
        assertEquals(List.of("7"), rows("SELECT $1 FROM @\"lx_st\""));
        assertEquals(List.of("1"), rows("SELECT * FROM \"lx_mv\""));
        engine.execute("COMMENT ON VIEW \"lx_v\" IS 'x'");
        engine.execute("EXECUTE ALERT \"lx_a\"");
        assertEquals("lx_j", rows("DESCRIBE JOIN POLICY \"lx_j\"").get(0).split("\\|")[0]);
        engine.execute("DROP JOIN POLICY \"lx_j\"");
        engine.execute("DROP MATERIALIZED VIEW \"lx_mv\"");
        engine.execute("DROP ALERT \"lx_a\"");
        assertTrue(rows("SHOW ALERTS LIKE 'lx_a'").isEmpty());
        assertTrue(rows("SHOW MATERIALIZED VIEWS LIKE 'lx_mv'").isEmpty());
    }
}
