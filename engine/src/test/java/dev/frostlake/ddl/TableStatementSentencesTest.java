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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The sentences table statements answer with: a CREATE OR ALTER that alters, a cloned dynamic table, a create
 * over a name that is taken, a recluster of a table that is not there, and a clustering key written LINEAR.
 */
public class TableStatementSentencesTest extends BaseDatabaseTest {

    private static final String PREFIX = "SQL compilation error:\n";

    private String options;

    @BeforeEach
    public void aBaseTable() {
        final Object warehouse = engine.executeQuery("SELECT CURRENT_WAREHOUSE()").getRows().get(0).getValue(0);
        options = " TARGET_LAG = '1 hour' WAREHOUSE = " + (warehouse == null ? "COMPUTE_WH" : warehouse) + " ";
        engine.execute("CREATE TABLE base (i INT, n VARCHAR)");
        engine.execute("INSERT INTO base VALUES (1, 'a'), (2, 'b')");
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

    private Object cell(final String listing, final String column) {
        final ResultSet rs = engine.executeQuery(listing);
        return rs.getRows().get(0).getValue(rs.getColumnIndex(column));
    }

    @Test
    public void aCreateOrAlterThatAltersAnswersAsAnAlterDoes() {
        assertEquals("Table T5 successfully created.", status("CREATE OR ALTER TABLE t5 (a INT)"));
        assertEquals("Statement executed successfully.", status("CREATE OR ALTER TABLE t5 (a INT, b VARCHAR)"));
        assertEquals("View V5 successfully created.", status("CREATE OR ALTER VIEW v5 AS SELECT a FROM t5"));
        assertEquals("Statement executed successfully.", status("CREATE OR ALTER VIEW v5 AS SELECT a, b FROM t5"));
    }

    @Test
    public void aClonedDynamicTableIsAnsweredForAsATable() {
        assertEquals("Dynamic table DT1 successfully created.",
            status("CREATE DYNAMIC TABLE dt1" + options + "AS SELECT i, n FROM base"));
        assertEquals("Table DTC successfully created.", status("CREATE DYNAMIC TABLE dtc CLONE dt1"));
        assertEquals("DTC already exists, statement succeeded.", status("CREATE DYNAMIC TABLE IF NOT EXISTS dtc CLONE dt1"));
    }

    @Test
    public void aNameThatIsTakenIsEchoedAsTheStatementWroteIt() {
        engine.execute("CREATE TABLE ut (a INT)");
        assertEquals(PREFIX + "Object 'UT' already exists.", refusal("CREATE TABLE ut (a INT)"));
        assertEquals(PREFIX + "Object 'UT' already exists.", refusal("CREATE TABLE \"UT\" (a INT)"));
        assertEquals(PREFIX + "Object 'TEST_SCHEMA.UT' already exists.", refusal("CREATE TABLE test_schema.ut (a INT)"));
        assertEquals(PREFIX + "Object 'TEST_DB.TEST_SCHEMA.UT' already exists.",
            refusal("CREATE TABLE test_db.test_schema.ut (a INT)"));
        assertEquals(PREFIX + "Object 'TEST_DB.TEST_SCHEMA.UT' already exists.",
            refusal("CREATE TABLE test_db.test_schema.ut AS SELECT 1 AS a"));
        assertEquals(PREFIX + "Object 'TEST_DB.TEST_SCHEMA.UT' already exists.",
            refusal("CREATE TABLE test_db.test_schema.ut LIKE base"));
        assertEquals(PREFIX + "Object 'TEST_DB.TEST_SCHEMA.UT' already exists.",
            refusal("CREATE TABLE test_db.test_schema.ut CLONE base"));
        // A name another kind holds is told by the name alone.
        assertEquals(PREFIX + "Object 'UT' already exists as TABLE",
            refusal("CREATE VIEW test_db.test_schema.ut AS SELECT 1 AS a"));
        assertEquals("UT already exists, statement succeeded.",
            status("CREATE TABLE IF NOT EXISTS test_db.test_schema.ut (a INT)"));
        engine.execute("CREATE DYNAMIC TABLE dt1" + options + "AS SELECT i, n FROM base");
        assertEquals(PREFIX + "Object 'DT1' already exists.",
            refusal("CREATE DYNAMIC TABLE dt1" + options + "AS SELECT i FROM base"));
        assertEquals(PREFIX + "Object 'TEST_DB.TEST_SCHEMA.DT1' already exists.",
            refusal("CREATE DYNAMIC TABLE test_db.test_schema.dt1" + options + "AS SELECT i FROM base"));
        // A dropped table's name that a new table took cannot be restored over it.
        engine.execute("DROP TABLE ut");
        engine.execute("CREATE TABLE ut (a INT)");
        assertEquals(PREFIX + "Object 'UT' already exists.", refusal("UNDROP TABLE ut"));
    }

    @Test
    public void aReclusterOfATableThatIsNotThereIsRefusedAsATables() {
        assertEquals(hinted(PREFIX + "Table 'TEST_DB.TEST_SCHEMA.DT_NOSUCH' does not exist or not authorized."),
            refusal("ALTER DYNAMIC TABLE dt_nosuch SUSPEND RECLUSTER"));
        assertEquals(hinted(PREFIX + "Table 'TEST_DB.TEST_SCHEMA.DT_NOSUCH' does not exist or not authorized."),
            refusal("ALTER DYNAMIC TABLE test_db.test_schema.dt_nosuch RESUME RECLUSTER"));
        assertEquals(hinted(PREFIX + "Dynamic table 'TEST_DB.TEST_SCHEMA.DT_NOSUCH' does not exist or not authorized."),
            refusal("ALTER DYNAMIC TABLE dt_nosuch SUSPEND"));
        engine.execute("CREATE DYNAMIC TABLE dt1" + options + "AS SELECT i, n FROM base");
        assertEquals("Table 'DT1' is not clustered\n", refusal("ALTER DYNAMIC TABLE dt1 SUSPEND RECLUSTER"));
    }

    @Test
    public void aClusteringKeyMayBeWrittenLinear() {
        assertEquals("Table CK3 successfully created.", status("CREATE TABLE ck3 (id INT, v VARCHAR) CLUSTER BY"
            + " LINEAR(id, v)"));
        assertEquals("LINEAR(id, v)", cell("SHOW TABLES LIKE 'CK3'", "cluster_by"));
        assertEquals("ON", cell("SHOW TABLES LIKE 'CK3'", "automatic_clustering"));
        engine.execute("CREATE TABLE ck (id INT, v VARCHAR) CLUSTER BY (id)");
        final ResultSet listed = engine.executeQuery("SELECT CLUSTERING_KEY, AUTO_CLUSTERING_ON FROM"
            + " INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'CK'");
        assertEquals("LINEAR(id)", listed.getRows().get(0).getValue(0));
        assertEquals("YES", listed.getRows().get(0).getValue(1));
        engine.execute("ALTER TABLE ck CLUSTER BY LINEAR(v)");
        assertEquals("LINEAR(v)", cell("SHOW TABLES LIKE 'CK'", "cluster_by"));
    }
}
