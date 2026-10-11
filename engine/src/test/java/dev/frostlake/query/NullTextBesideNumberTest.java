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
 * The word NULL cast to a text type is that type standing alone, and the bare word where it meets an exact
 * number: in arithmetic the number decides the type as it does beside NULL, a conditional whose other branches
 * are numbers takes theirs, and a set operation's or a VALUES clause's column takes the number's type and
 * value. Beside a text, a FLOAT, a BOOLEAN, a date or another NULL it keeps the rule a text has there, and a
 * text column or a text value is still read as a NUMBER(18,5). Every answer is live-verified.
 */
public class NullTextBesideNumberTest extends MeasuredCellsTestSupport {

    @Test
    public void aNullOfATextTypeMeetsANumber() {
        engine.execute(
            "CREATE OR REPLACE TABLE tn (s VARCHAR(10), n NUMBER(10,2))");
        engine.execute(
            "INSERT INTO tn VALUES (NULL, 1.5)");
        try {
            assertAnswer(
                "NUMBER(19,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) + 1)");
            assertAnswer(
                "NUMBER(19,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) - 1)");
            assertAnswer(
                "NUMBER(19,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(1 + NULL::VARCHAR(10))");
            assertAnswer(
                "NUMBER(19,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) * 2)");
            assertAnswer(
                "NUMBER(19,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR + 1)");
            assertAnswer(
                "NUMBER(19,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(CAST(NULL AS VARCHAR(10)) + 1)");
            assertAnswer(
                "NUMBER(19,1)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) + 1.5)");
            assertAnswer(
                "NUMBER(24,6)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) / 2)");
            assertAnswer(
                "NUMBER(1,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(COALESCE(NULL::VARCHAR(10), 5))");
            assertAnswer(
                "NUMBER(1,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NVL(NULL::VARCHAR(10), 5))");
            assertAnswer(
                "NUMBER(1,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL::VARCHAR(10), 5))");
            assertAnswer(
                "NUMBER(1,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(IFNULL(NULL::VARCHAR(10), 5))");
            assertAnswer(
                "NUMBER(1,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(GREATEST(NULL::VARCHAR(10), 5))");
            assertAnswer(
                "NUMBER(1,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(DECODE(1, 1, NULL::VARCHAR(10), 5))");
            assertAnswer(
                "NUMBER(1,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(CASE WHEN TRUE THEN NULL::VARCHAR(10) ELSE 5 END)");
            assertAnswer(
                "NUMBER(19,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL + 1)");
            assertAnswer(
                "NUMBER(19,5)[SB16]",
                "SELECT SYSTEM$TYPEOF('5'::VARCHAR(10) + 1)");
            assertAnswer(
                "VARCHAR(10)[LOB]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10))");
            assertAnswer(
                "VARCHAR(11)[LOB]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) || 'a')");
            assertAnswer(
                "DATE[SB4]",
                "SELECT SYSTEM$TYPEOF(COALESCE(NULL::VARCHAR(10), '2024-01-01'::DATE))");
            assertAnswer(
                "VARCHAR(134217728)[LOB]",
                "SELECT SYSTEM$TYPEOF(COALESCE(NULL::VARCHAR(10), TRUE))");
            assertAnswer(
                "NUMBER(19,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::CHAR + 1)");
            assertAnswer(
                "NUMBER(19,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::STRING + 1)");
            assertAnswer(
                "NUMBER(20,1)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::TEXT * 1.5)");
            assertAnswer(
                "FLOAT[DOUBLE]",
                "SELECT SYSTEM$TYPEOF(-NULL::VARCHAR(10))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) % 2)");
            assertAnswer(
                "FLOAT[DOUBLE]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) + NULL::VARCHAR(10))");
            assertAnswer(
                "FLOAT[DOUBLE]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) + NULL)");
            assertAnswer(
                "FLOAT[DOUBLE]",
                "SELECT SYSTEM$TYPEOF(ABS(NULL::VARCHAR(10)))");
            assertAnswer(
                "FLOAT[DOUBLE]",
                "SELECT SYSTEM$TYPEOF(ROUND(NULL::VARCHAR(10)))");
            assertAnswer(
                "FLOAT[DOUBLE]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) + 1::FLOAT)");
            assertAnswer(
                "NUMBER(2,1)[SB1]",
                "SELECT SYSTEM$TYPEOF(COALESCE(NULL::VARCHAR(10), 5.5))");
            assertAnswer(
                "NUMBER(1,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(COALESCE(5, NULL::VARCHAR(10)))");
            assertAnswer(
                "VARCHAR(10)[LOB]",
                "SELECT SYSTEM$TYPEOF(COALESCE(NULL::VARCHAR(10), 'a'))");
            assertAnswer(
                "NUMBER(1,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NVL2(NULL::VARCHAR(10), 1, 2))");
            assertAnswer(
                "VARCHAR(134217728)[LOB]",
                "SELECT SYSTEM$TYPEOF(NULLIF(NULL::VARCHAR(10), 5))");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10)::NUMBER + 1)");
            assertAnswer(
                "NUMBER(38,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(TO_NUMBER(NULL::VARCHAR(10)) + 1)");
            assertAnswer(
                "FLOAT[DOUBLE]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) + '1')");
            assertAnswer(
                "BOOLEAN[ROWINDEX]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) = 1)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | Invalid argument types for function "
                    + "'+': (BINARY(67108864), NUMBER(1,0))",
                "SELECT SYSTEM$TYPEOF(NULL::BINARY + 1)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 35 | Invalid argument types for function "
                    + "'+': (BOOLEAN, NUMBER(1,0))",
                "SELECT SYSTEM$TYPEOF(NULL::BOOLEAN + 1)");
            assertAnswer(
                "DATE[SB4]",
                "SELECT SYSTEM$TYPEOF(NULL::DATE + 1)");
            assertAnswer(
                "FLOAT[DOUBLE]",
                "SELECT SYSTEM$TYPEOF(NULL::VARIANT + 1)");
            assertShape(
                "NULL::VARCHAR(10) + 1:NUMBER(19,0), IFF(TRUE, NULL::VARCHAR(10), 5):NUMBER(1,0), "
                    + "COALESCE(NULL::VARCHAR(10), 5):NUMBER(1,0), NULL::VARCHAR(10) / 2:NUMBER(24,6)",
                "SELECT NULL::VARCHAR(10) + 1, IFF(TRUE, NULL::VARCHAR(10), 5), COALESCE(NULL::VARCHAR(10), 5), "
                    + "NULL::VARCHAR(10) / 2");
            assertAnswer(
                "5 ;; null",
                "SELECT a FROM (SELECT NULL::VARCHAR(10) AS a UNION ALL SELECT 5) ORDER BY a");
            assertShape(
                "A:NUMBER(1,0)",
                "SELECT a FROM (SELECT NULL::VARCHAR(10) AS a UNION ALL SELECT 5)");
            assertAnswer(
                "NUMBER(1,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(a) FROM (SELECT NULL::VARCHAR(10) AS a UNION ALL SELECT 5) LIMIT 1");
            assertAnswer(
                "2024-01-01 ;; null",
                "SELECT a FROM (SELECT NULL::VARCHAR(10) AS a UNION ALL SELECT '2024-01-01'::DATE) ORDER BY a");
            assertShape(
                "A:DATE",
                "SELECT a FROM (SELECT NULL::VARCHAR(10) AS a UNION ALL SELECT '2024-01-01'::DATE)");
            assertShape(
                "A:VARCHAR(10)",
                "SELECT a FROM (SELECT NULL::VARCHAR(10) AS a UNION ALL SELECT TRUE)");
            assertShape(
                "A:NUMBER(1,0)",
                "SELECT a FROM (SELECT 5 AS a UNION ALL SELECT NULL::VARCHAR(10))");
            assertShape(
                "A:VARCHAR(10)",
                "SELECT a FROM (SELECT NULL::VARCHAR(10) AS a UNION ALL SELECT 'abc')");
            assertAnswer(
                "5 ;; null",
                "SELECT $1 FROM VALUES (NULL::VARCHAR(10)), (5) ORDER BY 1");
            assertShape(
                "$1:NUMBER(1,0)",
                "SELECT $1 FROM VALUES (NULL::VARCHAR(10)), (5)");
            assertAnswer(
                "NUMBER(19,2)[SB1]|NUMBER(10,2)[SB2]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) + n), SYSTEM$TYPEOF(COALESCE(NULL::VARCHAR(10), n)) FROM "
                    + "tn");
            assertAnswer(
                "NUMBER(19,0)[SB1]",
                "DECLARE s VARCHAR(10); res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s + 1) AS b1); RETURN "
                    + "TABLE(res); END;");
            assertAnswer(
                "NUMBER(1,0)[SB1]",
                "DECLARE s VARCHAR(10); res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(COALESCE(:s, 5)) AS "
                    + "b2); RETURN TABLE(res); END;");
            assertAnswer(
                "NUMBER(19,0)[SB1]",
                "DECLARE s VARCHAR(10) DEFAULT '5'; res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s + 1) AS "
                    + "b3); RETURN TABLE(res); END;");
            assertAnswer(
                "null|5|null",
                "SELECT NULL::VARCHAR(10) + 1, COALESCE(NULL::VARCHAR(10), 5), NULL::VARCHAR(10) / 2");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void moreOperatorsAndBranches() {
        engine.execute(
            "CREATE OR REPLACE TABLE tp (k INT, v INT)");
        engine.execute(
            "INSERT INTO tp VALUES (1, 10), (2, 20), (3, 30)");
        engine.execute(
            "CREATE OR REPLACE TABLE tb (b BOOLEAN, n NUMBER(10,2), i INT)");
        engine.execute(
            "INSERT INTO tb VALUES (TRUE, 1.5, 0)");
        engine.execute(
            "CREATE OR REPLACE TABLE tv (n NUMBER(10,2))");
        try {
            assertAnswer(
                "NUMBER(19,2)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) + 1::NUMBER(10,2))");
            assertAnswer(
                "NUMBER(28,2)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) * 1.5::NUMBER(10,2))");
            assertAnswer(
                "NUMBER(1,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(LEAST(NULL::VARCHAR(10), 5))");
            assertAnswer(
                "NUMBER(1,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(IFF(TRUE, 5, NULL::VARCHAR(10)))");
            assertAnswer(
                "NUMBER(1,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NVL2(1, NULL::VARCHAR(10), 5))");
            assertAnswer(
                "NUMBER(18,5)[SB4]",
                "SELECT SYSTEM$TYPEOF(COALESCE(NULL::VARCHAR(10), 5, 'a'))");
            assertAnswer(
                "NUMBER(1,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(COALESCE(NULL::VARCHAR(10), NULL, 5))");
            assertAnswer(
                "FLOAT[DOUBLE]",
                "SELECT SYSTEM$TYPEOF(ZEROIFNULL(NULL::VARCHAR(10)))");
            assertAnswer(
                "NUMBER(19,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(CAST(NULL AS CHAR(3)) + 1)");
            assertAnswer(
                "NUMBER(19,0)[SB1]",
                "SELECT SYSTEM$TYPEOF((NULL::VARCHAR(10)) + 1)");
            assertAnswer(
                "NUMBER(20,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) + 1 + 1)");
            assertAnswer(
                "NUMBER(20,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(1 - NULL::VARCHAR(10) * 2)");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(MOD(NULL::VARCHAR(10), 2))");
            assertAnswer(
                "NUMBER(24,6)[SB1]",
                "SELECT SYSTEM$TYPEOF(DIV0(NULL::VARCHAR(10), 2))");
            assertAnswer(
                "VARCHAR(134217728)[LOB]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) || 1)");
            assertAnswer(
                "FLOAT[DOUBLE]",
                "SELECT SYSTEM$TYPEOF(COALESCE(NULL::VARCHAR(10), 1::FLOAT))");
            assertAnswer(
                "NUMBER(1,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(COALESCE(NULL::VARCHAR(10), NULL::VARCHAR(5), 5))");
            assertAnswer(
                "NUMBER(1,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(CASE 1 WHEN 1 THEN 5 ELSE NULL::VARCHAR(10) END)");
            assertAnswer(
                "NUMBER(19,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(TRY_CAST(NULL AS VARCHAR) + 1)");
            assertAnswer(
                "NUMBER(19,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::NVARCHAR(10) + 1)");
            assertAnswer(
                "NUMBER(19,1)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) - 1.5)");
            assertAnswer(
                "NUMBER(24,6)[SB1]",
                "SELECT SYSTEM$TYPEOF(NULL::VARCHAR(10) / 3.0)");
            assertAnswer(
                "NUMBER(7,6)[SB1]",
                "SELECT SYSTEM$TYPEOF(2 / NULL::VARCHAR(10))");
            assertAnswer(
                "NUMBER(2,0)[SB1]",
                "SELECT SYSTEM$TYPEOF(7 % NULL::VARCHAR(10))");
            assertAnswer(
                "VARCHAR(134217728)[LOB]",
                "SELECT SYSTEM$TYPEOF(NULLIFZERO(NULL::VARCHAR(10)))");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
