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
package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * SYSTEM$GET_TAG and SYSTEM$GET_TAG_ALLOWED_VALUES answer while the statement compiles, from constant
 * arguments alone: what each takes as a constant, how a refusal echoes the argument, where a view over the
 * call is created, and what the call's type is (live-verified).
 */
public class SystemTagConstantArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void theListRefusesWhatReadsARow() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tsrc (n VARCHAR, i INT)");
            answer(
                "INSERT INTO tsrc VALUES ('e1', 1), ('e9', 2)");
            answer(
                "CREATE OR REPLACE TAG e1 ALLOWED_VALUES 'a', 'b'");
            answer(
                "CREATE OR REPLACE TAG e9");
            answer(
                "CREATE OR REPLACE TABLE tt (a INT)");
            answer(
                "ALTER TABLE tt SET TAG e1 = 'a'");
            answer(
                "CREATE OR REPLACE STREAM tsm ON TABLE tsrc");
            answer(
                "CREATE OR REPLACE TABLE tcl (i INT, v VARCHAR) CLUSTER BY (i)");
            answer(
                "INSERT INTO tcl VALUES (1, 'a'), (2, 'b')");
            answer(
                "CREATE OR REPLACE TABLE tcl2 (i INT, v VARCHAR) CLUSTER BY LINEAR(i, v)");
            answer(
                "CREATE OR REPLACE TABLE tcl3 (i INT, v VARCHAR) CLUSTER BY (UPPER(v), i)");
            answer(
                "CREATE OR REPLACE VIEW vcl AS SELECT * FROM tcl");
            answer(
                "SET tagname = 'e1'");
            answer(
                "CREATE OR REPLACE TABLE tem (i INT)");
            answer(
                "SET tblname = 'tsrc'");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TSRC.N] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(n) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TSRC.N] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(n) FROM tsrc WHERE 1 = 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [UPPER(TSRC.N)] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(UPPER(n)) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TSRC.N] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(tsrc.n) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [T.N] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(t.n) FROM tsrc t");
            assertAnswer(
                "[\"a\",\"b\"]",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e' || '1')");
            assertAnswer(
                "[\"a\",\"b\"]",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(UPPER('e1'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value ['e' || TSRC.N] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e' || n) FROM tsrc");
            assertAnswer(
                "[\"a\",\"b\"]",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES((SELECT 'e1'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [(SELECT TSRC.N AS \"N\" FROM TSRC AS TSRC LIMIT "
                    + "1 OFFSET 0)] for function 'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments "
                    + "expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES((SELECT n FROM tsrc LIMIT 1))");
            assertAnswer(
                "[\"a\",\"b\"]",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(CURRENT_DATABASE() || '.test_schema.e1')");
            assertAnswer(
                "[\"a\",\"b\"]",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES($tagname)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TSRC.I] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(i) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(TSRC.I AS VARCHAR(134217728))] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(i::VARCHAR) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$GET_TAG_ALLOWED_VALUES needs to "
                    + "be constant, found 'null'",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$GET_TAG_ALLOWED_VALUES needs to "
                    + "be constant, found 'null'",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(NULL::VARCHAR)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument needs to be a string: 'SYSTEM$GET_TAG_ALLOWED_VALUES'",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(1)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument needs to be a string: 'SYSTEM$GET_TAG_ALLOWED_VALUES'",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(TRUE)");
            assertAnswer(
                "REFUSED SQL compilation error: | Tag '\"\"' does not exist or not authorized. Your primary role "
                    + "<ROLE> must have at least one privilege granted on TAG \"\".",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('')");
            assertAnswer(
                "REFUSED SQL compilation error: | Tag '\" \"' does not exist or not authorized. Your primary role "
                    + "<ROLE> must have at least one privilege granted on TAG \" \".",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(' ')");
            assertAnswer(
                "[\"a\",\"b\"]",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e1')");
            assertAnswer(
                "REFUSED SQL compilation error: | Tag '\"e1\"' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TAG \"e1\".",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('\"e1\"')");
            assertAnswer(
                "REFUSED SQL compilation error: | Tag 'NOSUCH' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TAG NOSUCH.",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('nosuch')");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('a.b.c.d')");
            assertAnswer(
                "REFUSED SQL compilation error: | Tag '\"A B\"' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TAG \"A B\".",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('a b')");
            assertAnswer(
                "REFUSED SQL compilation error: | Tag '\"1X\"' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TAG \"1X\".",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('1x')");
            assertAnswer(
                "REFUSED SQL compilation error: | Tag 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on TAG "
                    + "TEST_DB.TEST_SCHEMA.NOSUCH.",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('test_schema.nosuch')");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "TEST_DB.NOSUCH_SCHEMA.",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('nosuch_schema.e1')");
            assertAnswer(
                "VARCHAR[LOB]",
                "SELECT SYSTEM$TYPEOF(SYSTEM$GET_TAG_ALLOWED_VALUES('e1'))");
            assertAnswer(
                "VARCHAR[LOB]",
                "SELECT SYSTEM$TYPEOF(SYSTEM$GET_TAG_ALLOWED_VALUES('e9'))");
            assertShape(
                "C:VARCHAR(134217728)",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e1') AS c");
            assertShape(
                "C:VARCHAR(134217728)",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e9') AS c");
            assertAnswer(
                "[\"a\",\"b\"]x",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e1') || 'x'");
            assertAnswer(
                "9",
                "SELECT LENGTH(SYSTEM$GET_TAG_ALLOWED_VALUES('e1'))");
            assertAnswer(
                "[\"a\",\"b\"]|e1 ;; [\"a\",\"b\"]|e9",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e1'), n FROM tsrc ORDER BY n");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TSRC.N] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT n FROM tsrc WHERE SYSTEM$GET_TAG_ALLOWED_VALUES(n) IS NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [MAX(TSRC.N)] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(MAX(n)) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid function type [SYSTEM$GET_TAG_ALLOWED_VALUES] for "
                    + "window function.",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(n) OVER () FROM tsrc");
        } finally {
            answer(
                "UNSET tagname");
            answer(
                "UNSET tblname");
        }
    }

    @Test
    public void theLookupReadsItsArgumentsUnresolved() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tsrc (n VARCHAR, i INT)");
            answer(
                "INSERT INTO tsrc VALUES ('e1', 1), ('e9', 2)");
            answer(
                "CREATE OR REPLACE TAG e1 ALLOWED_VALUES 'a', 'b'");
            answer(
                "CREATE OR REPLACE TAG e9");
            answer(
                "CREATE OR REPLACE TABLE tt (a INT)");
            answer(
                "ALTER TABLE tt SET TAG e1 = 'a'");
            answer(
                "CREATE OR REPLACE STREAM tsm ON TABLE tsrc");
            answer(
                "CREATE OR REPLACE TABLE tcl (i INT, v VARCHAR) CLUSTER BY (i)");
            answer(
                "INSERT INTO tcl VALUES (1, 'a'), (2, 'b')");
            answer(
                "CREATE OR REPLACE TABLE tcl2 (i INT, v VARCHAR) CLUSTER BY LINEAR(i, v)");
            answer(
                "CREATE OR REPLACE TABLE tcl3 (i INT, v VARCHAR) CLUSTER BY (UPPER(v), i)");
            answer(
                "CREATE OR REPLACE VIEW vcl AS SELECT * FROM tcl");
            answer(
                "SET tagname = 'e1'");
            answer(
                "CREATE OR REPLACE TABLE tem (i INT)");
            answer(
                "SET tblname = 'tsrc'");
            assertAnswer(
                "a",
                "SELECT SYSTEM$GET_TAG('e1', 'tt', 'TABLE')");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                    + "constant arguments expected",
                "SELECT SYSTEM$GET_TAG(n, 'tt', 'TABLE') FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 1: "
                    + "constant arguments expected",
                "SELECT SYSTEM$GET_TAG('e1', n, 'TABLE') FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 2: "
                    + "constant arguments expected",
                "SELECT SYSTEM$GET_TAG('e1', 'tt', n) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                    + "constant arguments expected",
                "SELECT SYSTEM$GET_TAG(n, n, n) FROM tsrc");
            assertAnswer(
                "a ;; a",
                "SELECT SYSTEM$GET_TAG('e1', 'tt', 'TABLE') FROM tsrc");
            assertAnswer(
                "VARCHAR[LOB]",
                "SELECT SYSTEM$TYPEOF(SYSTEM$GET_TAG('e1', 'tt', 'TABLE'))");
            assertAnswer(
                "VARCHAR[LOB]",
                "SELECT SYSTEM$TYPEOF(SYSTEM$GET_TAG('e9', 'tt', 'TABLE'))");
            assertShape(
                "C:VARCHAR(134217728)",
                "SELECT SYSTEM$GET_TAG('e1', 'tt', 'TABLE') AS c");
            assertAnswer(
                "a",
                "SELECT SYSTEM$GET_TAG('e' || '1', 'tt', 'TABLE')");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$GET_TAG needs to be constant, "
                    + "found 'null'",
                "SELECT SYSTEM$GET_TAG(NULL, 'tt', 'TABLE')");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$GET_TAG needs to be constant, "
                    + "found 'null'",
                "SELECT SYSTEM$GET_TAG('e1', NULL, 'TABLE')");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 2 to function SYSTEM$GET_TAG needs to be constant, "
                    + "found 'null'",
                "SELECT SYSTEM$GET_TAG('e1', 'tt', NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument needs to be a string: 'SYSTEM$GET_TAG'",
                "SELECT SYSTEM$GET_TAG(1, 'tt', 'TABLE')");
            assertAnswer(
                "REFUSED SQL compilation error: | argument needs to be a string: 'SYSTEM$GET_TAG'",
                "SELECT SYSTEM$GET_TAG('e1', 1, 'TABLE')");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [(SELECT 'e1' AS \"'E1'\" FROM (VALUES (null)) "
                    + "DUAL)] for function 'SYSTEM$GET_TAG', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG((SELECT 'e1'), 'tt', 'TABLE')");
            assertAnswer(
                "a",
                "SELECT SYSTEM$GET_TAG($tagname, 'tt', 'TABLE')");
            assertAnswer(
                "ax",
                "SELECT SYSTEM$GET_TAG('e1', 'tt', 'TABLE') || 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'N'",
                "SELECT SYSTEM$GET_TAG(UPPER(n), 'tt', 'TABLE') FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TSRC.N] for function 'SYSTEM$GET_TAG', parameter "
                    + "0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG(tsrc.n, 'tt', 'TABLE') FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [T.N] for function 'SYSTEM$GET_TAG', parameter 0: "
                    + "constant arguments expected",
                "SELECT SYSTEM$GET_TAG(t.n, 'tt', 'TABLE') FROM tsrc t");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 29 | invalid identifier 'N'",
                "SELECT SYSTEM$GET_TAG('e' || n, 'tt', 'TABLE') FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 22 | invalid identifier 'I'",
                "SELECT SYSTEM$GET_TAG(i::VARCHAR, 'tt', 'TABLE') FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [I] for function 'SYSTEM$GET_TAG', parameter 0: "
                    + "constant arguments expected",
                "SELECT SYSTEM$GET_TAG(i, 'tt', 'TABLE') FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier 'N'",
                "SELECT SYSTEM$GET_TAG(MAX(n), 'tt', 'TABLE') FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [(SELECT N AS \"N\" FROM TSRC AS TSRC LIMIT 1 "
                    + "OFFSET 0)] for function 'SYSTEM$GET_TAG', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG((SELECT n FROM tsrc LIMIT 1), 'tt', 'TABLE')");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [(SELECT 'tt' AS \"'TT'\" FROM (VALUES (null)) "
                    + "DUAL)] for function 'SYSTEM$GET_TAG', parameter 1: constant arguments expected",
                "SELECT SYSTEM$GET_TAG('e1', (SELECT 'tt'), 'TABLE')");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [(SELECT 'TABLE' AS \"'TABLE'\" FROM (VALUES "
                    + "(null)) DUAL)] for function 'SYSTEM$GET_TAG', parameter 2: constant arguments expected",
                "SELECT SYSTEM$GET_TAG('e1', 'tt', (SELECT 'TABLE'))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | invalid identifier 'N'",
                "SELECT SYSTEM$GET_TAG('e1', UPPER(n), 'TABLE') FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | invalid identifier 'N'",
                "SELECT SYSTEM$GET_TAG('e1', 'tt', LOWER(n)) FROM tsrc");
            assertAnswer(
                "a",
                "SELECT SYSTEM$GET_TAG(UPPER('e1'), LOWER('TT'), 'TAB' || 'LE')");
            assertAnswer(
                "a",
                "SELECT SYSTEM$GET_TAG(CURRENT_DATABASE() || '.test_schema.e1', 'tt', 'TABLE')");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                    + "constant arguments expected",
                "SELECT n FROM tsrc WHERE SYSTEM$GET_TAG(n, 'tt', 'TABLE') IS NULL");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                    + "constant arguments expected",
                "SELECT SYSTEM$GET_TAG(n, 'tt', 'TABLE') FROM tsrc WHERE 1 = 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SELECT SYSTEM$GET_TAG('a.b.c.d', 'tt', 'TABLE')");
            assertAnswer(
                "REFUSED SQL compilation error: | Tag '\"\"' does not exist or not authorized. Your primary role "
                    + "<ROLE> must have at least one privilege granted on TAG \"\".",
                "SELECT SYSTEM$GET_TAG('', 'tt', 'TABLE')");
            answer(
                "SELECT SYSTEM$GET_TAG(n, 'tt', 'TABLE') OVER () FROM tsrc");
        } finally {
            answer(
                "UNSET tagname");
            answer(
                "UNSET tblname");
        }
    }

    @Test
    public void theListTakesOnlyWhatFoldsEarly() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tsrc (n VARCHAR, i INT)");
            answer(
                "INSERT INTO tsrc VALUES ('e1', 1), ('e9', 2)");
            answer(
                "CREATE OR REPLACE TAG e1 ALLOWED_VALUES 'a', 'b'");
            answer(
                "CREATE OR REPLACE TAG e9");
            answer(
                "CREATE OR REPLACE TABLE tt (a INT)");
            answer(
                "ALTER TABLE tt SET TAG e1 = 'a'");
            answer(
                "CREATE OR REPLACE STREAM tsm ON TABLE tsrc");
            answer(
                "CREATE OR REPLACE TABLE tcl (i INT, v VARCHAR) CLUSTER BY (i)");
            answer(
                "INSERT INTO tcl VALUES (1, 'a'), (2, 'b')");
            answer(
                "CREATE OR REPLACE TABLE tcl2 (i INT, v VARCHAR) CLUSTER BY LINEAR(i, v)");
            answer(
                "CREATE OR REPLACE TABLE tcl3 (i INT, v VARCHAR) CLUSTER BY (UPPER(v), i)");
            answer(
                "CREATE OR REPLACE VIEW vcl AS SELECT * FROM tcl");
            answer(
                "SET tagname = 'e1'");
            answer(
                "CREATE OR REPLACE TABLE tem (i INT)");
            answer(
                "SET tblname = 'tsrc'");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(TSRC.N AS VARCHAR(10))] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(n::VARCHAR(10)) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TRIM(TSRC.N)] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(TRIM(n)) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CASE_FLATTENED(TSRC.I = 1, TSRC.N, null)] for "
                    + "function 'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(CASE WHEN i = 1 THEN n END) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [IFF(TSRC.I = 1, TSRC.N, TSRC.N)] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(IFF(i = 1, n, n)) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TSRC.N || TSRC.N] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(n || n) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TSRC.I + 1] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(i + 1) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [IFNULL(TSRC.N, 'e1')] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(COALESCE(n, 'e1')) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TSRC.N] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(\"N\") FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [X.N] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(x.n) FROM (SELECT n FROM tsrc) x");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [\"values\".N] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(n) FROM (SELECT 'e1' AS n)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [V.C] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(c) FROM (VALUES ('e1')) v(c)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [(SELECT MAX(TSRC.N) AS \"MAX(N)\" FROM TSRC AS "
                    + "TSRC)] for function 'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES((SELECT MAX(n) FROM tsrc))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [(SELECT 'e1' AS \"'E1'\" FROM TSRC AS TSRC LIMIT "
                    + "1 OFFSET 0)] for function 'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments "
                    + "expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES((SELECT 'e1' FROM tsrc LIMIT 1))");
            assertAnswer(
                "[\"a\",\"b\"]",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES((SELECT $tagname))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value ['e1'] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e1'::VARCHAR(2))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value ['e1'] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(CAST('e1' AS VARCHAR))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value ['e1'] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(TO_VARCHAR('e1'))");
            assertAnswer(
                "[\"a\",\"b\"]",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(CURRENT_SCHEMA() || '.e1')");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                    + "[SYSTEM$GET_TAG_ALLOWED_VALUES('e1', 'x')] expected 1, got 2",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e1', 'x')");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                    + "[SYSTEM$GET_TAG_ALLOWED_VALUES()], expected 1, got 0",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES()");
            assertAnswer(
                "REFUSED SQL compilation error: | argument needs to be a string: 'SYSTEM$GET_TAG_ALLOWED_VALUES'",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(1.5)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument needs to be a string: 'SYSTEM$GET_TAG_ALLOWED_VALUES'",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(CURRENT_DATE)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$GET_TAG_ALLOWED_VALUES needs to "
                    + "be constant, found 'null'",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(NULL || 'e1')");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TSRC.N] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(n) FROM tsrc GROUP BY n");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TSRC.N] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT COUNT(*) FROM tsrc GROUP BY SYSTEM$GET_TAG_ALLOWED_VALUES(n)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TSRC.N] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT 1 FROM tsrc ORDER BY SYSTEM$GET_TAG_ALLOWED_VALUES(n)");
            assertAnswer(
                "[\"a\",\"b\"]",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e1') FROM tsrc GROUP BY 1");
            assertAnswer(
                "[\"a\",\"b\"]",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('test_db.test_schema.e1')");
            assertAnswer(
                "[\"a\",\"b\"]",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('TEST_SCHEMA.E1')");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('nosuch_db.test_schema.e1')");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('a.b.c.d.e')");
            assertAnswer(
                "REFUSED SQL compilation error: | Tag 'TT' does not exist or not authorized. Your primary role "
                    + "<ROLE> must have at least one privilege granted on TAG TT.",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('tt')");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(1 AS VARCHAR(134217728))] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(1::VARCHAR)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CAST(1 AS VARCHAR(134217728))] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(TO_VARCHAR(1))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value ['e1'] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e1'::VARCHAR)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value ['e1'] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(TRY_CAST('e1' AS VARCHAR))");
            assertAnswer(
                "[\"a\",\"b\"]",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(('e1'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value ['e1'] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e1'::TEXT)");
            assertAnswer(
                "[\"a\",\"b\"]",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(CONCAT('e', '1'))");
            assertAnswer(
                "[\"a\",\"b\"]",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(LOWER('E1'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value ['e1'] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(TO_CHAR('e1'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [NVL('e1', 'x')] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(NVL('e1', 'x'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [IFF(CAST(TRUE AS BOOLEAN), 'e1', 'e9')] for "
                    + "function 'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(IFF(TRUE, 'e1', 'e9'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CASE_FLATTENED(CAST(TRUE AS BOOLEAN), 'e1', "
                    + "null)] for function 'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(CASE WHEN TRUE THEN 'e1' END)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [SUBSTR('e1x', 1, 2)] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(SUBSTR('e1x', 1, 2))");
            assertAnswer(
                "REFUSED SQL compilation error: | Tag 'TEST_SCHEMA' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have at least one privilege granted on TAG TEST_SCHEMA.",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(CURRENT_SCHEMA())");
            assertAnswer(
                "[\"a\",\"b\"]",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES($tagname || '')");
            assertAnswer(
                "[\"a\",\"b\"]",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(GETVARIABLE('TAGNAME'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [UPPER('e1')] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(UPPER('e1'::VARCHAR))");
            answer(
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e1'::VARCHAR || '')");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [UPPER('e1')] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(UPPER(TO_VARCHAR('e1')))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value ['e' || (CAST(1 AS VARCHAR(134217728)))] for "
                    + "function 'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e' || 1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TRIM(' e1 ')] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(TRIM(' e1 '))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [(SELECT MAX('e1') AS \"MAX('E1')\" FROM (VALUES "
                    + "(null)) DUAL)] for function 'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments "
                    + "expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES((SELECT MAX('e1')))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [(SELECT 'e1' AS \"'E1'\" FROM (VALUES (null)) "
                    + "DUAL WHERE 1 = 1)] for function 'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant "
                    + "arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES((SELECT 'e1' WHERE 1 = 1))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [(SELECT SET_COMBINE(WB$0.\"'E1'\", "
                    + "WB$1.\"'E1'\") AS \"'E1'\" FROM (SELECT OB$0.\"'E1'\" AS \"'E1'\" FROM (SELECT 'e1' AS "
                    + "\"'E1'\" FROM (VALUES (null)) DUAL) OB$0) WB$0 UNION ALL (SELECT OB$1.\"'E1'\" AS \"'E1'\" "
                    + "FROM (SELECT 'e1' AS \"'E1'\" FROM (VALUES (null)) DUAL) OB$1) WB$1 LIMIT 1 OFFSET 0)] for "
                    + "function 'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES((SELECT 'e1' UNION ALL SELECT 'e1' LIMIT 1))");
            answer(
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(CURRENT_TIMESTAMP()::VARCHAR)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value ['e1'] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e1'::CHAR(2))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value ['e1'] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e1'::STRING)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [$TAGNAME] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(CAST($tagname AS VARCHAR))");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [$TAGNAME] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES($tagname::VARCHAR(2))");
        } finally {
            answer(
                "UNSET tagname");
            answer(
                "UNSET tblname");
        }
    }

    @Test
    public void aViewOverTheListIsCreatedAndItsReaderRefused() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tsrc (n VARCHAR, i INT)");
            answer(
                "INSERT INTO tsrc VALUES ('e1', 1), ('e9', 2)");
            answer(
                "CREATE OR REPLACE TAG e1 ALLOWED_VALUES 'a', 'b'");
            answer(
                "CREATE OR REPLACE TAG e9");
            answer(
                "CREATE OR REPLACE TABLE tt (a INT)");
            answer(
                "ALTER TABLE tt SET TAG e1 = 'a'");
            answer(
                "CREATE OR REPLACE STREAM tsm ON TABLE tsrc");
            answer(
                "CREATE OR REPLACE TABLE tcl (i INT, v VARCHAR) CLUSTER BY (i)");
            answer(
                "INSERT INTO tcl VALUES (1, 'a'), (2, 'b')");
            answer(
                "CREATE OR REPLACE TABLE tcl2 (i INT, v VARCHAR) CLUSTER BY LINEAR(i, v)");
            answer(
                "CREATE OR REPLACE TABLE tcl3 (i INT, v VARCHAR) CLUSTER BY (UPPER(v), i)");
            answer(
                "CREATE OR REPLACE VIEW vcl AS SELECT * FROM tcl");
            answer(
                "SET tagname = 'e1'");
            answer(
                "CREATE OR REPLACE TABLE tem (i INT)");
            answer(
                "SET tblname = 'tsrc'");
            assertAnswer(
                "View VG successfully created.",
                "CREATE OR REPLACE VIEW vg AS SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(n) AS c FROM tsrc");
            assertAnswer(
                "View VG successfully created.",
                "CREATE OR REPLACE VIEW vg AS SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e1') AS c");
            assertAnswer(
                "[\"a\",\"b\"]",
                "SELECT * FROM vg");
            assertShape(
                "C:VARCHAR(134217728)",
                "SELECT * FROM vg");
            assertAnswer(
                "Table TG successfully created.",
                "CREATE OR REPLACE TABLE tg AS SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e1') AS c");
            assertShape(
                "C:VARCHAR(16777216)",
                "SELECT * FROM tg");
            assertAnswer(
                "TEXT|16777216",
                "SELECT data_type, character_maximum_length FROM information_schema.columns WHERE table_name = "
                    + "'TG'");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                    + "constant arguments expected",
                "CREATE OR REPLACE VIEW vg2 AS SELECT SYSTEM$GET_TAG(n, 'tt', 'TABLE') AS c FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'VG2' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE VG2.",
                "SELECT * FROM vg2");
            assertAnswer(
                "View VG3 successfully created.",
                "CREATE OR REPLACE VIEW vg3 AS SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(n) AS c FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TSRC.N] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT * FROM vg3");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [TSRC.N] for function "
                    + "'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected",
                "SELECT COUNT(*) FROM vg3");
            assertAnswer(
                "View VG4 successfully created.",
                "CREATE OR REPLACE VIEW vg4 AS SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('nosuch') AS c");
            assertAnswer(
                "REFUSED SQL compilation error: | Tag 'NOSUCH' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TAG NOSUCH.",
                "SELECT * FROM vg4");
            assertAnswer(
                "REFUSED SQL compilation error: | Tag 'NOSUCH' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TAG NOSUCH.",
                "CREATE OR REPLACE VIEW vg5 AS SELECT SYSTEM$GET_TAG('nosuch', 'tt', 'TABLE') AS c");
            assertAnswer(
                "REFUSED SQL compilation error: | Tag 'NOSUCH' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TAG NOSUCH.",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('nosuch') FROM tsrc WHERE 1 = 0");
            assertAnswer(
                "",
                "SELECT SYSTEM$GET_TAG_ALLOWED_VALUES('e1') FROM tsrc WHERE 1 = 0");
        } finally {
            answer(
                "UNSET tagname");
            answer(
                "UNSET tblname");
        }
    }

    @Test
    public void theLookupNamesAColumnAsWritten() {
        try {
            answer(
                "CREATE OR REPLACE TABLE tsrc (n VARCHAR, i INT)");
            answer(
                "INSERT INTO tsrc VALUES ('e1', 1), ('e9', 2)");
            answer(
                "CREATE OR REPLACE TAG e1 ALLOWED_VALUES 'a', 'b'");
            answer(
                "CREATE OR REPLACE TAG e9");
            answer(
                "CREATE OR REPLACE TABLE tt (a INT)");
            answer(
                "ALTER TABLE tt SET TAG e1 = 'a'");
            answer(
                "CREATE OR REPLACE STREAM tsm ON TABLE tsrc");
            answer(
                "CREATE OR REPLACE TABLE tcl (i INT, v VARCHAR) CLUSTER BY (i)");
            answer(
                "INSERT INTO tcl VALUES (1, 'a'), (2, 'b')");
            answer(
                "CREATE OR REPLACE TABLE tcl2 (i INT, v VARCHAR) CLUSTER BY LINEAR(i, v)");
            answer(
                "CREATE OR REPLACE TABLE tcl3 (i INT, v VARCHAR) CLUSTER BY (UPPER(v), i)");
            answer(
                "CREATE OR REPLACE VIEW vcl AS SELECT * FROM tcl");
            answer(
                "SET tagname = 'e1'");
            answer(
                "CREATE OR REPLACE TABLE tem (i INT)");
            answer(
                "SET tblname = 'tsrc'");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'TSRC.N'",
                "SELECT SYSTEM$GET_TAG(UPPER(tsrc.n), 'tt', 'TABLE') FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: "
                    + "constant arguments expected",
                "SELECT SYSTEM$GET_TAG(\"N\", 'tt', 'TABLE') FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [\"n\"] for function 'SYSTEM$GET_TAG', parameter "
                    + "0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG(\"n\", 'tt', 'TABLE') FROM tsrc");
            assertAnswer(
                "a",
                "SELECT SYSTEM$GET_TAG('e1'::VARCHAR, 'tt', 'TABLE')");
            assertAnswer(
                "a",
                "SELECT SYSTEM$GET_TAG(TO_VARCHAR('e1'), 'tt', 'TABLE')");
            assertAnswer(
                "a",
                "SELECT SYSTEM$GET_TAG(('e1'), 'tt', 'TABLE')");
            assertAnswer(
                "a",
                "SELECT SYSTEM$GET_TAG(UPPER((SELECT 'e1')), 'tt', 'TABLE')");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid function type [SYSTEM$GET_TAG] for window function.",
                "SELECT SYSTEM$GET_TAG('e1', 'tt', 'TABLE') OVER ()");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [NOSUCHCOL] for function 'SYSTEM$GET_TAG', "
                    + "parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG(nosuchcol, 'tt', 'TABLE') FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [NOSUCHCOL] for function 'SYSTEM$GET_TAG', "
                    + "parameter 0: constant arguments expected",
                "SELECT SYSTEM$GET_TAG(nosuchcol, 'tt', 'TABLE')");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier 'I'",
                "SELECT SYSTEM$GET_TAG(1 + i, 'tt', 'TABLE') FROM tsrc");
            answer(
                "SELECT SYSTEM$GET_TAG(n, 'tt') FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'I'",
                "SELECT SYSTEM$GET_TAG(-i, 'tt', 'TABLE') FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 32 | invalid identifier 'I'",
                "SELECT SYSTEM$GET_TAG(CASE WHEN i = 1 THEN n END, 'tt', 'TABLE') FROM tsrc");
        } finally {
            answer(
                "UNSET tagname");
            answer(
                "UNSET tblname");
        }
    }
}
