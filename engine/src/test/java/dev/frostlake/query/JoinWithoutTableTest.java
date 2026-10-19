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
 * A JOIN with no table after it is refused at the token where the table belongs: the end of the input, a
 * semicolon, a clause keyword, a set operator, a closing parenthesis, a number. What follows is read as if
 * the join had not been written, so a later fault of the statement and the faults of the statements after
 * it are reported too, and a closing parenthesis refused there is passed over. A join type written after a
 * JOIN is passed over and the token after it refused. A JOIN outside a FROM list is refused itself. Every
 * answer is live-verified.
 */
public class JoinWithoutTableTest extends MeasuredCellsTestSupport {

    @Test
    public void theTokenWhereTheTableBelongsIsRefused() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ';'. | syntax "
                + "error line 1 at position 33 unexpected 'y'.",
            "SELECT 1 FROM t JOIN; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'WHERE'.",
            "SELECT 1 FROM t JOIN WHERE a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ORDER'.",
            "SELECT 1 FROM t JOIN ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
            "SELECT 1 FROM t LEFT JOIN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'.",
            "SELECT 1 FROM t CROSS JOIN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<EOF>'.",
            "SELECT 1 FROM t NATURAL JOIN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ';'. | syntax "
                + "error line 1 at position 38 unexpected 'y'.",
            "SELECT 1 FROM t LEFT JOIN; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'GROUP'.",
            "SELECT 1 FROM t JOIN GROUP BY a");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'HAVING'.",
            "SELECT 1 FROM t JOIN HAVING a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'QUALIFY'.",
            "SELECT 1 FROM t JOIN QUALIFY 1 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'.",
            "SELECT 1 FROM t JOIN LIMIT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'UNION'.",
            "SELECT 1 FROM t JOIN UNION SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN ON a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ')'.",
            "SELECT 1 FROM t JOIN )");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ')'.",
            "SELECT 1 FROM (SELECT 1 FROM t JOIN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'.",
            "SELECT 1 FROM t INNER JOIN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '1'.",
            "SELECT 1 FROM t JOIN 1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid URL prefix found in: 'q'",
            "SELECT 1 FROM t JOIN 'q'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '<EOF>'.",
            "SELECT 1 FROM t x JOIN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN u JOIN");
    }

    @Test
    public void whatFollowsIsReadWithoutTheJoin() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'WHERE'. | syntax "
                + "error line 1 at position 33 unexpected 'x'.",
            "SELECT 1 FROM t JOIN WHERE a = 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN ON a = 1 x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ';'. | syntax "
                + "error line 1 at position 43 unexpected 'y'.",
            "SELECT 1 FROM t JOIN; SELECT 2; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'WHERE'. | syntax "
                + "error line 1 at position 45 unexpected 'y'.",
            "SELECT 1 FROM t JOIN WHERE a = 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ON'. | syntax "
                + "error line 1 at position 42 unexpected 'y'.",
            "SELECT 1 FROM t JOIN ON a = 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '1'. | syntax "
                + "error line 1 at position 35 unexpected 'y'.",
            "SELECT 1 FROM t JOIN 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ')'. | syntax "
                + "error line 1 at position 35 unexpected 'y'.",
            "SELECT 1 FROM t JOIN ); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'n'.",
            "SELECT 1 FROM t JOIN JOIN n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN (");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ')'.",
            "SELECT 1 FROM t JOIN ()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN n ON t.a = n.a JOIN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ','.",
            "SELECT 1 FROM t JOIN,");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ','.",
            "SELECT 1 FROM t JOIN, n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
            "SELECT 1 FROM t FULL JOIN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'WHERE'.",
            "SELECT 1 FROM t LEFT OUTER JOIN WHERE 1 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'WHERE'.",
            "SELECT 1 FROM t INNER JOIN WHERE a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'WHERE'.",
            "SELECT 1 FROM t LEFT JOIN WHERE a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'WHERE'.",
            "SELECT 1 FROM t CROSS JOIN WHERE a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'ORDER'.",
            "SELECT 1 FROM t NATURAL JOIN ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'ON'.",
            "SELECT 1 FROM t LEFT JOIN ON a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ')'.",
            "SELECT 1 FROM t LEFT JOIN )");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1'.",
            "SELECT 1 FROM t LEFT JOIN 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN LATERAL");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'SELECT'.",
            "SELECT 1 FROM t JOIN SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '<EOF>'.",
            "UPDATE t SET a = 1 FROM n JOIN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'.",
            "DELETE FROM t USING n JOIN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '1'.",
            "SELECT 1 FROM t JOIN FETCH FIRST 1 ROWS ONLY");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'INTERSECT'.",
            "SELECT 1 FROM t JOIN INTERSECT SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'.",
            "SELECT 1 FROM t JOIN EXCEPT SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'MINUS'.",
            "SELECT 1 FROM t JOIN MINUS SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'UNION'. | syntax "
                + "error line 1 at position 42 unexpected 'y'.",
            "SELECT 1 FROM t JOIN UNION ALL SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'GROUP'. | syntax "
                + "error line 1 at position 32 unexpected 'x'.",
            "SELECT 1 FROM t JOIN GROUP BY a x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'ORDER'. | syntax "
                + "error line 1 at position 44 unexpected 'y'.",
            "SELECT 1 FROM t JOIN ORDER BY 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'GROUP'. | syntax "
                + "error line 1 at position 44 unexpected 'y'.",
            "SELECT 1 FROM t JOIN GROUP BY a; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'HAVING'. | "
                + "syntax error line 1 at position 46 unexpected 'y'.",
            "SELECT 1 FROM t JOIN HAVING a = 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'QUALIFY'. | "
                + "syntax error line 1 at position 47 unexpected 'y'.",
            "SELECT 1 FROM t JOIN QUALIFY 1 = 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'UNION'. | syntax "
                + "error line 1 at position 48 unexpected 'y'.",
            "SELECT 1 FROM t JOIN UNION SELECT 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ')'. | syntax "
                + "error line 1 at position 49 unexpected 'y'.",
            "SELECT 1 FROM (SELECT 1 FROM t JOIN); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ')'. | syntax "
                + "error line 1 at position 43 unexpected '<EOF>'.",
            "SELECT 1 FROM (SELECT 1 FROM t JOIN) q JOIN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '='.",
            "SELECT 1 FROM t JOIN = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '+'.",
            "SELECT 1 FROM t JOIN + 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '*'.",
            "SELECT 1 FROM t JOIN *");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'TRUE' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE TRUE.",
            "SELECT 1 FROM t JOIN TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'NULL'.",
            "SELECT 1 FROM t JOIN NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'AS'.",
            "SELECT 1 FROM t JOIN AS x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'SAMPLE'.",
            "SELECT 1 FROM t JOIN SAMPLE (10)");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'PIVOT' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE PIVOT.",
            "SELECT 1 FROM t JOIN PIVOT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
            "SELECT 1 FROM t AS x JOIN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'WHERE'.",
            "SELECT 1 FROM t x JOIN WHERE a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '<EOF>'.",
            "SELECT 1 FROM t, n JOIN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN n ON 1 = 1, n JOIN");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ')'.",
            "WITH c AS (SELECT 1 FROM t JOIN) SELECT 1 FROM c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'.",
            "SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t JOIN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 0 unexpected '<EOF>'.",
            """
                SELECT 1 FROM t JOIN
""");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN -- c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'START'. | syntax "
                + "error line 1 at position 37 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN START WITH a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'CONNECT'.",
            "SELECT 1 FROM t JOIN CONNECT BY a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'MATCH_RECOGNIZE' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on TABLE MATCH_RECOGNIZE.",
            "SELECT 1 FROM t JOIN MATCH_RECOGNIZE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'INTO'.",
            "SELECT 1 FROM t JOIN INTO x");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'JOIN'.",
            "SELECT 1 FROM t JOIN CROSS JOIN n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'JOIN'.",
            "SELECT 1 FROM t JOIN LEFT JOIN n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'JOIN'.",
            "SELECT 1 FROM t JOIN INNER JOIN n ON 1 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'JOIN'.",
            "SELECT 1 FROM t JOIN NATURAL JOIN n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'JOIN'.",
            "SELECT 1 FROM t JOIN FULL JOIN n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'JOIN'.",
            "SELECT 1 FROM t JOIN RIGHT JOIN n");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'OUTER' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE OUTER.",
            "SELECT 1 FROM t JOIN OUTER");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN LEFT");
    }

    @Test
    public void afterAJoinTypeAndOutsideAFromList() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        engine.execute("CREATE OR REPLACE TABLE tt (a INT, s VARCHAR(5), o OBJECT, v VARIANT, tm TIME, d DATE, ts "
            + "TIMESTAMP_NTZ, tz TIMESTAMP_TZ, bo BOOLEAN)");
        engine.execute("INSERT INTO tt SELECT 86400, 'abc', OBJECT_CONSTRUCT('x', '2024-01-02'), PARSE_JSON('{\"x\": "
            + "\"2024-01-02\"}'), '03:04:05'::TIME, '2024-01-02'::DATE, '2024-01-02 03:04:05'::TIMESTAMP_NTZ, "
            + "'2024-01-02 03:04:05 +02:00'::TIMESTAMP_TZ, TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'WHERE'. | syntax "
                + "error line 1 at position 26 unexpected '<EOF>'.",
            "SELECT 1 FROM t JOIN WHERE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'WHERE'. | syntax "
                + "error line 1 at position 38 unexpected 'x'.",
            "SELECT 1 FROM t LEFT JOIN WHERE a = 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'WHERE'. | syntax "
                + "error line 1 at position 50 unexpected 'y'.",
            "SELECT 1 FROM t LEFT JOIN WHERE a = 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '<EOF>'.",
            "SELECT 1 FROM t LEFT JOIN LATERAL");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'n'.",
            "SELECT 1 JOIN n");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'JOIN'.",
            "SELECT 1 FROM t WHERE a = 1 JOIN");
    }
}
