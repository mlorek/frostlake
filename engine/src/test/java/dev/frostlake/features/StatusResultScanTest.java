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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RESULT_SCAN reads the status row of a statement that is no query (live-verified): after a DDL, a SET or UNSET,
 * a USE, an ALTER SESSION, a transaction statement, a GRANT or a COMMENT the scan answers the one VARCHAR column
 * {@code status} the statement itself answered.
 */
public class StatusResultScanTest extends BaseDatabaseTest {

    private static final String SCAN = "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))";

    private String scanned(final String statement) {
        engine.execute(statement);
        final ResultSet rs = engine.executeQuery(SCAN);
        assertEquals(1, rs.getColumnCount(), statement);
        assertEquals("status", rs.getColumns().get(0).getName(), statement);
        assertEquals(1, rs.getRowCount(), statement);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void aDdlStatementsStatusRowIsScanned() {
        assertEquals("Table SRS_T successfully created.", scanned("CREATE TABLE srs_t (a INT)"));
        assertEquals("View SRS_V successfully created.", scanned("CREATE VIEW srs_v AS SELECT 1 AS x"));
        assertEquals("Statement executed successfully.", scanned("ALTER TABLE srs_t ADD COLUMN b INT"));
        assertEquals("Statement executed successfully.", scanned("COMMENT ON TABLE srs_t IS 'c'"));
        assertEquals("Statement executed successfully.", scanned("GRANT SELECT ON TABLE srs_t TO ROLE PUBLIC"));
        assertEquals("Statement executed successfully.", scanned("TRUNCATE TABLE srs_t"));
        assertEquals("Drop statement executed successfully (nosuchCase already dropped).",
            scanned("DROP FUNCTION IF EXISTS \"nosuchCase\"()"));
        assertEquals("SRS_V successfully dropped.", scanned("DROP VIEW srs_v"));
        assertEquals("SRS_T successfully dropped.", scanned("DROP TABLE srs_t"));
    }

    @Test
    public void aSessionStatementsStatusRowIsScanned() {
        assertEquals("Statement executed successfully.", scanned("SET srs_var = 5"));
        assertEquals("Statement executed successfully.", scanned("UNSET srs_var"));
        assertEquals("Statement executed successfully.", scanned("USE SCHEMA test_schema"));
        assertEquals("Statement executed successfully.", scanned("ALTER SESSION SET QUERY_TAG = 'srs'"));
        assertEquals("Statement executed successfully.", scanned("ALTER SESSION UNSET QUERY_TAG"));
        assertEquals("Statement executed successfully.", scanned("BEGIN"));
        assertEquals("Statement executed successfully.", scanned("COMMIT"));
    }

    @Test
    public void theScanDeclaresTheStatusColumnAsTheStatementDoes() {
        engine.execute("SET srs_var2 = 5");
        engine.execute("CREATE TABLE srs_copy AS " + SCAN);
        final ResultSet described = engine.executeQuery("DESCRIBE TABLE srs_copy");
        assertEquals("status", String.valueOf(described.getRows().get(0).getValue(0)));
        assertEquals("VARCHAR(16777216)", String.valueOf(described.getRows().get(0).getValue(1)));
        engine.execute("UNSET srs_var2");
    }

    @Test
    public void anEarlierStatementIsScannedByItsOffset() {
        engine.execute("CREATE TABLE srs_o (a INT)");
        engine.execute("DROP TABLE srs_o");
        engine.execute("SELECT 1");
        assertEquals("SRS_O successfully dropped.", String.valueOf(engine.executeQuery(
            "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-2)))").getRows().get(0).getValue(0)));
    }
}
