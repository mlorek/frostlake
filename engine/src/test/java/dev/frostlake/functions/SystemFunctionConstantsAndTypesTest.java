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
 * The SYSTEM$ functions that answer while the statement compiles refuse an argument that reads a row, each in
 * its own sentence, and each has a type of its own (live-verified).
 */
public class SystemFunctionConstantsAndTypesTest extends MeasuredCellsTestSupport {

    @Test
    public void theTypeOfEachCall() {
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
                "VARCHAR[LOB]",
                "SELECT SYSTEM$TYPEOF(SYSTEM$TYPEOF(1))");
            assertAnswer(
                "BOOLEAN[SB1]",
                "SELECT SYSTEM$TYPEOF(SYSTEM$STREAM_HAS_DATA('tsm'))");
            assertAnswer(
                "FLOAT[DOUBLE]",
                "SELECT SYSTEM$TYPEOF(SYSTEM$CLUSTERING_DEPTH('tsrc', '(i)'))");
            assertAnswer(
                "VARCHAR[LOB]",
                "SELECT SYSTEM$TYPEOF(SYSTEM$CLUSTERING_INFORMATION('tsrc', '(i)'))");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$STREAM_HAS_DATA needs to be "
                    + "constant, found 'TSRC.N'",
                "SELECT SYSTEM$STREAM_HAS_DATA(n) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$CLUSTERING_DEPTH needs to be "
                    + "constant, found 'TSRC.N'",
                "SELECT SYSTEM$CLUSTERING_DEPTH(n) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$LAST_CHANGE_COMMIT_TIME needs to "
                    + "be constant, found 'TSRC.N'",
                "SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME(n) FROM tsrc");
            assertAnswer(
                "false",
                "SELECT SYSTEM$STREAM_HAS_DATA('t' || 'sm')");
            assertAnswer(
                "FLOAT[DOUBLE]",
                "SELECT SYSTEM$TYPEOF(SYSTEM$CLUSTERING_DEPTH('tcl'))");
            assertAnswer(
                "VARCHAR[LOB]",
                "SELECT SYSTEM$TYPEOF(SYSTEM$CLUSTERING_INFORMATION('tcl'))");
            assertAnswer(
                "VARCHAR[LOB]",
                "SELECT SYSTEM$TYPEOF(SYSTEM$ALLOWLIST())");
            assertAnswer(
                "VARCHAR[LOB]",
                "SELECT SYSTEM$TYPEOF(SYSTEM$WHITELIST())");
            assertAnswer(
                "VARCHAR[LOB]",
                "SELECT SYSTEM$TYPEOF(SYSTEM$GET_SNOWFLAKE_PLATFORM_INFO())");
            assertAnswer(
                "VARCHAR[LOB]",
                "SELECT SYSTEM$TYPEOF(SYSTEM$CURRENT_USER_TASK_NAME())");
            assertAnswer(
                "VARCHAR[LOB]",
                "SELECT SYSTEM$TYPEOF(SYSTEM$WAIT(0))");
        } finally {
            answer(
                "UNSET tagname");
            answer(
                "UNSET tblname");
        }
    }

    @Test
    public void changeTimesStreamsAndWaits() {
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
                "true",
                "SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('tsrc') > 0");
            answer(
                "SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('tem') > 0");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$LAST_CHANGE_COMMIT_TIME needs to "
                    + "be constant, found 'UPPER(TSRC.N)'",
                "SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME(UPPER(n)) FROM tsrc");
            assertAnswer(
                "true",
                "SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME((SELECT 'tsrc')) > 0");
            assertAnswer(
                "true",
                "SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME($tblname) > 0");
            assertAnswer(
                "true",
                "SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('ts' || 'rc') > 0");
            assertAnswer(
                "null",
                "SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME(NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$LAST_CHANGE_COMMIT_TIME needs to "
                    + "be constant, found '1'",
                "SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME(1)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$STREAM_HAS_DATA needs to be "
                    + "constant, found 'UPPER(TSRC.N)'",
                "SELECT SYSTEM$STREAM_HAS_DATA(UPPER(n)) FROM tsrc");
            assertAnswer(
                "false",
                "SELECT SYSTEM$STREAM_HAS_DATA((SELECT 'tsm'))");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$STREAM_HAS_DATA needs to be "
                    + "constant, found 'TSRC.I'",
                "SELECT SYSTEM$STREAM_HAS_DATA(i) FROM tsrc");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$STREAM_HAS_DATA needs to be "
                    + "constant, found 'null'",
                "SELECT SYSTEM$STREAM_HAS_DATA(NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$STREAM_GET_TABLE_TIMESTAMP needs "
                    + "to be constant, found 'TSRC.N'",
                "SELECT SYSTEM$STREAM_GET_TABLE_TIMESTAMP(n) FROM tsrc");
            assertAnswer(
                "true",
                "SELECT SYSTEM$STREAM_GET_TABLE_TIMESTAMP('tsm') >= '0'");
            assertAnswer(
                "VARCHAR(16777216)[LOB] ;; VARCHAR(16777216)[LOB]",
                "SELECT SYSTEM$TYPEOF(n) FROM tsrc ORDER BY 1");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SqlIdentifier{qualifierNames=[], "
                    + "identifierName=SYSTEM$WAIT} needs to be constant, found 'TSRC.I'",
                "SELECT SYSTEM$WAIT(i) FROM tsrc");
            assertAnswer(
                "REFUSED Function SYSTEM$GET_TASK_GRAPH_CONFIG must be called from within a task.",
                "SELECT SYSTEM$GET_TASK_GRAPH_CONFIG()");
            assertAnswer(
                "",
                "SELECT SYSTEM$CURRENT_USER_TASK_NAME()");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$LAST_CHANGE_COMMIT_TIME needs to "
                    + "be constant, found 'CAST(1.5 AS NUMBER(38,0))'",
                "SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME(1.5)");
            assertAnswer(
                "REFUSED SQL compilation error: | Can not convert parameter 'TRUE' of type [BOOLEAN] into "
                    + "expected type [NUMBER(38,0)]",
                "SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME(TRUE)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'NOSUCH' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH.",
                "SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('nosuch')");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$LAST_CHANGE_COMMIT_TIME needs to "
                    + "be constant, found 'TSRC.N'",
                "SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME(n) FROM tsrc WHERE 1 = 0");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                    + "[SYSTEM$LAST_CHANGE_COMMIT_TIME('tsrc', 'x')] expected 1, got 2",
                "SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('tsrc', 'x')");
            assertAnswer(
                "true",
                "SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME('vcl') > 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [1] for function 'SYSTEM$STREAM_HAS_DATA', "
                    + "parameter 1: must be a valid stream name",
                "SELECT SYSTEM$STREAM_HAS_DATA(1)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$STREAM_HAS_DATA needs to be "
                    + "constant, found 'CAST(TRUE AS VARCHAR(134217728))'",
                "SELECT SYSTEM$STREAM_HAS_DATA(TRUE)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value ['nosuch'] for function 'SYSTEM$STREAM_HAS_DATA', "
                    + "parameter 1: must be a valid stream name",
                "SELECT SYSTEM$STREAM_HAS_DATA('nosuch')");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$STREAM_HAS_DATA needs to be "
                    + "constant, found 'null'",
                "SELECT SYSTEM$STREAM_HAS_DATA(NULL::VARCHAR)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$STREAM_HAS_DATA needs to be "
                    + "constant, found 'TSRC.N'",
                "SELECT SYSTEM$STREAM_HAS_DATA(n) FROM tsrc WHERE 1 = 0");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$STREAM_HAS_DATA needs to be "
                    + "constant, found 'TSRC.N'",
                "SELECT 1 FROM tsrc WHERE SYSTEM$STREAM_HAS_DATA(n)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$STREAM_HAS_DATA needs to be "
                    + "constant, found '(SELECT TSRC.N AS \"N\" FROM TSRC AS TSRC LIMIT 1 OFFSET 0)'",
                "SELECT SYSTEM$STREAM_HAS_DATA((SELECT n FROM tsrc LIMIT 1))");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$STREAM_GET_TABLE_TIMESTAMP needs "
                    + "to be constant, found 'null'",
                "SELECT SYSTEM$STREAM_GET_TABLE_TIMESTAMP(NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SYSTEM$STREAM_GET_TABLE_TIMESTAMP needs "
                    + "to be constant, found 'CAST(1 AS VARCHAR(134217728))'",
                "SELECT SYSTEM$STREAM_GET_TABLE_TIMESTAMP(1)");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value ['nosuch'] for function "
                    + "'SYSTEM$STREAM_GET_TABLE_TIMESTAMP', parameter 1: must be a valid stream name",
                "SELECT SYSTEM$STREAM_GET_TABLE_TIMESTAMP('nosuch')");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SqlIdentifier{qualifierNames=[], "
                    + "identifierName=SYSTEM$WAIT} needs to be constant, found 'TSRC.I + 1'",
                "SELECT SYSTEM$WAIT(i + 1) FROM tsrc WHERE 1 = 0");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 1 to function SqlIdentifier{qualifierNames=[], "
                    + "identifierName=SYSTEM$WAIT} needs to be constant, found 'TSRC.N'",
                "SELECT SYSTEM$WAIT(0, n) FROM tsrc");
            assertAnswer(
                "waited 0 seconds",
                "SELECT SYSTEM$WAIT((SELECT 0))");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SqlIdentifier{qualifierNames=[], "
                    + "identifierName=SYSTEM$WAIT} needs to be constant, found '(SELECT 0 AS \"0\" FROM TSRC AS TSRC "
                    + "LIMIT 1 OFFSET 0)'",
                "SELECT SYSTEM$WAIT((SELECT 0 FROM tsrc LIMIT 1))");
            assertAnswer(
                "waited 0 seconds ;; waited 0 seconds",
                "SELECT SYSTEM$WAIT(0) FROM tsrc");
        } finally {
            answer(
                "UNSET tagname");
            answer(
                "UNSET tblname");
        }
    }
}
