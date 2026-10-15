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
package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The width a BINARY reads at. A column written bare is BINARY(8388608) and a cast or a function's answer is
 * unsized, while what a block or a procedure holds is at the type's maximum: a procedure's parameter written
 * bare, and a procedure's or a block's binary answer, whatever width RETURNS wrote, read BINARY(67108864) to
 * SYSTEM$TYPEOF and to DESCRIBE RESULT, which spells an unsized result column at that maximum too (live-verified).
 */
public class BinaryAnswerWidthTest extends MeasuredCellsTestSupport {

    @Test
    public void columnsCastsAndProcedures() {
        try {
            answer(
                "CREATE OR REPLACE TABLE wb (b BINARY, c BINARY(4), d VARBINARY, v VARCHAR)");
            answer(
                "INSERT INTO wb SELECT TO_BINARY('ABCD'), TO_BINARY('ABCD'), TO_BINARY('ABCD'), 'x'");
            answer(
                "CREATE OR REPLACE PROCEDURE wp(a BINARY) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                    + "SYSTEM$TYPEOF(a); END $$");
            answer(
                "CREATE OR REPLACE FUNCTION wf(a BINARY) RETURNS VARCHAR AS 'SYSTEM$TYPEOF(a)'");
            answer(
                "CREATE OR REPLACE PROCEDURE w9() RETURNS BINARY LANGUAGE SQL AS $$ BEGIN RETURN "
                    + "TO_BINARY('ABCD'); END; $$");
            answer(
                "CREATE OR REPLACE PROCEDURE w8() RETURNS BINARY(2) LANGUAGE SQL AS $$ BEGIN RETURN "
                    + "TO_BINARY('ABCD'); END; $$");
            answer(
                "CREATE OR REPLACE FUNCTION wg() RETURNS BINARY AS 'TO_BINARY(''ABCD'')'");
            assertAnswer(
                "BINARY(8388608)[LOB]|BINARY(4)[LOB]|BINARY(8388608)[LOB]",
                "SELECT SYSTEM$TYPEOF(b), SYSTEM$TYPEOF(c), SYSTEM$TYPEOF(d) FROM wb");
            assertAnswer(
                "BINARY[LOB]",
                "SELECT SYSTEM$TYPEOF(TO_BINARY('ABCD'))");
            assertAnswer(
                "BINARY[LOB]",
                "SELECT SYSTEM$TYPEOF('ABCD'::BINARY)");
            assertAnswer(
                "BINARY[LOB]",
                "SELECT SYSTEM$TYPEOF(CAST('ABCD' AS BINARY))");
            assertAnswer(
                "BINARY[LOB]",
                "SELECT SYSTEM$TYPEOF(CAST('ABCD' AS VARBINARY))");
            assertAnswer(
                "BINARY(4)[LOB]",
                "SELECT SYSTEM$TYPEOF(CAST('ABCD' AS BINARY(4)))");
            assertAnswer(
                "BINARY(67108864)[LOB]",
                "CALL wp(TO_BINARY('ABCD'))");
            assertAnswer(
                "BINARY[LOB]",
                "SELECT wf(TO_BINARY('ABCD'))");
            assertAnswer(
                "ABCD",
                "CALL w9()");
            assertAnswer(
                "BINARY(67108864)[LOB]",
                "SELECT SYSTEM$TYPEOF($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "ABCD",
                "CALL w9()");
            assertAnswer(
                "W9|BINARY(67108864)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
                "DESCRIBE RESULT LAST_QUERY_ID()");
            assertAnswer(
                "ABCD",
                "CALL w8()");
            assertAnswer(
                "BINARY(67108864)[LOB]",
                "SELECT SYSTEM$TYPEOF($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "BINARY[LOB]",
                "SELECT SYSTEM$TYPEOF(wg())");
            assertAnswer(
                "B|BINARY(8388608)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                    + "C|BINARY(4)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                    + "D|BINARY(8388608)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                    + "V|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
                "DESCRIBE TABLE wb");
            assertAnswer(
                "B|BINARY|null|null ;; C|BINARY|null|null ;; D|BINARY|null|null ;; V|TEXT|16777216|67108864",
                "SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, CHARACTER_OCTET_LENGTH FROM "
                    + "INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'WB' ORDER BY ORDINAL_POSITION");
            assertAnswer(
                "BINARY[LOB]",
                "SELECT SYSTEM$TYPEOF(a) FROM (SELECT TO_BINARY('AB') AS a UNION ALL SELECT b FROM wb) LIMIT 1");
            assertAnswer(
                "Table WB2 successfully created.",
                "CREATE OR REPLACE TABLE wb2 AS SELECT TO_BINARY('ABCD') AS x, 'ab'::BINARY AS y, b AS z, c AS q "
                    + "FROM wb");
            assertAnswer(
                "X|BINARY(8388608)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                    + "Y|BINARY(8388608)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                    + "Z|BINARY(8388608)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                    + "Q|BINARY(4)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
                "DESCRIBE TABLE wb2");
            assertAnswer(
                "BINARY(1)[LOB]",
                "SELECT SYSTEM$TYPEOF(HEX_DECODE_BINARY('41'))");
            assertAnswer(
                "BINARY(3)[LOB]",
                "SELECT SYSTEM$TYPEOF(BASE64_DECODE_BINARY('QQ=='))");
            assertAnswer(
                "BINARY(16)[LOB]",
                "SELECT SYSTEM$TYPEOF(MD5_BINARY('a'))");
            assertAnswer(
                "BINARY(64)[LOB]",
                "SELECT SYSTEM$TYPEOF(SHA2_BINARY('a'))");
            assertAnswer(
                "BINARY[LOB]",
                "SELECT SYSTEM$TYPEOF(TO_BINARY('a', 'UTF-8'))");
            assertAnswer(
                "BINARY[LOB]",
                "SELECT SYSTEM$TYPEOF(TRY_TO_BINARY('41'))");
            assertAnswer(
                "BINARY[LOB]",
                "SELECT SYSTEM$TYPEOF(NULL::BINARY)");
            assertAnswer(
                "VARCHAR[LOB]",
                "SELECT SYSTEM$TYPEOF('a'::VARCHAR)");
            assertAnswer(
                "BINARY(8388612)[LOB]",
                "SELECT SYSTEM$TYPEOF(b || c) FROM wb");
            assertAnswer(
                "BINARY(67108864)[LOB]",
                "SELECT SYSTEM$TYPEOF(COALESCE(b, TO_BINARY('AB'))) FROM wb");
            assertAnswer(
                "BINARY(8388608)[LOB]",
                "SELECT SYSTEM$TYPEOF(SUBSTR(b, 1, 1)) FROM wb");
            assertAnswer(
                "View WV successfully created.",
                "CREATE OR REPLACE VIEW wv AS SELECT b, c FROM wb");
            assertAnswer(
                "B|BINARY(8388608)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                    + "C|BINARY(4)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
                "DESCRIBE VIEW wv");
            assertAnswer(
                "",
                "SHOW PARAMETERS LIKE 'NOSUCH_PARAM_ZQ7'");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS wp(BINARY)");
            answer(
                "DROP FUNCTION IF EXISTS wf(BINARY)");
            answer(
                "DROP PROCEDURE IF EXISTS w9()");
            answer(
                "DROP PROCEDURE IF EXISTS w8()");
            answer(
                "DROP FUNCTION IF EXISTS wg()");
            answer(
                "DROP VIEW IF EXISTS wv");
        }
    }

    @Test
    public void answersAndResultColumns() {
        try {
            answer(
                "CREATE OR REPLACE TABLE wb (b BINARY, c BINARY(4))");
            answer(
                "INSERT INTO wb SELECT TO_BINARY('ABCD'), TO_BINARY('ABCD')");
            answer(
                "CREATE OR REPLACE PROCEDURE wp4(a BINARY(4)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                    + "SYSTEM$TYPEOF(a); END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE wpv(a VARBINARY) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                    + "SYSTEM$TYPEOF(a); END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE wr() RETURNS VARBINARY LANGUAGE SQL AS $$ BEGIN RETURN "
                    + "TO_BINARY('ABCD'); END; $$");
            answer(
                "CREATE OR REPLACE PROCEDURE wn() RETURNS BINARY LANGUAGE SQL AS $$ BEGIN RETURN NULL; END; $$");
            assertAnswer(
                "BINARY(4)[LOB]",
                "CALL wp4(TO_BINARY('ABCD'))");
            assertAnswer(
                "BINARY(67108864)[LOB]",
                "CALL wpv(TO_BINARY('ABCD'))");
            assertAnswer(
                "ABCD",
                "CALL wr()");
            assertAnswer(
                "BINARY(67108864)[LOB]",
                "SELECT SYSTEM$TYPEOF($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "ABCD",
                "CALL wr()");
            assertAnswer(
                "WR|BINARY(67108864)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
                "DESCRIBE RESULT LAST_QUERY_ID()");
            assertAnswer(
                "null",
                "CALL wn()");
            assertAnswer(
                "BINARY(67108864)[LOB]",
                "SELECT SYSTEM$TYPEOF($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "null",
                "CALL wn()");
            assertAnswer(
                "WN|BINARY(67108864)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
                "DESCRIBE RESULT LAST_QUERY_ID()");
            assertAnswer(
                "ABCD",
                "SELECT COALESCE(b, TO_BINARY('AB')) AS x FROM wb");
            assertAnswer(
                "X|BINARY(67108864)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
                "DESCRIBE RESULT LAST_QUERY_ID()");
            assertAnswer(
                "ABCD",
                "SELECT TO_BINARY('ABCD') AS x");
            assertAnswer(
                "X|BINARY(67108864)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
                "DESCRIBE RESULT LAST_QUERY_ID()");
            assertAnswer(
                "ABCD|ABCD",
                "SELECT b, c FROM wb");
            assertAnswer(
                "B|BINARY(8388608)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                    + "C|BINARY(4)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
                "DESCRIBE RESULT LAST_QUERY_ID()");
            assertAnswer(
                "ABCDABCD|AB",
                "SELECT b || c AS x, SUBSTR(b, 1, 1) AS y FROM wb");
            assertAnswer(
                "X|BINARY(8388612)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                    + "Y|BINARY(8388608)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
                "DESCRIBE RESULT LAST_QUERY_ID()");
            assertAnswer(
                "BINARY(67108864)[LOB]",
                "DECLARE v BINARY; BEGIN RETURN SYSTEM$TYPEOF(v); END;");
            assertAnswer(
                "BINARY(4)[LOB]",
                "DECLARE v BINARY(4); BEGIN RETURN SYSTEM$TYPEOF(v); END;");
            assertAnswer(
                "ABCD",
                "BEGIN LET v BINARY := TO_BINARY('ABCD'); RETURN v; END;");
            assertAnswer(
                "BINARY(67108864)[LOB]",
                "SELECT SYSTEM$TYPEOF($1) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
            assertAnswer(
                "41",
                "SELECT HEX_DECODE_BINARY(v) AS x FROM (SELECT '41' AS v)");
            assertAnswer(
                "X|BINARY(1)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
                "DESCRIBE RESULT LAST_QUERY_ID()");
            assertAnswer(
                "Table WC successfully created.",
                "CREATE OR REPLACE TABLE wc AS SELECT COALESCE(b, TO_BINARY('AB')) AS x FROM wb");
            assertAnswer(
                "X|BINARY(8388608)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
                "DESCRIBE TABLE wc");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS wp4(BINARY)");
            answer(
                "DROP PROCEDURE IF EXISTS wpv(VARBINARY)");
            answer(
                "DROP PROCEDURE IF EXISTS wr()");
            answer(
                "DROP PROCEDURE IF EXISTS wn()");
        }
    }
}
