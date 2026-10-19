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

import org.junit.jupiter.api.Test;

/**
 * A stored body reads the objects it names, never a CTE of the statement reading it (live-verified): a view's,
 * a secure view's, a nested view's and a materialized view's body read their tables when the reading query
 * defines a CTE of the same name, a row access and a masking policy's body read their mapping tables, and a SQL
 * function's or table function's body reads its table, whether the caller's query names the CTE or not.
 */
public class StoredBodyCteScopeTest extends MeasuredCellsTestSupport {

    @Test
    public void viewsReadTheirOwnObjects() {
        engine.execute("CREATE OR REPLACE TABLE ct (a INT)");
        engine.execute("INSERT INTO ct VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE VIEW cv1 AS SELECT COUNT(*) AS n FROM ct");
        engine.execute("CREATE OR REPLACE SECURE VIEW csv1 AS SELECT a FROM ct WHERE a IN (SELECT a FROM ct WHERE a "
            + "< 3)");
        engine.execute("CREATE OR REPLACE VIEW cv2 AS SELECT n FROM cv1");
        engine.execute("CREATE OR REPLACE MATERIALIZED VIEW cmv AS SELECT a FROM ct");
        assertCells(new String[][] {
            {"WITH ct AS (SELECT 42 AS a) SELECT * FROM cv1", "3"},
            {"WITH cv1 AS (SELECT 7 AS n) SELECT * FROM cv2", "3"},
            {"WITH ct AS (SELECT 42 AS a) SELECT * FROM csv1 ORDER BY 1", "1 ;; 2"},
            {"WITH ct AS (SELECT 42 AS a) SELECT * FROM cmv ORDER BY 1", "1 ;; 2 ;; 3"},
            {"WITH ct AS (SELECT 42 AS a) SELECT (SELECT n FROM cv1), (SELECT COUNT(*) FROM ct)", "3|1"},
            {"SELECT * FROM (WITH ct AS (SELECT 42 AS a) SELECT * FROM cv1)", "3"},
        });
    }

    @Test
    public void policiesReadTheirOwnMappingTables() {
        engine.execute("CREATE OR REPLACE TABLE ent (region VARCHAR)");
        engine.execute("INSERT INTO ent VALUES ('EU')");
        engine.execute("CREATE OR REPLACE TABLE unmask (k INT)");
        engine.execute("CREATE OR REPLACE TABLE sales (id INT, region VARCHAR, ssn VARCHAR)");
        engine.execute("INSERT INTO sales VALUES (1, 'EU', '111'), (2, 'US', '222')");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY rap AS (r VARCHAR) RETURNS BOOLEAN -> r IN (SELECT "
            + "region FROM ent)");
        engine.execute("ALTER TABLE sales ADD ROW ACCESS POLICY rap ON (region)");
        engine.execute("CREATE OR REPLACE MASKING POLICY mp AS (v VARCHAR) RETURNS VARCHAR -> CASE WHEN EXISTS "
            + "(SELECT 1 FROM unmask) THEN v ELSE '***' END");
        engine.execute("ALTER TABLE sales MODIFY COLUMN ssn SET MASKING POLICY mp");
        assertCells(new String[][] {
            {"SELECT * FROM sales ORDER BY 1", "1|EU|***"},
            {"WITH ent AS (SELECT column1 AS region FROM VALUES ('EU'), ('US')), unmask AS (SELECT 1 AS k) SELECT * "
                + "FROM sales ORDER BY 1",
                "1|EU|***"},
            {"WITH ent AS (SELECT 'US' AS region) SELECT id FROM sales ORDER BY 1", "1"},
        });
    }

    @Test
    public void sqlFunctionsReadTheirOwnTables() {
        engine.execute("CREATE OR REPLACE TABLE ut (a INT)");
        engine.execute("INSERT INTO ut VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE FUNCTION f3() RETURNS INT AS 'SELECT MAX(a) FROM ut'");
        engine.execute("CREATE OR REPLACE FUNCTION ft() RETURNS TABLE (a INT) AS 'SELECT a FROM ut'");
        assertCells(new String[][] {
            {"SELECT f3()", "3"},
            {"WITH ut AS (SELECT 42 AS a) SELECT f3()", "3"},
            {"WITH ut AS (SELECT 42 AS a) SELECT f3(), a FROM ut", "3|42"},
            {"SELECT * FROM (WITH ut AS (SELECT 42 AS a) SELECT f3() AS x)", "3"},
            {"WITH ut AS (SELECT 42 AS a) SELECT * FROM TABLE(ft()) ORDER BY 1", "1 ;; 2 ;; 3"},
        });
    }
}
