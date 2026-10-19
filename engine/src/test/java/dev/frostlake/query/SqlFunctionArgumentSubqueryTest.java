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
 * A correlated scalar subquery the account cannot evaluate, written among the arguments of a SQL function: the
 * refusal names the innermost such function, as it is stored, and gives no place - under a built-in call, a
 * CASE, a cast or an operator too, in every clause, in DML and in a table function's arguments - where the
 * same subquery under a built-in alone is placed. Of several refused subqueries the one written last in the
 * select list is reported, and with none there the last of the first other clause. Every answer is
 * live-verified.
 */
public class SqlFunctionArgumentSubqueryTest extends MeasuredCellsTestSupport {

    @Test
    public void theFunctionIsNamed() {
        engine.execute(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS 'x + 1'");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f2(x INT, y INT) RETURNS INT AS 'x + y'");
        engine.execute(
            "CREATE OR REPLACE FUNCTION \"f3\"(x INT) RETURNS INT AS 'x + 1'");
        engine.execute(
            "CREATE OR REPLACE FUNCTION fs(x VARCHAR) RETURNS VARCHAR AS 'x || ''!'''");
        engine.execute(
            "CREATE OR REPLACE FUNCTION ft(x INT) RETURNS TABLE (y INT) AS 'SELECT x + 1'");
        engine.execute(
            "CREATE OR REPLACE FUNCTION fq(x INT) RETURNS INT AS 'SELECT MAX(v) + x FROM g'");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f0() RETURNS INT AS '1'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 11",
            "SELECT ABS((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT ABS(f1((SELECT MAX(fz.id + g.v) FROM g))) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1(ABS((SELECT MAX(fz.id + g.v) FROM g))) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1(f1((SELECT MAX(fz.id + g.v) FROM g))) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F2",
            "SELECT f2(1, (SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F2",
            "SELECT f2((SELECT MAX(fz.id + g.v) FROM g), 1) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F2",
            "SELECT f2((SELECT MAX(fz.id + g.v) FROM g), (SELECT MIN(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: f3",
            "SELECT \"f3\"((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: FS",
            "SELECT fs((SELECT MAX(fz.id + g.v) FROM g)::VARCHAR) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: FQ",
            "SELECT fq((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT test_schema.f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT test_db.test_schema.f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT F1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1(-(SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g) + 1) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1(CASE WHEN b THEN (SELECT MAX(fz.id + g.v) FROM g) END) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1(x => (SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g)) + 1 FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT id, f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT id FROM fz WHERE f1((SELECT MAX(fz.id + g.v) FROM g)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT id FROM fz ORDER BY f1((SELECT MAX(fz.id + g.v) FROM g))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT id FROM fz GROUP BY id HAVING f1((SELECT MAX(fz.id + g.v) FROM g)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | found QUALIFY clause but no window "
                + "function.",
            "SELECT id FROM fz QUALIFY f1((SELECT MAX(fz.id + g.v) FROM g)) > 0");
        assertAnswer(
            "51 ;; null",
            "SELECT f1((SELECT MAX(g.v) FROM g WHERE g.id = fz.id)) FROM fz ORDER BY 1");
        assertAnswer(
            "61 ;; 61",
            "SELECT f1((SELECT MAX(g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT g.v FROM g WHERE g.id = fz.id)) FROM fz ORDER BY 1");
        assertAnswer(
            "1 ;; 2",
            "SELECT f1((SELECT COUNT(*) FROM g WHERE g.id > fz.id)) FROM fz ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT v FROM g WHERE g.id > fz.id LIMIT 1)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 11",
            "SELECT ABS((SELECT v FROM g WHERE g.id > fz.id LIMIT 1)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 45 | invalid identifier 'NOSUCH'",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g)), nosuch FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'NOSUCH'",
            "SELECT nosuch, f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: FT",
            "SELECT * FROM fz, TABLE(ft((SELECT MAX(fz.id + g.v) FROM g)))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NOSUCHFN.",
            "SELECT nosuchfn((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g))::VARCHAR FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT COALESCE(f1((SELECT MAX(fz.id + g.v) FROM g)), 0) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT SUM(f1((SELECT MAX(fz.id + g.v) FROM g))) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1(SUM((SELECT MAX(fz.id + g.v) FROM g))) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT (SELECT f1((SELECT MAX(fz.id + g.v) FROM g))) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g)) AS c FROM fz WHERE id = 5");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT MAX(fz.id) FROM g)) FROM fz");
        assertAnswer(
            "66 ;; 68",
            "SELECT f1((SELECT fz.id + MAX(g.v) FROM g)) FROM fz ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT ABS((SELECT MAX(fz.id + g.v) FROM g)) + f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 50",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g)) + ABS((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 18",
            "SELECT f1(1), ABS((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 49",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g)), ABS((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT ABS((SELECT MAX(fz.id + g.v) FROM g)), f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1(\n  (SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT\n  f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT MAX(z.id + g.v) FROM g)) FROM fz AS z");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1(IFF(b, (SELECT MAX(fz.id + g.v) FROM g), 0)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1(COALESCE((SELECT MAX(fz.id + g.v) FROM g), 0)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g)::INT) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1(CAST((SELECT MAX(fz.id + g.v) FROM g) AS INT)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1(((SELECT MAX(fz.id + g.v) FROM g))) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 15",
            "SELECT f0() + (SELECT MAX(fz.id + g.v) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 17",
            "SELECT f1(id) + (SELECT MAX(fz.id + g.v) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 16",
            "SELECT f1(id), (SELECT MAX(fz.id + g.v) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'F1': (BOOLEAN)",
            "SELECT f1(id IN (SELECT fz.id + g.v FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT g.v FROM g WHERE g.id = fz.id) + (SELECT MAX(g.v) FROM g)) FROM fz ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F2",
            "SELECT ABS(f2(1, ABS((SELECT MAX(fz.id + g.v) FROM g)))) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f2(f1(1), f1((SELECT MAX(fz.id + g.v) FROM g))) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f2(f1((SELECT MAX(fz.id + g.v) FROM g)), f0()) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT * FROM (SELECT f1((SELECT MAX(fz.id + g.v) FROM g)) AS c FROM fz)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "WITH c AS (SELECT f1((SELECT MAX(fz.id + g.v) FROM g)) AS c FROM fz) SELECT * FROM c");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz UNION ALL SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT 1 UNION ALL SELECT f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
    }

    @Test
    public void inDataChangingStatements() {
        engine.execute(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS 'x + 1'");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f2(x INT, y INT) RETURNS INT AS 'x + y'");
        engine.execute(
            "CREATE OR REPLACE FUNCTION \"f3\"(x INT) RETURNS INT AS 'x + 1'");
        engine.execute(
            "CREATE OR REPLACE FUNCTION fs(x VARCHAR) RETURNS VARCHAR AS 'x || ''!'''");
        engine.execute(
            "CREATE OR REPLACE FUNCTION ft(x INT) RETURNS TABLE (y INT) AS 'SELECT x + 1'");
        engine.execute(
            "CREATE OR REPLACE FUNCTION fq(x INT) RETURNS INT AS 'SELECT MAX(v) + x FROM g'");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f0() RETURNS INT AS '1'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "UPDATE fz SET id = f1((SELECT MAX(fz.id + g.v) FROM g))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "DELETE FROM fz WHERE f1((SELECT MAX(fz.id + g.v) FROM g)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "INSERT INTO g SELECT id, f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "View V1 successfully created.",
            "CREATE OR REPLACE VIEW v1 AS SELECT f1((SELECT MAX(fz.id + g.v) FROM g)) AS c FROM fz");
    }

    @Test
    public void ofSeveralTheLastIsReported() {
        engine.execute(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS 'x + 1'");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f2(x INT, y INT) RETURNS INT AS 'x + y'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 50",
            "SELECT ABS((SELECT MAX(fz.id + g.v) FROM g)), ABS((SELECT MIN(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 51",
            "SELECT ABS((SELECT MAX(fz.id + g.v) FROM g)) + ABS((SELECT MIN(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 89",
            "SELECT ABS((SELECT MAX(fz.id + g.v) FROM g)), ABS((SELECT MIN(fz.id + g.v) FROM g)), ABS((SELECT "
                + "MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F2",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g)), f2(1, (SELECT MIN(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f2(1, (SELECT MIN(fz.id + g.v) FROM g)), f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 11",
            "SELECT ABS((SELECT MAX(fz.id + g.v) FROM g)) FROM fz WHERE ABS((SELECT MIN(fz.id + g.v) FROM g)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz WHERE ABS((SELECT MIN(fz.id + g.v) FROM g)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 11",
            "SELECT ABS((SELECT MAX(fz.id + g.v) FROM g)) FROM fz WHERE f1((SELECT MIN(fz.id + g.v) FROM g)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F2",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g)), ABS((SELECT MIN(fz.id + g.v) FROM g)), f2(1, (SELECT "
                + "MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 88",
            "SELECT ABS((SELECT MAX(fz.id + g.v) FROM g)), f1((SELECT MIN(fz.id + g.v) FROM g)), ABS((SELECT "
                + "MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 74",
            "SELECT id FROM fz WHERE ABS((SELECT MAX(fz.id + g.v) FROM g)) > 0 AND ABS((SELECT MIN(fz.id + g.v) "
                + "FROM g)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 73",
            "SELECT id FROM fz WHERE f1((SELECT MAX(fz.id + g.v) FROM g)) > 0 AND ABS((SELECT MIN(fz.id + g.v) "
                + "FROM g)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT id FROM fz WHERE ABS((SELECT MAX(fz.id + g.v) FROM g)) > 0 AND f1((SELECT MIN(fz.id + g.v) "
                + "FROM g)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 42",
            "SELECT (SELECT MAX(fz.id + g.v) FROM g), (SELECT MIN(fz.id + g.v) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 43",
            "SELECT (SELECT MAX(fz.id + g.v) FROM g) + (SELECT MIN(fz.id + g.v) FROM g) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 11",
            "SELECT ABS((SELECT MAX(fz.id + g.v) FROM g)) FROM fz ORDER BY ABS((SELECT MIN(fz.id + g.v) FROM g))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz ORDER BY ABS((SELECT MIN(fz.id + g.v) FROM g))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 11",
            "SELECT ABS((SELECT MAX(fz.id + g.v) FROM g)) FROM fz ORDER BY f1((SELECT MIN(fz.id + g.v) FROM g))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT id FROM fz GROUP BY id HAVING ABS((SELECT MAX(fz.id + g.v) FROM g)) > 0 AND f1((SELECT "
                + "MIN(fz.id + g.v) FROM g)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 86",
            "SELECT id FROM fz GROUP BY id HAVING f1((SELECT MAX(fz.id + g.v) FROM g)) > 0 AND ABS((SELECT "
                + "MIN(fz.id + g.v) FROM g)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 57",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1), ABS((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT (SELECT v FROM g WHERE g.id > fz.id LIMIT 1), f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 50",
            "SELECT ABS((SELECT MAX(fz.id + g.v) FROM g)), ABS((SELECT MIN(fz.id + g.v) FROM g)) FROM fz WHERE "
                + "ABS((SELECT MAX(fz.id + g.v) FROM g)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz WHERE f2(1, (SELECT MIN(fz.id + g.v) FROM g)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 51",
            "SELECT GREATEST((SELECT MAX(fz.id + g.v) FROM g), (SELECT MIN(fz.id + g.v) FROM g)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 58",
            "SELECT GREATEST(f1((SELECT MAX(fz.id + g.v) FROM g)), ABS((SELECT MIN(fz.id + g.v) FROM g))) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT GREATEST(ABS((SELECT MAX(fz.id + g.v) FROM g)), f1((SELECT MIN(fz.id + g.v) FROM g))) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F2",
            "SELECT f2((SELECT MAX(fz.id + g.v) FROM g), ABS((SELECT MIN(fz.id + g.v) FROM g))) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 95",
            "SELECT ABS((SELECT MAX(fz.id + g.v) FROM g)) AS a, f1((SELECT MIN(fz.id + g.v) FROM g)) AS b, "
                + "(SELECT MAX(fz.id + g.v) FROM g) AS c FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT CASE WHEN id > 0 THEN f1((SELECT MAX(fz.id + g.v) FROM g)) ELSE ABS((SELECT MIN(fz.id + g.v) "
                + "FROM g)) END FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 33",
            "SELECT CASE WHEN id > 0 THEN ABS((SELECT MAX(fz.id + g.v) FROM g)) ELSE f1((SELECT MIN(fz.id + g.v) "
                + "FROM g)) END FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: F1",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY ABS((SELECT "
                + "MIN(fz.id + g.v) FROM g))) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 11",
            "SELECT ABS((SELECT MAX(fz.id + g.v) FROM g)) FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY f1((SELECT "
                + "MIN(fz.id + g.v) FROM g))) = 1");
        assertAnswer(
            "6",
            "SELECT f1(id) FROM fz WHERE id IN (SELECT g.id FROM g WHERE f1((SELECT MAX(g.id + g.v) FROM g)) > 0)");
    }
}
