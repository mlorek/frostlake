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

package dev.frostlake.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code TABLE <name>} is a query of its own (live-verified): every column of the table, as {@code SELECT * FROM
 * <name>} answers them, time travel included, wherever a query may stand — a statement, with ORDER BY, LIMIT and
 * FETCH after it, a subquery, an IN's, an EXISTS's and a quantifier's query, a set operation's operand, a derived
 * table, a CTE's, a view's, a CTAS's and an INSERT's body. Nothing else follows the name. A class instance's method
 * is no table function: {@code TABLE(x!y())} is refused naming it.
 */
public class TableQueryTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void createTheTables() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR)", "Table FZ successfully created."},
            {"INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')", "2"},
            {"CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)", "Table G successfully created."},
            {"INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')", "2"},
            {"CREATE OR REPLACE TABLE pq (n INT, a ARRAY)", "Table PQ successfully created."},
            {"INSERT INTO pq SELECT 1, [1]", "1"},
            {"CREATE OR REPLACE TABLE pq1 (n INT)", "Table PQ1 successfully created."},
            {"INSERT INTO pq1 VALUES (1), (2)", "2"},
            {"CREATE OR REPLACE TABLE pq3 (n INT)", "Table PQ3 successfully created."},
            {"INSERT INTO pq3 VALUES (3)", "1"},
            {"CREATE OR REPLACE TABLE pq2 (n INT)", "Table PQ2 successfully created."},
            {"INSERT INTO pq2 VALUES (1), (3)", "2"},
        });
    }

    @Test
    public void aTableQueryIsAStatement() {
        assertCells(new String[][] {
            {"TABLE pq1 ORDER BY n DESC", "2 ;; 1"},
            {"TABLE pq1 LIMIT 1", "1"},
            {"(TABLE pq1) ORDER BY n", "1 ;; 2"},
            {"TABLE IDENTIFIER('pq3')", "3"},
            {"TABLE \"PQ3\"", "3"},
            {"TABLE test_db.test_schema.pq3", "3"},
            {"TABLE pq1 WHERE n = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'WHERE'."},
            {"TABLE pq1, pq3", "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected ','."},
            {"TABLE pq1 t", "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 't'."},
            {"TABLE", "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected '<EOF>'."},
            {"TABLE (pq3)", "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected '('."},
            {"TABLE fz ORDER BY id", "5|true|a ;; 7|false|b"},
            {"TABLE pq1 FETCH FIRST 1 ROWS ONLY", "1"},
            {"TABLE pq1 UNION ALL TABLE pq3 ORDER BY n", "1 ;; 2 ;; 3"},
            {"TABLE nosuch",
                "REFUSED SQL compilation error: | Object 'NOSUCH' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH."},
            {"TABLE pq1 AS t", "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'AS'."},
            {"TABLE pq1 SAMPLE (100)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'SAMPLE'."},
            {"TABLE pq1 AT (OFFSET => 0)", "1 ;; 2"},
            {"TABLE @st", "REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected '@st'."},
            {"TABLE pq1 QUALIFY TRUE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'QUALIFY'."},
            {"TABLE g.s",
                "REFUSED SQL compilation error: | Schema 'TEST_DB.G' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.G."},
            {"TABLE information_schema.tables LIMIT 0", ""},
            {"TABLE pq1 GROUP BY n",
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'GROUP'."},
            {"TABLE pq1", "1 ;; 2"},
            {"TABLE pq1 UNION TABLE pq1", "1 ;; 2"},
            {"TABLE pq1 ORDER BY 1 DESC", "2 ;; 1"},
            {"TABLE pq1 t, pq3", "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 't'."},
            {"TABLE pq1 LIMIT 1 OFFSET 1", "2"},
        });
    }

    @Test
    public void aTableQueryIsASubquery() {
        assertCells(new String[][] {
            {"SELECT 1 = ANY (TABLE pq)",
                "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for function "
                + "'=': (NUMBER(1,0), ROW(NUMBER(38,0), ARRAY))"},
            {"SELECT 1 = ANY (TABLE pq1)", "true"},
            {"SELECT 3 = ANY (TABLE pq1)", "false"},
            {"SELECT 1 = ALL (TABLE pq1)", "false"},
            {"SELECT 1 > ANY (TABLE pq1)", "false"},
            {"SELECT 1 IN (TABLE pq1)", "true"},
            {"SELECT EXISTS (TABLE pq1)", "true"},
            {"SELECT * FROM (TABLE pq1)", "1 ;; 2"},
            {"SELECT (TABLE pq1)", "REFUSED Single-row subquery returns more than one row."},
            {"SELECT n FROM pq1 WHERE n = ANY (TABLE pq1) ORDER BY n", "1 ;; 2"},
            {"SELECT 1 = ANY (TABLE nosuch)",
                "REFUSED SQL compilation error: | Object 'NOSUCH' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH."},
            {"SELECT 1 = ANY ((TABLE pq1))", "true"},
            {"SELECT 1 = ANY (TABLE test_db.pq1)",
                "REFUSED SQL compilation error: | Schema 'TEST_DB.TEST_DB' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.TEST_DB."},
            {"SELECT 'a' LIKE ANY (TABLE pq1)", "false"},
            {"SELECT COUNT(*) FROM (TABLE pq1 UNION ALL TABLE pq1)", "4"},
            {"SELECT COUNT(*) FROM (SELECT 1 UNION ALL TABLE pq1)", "3"},
            {"SELECT COUNT(*) FROM (TABLE pq1 MINUS TABLE pq3)", "2"},
            {"SELECT * FROM TABLE pq3",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'pq3'."},
            {"SELECT n FROM (TABLE pq1) t ORDER BY n", "1 ;; 2"},
            {"SELECT t.n FROM (TABLE pq1) AS t ORDER BY n", "1 ;; 2"},
            {"SELECT id FROM fz WHERE id IN (TABLE pq1)", ""},
            {"SELECT (TABLE pq3)", "3"},
            {"SELECT (TABLE pq3) + 1", "4"},
            {"SELECT id FROM fz WHERE EXISTS (TABLE pq1) ORDER BY id", "5 ;; 7"},
            {"SELECT COUNT(*) FROM (TABLE fz)", "2"},
            {"SELECT id, id IN (TABLE pq1) FROM fz ORDER BY id", "5|false ;; 7|false"},
            {"SELECT 1 IN ((TABLE pq1))", "true"},
            {"SELECT * FROM fz WHERE (id, s) IN (TABLE fz) ORDER BY id",
                "REFUSED SQL compilation error: | Can not convert parameter 'ANY(SELECT FZ.ID AS \"ID\", FZ.B AS "
                + "\"B\", FZ.S AS \"S\" FROM FZ AS FZ)' of type [ROW(NUMBER(38,0), BOOLEAN, VARCHAR(16777216))] into "
                + "expected type [ROW(NUMBER(38,0), VARCHAR(16777216))]"},
            {"SELECT 1 = ANY (TABLE pq1 ORDER BY n)", "true"},
            {"SELECT 1 = ANY (TABLE pq1 UNION ALL TABLE pq3)", "true"},
            {"SELECT 1 FROM fz, LATERAL (TABLE pq3)", "1 ;; 1"},
            {"SELECT * FROM fz JOIN (TABLE pq3) ON TRUE ORDER BY id", "5|true|a|3 ;; 7|false|b|3"},
            {"SELECT 1 FROM fz WHERE id = (TABLE pq3)", ""},
            {"SELECT n FROM pq1 WHERE n IN (TABLE pq1 MINUS SELECT 1)", "2"},
            {"SELECT * FROM fz WHERE s LIKE ANY (TABLE g)",
                "REFUSED SQL compilation error: error line 1 at position 25 | Invalid argument types for function "
                + "'LIKE': (VARCHAR(16777216), ROW(NUMBER(38,0), NUMBER(38,0), VARCHAR(16777216)))"},
            {"SELECT * FROM (TABLE pq1 ORDER BY n DESC LIMIT 1)", "2"},
            {"WITH RECURSIVE r AS (TABLE pq3) TABLE r", "3"},
            {"SELECT 1 FROM pq1 WHERE NOT EXISTS (TABLE pq3)", ""},
        });
    }

    @Test
    public void aTableQueryIsABody() {
        assertCells(new String[][] {
            {"WITH c AS (TABLE pq1) SELECT n FROM c ORDER BY n", "1 ;; 2"},
            {"WITH c AS (SELECT 1 AS n) TABLE c", "1"},
            {"CREATE OR REPLACE TABLE pq2 AS TABLE pq1", "Table PQ2 successfully created."},
            {"SELECT n FROM pq2 ORDER BY n", "1 ;; 2"},
            {"CREATE OR REPLACE VIEW vq AS TABLE pq1", "View VQ successfully created."},
            {"SELECT n FROM vq ORDER BY n", "1 ;; 2"},
            {"INSERT INTO pq2 TABLE pq3", "1"},
            {"SELECT n FROM pq2 ORDER BY n", "1 ;; 2 ;; 3"},
        });
    }

    @Test
    public void aTableQueryFeedsADataChange() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE 'TABLE pq3'", "3"},
            {"DELETE FROM pq2 USING (TABLE pq3) s WHERE pq2.n = s.n", "1"},
            {"SELECT n FROM pq2 ORDER BY n", "1"},
            {"MERGE INTO pq2 USING (TABLE pq3) s ON pq2.n = s.n WHEN NOT MATCHED THEN INSERT VALUES (s.n)", "1"},
            {"UPDATE pq2 SET n = n + 10 FROM (TABLE pq3) s WHERE pq2.n = s.n",
                "REFUSED SQL compilation error: | ambiguous column name 'N'"},
            {"SELECT n FROM pq2 ORDER BY n", "1 ;; 3"},
        });
    }

    @Test
    public void aClassInstancesMethodIsNoTableFunction() {
        assertCells(new String[][] {
            {"SELECT * FROM TABLE(x!y())", "REFUSED SQL compilation error: | Unknown table function X!Y"},
            {"SELECT * FROM TABLE(x!y(1))", "REFUSED SQL compilation error: | Unknown table function X!Y"},
            {"SELECT * FROM TABLE(a.x!y())",
                "REFUSED SQL compilation error: | Unknown user-defined table function A.X!Y"},
            {"SELECT * FROM TABLE(x!y()) t", "REFUSED SQL compilation error: | Unknown table function X!Y"},
            {"SELECT * FROM TABLE(x ! y())", "REFUSED SQL compilation error: | Unknown table function X!Y"},
            {"SELECT x!y()", "REFUSED SQL compilation error: | Unknown function X!Y."},
            {"SELECT * FROM TABLE(\"x\"!y())", "REFUSED SQL compilation error: | Unknown table function \"x\"!Y"},
            {"SELECT * FROM TABLE(x!\"y\"())", "REFUSED SQL compilation error: | Unknown table function X!\"y\""},
            {"SELECT * FROM fz, TABLE(x!y(fz.id))", "REFUSED SQL compilation error: | Unknown table function X!Y"},
            {"SELECT * FROM TABLE(x!y()) WHERE 1 = 1", "REFUSED SQL compilation error: | Unknown table function X!Y"},
        });
    }
}
