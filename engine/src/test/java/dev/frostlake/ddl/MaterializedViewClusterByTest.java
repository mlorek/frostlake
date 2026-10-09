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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A materialized view takes a clustering key: CLUSTER BY stands on either side of the view's COMMENT, reads the
 * view's own columns, and is listed by SHOW MATERIALIZED VIEWS, INFORMATION_SCHEMA.TABLES and GET_DDL. ALTER sets
 * another key, pauses and resumes reclustering, and drops the key, as on a clustered table (all live-verified).
 */
public class MaterializedViewClusterByTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t1 (x INT, y INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 2), (3, 4)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private Object first(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertTrue(rs.next(), sql);
        return rs.getValue(0);
    }

    /** The key, the reclustering state, the text and the comment SHOW MATERIALIZED VIEWS lists for a view. */
    private String shown(final String name) {
        engine.executeQuery("SHOW MATERIALIZED VIEWS LIKE '" + name + "'");
        final ResultSet rs = engine.executeQuery("SELECT \"cluster_by\", \"automatic_clustering\", \"text\", \"comment\""
            + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertTrue(rs.next(), name);
        return rs.getValue(0) + " | " + rs.getValue(1) + " | " + rs.getValue(2) + " | " + rs.getValue(3);
    }

    @Test
    public void aKeyStandsOnEitherSideOfTheComment() {
        engine.execute("CREATE MATERIALIZED VIEW p_mv2 CLUSTER BY (x) COMMENT = 'hello' AS SELECT x FROM t1 WHERE x > 0");
        assertEquals("LINEAR(x) | ON | CREATE MATERIALIZED VIEW p_mv2 CLUSTER BY (x) comment = 'hello' AS SELECT x FROM t1"
            + " WHERE x > 0 | hello", shown("P_MV2"));
        engine.execute("CREATE MATERIALIZED VIEW mv_cl COMMENT = 'c1' CLUSTER BY (x, y) AS SELECT x, y FROM t1");
        assertEquals("LINEAR(x, y) | ON | CREATE MATERIALIZED VIEW mv_cl comment = 'c1' CLUSTER BY (x, y) AS SELECT x, y"
            + " FROM t1 | c1", shown("MV_CL"));
        final String ddl = "create or replace materialized view MV_CL(\n\tX,\n\tY\n) COMMENT='c1'\n cluster by (x, y) as"
            + " SELECT x, y FROM t1;";
        assertEquals(ddl, first("SELECT GET_DDL('view', 'mv_cl')"));
        assertEquals(ddl, first("SELECT GET_DDL('table', 'mv_cl')"));
        assertEquals(ddl, first("SELECT GET_DDL('materialized view', 'mv_cl')"));
        // Of several clauses the last one stands.
        engine.execute("CREATE MATERIALIZED VIEW mv_two CLUSTER BY (x) CLUSTER BY (y) AS SELECT x, y FROM t1");
        assertEquals("LINEAR(y) | ON | CREATE MATERIALIZED VIEW mv_two CLUSTER BY (x) CLUSTER BY (y) AS SELECT x, y FROM t1"
            + " | ", shown("MV_TWO"));
        engine.execute("CREATE MATERIALIZED VIEW mv_cq CLUSTER BY (x) COMMENT = 'a' CLUSTER BY (y) AS SELECT x, y FROM t1");
        assertTrue(shown("MV_CQ").startsWith("LINEAR(y) | ON | "), shown("MV_CQ"));
    }

    @Test
    public void aKeyIsAnExpressionOverTheViewsOwnColumns() {
        engine.execute("CREATE MATERIALIZED VIEW mv_ex CLUSTER BY (x + 1, y) AS SELECT x, y FROM t1");
        assertEquals("LINEAR(x + 1, y) | ON | CREATE MATERIALIZED VIEW mv_ex CLUSTER BY (x + 1, y) AS SELECT x, y FROM t1"
            + " | ", shown("MV_EX"));
        assertEquals("create or replace materialized view MV_EX(\n\tX,\n\tY\n) cluster by (x + 1, y) as SELECT x, y FROM"
            + " t1;", first("SELECT GET_DDL('view', 'mv_ex')"));
        engine.execute("CREATE SECURE MATERIALIZED VIEW mv_sec CLUSTER BY (y) AS SELECT x, y FROM t1");
        assertEquals("create or replace secure materialized view MV_SEC(\n\tX,\n\tY\n) cluster by (y) as SELECT x, y FROM"
            + " t1;", first("SELECT GET_DDL('view', 'mv_sec')"));
        // The key reads the names the view's column list gives.
        engine.execute("CREATE MATERIALIZED VIEW mv_alias (a, b) CLUSTER BY (a) AS SELECT x, y FROM t1");
        assertTrue(shown("MV_ALIAS").startsWith("LINEAR(a) | ON | "), shown("MV_ALIAS"));

        assertEquals("SQL compilation error: error line 1 at position 44\ninvalid identifier 'NOSUCH'",
            refusal("CREATE MATERIALIZED VIEW mv_bad CLUSTER BY (nosuch) AS SELECT x, y FROM t1"));
        assertEquals("SQL compilation error: error line 1 at position 45\ninvalid identifier 'T1.X'",
            refusal("CREATE MATERIALIZED VIEW mv_qual CLUSTER BY (t1.x) AS SELECT x, y FROM t1"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 46 unexpected ')'.",
            refusal("CREATE MATERIALIZED VIEW mv_empty CLUSTER BY () AS SELECT x, y FROM t1"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 65 unexpected 'BY'.",
            refusal("CREATE MATERIALIZED VIEW mv_after AS SELECT x, y FROM t1 CLUSTER BY (x)"));
        assertEquals("SQL compilation error:\nduplicate property 'COMMENT';",
            refusal("CREATE MATERIALIZED VIEW mv_cc COMMENT = 'a' COMMENT = 'b' AS SELECT x, y FROM t1"));
    }

    @Test
    public void alterSetsPausesAndDropsTheKey() {
        engine.execute("CREATE MATERIALIZED VIEW mv_cl COMMENT = 'c1' CLUSTER BY (x, y) AS SELECT x, y FROM t1");
        final String text = " | CREATE MATERIALIZED VIEW mv_cl comment = 'c1' CLUSTER BY (x, y) AS SELECT x, y FROM t1 | c1";
        engine.execute("ALTER MATERIALIZED VIEW mv_cl CLUSTER BY (y)");
        assertEquals("LINEAR(y) | ON" + text, shown("MV_CL"));
        engine.execute("ALTER MATERIALIZED VIEW mv_cl SUSPEND RECLUSTER");
        assertEquals("LINEAR(y) | OFF" + text, shown("MV_CL"));
        assertEquals("LINEAR(y)|NO", first("SELECT clustering_key || '|' || auto_clustering_on FROM information_schema.tables"
            + " WHERE table_name = 'MV_CL'"));
        engine.execute("ALTER MATERIALIZED VIEW mv_cl RESUME RECLUSTER");
        assertEquals("LINEAR(y) | ON" + text, shown("MV_CL"));
        assertEquals("LINEAR(y)|YES", first("SELECT clustering_key || '|' || auto_clustering_on FROM information_schema.tables"
            + " WHERE table_name = 'MV_CL'"));
        assertEquals("SQL compilation error: error line 1 at position 42\ninvalid identifier 'NOSUCH'",
            refusal("ALTER MATERIALIZED VIEW mv_cl CLUSTER BY (nosuch)"));
        assertEquals("Invalid clustering key 1\n", refusal("ALTER MATERIALIZED VIEW mv_cl CLUSTER BY (1)"));
        engine.execute("ALTER MATERIALIZED VIEW mv_cl DROP CLUSTERING KEY");
        assertEquals(" | OFF" + text, shown("MV_CL"));
        assertEquals("create or replace materialized view MV_CL(\n\tX,\n\tY\n) COMMENT='c1'\n as SELECT x, y FROM t1;",
            first("SELECT GET_DDL('view', 'mv_cl')"));
        // Dropping the key of a view that has none is no fault; pausing what is not clustered is.
        engine.execute("ALTER MATERIALIZED VIEW mv_cl DROP CLUSTERING KEY");
        assertEquals("Table 'MV_CL' is not clustered\n", refusal("ALTER MATERIALIZED VIEW mv_cl SUSPEND RECLUSTER"));
        assertEquals("Table 'MV_CL' is not clustered\n", refusal("ALTER MATERIALIZED VIEW mv_cl RESUME RECLUSTER"));
        engine.execute("ALTER MATERIALIZED VIEW mv_cl CLUSTER BY (MOD(x, 2))");
        assertEquals("LINEAR(MOD(x, 2)) | ON" + text, shown("MV_CL"));
    }

    @Test
    public void copyGrantsNeedsAnObjectToCopyFrom() {
        final String refused = "SQL compilation error:\nInvalid operation COPY GRANTS without specifying source object.";
        assertEquals(refused, refusal("CREATE MATERIALIZED VIEW IF NOT EXISTS mv_ine COPY GRANTS AS SELECT x, y FROM t1"));
        assertEquals(refused, refusal("CREATE MATERIALIZED VIEW mv_cg COPY GRANTS AS SELECT x, y FROM t1"));
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mv_or COPY GRANTS AS SELECT x, y FROM t1");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW mv_or COPY GRANTS CLUSTER BY (x) AS SELECT x, y FROM t1");
        assertTrue(shown("MV_OR").startsWith("LINEAR(x) | ON | "), shown("MV_OR"));
        assertEquals(refused, refusal("CREATE TABLE ug8 COPY GRANTS (a INT)"));
        assertEquals(refused, refusal("CREATE TABLE IF NOT EXISTS ug9 (a INT) COPY GRANTS"));
        assertEquals(refused, refusal("CREATE TABLE ug11 COPY GRANTS AS SELECT 1 AS a"));
        engine.execute("CREATE OR REPLACE TABLE ug10 (a INT) COPY GRANTS");
        engine.execute("CREATE TABLE ug13 LIKE t1 COPY GRANTS");
        engine.execute("CREATE TABLE ug14 CLONE t1 COPY GRANTS");
    }
}
