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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * GET_DDL finds the object it names while the statement compiles: a missing object, or a type it does not know, is
 * refused wherever the call stands — under SYSTEM$TYPEOF, behind a condition that is never true, over no row —
 * and never by a definition that is only kept, a view's or a function's, which hands the refusal to its reader.
 * The call's result is a string of no width (all live-verified).
 */
public class GetDdlCompileTimeLookupTest extends BaseDatabaseTest {

    private static final String MISSING_TABLE =
        "SQL compilation error:\nTable 'NOSUCH_T' does not exist or not authorized.";

    private static final String NOT_CONSTANT = " for function '2', parameter EXPORT_DDL: constant arguments expected";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE gd1 (a INT)");
        engine.execute("CREATE OR REPLACE TABLE gd2 (s VARCHAR)");
        engine.execute("INSERT INTO gd1 VALUES (1)");
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
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void aMissingObjectIsRefusedUnderSystemTypeof() {
        final String head = "SQL compilation error:\n";
        final String[][] refused = {
            {"GET_DDL('database', 'nosuch_db_x')",
                hinted(head + "Database 'NOSUCH_DB_X' does not exist or not authorized.")},
            {"GET_DDL('table', 'nosuch_t')", hinted(MISSING_TABLE)},
            {"GET_DDL('schema', 'nosuch_s')", hinted(head + "Schema 'TEST_DB.NOSUCH_S' does not exist or not authorized.")},
            {"GET_DDL('view', 'nosuch_v')", hinted(head + "View 'NOSUCH_V' does not exist or not authorized.")},
            {"GET_DDL('sequence', 'nosuch_sq')",
                hinted(head + "Sequence 'NOSUCH_SQ' does not exist or not authorized.")},
            {"GET_DDL('stage', 'nosuch_st')",
                hinted(head + "Stage 'TEST_DB.TEST_SCHEMA.NOSUCH_ST' does not exist or not authorized.")},
            {"GET_DDL('policy', 'nosuch_pl')",
                hinted(head + "Policy 'TEST_DB.TEST_SCHEMA.NOSUCH_PL' does not exist or not authorized.")},
            {"GET_DDL('warehouse', 'nosuch_w')",
                hinted(head + "Warehouse 'NOSUCH_W' does not exist or not authorized.")},
            {"GET_DDL('function', 'nosuch_f()')", head + "Object 'nosuch_f()' does not exist or not authorized."},
            {"GET_DDL('procedure', 'nosuch_p()')", head + "Object 'nosuch_p()' does not exist or not authorized."},
            {"GET_DDL('nokind', 'gd1')", head + "Invalid object type: 'NOKIND'"},
            {"GET_DDL('table', 'nosuch_t', TRUE)", hinted(MISSING_TABLE)},
            {"GET_DDL('table', 'nosuch_t', 'abc')", hinted(MISSING_TABLE)},
            {"GET_DDL('table', 'nosuch' || '_t')", hinted(MISSING_TABLE)},
            {"GET_DDL('table', (SELECT 'nosuch_t'))", hinted(MISSING_TABLE)},
            {"LENGTH(GET_DDL('table', 'nosuch_t'))", hinted(MISSING_TABLE)},
        };
        for (final String[] cell : refused) {
            assertEquals(cell[1], refusal("SELECT SYSTEM$TYPEOF(" + cell[0] + ")"), cell[0]);
        }
    }

