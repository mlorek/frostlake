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
 * BETWEEN is the two comparisons it stands for under three-valued logic: FALSE when either is FALSE, NULL when
 * either is NULL and none is FALSE, TRUE otherwise - and NOT BETWEEN its negation, which keeps the NULL. So a
 * WHERE keeps the rows a NOT BETWEEN or a NOT (… BETWEEN …) is TRUE for, and drops those it is NULL for, over
 * literals, columns, dates, texts and a block's variables alike. Every answer is live-verified.
 */
public class BetweenThreeValuedTest extends MeasuredCellsTestSupport {

    @Test
    public void betweenOverNulls() {
        engine.execute(
            "CREATE OR REPLACE TABLE tb (a INT, lo INT, hi INT)");
        engine.execute(
            "INSERT INTO tb VALUES (1, NULL, 2), (NULL, 1, 2), (5, 1, NULL), (5, NULL, 2), (5, 1, 2), (1, 1, 2), "
                + "(NULL, NULL, NULL)");
        try {
            assertAnswer(
                "null|null|null",
                "SELECT NULL::DATE BETWEEN NULL AND NULL, 1 BETWEEN NULL AND 2, NULL BETWEEN 1 AND 2");
            assertAnswer(
                "null|false|false|null|null",
                "SELECT 1 BETWEEN 0 AND NULL, 5 BETWEEN NULL AND 2, 1 BETWEEN 2 AND NULL, 3 BETWEEN 1 AND NULL, 0 "
                    + "BETWEEN NULL AND 2");
            assertAnswer(
                "null|true|null|true",
                "SELECT 1 NOT BETWEEN NULL AND 2, 5 NOT BETWEEN NULL AND 2, NULL NOT BETWEEN 1 AND 2, 1 NOT "
                    + "BETWEEN 2 AND NULL");
            assertAnswer(
                "null|null|true|false",
                "SELECT NULL BETWEEN NULL AND NULL, NULL NOT BETWEEN NULL AND NULL, 1 BETWEEN 1 AND 1, 1 NOT "
                    + "BETWEEN 1 AND 1");
            assertAnswer(
                "1|1|2|true|false ;; 1|null|2|null|null ;; 5|1|2|false|true ;; 5|1|null|null|null ;; "
                    + "5|null|2|false|true ;; null|1|2|null|null ;; null|null|null|null|null",
                "SELECT a, lo, hi, a BETWEEN lo AND hi, a NOT BETWEEN lo AND hi FROM tb ORDER BY 1, 2, 3");
            assertAnswer(
                "1|1|2|false|true ;; 1|null|2|null|null ;; 5|1|2|true|false ;; 5|1|null|null|null ;; "
                    + "5|null|2|true|false ;; null|1|2|null|null ;; null|null|null|null|null",
                "SELECT a, lo, hi, NOT (a BETWEEN lo AND hi), NOT (a NOT BETWEEN lo AND hi) FROM tb ORDER BY 1, 2,"
                    + " 3");
            assertAnswer(
                "1|1|2|false|false ;; 1|null|2|true|true ;; 5|1|2|false|false ;; 5|1|null|true|true ;; "
                    + "5|null|2|false|false ;; null|1|2|true|true ;; null|null|null|true|true",
                "SELECT a, lo, hi, (a BETWEEN lo AND hi) IS NULL, (a NOT BETWEEN lo AND hi) IS NULL FROM tb ORDER "
                    + "BY 1, 2, 3");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tb WHERE a BETWEEN lo AND hi");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM tb WHERE a NOT BETWEEN lo AND hi");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM tb WHERE NOT (a BETWEEN lo AND hi)");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tb WHERE NOT (a NOT BETWEEN lo AND hi)");
            assertAnswer(
                "4",
                "SELECT COUNT(*) FROM tb WHERE (a BETWEEN lo AND hi) IS NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'IS'.",
                "SELECT COUNT(*) FROM tb WHERE (a BETWEEN lo AND hi) IS NOT TRUE");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected 'IS'.",
                "SELECT COUNT(*) FROM tb WHERE (a NOT BETWEEN lo AND hi) IS NOT FALSE");
            assertAnswer(
                "3",
                "SELECT COUNT(*) FROM tb WHERE a BETWEEN lo AND hi OR a NOT BETWEEN lo AND hi");
            assertAnswer(
                "1|1|2|in ;; 1|null|2|unknown ;; 5|1|2|out ;; 5|1|null|unknown ;; 5|null|2|out ;; "
                    + "null|1|2|unknown ;; null|null|null|unknown",
                "SELECT a, lo, hi, CASE WHEN a BETWEEN lo AND hi THEN 'in' WHEN a NOT BETWEEN lo AND hi THEN "
                    + "'out' ELSE 'unknown' END FROM tb ORDER BY 1, 2, 3");
            assertAnswer(
                "1|1|2|in|not ;; 1|null|2|not|not ;; 5|1|2|not|out ;; 5|1|null|not|not ;; 5|null|2|not|out ;; "
                    + "null|1|2|not|not ;; null|null|null|not|not",
                "SELECT a, lo, hi, IFF(a BETWEEN lo AND hi, 'in', 'not'), IFF(a NOT BETWEEN lo AND hi, 'out', "
                    + "'not') FROM tb ORDER BY 1, 2, 3");
            assertAnswer(
                "1|2|false|true",
                "SELECT COUNT_IF(a BETWEEN lo AND hi), COUNT_IF(a NOT BETWEEN lo AND hi), BOOLAND_AGG(a BETWEEN "
                    + "lo AND hi), BOOLOR_AGG(a NOT BETWEEN lo AND hi) FROM tb");
            assertAnswer(
                "1|1|2 ;; 1|1|2 ;; 1|1|2 ;; 1|1|2 ;; 1|1|2 ;; 1|1|2",
                "SELECT x.a, y.lo, y.hi FROM tb x JOIN tb y ON x.a BETWEEN y.lo AND y.hi WHERE x.a = 1 ORDER BY 1,"
                    + " 2, 3");
            assertAnswer(
                "15",
                "SELECT COUNT(*) FROM tb x JOIN tb y ON x.a NOT BETWEEN y.lo AND y.hi");
            assertAnswer(
                "11",
                "SELECT COUNT(*) FROM tb x LEFT JOIN tb y ON x.a BETWEEN y.lo AND y.hi");
            assertAnswer(
                "",
                "SELECT a, COUNT(*) FROM tb GROUP BY a HAVING a BETWEEN 2 AND NULL ORDER BY 1");
            assertAnswer(
                "1|2",
                "SELECT a, COUNT(*) FROM tb GROUP BY a HAVING a NOT BETWEEN 2 AND NULL ORDER BY 1");
            assertAnswer(
                "null|false|true|null",
                "SELECT 'b' BETWEEN 'a' AND NULL, 'b' BETWEEN NULL AND 'a', 'b' NOT BETWEEN NULL AND 'a', "
                    + "NULL::VARCHAR BETWEEN 'a' AND 'c'");
            assertAnswer(
                "null|true",
                "SELECT '2024-01-02'::DATE BETWEEN '2024-01-01'::DATE AND NULL, '2024-01-02'::DATE NOT BETWEEN "
                    + "NULL AND '2024-01-01'::DATE");
            assertAnswer(
                "false|true|null|false",
                "SELECT 1.5 BETWEEN NULL AND 1, 1.5 NOT BETWEEN NULL AND 1, TRUE BETWEEN FALSE AND NULL, TRUE "
                    + "BETWEEN NULL AND FALSE");
            assertAnswer(
                "BOOLEAN[ROWINDEX]|BOOLEAN[ROWINDEX]|BOOLEAN[ROWINDEX]",
                "SELECT SYSTEM$TYPEOF(1 BETWEEN NULL AND 2), SYSTEM$TYPEOF(5 BETWEEN NULL AND 2), "
                    + "SYSTEM$TYPEOF(NULL BETWEEN 1 AND 2)");
            assertShape(
                "1 BETWEEN NULL AND 2:BOOLEAN, 5 NOT BETWEEN NULL AND 2:BOOLEAN",
                "SELECT 1 BETWEEN NULL AND 2, 5 NOT BETWEEN NULL AND 2");
            assertAnswer(
                "false|true|false|true",
                "SELECT 1 BETWEEN 2 AND 0, 1 NOT BETWEEN 2 AND 0, 1 BETWEEN NULL AND 0, 1 NOT BETWEEN NULL AND 0");
            assertAnswer(
                "false|true|null|true",
                "SELECT (1 BETWEEN NULL AND 2) AND FALSE, (1 BETWEEN NULL AND 2) OR TRUE, (1 BETWEEN NULL AND 2) "
                    + "AND TRUE, (5 NOT BETWEEN NULL AND 2) AND TRUE");
            assertAnswer(
                "false|true",
                "SELECT NVL(1 BETWEEN NULL AND 2, FALSE), COALESCE(NULL BETWEEN 1 AND 2, TRUE)");
            assertAnswer(
                "null|null",
                "DECLARE s DATE; r RESULTSET; BEGIN r := (SELECT :s BETWEEN NULL AND NULL, :s NOT BETWEEN NULL "
                    + "AND NULL); RETURN TABLE(r); END;");
            assertAnswer(
                "null|false|true",
                "DECLARE s INT; r RESULTSET; BEGIN r := (SELECT 1 BETWEEN :s AND 2, 5 BETWEEN :s AND 2, 5 NOT "
                    + "BETWEEN :s AND 2); RETURN TABLE(r); END;");
            assertAnswer(
                "no",
                "DECLARE s INT; BEGIN IF (1 BETWEEN s AND 2) THEN RETURN 'yes'; ELSE RETURN 'no'; END IF; END;");
            assertAnswer(
                "yes",
                "DECLARE s INT; BEGIN IF (5 NOT BETWEEN s AND 2) THEN RETURN 'yes'; ELSE RETURN 'no'; END IF; END;");
            assertAnswer(
                "null",
                "DECLARE s INT; BEGIN RETURN (1 BETWEEN s AND 2); END;");
            assertAnswer(
                "true",
                "DECLARE s INT; BEGIN RETURN (5 NOT BETWEEN s AND 2); END;");
            assertAnswer(
                "Table TB2 successfully created.",
                "CREATE OR REPLACE TABLE tb2 AS SELECT * FROM tb");
            assertAnswer(
                "2",
                "DELETE FROM tb2 WHERE a NOT BETWEEN lo AND hi");
            assertAnswer(
                "5",
                "SELECT COUNT(*) FROM tb2");
            assertAnswer(
                "0|0",
                "UPDATE tb2 SET a = -1 WHERE NOT (a BETWEEN lo AND hi)");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tb2 WHERE a = -1");
            assertAnswer(
                "",
                "SELECT a FROM tb WHERE a BETWEEN (SELECT MIN(lo) FROM tb) AND (SELECT MAX(hi) FROM tb WHERE "
                    + "FALSE) ORDER BY 1");
            assertAnswer(
                "5",
                "SELECT COUNT(*) FROM tb WHERE a NOT BETWEEN (SELECT MAX(hi) FROM tb WHERE FALSE) AND 0");
            assertAnswer(
                "5",
                "SELECT COUNT(*) FROM tb WHERE NOT EXISTS (SELECT 1 FROM tb y WHERE tb.a BETWEEN y.lo AND y.hi)");
            assertAnswer(
                "View VB successfully created.",
                "CREATE OR REPLACE VIEW vb AS SELECT a, a BETWEEN lo AND hi AS x, a NOT BETWEEN lo AND hi AS y "
                    + "FROM tb");
            assertAnswer(
                "7|3|3|1|2",
                "SELECT COUNT(*), COUNT(x), COUNT(y), COUNT_IF(x), COUNT_IF(y) FROM vb");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tb WHERE CASE WHEN a BETWEEN lo AND hi THEN TRUE END");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tb WHERE a BETWEEN lo AND hi AND a IS NOT NULL");
            assertAnswer(
                "4",
                "SELECT COUNT(*) FROM tb WHERE NOT (a BETWEEN lo AND hi AND lo IS NOT NULL)");
            assertAnswer(
                "5|1|2 ;; 5|null|2 ;; 1|1|2 ;; 1|null|2 ;; 5|1|null ;; null|1|2 ;; null|null|null",
                "SELECT a, lo, hi FROM tb ORDER BY a BETWEEN lo AND hi, 1, 2, 3");
            assertAnswer(
                "false|2 ;; true|1 ;; null|4",
                "SELECT (a BETWEEN lo AND hi)::VARCHAR, COUNT(*) FROM tb GROUP BY 1 ORDER BY 1");
            assertAnswer(
                "false ;; true ;; null",
                "SELECT DISTINCT a NOT BETWEEN lo AND hi FROM tb ORDER BY 1");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
