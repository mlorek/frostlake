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
 * A correlated subquery the account cannot evaluate is refused under EXPLAIN as the statement itself is refused, at its
 * place in the EXPLAIN (live-verified): under each EXPLAIN form, after a line break, inside a CTE, a derived table, a
 * CREATE TABLE AS and an INSERT, and for a membership as for a scalar subquery. The query is judged by the rows it
 * reads, so it is planned where a run reads no row the subquery is asked for or its outer relation holds one stored row
 * at most. A view whose body holds such a subquery is refused under the view's name, the outer view's where one view
 * reads another, whether the statement reads it or explains reading it.
 */
public class ExplainUnsupportedSubqueryTest extends MeasuredCellsTestSupport {

    /** A scalar subquery correlated to a table of two rows, under each EXPLAIN form and nesting. */
    @Test
    public void aQueryTheAccountRefusesIsRefusedUnderEveryExplain() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE u48 (id INT, v INT)",
                "Table U48 successfully created."},
            {"INSERT INTO u48 VALUES (1, 10), (2, 20)",
                "2"},
            {"CREATE OR REPLACE TABLE w48 (id INT, v INT)",
                "Table W48 successfully created."},
            {"INSERT INTO w48 VALUES (1, 10)",
                "1"},
            {"CREATE OR REPLACE TABLE e48 (id INT, v INT)",
                "Table E48 successfully created."},
            {"CREATE OR REPLACE TABLE t48 (id INT, y INT)",
                "Table T48 successfully created."},
            {"SELECT u48.id, (SELECT v FROM u48 x WHERE x.id = u48.id) y FROM u48",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "16"},
            {"EXPLAIN SELECT u48.id, (SELECT v FROM u48 x WHERE x.id = u48.id) y FROM u48",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "24"},
            {"EXPLAIN USING TEXT SELECT u48.id, (SELECT v FROM u48 x WHERE x.id = u48.id) y FROM u48",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "35"},
            {"EXPLAIN USING JSON SELECT u48.id, (SELECT v FROM u48 x WHERE x.id = u48.id) y FROM u48",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "35"},
            {"EXPLAIN\nSELECT u48.id, (SELECT v FROM u48 x WHERE x.id = u48.id) y FROM u48",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 2, position "
                + "16"},
            {"EXPLAIN WITH c AS (SELECT u48.id, (SELECT v FROM u48 x WHERE x.id = u48.id) y FROM u48) SELECT * FROM c",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "35"},
            {"EXPLAIN SELECT * FROM (SELECT u48.id, (SELECT v FROM u48 x WHERE x.id = u48.id) y FROM u48) q",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "39"},
            {"EXPLAIN CREATE TABLE t49 AS SELECT u48.id, (SELECT v FROM u48 x WHERE x.id = u48.id) y FROM u48",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "44"},
            {"EXPLAIN INSERT INTO t48 SELECT u48.id, (SELECT v FROM u48 x WHERE x.id = u48.id) y FROM u48",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "40"},
            {"CREATE TABLE t49 AS SELECT u48.id, (SELECT v FROM u48 x WHERE x.id = u48.id) y FROM u48",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "36"},
            {"INSERT INTO t48 SELECT u48.id, (SELECT v FROM u48 x WHERE x.id = u48.id) y FROM u48",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "32"},
            {"WITH c AS (SELECT u48.id, (SELECT v FROM u48 x WHERE x.id = u48.id) y FROM u48) SELECT * FROM c",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "27"},
            {"CREATE OR REPLACE VIEW v48 AS SELECT u48.id, (SELECT v FROM u48 x WHERE x.id = u48.id) y FROM u48",
                "View V48 successfully created."},
            {"SELECT * FROM v48",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW object: "
                + "V48"},
            {"EXPLAIN SELECT * FROM v48",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW object: "
                + "V48"},
            {"EXECUTE IMMEDIATE $$BEGIN LET r RESULTSET := (SELECT u48.id, (SELECT v FROM u48 x WHERE x.id = u48.id) y "
                + "FROM u48); RETURN 1; END$$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : SQL compilation "
                + "error: | Unsupported subquery type cannot be evaluated at line 1, position 16"},
            {"EXECUTE IMMEDIATE $$BEGIN\n  LET r RESULTSET := (SELECT u48.id, (SELECT v FROM u48 x WHERE x.id = "
                + "u48.id) y FROM u48);\n  RETURN 1;\nEND$$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 22 : SQL compilation "
                + "error: | Unsupported subquery type cannot be evaluated at line 1, position 16"},
        });
        engine.execute("EXPLAIN SELECT w48.id, (SELECT v FROM w48 x WHERE x.id = w48.id) y FROM w48");
        engine.execute("EXPLAIN SELECT e48.id, (SELECT v FROM e48 x WHERE x.id = e48.id) y FROM e48");
        assertCells(new String[][] {
            {"SELECT u48.id, (SELECT v FROM u48 x WHERE x.id = u48.id) y FROM u48 WHERE FALSE",
                ""},
        });
        engine.execute("EXPLAIN SELECT u48.id, (SELECT v FROM u48 x WHERE x.id = u48.id) y FROM u48 WHERE FALSE");
        assertCells(new String[][] {
            {"SELECT u48.id, (SELECT v FROM u48 x WHERE x.id = u48.id) y FROM u48 LIMIT 0",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "16"},
            {"EXPLAIN SELECT u48.id, (SELECT v FROM u48 x WHERE x.id = u48.id) y FROM u48 LIMIT 0",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "24"},
        });
    }

    /** The subquery over a derived relation: refused where a row reaches it, planned where none does. */
    @Test
    public void aDerivedOuterIsJudgedByTheRowsItReads() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE u49 (id INT, v INT)",
                "Table U49 successfully created."},
            {"INSERT INTO u49 VALUES (1, 10), (2, 20)",
                "2"},
            {"SELECT d.id, (SELECT v FROM u49 WHERE u49.id = d.id) x FROM (SELECT id + 0 AS id FROM u49) d WHERE "
                + "MOD(d.id, 5) = 3",
                ""},
        });
        engine.execute("EXPLAIN SELECT d.id, (SELECT v FROM u49 WHERE u49.id = d.id) x FROM (SELECT id + 0 AS id FROM "
            + "u49) d WHERE MOD(d.id, 5) = 3");
        assertCells(new String[][] {
            {"SELECT d.id, (SELECT v FROM u49 WHERE u49.id = d.id) x FROM (SELECT id + 0 AS id FROM u49) d WHERE d.id "
                + "> 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "14"},
            {"EXPLAIN SELECT d.id, (SELECT v FROM u49 WHERE u49.id = d.id) x FROM (SELECT id + 0 AS id FROM u49) d "
                + "WHERE d.id > 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "22"},
            {"SELECT d.id, (SELECT v FROM u49 WHERE u49.id = d.id) x FROM (SELECT id + 0 AS id FROM u49) d WHERE d.id "
                + "> 5",
                ""},
            {"SELECT d.id, (SELECT v FROM u49 WHERE u49.id = d.id) x FROM (SELECT id + 0 AS id FROM u49) d WHERE d.id "
                + "= 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "14"},
            {"EXPLAIN SELECT d.id, (SELECT v FROM u49 WHERE u49.id = d.id) x FROM (SELECT id + 0 AS id FROM u49) d "
                + "WHERE d.id = 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "22"},
            {"CREATE OR REPLACE SEQUENCE s49",
                "Sequence S49 successfully created."},
        });
    }

    /** An IN, an EXISTS and an ANY whose subquery the account cannot evaluate. */
    @Test
    public void aMembershipTheAccountRefusesIsRefusedUnderExplain() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE fz50 (id INT, b BOOLEAN)",
                "Table FZ50 successfully created."},
            {"INSERT INTO fz50 VALUES (5, TRUE), (7, FALSE)",
                "2"},
            {"CREATE OR REPLACE TABLE g50 (id INT, v INT)",
                "Table G50 successfully created."},
            {"INSERT INTO g50 VALUES (5, 50), (6, 60)",
                "2"},
            {"CREATE OR REPLACE TABLE h50 (k INT, w INT)",
                "Table H50 successfully created."},
            {"INSERT INTO h50 VALUES (5, 500), (7, 700)",
                "2"},
            {"SELECT id FROM fz50 WHERE id IN (SELECT MAX(v) + fz50.id FROM g50 WHERE FALSE)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "33"},
            {"EXPLAIN SELECT id FROM fz50 WHERE id IN (SELECT MAX(v) + fz50.id FROM g50 WHERE FALSE)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "41"},
            {"SELECT id, EXISTS (SELECT 1 FROM g50 LEFT JOIN h50 ON h50.k = g50.id AND h50.k = fz50.id) FROM fz50 "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "11"},
            {"EXPLAIN SELECT id, EXISTS (SELECT 1 FROM g50 LEFT JOIN h50 ON h50.k = g50.id AND h50.k = fz50.id) FROM "
                + "fz50 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "19"},
            {"SELECT id FROM fz50 WHERE EXISTS (SELECT 1 FROM g50 LEFT JOIN h50 ON h50.k = g50.id AND h50.k = fz50.id)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "26"},
            {"EXPLAIN SELECT id FROM fz50 WHERE EXISTS (SELECT 1 FROM g50 LEFT JOIN h50 ON h50.k = g50.id AND h50.k = "
                + "fz50.id)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "34"},
            {"SELECT id FROM fz50 WHERE id = ANY (SELECT MAX(v) + fz50.id FROM g50 WHERE FALSE)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "31"},
            {"EXPLAIN SELECT id FROM fz50 WHERE id = ANY (SELECT MAX(v) + fz50.id FROM g50 WHERE FALSE)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "39"},
        });
    }

    /** Views over the refused subquery, read and explained, and INSERTs that write from them. */
    @Test
    public void aViewHoldingSuchASubqueryIsRefusedUnderItsName() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE u51 (id INT, v INT)",
                "Table U51 successfully created."},
            {"INSERT INTO u51 VALUES (1, 10), (2, 20)",
                "2"},
            {"CREATE OR REPLACE TABLE w51 (id INT, v INT)",
                "Table W51 successfully created."},
            {"INSERT INTO w51 VALUES (1, 10)",
                "1"},
            {"CREATE OR REPLACE VIEW v51 AS SELECT u51.id, (SELECT v FROM u51 x WHERE x.id = u51.id) y FROM u51",
                "View V51 successfully created."},
            {"SELECT * FROM v51",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW object: "
                + "V51"},
            {"EXPLAIN SELECT * FROM v51",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW object: "
                + "V51"},
            {"CREATE OR REPLACE VIEW v52 AS SELECT * FROM v51",
                "View V52 successfully created."},
            {"SELECT * FROM v52",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW object: "
                + "V52"},
            {"EXPLAIN SELECT * FROM v52",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW object: "
                + "V52"},
            {"CREATE OR REPLACE VIEW v53 AS SELECT d.id, (SELECT v FROM u51 WHERE u51.id = d.id) x FROM (SELECT id + 0 "
                + "AS id FROM u51) d",
                "View V53 successfully created."},
            {"SELECT * FROM v53",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW object: "
                + "V53"},
            {"EXPLAIN SELECT * FROM v53",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW object: "
                + "V53"},
            {"CREATE OR REPLACE VIEW v54 AS SELECT id FROM u51 WHERE EXISTS (SELECT 1 FROM w51 LEFT JOIN u51 x ON x.id "
                + "= w51.id AND x.id = u51.id)",
                "View V54 successfully created."},
            {"SELECT * FROM v54",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW object: "
                + "V54"},
            {"EXPLAIN SELECT * FROM v54",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW object: "
                + "V54"},
            {"CREATE OR REPLACE VIEW v55 AS SELECT w51.id, (SELECT v FROM w51 x WHERE x.id = w51.id) y FROM w51",
                "View V55 successfully created."},
            {"SELECT * FROM v55",
                "1|10"},
        });
        engine.execute("EXPLAIN SELECT * FROM v55");
        assertCells(new String[][] {
            {"SELECT v51.id FROM v51 ORDER BY 1",
                "1 ;; 2"},
            {"EXPLAIN SELECT v51.id FROM v51",
                "null|null|null|GlobalStats|null|null|null|1|1|1024 ;; 1|0|null|Result|null|null|U51.ID|null|null|null "
                + ";; 1|1|[0]|TableScan|TEST_DB.TEST_SCHEMA.U51|null|ID|1|1|1024"},
            {"SELECT * FROM (SELECT * FROM v51) q",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW object: "
                + "V51"},
            {"WITH c AS (SELECT * FROM v51) SELECT * FROM c",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW object: "
                + "V51"},
            {"SELECT q.id FROM u51 q WHERE q.id IN (SELECT id FROM v51)",
                "1 ;; 2"},
            {"CREATE OR REPLACE FUNCTION f51(k INT) RETURNS INT AS 'SELECT v FROM u51 x WHERE x.id = k'",
                "Function F51 successfully created."},
            {"SELECT id, f51(id) FROM u51 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F51"},
            {"EXPLAIN SELECT id, f51(id) FROM u51",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F51"},
            {"CREATE OR REPLACE TABLE t51 (id INT, y INT)",
                "Table T51 successfully created."},
            {"EXPLAIN INSERT INTO t51 SELECT u51.id, (SELECT v FROM u51 x WHERE x.id = u51.id) y FROM u51",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "40"},
            {"EXPLAIN INSERT INTO t51 (id, y) SELECT u51.id, (SELECT v FROM u51 x WHERE x.id = u51.id) y FROM u51",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "48"},
            {"EXPLAIN INSERT OVERWRITE INTO t51 SELECT u51.id, (SELECT v FROM u51 x WHERE x.id = u51.id) y FROM u51",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "50"},
            {"EXPLAIN INSERT INTO t51 SELECT d.id, (SELECT v FROM u51 WHERE u51.id = d.id) x FROM (SELECT id + 0 AS id "
                + "FROM u51) d",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "38"},
            {"EXPLAIN INSERT INTO t51 SELECT * FROM v51",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW object: "
                + "V51"},
            {"INSERT INTO t51 SELECT * FROM v51",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW object: "
                + "V51"},
            {"INSERT INTO t51 (id, y) SELECT u51.id, (SELECT v FROM u51 x WHERE x.id = u51.id) FROM u51",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "40"},
            {"EXPLAIN INSERT INTO t51 (id, y) SELECT u51.id, (SELECT v FROM u51 x WHERE x.id = u51.id) FROM u51",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "48"},
            {"EXPLAIN\nINSERT INTO t51\nSELECT u51.id, (SELECT v FROM u51 x WHERE x.id = u51.id) y FROM u51",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 3, position "
                + "16"},
        });
    }

    /** The derived form under WHERE FALSE, LIMIT 0, over one row and over none. */
    @Test
    public void anOuterOfOneRowOrNoneIsPlanned() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE u47 (id INT, v INT)",
                "Table U47 successfully created."},
            {"INSERT INTO u47 VALUES (1, 10), (2, 20)",
                "2"},
            {"CREATE OR REPLACE TABLE w47 (id INT, v INT)",
                "Table W47 successfully created."},
            {"INSERT INTO w47 VALUES (1, 10)",
                "1"},
            {"CREATE OR REPLACE TABLE e47 (id INT, v INT)",
                "Table E47 successfully created."},
        });
        engine.execute("EXPLAIN SELECT d.id, (SELECT v FROM u47 WHERE u47.id = d.id) x FROM (SELECT id + 0 AS id FROM "
            + "u47) d WHERE FALSE");
        assertCells(new String[][] {
            {"SELECT d.id, (SELECT v FROM u47 WHERE u47.id = d.id) x FROM (SELECT id + 0 AS id FROM u47) d WHERE FALSE",
                ""},
        });
        engine.execute("EXPLAIN SELECT d.id, (SELECT v FROM u47 WHERE u47.id = d.id) x FROM (SELECT id + 0 AS id FROM "
            + "u47) d WHERE 1 = 0");
        engine.execute("EXPLAIN SELECT d.id, (SELECT v FROM u47 WHERE u47.id = d.id) x FROM (SELECT id + 0 AS id FROM "
            + "u47) d LIMIT 0");
        assertCells(new String[][] {
            {"SELECT d.id, (SELECT v FROM u47 WHERE u47.id = d.id) x FROM (SELECT id + 0 AS id FROM u47) d LIMIT 0",
                ""},
        });
        engine.execute("EXPLAIN SELECT d.id, (SELECT v FROM w47 WHERE w47.id = d.id) x FROM (SELECT id + 0 AS id FROM "
            + "w47) d");
        assertCells(new String[][] {
            {"SELECT d.id, (SELECT v FROM w47 WHERE w47.id = d.id) x FROM (SELECT id + 0 AS id FROM w47) d",
                "1|10"},
        });
        engine.execute("EXPLAIN SELECT d.id, (SELECT v FROM e47 WHERE e47.id = d.id) x FROM (SELECT id + 0 AS id FROM "
            + "e47) d");
        assertCells(new String[][] {
            {"SELECT d.id, (SELECT v FROM e47 WHERE e47.id = d.id) x FROM (SELECT id + 0 AS id FROM e47) d",
                ""},
            {"EXPLAIN SELECT u47.id, (SELECT v FROM u47 x WHERE x.id = u47.id) y FROM u47",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "24"},
        });
        engine.execute("EXPLAIN SELECT d.id, (SELECT v FROM u47 WHERE u47.id = d.id) x FROM (SELECT id + 0 AS id FROM "
            + "u47) d WHERE d.id > 5");
        assertCells(new String[][] {
            {"EXPLAIN SELECT d.id, (SELECT v FROM w47 WHERE w47.id = d.id) x FROM (SELECT id + 0 AS id FROM u47) d",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "22"},
            {"SELECT d.id, (SELECT v FROM w47 WHERE w47.id = d.id) x FROM (SELECT id + 0 AS id FROM u47) d",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "14"},
        });
    }

    /** The derived form and its EXPLAIN, as text and over a plain derived relation. */
    @Test
    public void theDerivedFormIsRefusedUnderExplain() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE u42 (id INT, v INT)",
                "Table U42 successfully created."},
            {"INSERT INTO u42 VALUES (1, 10), (2, 20)",
                "2"},
            {"SELECT d.id, (SELECT v FROM u42 WHERE u42.id = d.id) x FROM (SELECT id + 0 AS id FROM u42) d",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "14"},
            {"EXPLAIN SELECT d.id, (SELECT v FROM u42 WHERE u42.id = d.id) x FROM (SELECT id + 0 AS id FROM u42) d",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "22"},
            {"EXPLAIN USING TEXT SELECT d.id, (SELECT v FROM u42 WHERE u42.id = d.id) x FROM (SELECT id + 0 AS id FROM "
                + "u42) d",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "33"},
            {"SELECT d.id, (SELECT v FROM u42 WHERE u42.id = d.id) x FROM (SELECT id AS id FROM u42) d ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "14"},
            {"EXPLAIN SELECT d.id, (SELECT v FROM u42 WHERE u42.id = d.id) x FROM (SELECT id AS id FROM u42) d",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "22"},
            {"SELECT d.id, (SELECT MAX(v) FROM u42 WHERE u42.id = d.id) x FROM (SELECT id + 0 AS id FROM u42) d ORDER "
                + "BY 1",
                "1|10 ;; 2|20"},
        });
        engine.execute("EXPLAIN SELECT d.id, (SELECT MAX(v) FROM u42 WHERE u42.id = d.id) x FROM (SELECT id + 0 AS id "
            + "FROM u42) d");
    }

    /** The derived form under EXPLAIN inside a CTE, a WHERE, a CREATE TABLE AS and an IN. */
    @Test
    public void theDerivedFormIsRefusedInsideACteAWhereAndACreate() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE u46 (id INT, v INT)",
                "Table U46 successfully created."},
            {"INSERT INTO u46 VALUES (1, 10), (2, 20)",
                "2"},
            {"EXPLAIN WITH c AS (SELECT d.id, (SELECT v FROM u46 WHERE u46.id = d.id) x FROM (SELECT id + 0 AS id FROM "
                + "u46) d) SELECT * FROM c",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "33"},
            {"EXPLAIN SELECT * FROM (SELECT id + 0 AS id FROM u46) d WHERE (SELECT v FROM u46 WHERE u46.id = d.id) > 0",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "62"},
            {"SELECT * FROM (SELECT id + 0 AS id FROM u46) d WHERE (SELECT v FROM u46 WHERE u46.id = d.id) > 0",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "54"},
            {"EXPLAIN CREATE TABLE t46 AS SELECT d.id, (SELECT v FROM u46 WHERE u46.id = d.id) x FROM (SELECT id + 0 "
                + "AS id FROM u46) d",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "42"},
            {"CREATE TABLE t46 AS SELECT d.id, (SELECT v FROM u46 WHERE u46.id = d.id) x FROM (SELECT id + 0 AS id "
                + "FROM u46) d",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "34"},
            {"EXPLAIN SELECT * FROM u46 WHERE id IN (SELECT d.id FROM (SELECT id + 0 AS id FROM u46) d WHERE (SELECT v "
                + "FROM u46 x WHERE x.id = d.id) > 0)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "96"},
        });
    }
}