    @Test
    public void theLookupIsMadeWhereverTheCallStands() {
        final String call = "GET_DDL('table', 'nosuch_t')";
        final String[] statements = {
            "SELECT 1 WHERE FALSE AND " + call + " IS NULL",
            "SELECT IFF(FALSE, " + call + ", 'x')",
            "SELECT " + call + " FROM gd1 WHERE 1 = 0",
            "SELECT CASE WHEN 1 = 0 THEN " + call + " ELSE 'x' END",
            "WITH c AS (SELECT " + call + " AS d) SELECT 1",
            "SELECT 1 FROM (SELECT " + call + " AS d) x",
            "SELECT EXISTS (SELECT " + call + ")",
            "SELECT (SELECT " + call + ")",
            "SELECT a FROM gd1 ORDER BY " + call,
            "SELECT COUNT(*) FROM gd1 GROUP BY " + call,
            "SELECT COUNT(*) FROM gd1 HAVING " + call + " IS NULL",
            "SELECT a FROM gd1 QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1 AND " + call + " IS NULL",
            "SELECT 1 FROM gd1 x JOIN gd1 y ON x.a = y.a AND " + call + " IS NULL WHERE 1 = 0",
            "SELECT * FROM (VALUES (" + call + ")) v WHERE 1 = 0",
            "SELECT 1 WHERE 1 = 0 AND 'a' IN (" + call + ")",
            "SELECT " + call + " LIMIT 0",
            "SELECT 'a' WHERE 1 = 0 UNION ALL SELECT " + call + " WHERE 1 = 0",
            "CREATE OR REPLACE TABLE gdc AS SELECT " + call + " AS d FROM gd1 WHERE 1 = 0",
            "INSERT INTO gd2 SELECT " + call + " FROM gd1 WHERE 1 = 0",
            "INSERT INTO gd2 VALUES (" + call + ")",
            "UPDATE gd2 SET s = " + call + " WHERE 1 = 0",
            "DELETE FROM gd2 WHERE 1 = 0 AND " + call + " IS NULL",
            "MERGE INTO gd2 USING (SELECT 'k' AS k FROM gd1 WHERE 1 = 0) q ON gd2.s = q.k"
                + " WHEN MATCHED THEN UPDATE SET s = " + call,
            "SET gdv1 = " + call,
            "EXPLAIN USING TEXT SELECT " + call,
        };
        final String expected = hinted(MISSING_TABLE);
        for (final String statement : statements) {
            assertEquals(expected, refusal(statement), statement);
        }
    }

    @Test
    public void aKeptDefinitionWaitsForItsReader() {
        engine.execute("CREATE OR REPLACE VIEW gdbad AS SELECT GET_DDL('table', 'nosuch_t') AS d");
        final Row described = engine.executeQuery("DESCRIBE VIEW gdbad").getRows().get(0);
        assertEquals("D", described.getValue(0));
        assertEquals("VARCHAR(16777216)", described.getValue(1));
        assertEquals(hinted(MISSING_TABLE), refusal("SELECT * FROM gdbad"));
        assertEquals(hinted(MISSING_TABLE), refusal("SELECT SYSTEM$TYPEOF(d) FROM gdbad"));
        assertEquals("create or replace view GDBAD(\n\tD\n) as SELECT GET_DDL('table', 'nosuch_t') AS d;",
            first("SELECT GET_DDL('view', 'gdbad')"));

        engine.execute("CREATE OR REPLACE VIEW gdbad2 AS SELECT GET_DDL('table', 'nosuch_t') AS d FROM gd1");
        assertEquals(hinted(MISSING_TABLE), refusal("SELECT x.d FROM gdbad2 x"));

        engine.execute("CREATE OR REPLACE VIEW gdbad3 AS SELECT GET_DDL('nokind', 'gd1') AS d");
        assertEquals("SQL compilation error:\nInvalid object type: 'NOKIND'", refusal("SELECT * FROM gdbad3"));

        engine.execute("CREATE OR REPLACE VIEW gdbad4 AS SELECT GET_DDL('table', a::VARCHAR) AS d FROM gd1");
        assertEquals("SQL compilation error:\nInvalid value [CAST(GD1.A AS VARCHAR(134217728))]" + NOT_CONSTANT,
            refusal("SELECT * FROM gdbad4"));

        engine.execute("CREATE OR REPLACE VIEW gdbad5 AS SELECT a FROM gd1 WHERE GET_DDL('table', 'nosuch_t') IS NULL");
        assertEquals(hinted(MISSING_TABLE), refusal("SELECT * FROM gdbad5"));

        engine.execute("CREATE OR REPLACE FUNCTION gdf() RETURNS VARCHAR AS $$ GET_DDL('table', 'nosuch_t') $$");
        assertEquals(hinted(MISSING_TABLE), refusal("SELECT gdf()"));

        engine.execute("CREATE OR REPLACE TABLE gdd (a INT, s VARCHAR DEFAULT GET_DDL('table', 'nosuch_t'))");
        assertEquals("ok", first("EXECUTE IMMEDIATE $$ BEGIN IF (1 = 0) THEN LET x VARCHAR :="
            + " GET_DDL('table', 'nosuch_t'); END IF; RETURN 'ok'; END $$"));
    }

