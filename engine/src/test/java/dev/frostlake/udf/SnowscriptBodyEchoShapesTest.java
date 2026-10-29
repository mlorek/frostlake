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
 * The echo of a query in the expression of a Snowscript UDF's body the account refuses (live-verified): an ORDER BY or
 * a row limit after brackets around the query, PIVOT, UNPIVOT, time travel, an ASOF join, CONNECT BY, a stage, LATERAL
 * FLATTEN and TABLE(FLATTEN(...)) with the argument's name as written, {*}, a star among a call's arguments; a text row
 * count and a word before a string make the block read as the expression it frames.
 */
public class SnowscriptBodyEchoShapesTest extends MeasuredCellsTestSupport {

    /** Each shape of query, as the refusal echoes it. */
    @Test
    public void echoShapes() {
        engine.execute("CREATE OR REPLACE TABLE rc_t (a INT, b INT, c INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(((SELECT a FROM rc_t) "
                + "ORDER BY a)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T  ORDER "
                + "BY A ASC NULLS LAST))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(((SELECT a FROM rc_t) "
                + "LIMIT 1)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T LIMIT "
                + "1 OFFSET 0))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t PIVOT "
                + "(SUM(b) FOR a IN (1, 2)))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT * FROM RC_T AS RC_T) "
                + "RC_T))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t "
                + "UNPIVOT (v FOR k IN (b, c)))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM SELECT * FROM RC_T AS RC_T "
                + "UNPIVOT (V FOR K IN (B, C)))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t AT "
                + "(OFFSET => -1))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AT(OFFSET => "
                + "-1) AS RC_T))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t BEFORE "
                + "(STATEMENT => 'x'))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T BEFORE(JOBID "
                + "=> 'x') AS RC_T))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT x.a FROM rc_t x "
                + "ASOF JOIN rc_t y MATCH_CONDITION (x.a >= y.a))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT X.A AS \"X.A\" FROM RC_T AS X ASOF "
                + "JOIN RC_T AS Y MATCH_CONDITION (\">=\"(X.A, Y.A))))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t START "
                + "WITH a = 1 CONNECT BY PRIOR a = b)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT A AS \"A\" FROM RC_T "
                + "AS RC_T) values))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT $1 FROM @st791)); "
                + "END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT $1 AS \"$1\" FROM TABLE (STAGE(1 => "
                + "'@st791'))))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t, "
                + "LATERAL FLATTEN(input => [1]))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM RC_T AS RC_T, TABLE "
                + "(FLATTEN(input => ARRAY_CONSTRUCT(1)))))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT value FROM "
                + "TABLE(FLATTEN(input => [1])))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT VALUE AS \"VALUE\" FROM TABLE "
                + "(FLATTEN(input => ARRAY_CONSTRUCT(1)))))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT {*} FROM rc_t)); "
                + "END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT OBJECT_CONSTRUCT(*)(*) AS \"{*}\" "
                + "FROM RC_T AS RC_T))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t LIMIT "
                + "'1')); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected '('."},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t LIMIT "
                + "1 OFFSET 2)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T LIMIT "
                + "1 OFFSET 2))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(*, (SELECT 1)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(*, (SELECT 1 AS \"1\" FROM (VALUES (null)) "
                + "DUAL))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(rc_t.*, (SELECT 1)); END "
                + "$$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(RC_T.*, (SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(LISTAGG(a) WITHIN GROUP "
                + "(ORDER BY a), (SELECT 1)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(LISTAGG(A), (SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM "
                + "test_db.test_schema.rc_t)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM "
                + "TEST_DB.TEST_SCHEMA.RC_T AS RC_T))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t ORDER "
                + "BY a DESC NULLS LAST LIMIT 2)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T  ORDER "
                + "BY A DESC NULLS LAST LIMIT 2 OFFSET 0))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t "
                + "QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T "
                + "QUALIFY \"=\"(ROW_NUMBER() OVER (ORDER BY A ASC NULLS LAST), 1)))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t SAMPLE "
                + "(10))); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T))"},
            {"CREATE OR REPLACE FUNCTION ee791(a INT) RETURNS INT AS $$ BEGIN RETURN foo 'x'; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'."},
        });
    }
}
