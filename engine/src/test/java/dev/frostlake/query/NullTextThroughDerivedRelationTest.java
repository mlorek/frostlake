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
 * A NULL of a text type — NULL::VARCHAR(10), CAST(NULL AS STRING), TO_VARCHAR(NULL) — is the bare word to an
 * exact number beside it, and a derived table, a common table expression, a view and a renamed column carry
 * it to the query above: a + 1 over such a column is typed as NULL + 1 is. A set operation, an aggregate and a
 * second cast do not, and a stored text column of NULLs is a text read as a number. Either way what is
 * computed from a text that is always NULL is tagged by the empty interval (live-verified).
 */
public class NullTextThroughDerivedRelationTest extends MeasuredCellsTestSupport {

    @Test
    public void theBareWordThroughADerivedRelation() {
        answer(
            "CREATE OR REPLACE TABLE tn (s VARCHAR(10), n NUMBER(10,2))");
        answer(
            "INSERT INTO tn VALUES (NULL, 1.5)");
        assertAnswer(
            "1.00|NUMBER(10,2)[SB8] ;; 1.50|NUMBER(10,2)[SB8]",
            "SELECT $1, SYSTEM$TYPEOF($1) FROM VALUES (1.50::NUMBER(10,2)), (1) ORDER BY 1");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM (SELECT NULL::VARCHAR(10) AS a)");
        assertAnswer(
            "NUMBER(19,5)[SB1]|NUMBER(19,5)[SB1]|NUMBER(18,5)[SB4]",
            "SELECT SYSTEM$TYPEOF(s + 1), SYSTEM$TYPEOF(s + n), SYSTEM$TYPEOF(COALESCE(s, 5)) FROM tn");
        assertAnswer(
            "NUMBER(19,5)[SB1]",
            "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10)::VARCHAR(5) + 1)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) + 1)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "WITH c AS (SELECT NULL::VARCHAR(10) AS a) SELECT SYSTEM$TYPEOF(a + 1) FROM c");
        assertAnswer(
            "NUMBER(19,0)[SB1]|VARCHAR(134217728)[LOB]|NUMBER(19,1)[SB1]",
            "SELECT SYSTEM$TYPEOF(a * 2), SYSTEM$TYPEOF(a || 'x'), SYSTEM$TYPEOF(a + 1.5) FROM (SELECT "
                + "NULL::VARCHAR AS a)");
        assertAnswer(
            "NUMBER(19,5)[SB1] ;; NUMBER(19,5)[SB1]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM (SELECT NULL::VARCHAR(10) AS a UNION ALL SELECT NULL::VARCHAR(10))");
        assertAnswer(
            "NUMBER(19,5)[SB1]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM (SELECT s AS a FROM tn)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM (SELECT NULL AS a)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM (SELECT NULL::STRING AS a)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM (SELECT CAST(NULL AS VARCHAR(10)) AS a)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM (SELECT TO_VARCHAR(NULL) AS a)");
        assertAnswer(
            "NUMBER(19,5)[SB1]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM (SELECT NULL::VARCHAR(10)::VARCHAR(5) AS a)");
        assertAnswer(
            "NUMBER(11,2)[SB1]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM (SELECT NULL::NUMBER(10,2) AS a)");
        assertAnswer(
            "DATE[SB4]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM (SELECT NULL::DATE AS a)");
        assertAnswer(
            "VARCHAR(10)[LOB]",
            "SELECT SYSTEM$TYPEOF(a) FROM (SELECT NULL::VARCHAR(10) AS a)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(b + 1) FROM (SELECT a AS b FROM (SELECT NULL::VARCHAR(10) AS a))");
        assertAnswer(
            "NUMBER(19,2)[SB1]",
            "SELECT SYSTEM$TYPEOF(a + n) FROM (SELECT NULL::VARCHAR(10) AS a), tn");
        assertAnswer(
            "NUMBER(18,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(LENGTH(a)) FROM (SELECT NULL::VARCHAR(10) AS a)");
    }

    @Test
    public void arithmeticOverATextColumnOfNulls() {
        answer(
            "CREATE OR REPLACE TABLE tn (s VARCHAR(10), n NUMBER(10,2))");
        answer(
            "INSERT INTO tn VALUES (NULL, 1.5)");
        assertAnswer(
            "NUMBER(19,5)[SB1]|NUMBER(28,7)[SB1]|NUMBER(24,11)[SB1]|FLOAT[DOUBLE]|FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(s - 1), SYSTEM$TYPEOF(s * n), SYSTEM$TYPEOF(s / 2), SYSTEM$TYPEOF(-s), "
                + "SYSTEM$TYPEOF(ABS(s)) FROM tn");
        assertAnswer(
            "NUMBER(18,5)[SB4]|NUMBER(18,5)[SB4]|NUMBER(1,0)[SB1]|VARCHAR(10)[LOB]",
            "SELECT SYSTEM$TYPEOF(COALESCE(s, 5)), SYSTEM$TYPEOF(NVL(s, 5)), SYSTEM$TYPEOF(IFF(s IS NULL, 5, 6)), "
                + "SYSTEM$TYPEOF(COALESCE(s, '5')) FROM tn");
        assertAnswer(
            "NUMBER(38,0)[SB1]|NUMBER(38,0)[SB1]|NUMBER(11,2)[SB1]",
            "SELECT SYSTEM$TYPEOF(s::NUMBER), SYSTEM$TYPEOF(TO_NUMBER(s)), SYSTEM$TYPEOF(s::NUMBER(10,2) + 1) "
                + "FROM tn");
        assertAnswer(
            "NUMBER(3,0)[SB2] ;; NUMBER(3,0)[SB2]",
            "SELECT SYSTEM$TYPEOF($1) FROM VALUES (1), (300)");
        assertAnswer(
            "NUMBER(3,2)[SB2] ;; NUMBER(3,2)[SB2]",
            "SELECT SYSTEM$TYPEOF($1) FROM VALUES (1.5), (2.25)");
        assertAnswer(
            "NUMBER(11,2)[SB8] ;; NUMBER(11,2)[SB8]",
            "SELECT SYSTEM$TYPEOF($1 + 1) FROM VALUES (1.50::NUMBER(10,2)), (1)");
        assertAnswer(
            "NUMBER(10,2)[SB8] ;; NUMBER(10,2)[SB8]",
            "SELECT SYSTEM$TYPEOF(x) FROM (VALUES (1.50::NUMBER(10,2)), (1)) v(x)");
        assertAnswer(
            "NUMBER(10,2)[SB8]",
            "SELECT SYSTEM$TYPEOF($1) FROM VALUES (1::NUMBER(10,2))");
        assertAnswer(
            "NUMBER(10,2)[SB8] ;; NUMBER(10,2)[SB8]",
            "SELECT SYSTEM$TYPEOF($1) FROM VALUES (NULL::NUMBER(10,2)), (1)");
        assertAnswer(
            "VARCHAR(3)[LOB] ;; VARCHAR(3)[LOB]",
            "SELECT SYSTEM$TYPEOF($1) FROM VALUES ('a'), ('bcd')");
        assertAnswer(
            "NUMBER(12,4)[SB8] ;; NUMBER(12,4)[SB8]",
            "SELECT SYSTEM$TYPEOF($1) FROM VALUES (1.50::NUMBER(10,2)), (1.25::NUMBER(12,4))");
        assertAnswer(
            "1",
            "INSERT INTO tn VALUES ('7', 2.5)");
        assertAnswer(
            "NUMBER(19,5)[SB16]|NUMBER(18,5)[SB8]",
            "SELECT SYSTEM$TYPEOF(s + 1), SYSTEM$TYPEOF(COALESCE(s, 5)) FROM tn LIMIT 1");
    }

    @Test
    public void viewsRenamesAndWhatDoesNotCarryIt() {
        answer(
            "CREATE OR REPLACE TABLE tn (s VARCHAR(10), n NUMBER(10,2))");
        answer(
            "INSERT INTO tn VALUES (NULL, 1.5)");
        answer(
            "CREATE OR REPLACE VIEW vnt AS SELECT NULL::VARCHAR(10) AS a");
        answer(
            "CREATE OR REPLACE TABLE tnt AS SELECT NULL::VARCHAR(10) AS a");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(TO_VARCHAR(NULL) + 1)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(TO_CHAR(NULL) + 1)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM (SELECT TO_CHAR(NULL) AS a)");
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(COALESCE(a, 5)) FROM (SELECT NULL::VARCHAR(10) AS a)");
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(IFF(TRUE, a, 5)) FROM (SELECT NULL::VARCHAR(10) AS a)");
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(NVL(a, 5)) FROM (SELECT NULL::VARCHAR(10) AS a)");
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(GREATEST(a, 5)) FROM (SELECT NULL::VARCHAR(10) AS a)");
        assertAnswer(
            "NUMBER(24,6)[SB1]",
            "SELECT SYSTEM$TYPEOF(a / 2) FROM (SELECT NULL::VARCHAR(10) AS a)");
        assertAnswer(
            "NUMBER(2,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(MOD(a, 2)) FROM (SELECT NULL::VARCHAR(10) AS a)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(a - 1) FROM (SELECT NULL::VARCHAR(10) AS a)");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(-a) FROM (SELECT NULL::VARCHAR(10) AS a)");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(a + 1::FLOAT) FROM (SELECT NULL::VARCHAR(10) AS a)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM vnt");
        assertAnswer(
            "NUMBER(19,5)[SB1]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM tnt");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(b + 1) FROM (SELECT NULL::VARCHAR(10)) AS d(b)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(d.a + 1) FROM (SELECT NULL::VARCHAR(10) AS a) d JOIN tn ON TRUE");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM (SELECT NULL::VARCHAR(10) AS a FROM tn)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM (SELECT DISTINCT NULL::VARCHAR(10) AS a)");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM (SELECT NULL::VARCHAR(10) AS a ORDER BY 1)");
        assertAnswer(
            "NUMBER(19,5)[SB16]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM (SELECT MAX(NULL::VARCHAR(10)) AS a)");
        assertAnswer(
            "NUMBER(19,5)[SB16] ;; NUMBER(19,5)[SB16]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM (SELECT NULL::VARCHAR(10) AS a UNION ALL SELECT 'x')");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(a + b) FROM (SELECT NULL::VARCHAR(10) AS a, NULL::VARCHAR(10) AS b)");
        assertAnswer(
            "VARCHAR(11)[LOB]",
            "SELECT SYSTEM$TYPEOF(a || 'x') FROM (SELECT NULL::VARCHAR(10) AS a)");
        assertAnswer(
            "BOOLEAN[ROWINDEX]",
            "SELECT SYSTEM$TYPEOF(a = 1) FROM (SELECT NULL::VARCHAR(10) AS a)");
    }

    @Test
    public void conversionsAndChoicesOverATextOfNulls() {
        answer(
            "CREATE OR REPLACE TABLE tn (s VARCHAR(10), n NUMBER(10,2))");
        answer(
            "INSERT INTO tn VALUES (NULL, 1.5)");
        answer(
            "CREATE OR REPLACE VIEW vnt AS SELECT NULL::VARCHAR(10) AS a");
        answer(
            "CREATE OR REPLACE TABLE tnt AS SELECT NULL::VARCHAR(10) AS a");
        assertAnswer(
            "NUMBER(19,5)[SB1]",
            "SELECT SYSTEM$TYPEOF(s + 1) FROM tn WHERE n > 0");
        assertAnswer(
            "NUMBER(31,5)[SB1]",
            "SELECT SYSTEM$TYPEOF(SUM(s + 1)) FROM tn");
        assertAnswer(
            "NUMBER(10,2)[SB1]",
            "SELECT SYSTEM$TYPEOF(s::NUMBER(10,2)) FROM tn");
        assertAnswer(
            "NUMBER(10,2)[SB1]",
            "SELECT SYSTEM$TYPEOF(TO_DECIMAL(s, 10, 2)) FROM tn");
        assertAnswer(
            "NUMBER(38,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(TRY_TO_NUMBER(s)) FROM tn");
        assertAnswer(
            "NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(LENGTH(s) + 1) FROM tn");
        assertAnswer(
            "NUMBER(18,5)[SB4]",
            "SELECT SYSTEM$TYPEOF(COALESCE(s, n)) FROM tn");
        assertAnswer(
            "NUMBER(10,2)[SB2]",
            "SELECT SYSTEM$TYPEOF(COALESCE(s::NUMBER(10,2), n)) FROM tn");
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(NVL2(s, 1, 2)) FROM tn");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(s + s) FROM tn");
        assertAnswer(
            "VARCHAR(11)[LOB]",
            "SELECT SYSTEM$TYPEOF(s || 'x') FROM tn");
        assertAnswer(
            "NUMBER(19,5)[SB1]",
            "SELECT SYSTEM$TYPEOF(MAX(s) + 1) FROM tn");
        assertAnswer(
            "NUMBER(19,5)[SB1]",
            "SELECT SYSTEM$TYPEOF(x + 1) FROM (SELECT MAX(s) AS x FROM tn)");
        assertAnswer(
            "NUMBER(19,5)[SB1]",
            "SELECT SYSTEM$TYPEOF(s * 2) FROM (SELECT s FROM tn)");
        assertAnswer(
            "NUMBER(19,5)[SB1]",
            "SELECT SYSTEM$TYPEOF(IFF(s IS NULL, s + 1, 2)) FROM tn");
    }

    @Test
    public void aggregatesRenamesAndNullTests() {
        answer(
            "CREATE OR REPLACE TABLE tn (s VARCHAR(10), n NUMBER(10,2))");
        answer(
            "INSERT INTO tn VALUES (NULL, 1.5)");
        answer(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        answer(
            "INSERT INTO tp VALUES (1, 10), (2, 20)");
        answer(
            "CREATE OR REPLACE TABLE tq (n NUMBER(10,2), s VARCHAR)");
        answer(
            "INSERT INTO tq VALUES (1.5, 'ab')");
        assertAnswer(
            "NUMBER(19,5)[SB16]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM (SELECT MIN(NULL::VARCHAR(10)) AS a)");
        assertAnswer(
            "NUMBER(19,5)[SB16]",
            "SELECT SYSTEM$TYPEOF(MAX(NULL::VARCHAR(10)) + 1)");
        assertAnswer(
            "NUMBER(11,2)[SB1]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM (SELECT MAX(NULL::NUMBER(10,2)) AS a)");
        assertAnswer(
            "NUMBER(19,5)[SB16]",
            "SELECT SYSTEM$TYPEOF(a + 1) FROM (SELECT ANY_VALUE(NULL::VARCHAR(10)) AS a)");
        assertAnswer(
            "VARCHAR(10)[LOB]",
            "SELECT SYSTEM$TYPEOF(a) FROM (SELECT MAX(NULL::VARCHAR(10)) AS a)");
        assertAnswer(
            "NUMBER(11,2)[SB2]",
            "SELECT SYSTEM$TYPEOF(b + 1) FROM (SELECT n FROM tn) AS d(b)");
        assertAnswer(
            "NUMBER(10,2)[SB2]",
            "SELECT SYSTEM$TYPEOF(b) FROM (SELECT n FROM tn) AS d(b)");
        assertAnswer(
            "NUMBER(4,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(b + 1) FROM (SELECT 300 AS a) AS d(b)");
        assertAnswer(
            "NUMBER(19,5)[SB1]",
            "SELECT SYSTEM$TYPEOF(IFF(s IS NOT NULL, 2, s + 1)) FROM tn");
        assertAnswer(
            "NUMBER(3,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(CASE WHEN s IS NULL THEN 1 ELSE 300 END) FROM tn");
        assertAnswer(
            "NUMBER(3,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(IFF(n IS NULL, 300, 1)) FROM tn");
        assertAnswer(
            "NUMBER(3,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(NVL2(s, 300, 1)) FROM tn");
        assertAnswer(
            "NUMBER(3,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(IFF(a IS NULL, 1, 300)) FROM (SELECT NULL::VARCHAR(10) AS a)");
        assertAnswer(
            "NUMBER(3,0)[SB2]",
            "SELECT SYSTEM$TYPEOF(IFF(s IS NULL, 300, 1)) FROM (SELECT s FROM tn)");
    }
}