    @Test
    public void theResultIsAStringOfNoWidth() {
        engine.execute("CREATE OR REPLACE VIEW gdv AS SELECT a FROM gd1");
        final String[][] typed = {
            {"GET_DDL('table', 'gd1')", "VARCHAR[LOB]"},
            {"GET_DDL('view', 'gdv')", "VARCHAR[LOB]"},
            {"GET_DDL('database', 'test_db')", "VARCHAR[LOB]"},
            {"GET_DDL('table', 'gd1', TRUE)", "VARCHAR[LOB]"},
            {"GET_DDL('table', NULL)", "VARCHAR[LOB]"},
            {"GET_DDL(NULL, 'nosuch_t')", "VARCHAR[LOB]"},
            {"GET_DDL('table', NULL::INT)", "VARCHAR[LOB]"},
            {"UPPER(GET_DDL('table', 'gd1'))", "VARCHAR[LOB]"},
            {"GET_DDL('table', 'gd1') || 'x'", "VARCHAR(134217728)[LOB]"},
            {"LEFT(GET_DDL('table', 'gd1'), 3)", "VARCHAR(134217728)[LOB]"},
        };
        for (final String[] cell : typed) {
            assertEquals(cell[1], first("SELECT SYSTEM$TYPEOF(" + cell[0] + ")"), cell[0]);
        }
        assertNull(first("SELECT GET_DDL('table', NULL::INT)"));
        engine.execute("CREATE OR REPLACE TABLE gdc2 AS SELECT GET_DDL('table', 'gd1') AS d");
        assertEquals("VARCHAR(16777216)", engine.executeQuery("DESCRIBE TABLE gdc2").getRows().get(0).getValue(1));
    }

    @Test
    public void aNameWrittenAsAWholeNumberNamesNoObject() {
        final String head = "SQL compilation error:\n";
        final String[][] refused = {
            {"GET_DDL('table', 1)", head + "Object 'table' does not exist or not authorized."},
            {"GET_DDL('TABLE', 1)", head + "Object 'TABLE' does not exist or not authorized."},
            {"GET_DDL('table', 0)", head + "Object 'table' does not exist or not authorized."},
            {"GET_DDL('table', -1)", head + "Object 'table' does not exist or not authorized."},
            {"GET_DDL('table', 1, TRUE)", head + "Object 'table' does not exist or not authorized."},
            {"GET_DDL('database', 1)", head + "Object 'database' does not exist or not authorized."},
            {"GET_DDL('schema', 1)", head + "Object 'schema' does not exist or not authorized."},
            {"GET_DDL('function', 1)", head + "Object 'function' does not exist or not authorized."},
            {"GET_DDL('sequence', 1)", head + "Object 'sequence' does not exist or not authorized."},
            {"GET_DDL('view', 1)", head + "Object 'view' does not exist or not authorized."},
            {"GET_DDL('gd1', 1)", head + "Invalid object type: 'GD1'"},
            {"GET_DDL('ta ble', 1)", head + "Invalid object type: 'TA BLE'"},
            {"GET_DDL('table', 1.5)", head + "Invalid value [CAST(1.5 AS NUMBER(18,0))]" + NOT_CONSTANT},
            {"GET_DDL('table', 1::INT)", head + "Invalid value [CAST(1 AS NUMBER(38,0))]" + NOT_CONSTANT},
            {"GET_DDL('table', TRUE)", head + "Invalid value [CAST(TRUE AS VARCHAR(134217728))]" + NOT_CONSTANT},
            {"GET_DDL('table', '1')", hinted(head + "Table '\"1\"' does not exist or not authorized.")},
        };
        for (final String[] cell : refused) {
            assertEquals(cell[1], refusal("SELECT " + cell[0]), cell[0]);
            assertEquals(cell[1], refusal("SELECT SYSTEM$TYPEOF(" + cell[0] + ")"), cell[0]);
        }
    }
}
