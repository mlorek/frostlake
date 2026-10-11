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
 * The result column of a SUM the plan rewrites - one over a column shifted by a constant - is reported at the
 * rewritten width: by the result's own columns, by DESCRIBE RESULT and by a RESULT_SCAN of it. A table created
 * from the query, a view over it, a derived table, a set operation's operand and a windowed SUM keep the
 * declared width. Every answer is live-verified.
 */
public class RewrittenSumResultWidthTest extends MeasuredCellsTestSupport {

    @Test
    public void theWidthTheResultReports() {
        engine.execute(
            "CREATE OR REPLACE TABLE g (v NUMBER(6,0), k INT)");
        engine.execute(
            "INSERT INTO g VALUES (-1000, 1), (1, 2)");
        engine.execute(
            "CREATE OR REPLACE TABLE t (n NUMBER(5,2), n40 NUMBER(4,0), k INT)");
        engine.execute(
            "INSERT INTO t VALUES (1.5, 17, 1), (2.5, 3, 2)");
        engine.execute(
            "CREATE OR REPLACE TABLE t847 (n NUMBER(5,2), n10 NUMBER(10,2))");
        engine.execute(
            "INSERT INTO t847 VALUES (1.5, 17.25), (2.5, 3.5)");
        engine.execute(
            "CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5), n52 NUMBER(5,2), v VARIANT)");
        engine.execute(
            "CREATE OR REPLACE TABLE src (g VARCHAR(5))");
        engine.execute(
            "INSERT INTO src VALUES ('\u00e9\u00e9\u00e9\u00e9\u00e9')");
        assertShape(
            "SUM(N + 1):NUMBER(24,2)",
            "SELECT SUM(n + 1) FROM t");
        assertShape(
            "SUM(N + 2):NUMBER(24,2)",
            "SELECT SUM(n + 2) FROM t");
        assertShape(
            "SUM(N + 0.05):NUMBER(22,2)",
            "SELECT SUM(n + 0.05) FROM t");
        assertShape(
            "SUM(N40 + 1):NUMBER(20,0)",
            "SELECT SUM(n40 + 1) FROM t");
        assertShape(
            "SUM(X):NUMBER(24,2)",
            "SELECT SUM(x) FROM (SELECT n + 1 x FROM t)");
        assertShape(
            "SUM(X):NUMBER(20,0)",
            "SELECT SUM(x) FROM (SELECT n40 + 1 x FROM t)");
        assertShape(
            "SUM(X):NUMBER(24,2)",
            "WITH c AS (SELECT n + 1 x FROM t) SELECT SUM(x) FROM c");
        assertShape(
            "SUM(X + 1):NUMBER(24,2)",
            "SELECT SUM(x + 1) FROM (SELECT n x FROM t)");
        assertShape(
            "SUM(N + 0.05):NUMBER(22,2), SUM(N + 1):NUMBER(24,2), SUM(N10 + 0.05):NUMBER(23,2)",
            "SELECT SUM(n + 0.05), SUM(n + 1), SUM(n10 + 0.05) FROM t847");
        assertShape(
            "SUM(N):NUMBER(17,2)",
            "SELECT SUM(n) FROM t");
        assertShape(
            "SUM(N - 1):NUMBER(24,2)",
            "SELECT SUM(n - 1) FROM t");
        assertShape(
            "SUM(1 + N):NUMBER(24,2)",
            "SELECT SUM(1 + n) FROM t");
        assertShape(
            "SUM(N + N40):NUMBER(19,2)",
            "SELECT SUM(n + n40) FROM t");
        assertShape(
            "SUM(N) + 1:NUMBER(18,2)",
            "SELECT SUM(n) + 1 FROM t");
        assertShape(
            "AVG(N + 1):NUMBER(24,8)",
            "SELECT AVG(n + 1) FROM t");
        assertShape(
            "SUM(N + 1) OVER ():NUMBER(18,2)",
            "SELECT SUM(n + 1) OVER () FROM t");
        assertShape(
            "SUM(N + 1):NUMBER(24,2)",
            "SELECT SUM(n + 1) FROM t GROUP BY k");
        assertShape(
            "SUM(DISTINCT N + 1):NUMBER(24,2)",
            "SELECT SUM(DISTINCT n + 1) FROM t");
        assertShape(
            "SUM(N + 1.5):NUMBER(24,2)",
            "SELECT SUM(n + 1.5) FROM t");
        assertShape(
            "SUM(N + 100):NUMBER(24,2)",
            "SELECT SUM(n + 100) FROM t");
        assertShape(
            "SUM(N40 + 0.05):NUMBER(19,2)",
            "SELECT SUM(n40 + 0.05) FROM t");
        assertShape(
            "SUM(-N):NUMBER(17,2)",
            "SELECT SUM(-n) FROM t");
        assertShape(
            "SUM(N * 2):NUMBER(18,2)",
            "SELECT SUM(n * 2) FROM t");
        assertShape(
            "S:NUMBER(24,2), C:NUMBER(18,0)",
            "SELECT SUM(n + 1) AS s, COUNT(*) AS c FROM t");
        assertShape(
            "SUM(N + 1) + SUM(N40 + 1):NUMBER(20,2)",
            "SELECT SUM(n + 1) + SUM(n40 + 1) FROM t");
        assertShape(
            "SUM(N + 1)::NUMBER(30,2):NUMBER(30,2)",
            "SELECT SUM(n + 1)::NUMBER(30,2) FROM t");
        assertShape(
            "K:NUMBER(38,0), SUM(N + 1):NUMBER(24,2)",
            "SELECT k, SUM(n + 1) FROM t GROUP BY k ORDER BY k");
        assertShape(
            "MAX(N + 1):NUMBER(6,2), MIN(N + 1):NUMBER(6,2), COUNT(N + 1):NUMBER(18,0)",
            "SELECT MAX(n + 1), MIN(n + 1), COUNT(n + 1) FROM t");
        assertShape(
            "SUM(N + 1):NUMBER(24,2)",
            "SELECT SUM(n + 1) FROM t WHERE FALSE");
        assertShape(
            "SUM(N + 1):NUMBER(18,2)",
            "SELECT SUM(n + 1) FROM t UNION ALL SELECT 1");
        assertShape(
            "(SELECT SUM(N + 1) FROM T):NUMBER(18,2)",
            "SELECT (SELECT SUM(n + 1) FROM t)");
        assertShape(
            "S:NUMBER(18,2)",
            "SELECT * FROM (SELECT SUM(n + 1) s FROM t)");
        assertShape(
            "S + 1:NUMBER(19,2)",
            "SELECT s + 1 FROM (SELECT SUM(n + 1) s FROM t)");
        assertAnswer(
            "6.00",
            "SELECT SUM(x) FROM (SELECT n + 1 x FROM t)");
        assertAnswer(
            "SUM(X)|NUMBER(24,2)",
            "DESCRIBE RESULT LAST_QUERY_ID() ->> SELECT \"name\", \"type\" FROM $1");
        assertAnswer(
            "4.10|6.00|20.85",
            "SELECT SUM(n + 0.05), SUM(n + 1), SUM(n10 + 0.05) FROM t847");
        assertAnswer(
            "6.00",
            "SELECT SUM(n + 1) FROM t");
        assertShape(
            "SUM(N + 1):NUMBER(24,2)",
            "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Table CT847 successfully created.",
            "CREATE OR REPLACE TABLE ct847 AS SELECT SUM(n + 0.05) AS s1, SUM(n + 1) AS s2 FROM t847");
        assertAnswer(
            "View V847 successfully created.",
            "CREATE OR REPLACE VIEW v847 AS SELECT SUM(n + 0.05) AS s1, SUM(n + 1) AS s2 FROM t847");
        assertShape(
            "S1:NUMBER(18,2), S2:NUMBER(18,2)",
            "SELECT * FROM v847");
        assertShape(
            "S2:NUMBER(18,2)",
            "SELECT s2 FROM v847");
        assertAnswer(
            "NUMBER(24,2)[SB16]|NUMBER(22,2)[SB8]|NUMBER(20,0)[SB16] ;; NUMBER(24,2)[SB16]|NUMBER(22,"
                + "2)[SB8]|NUMBER(20,0)[SB16]",
            "SELECT SYSTEM$TYPEOF(SUM(n + 1)), SYSTEM$TYPEOF(SUM(n + 0.05)), SYSTEM$TYPEOF(SUM(n40 + 1)) FROM t");
        assertAnswer(
            "Table IT847 successfully created.",
            "CREATE OR REPLACE TABLE it847 (s NUMBER(38,2))");
        assertAnswer(
            "1",
            "INSERT INTO it847 SELECT SUM(n + 1) FROM t847");
        assertAnswer(
            "6.00",
            "SELECT * FROM it847");
    }
}
