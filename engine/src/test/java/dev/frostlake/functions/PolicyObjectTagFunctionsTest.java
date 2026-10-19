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
 * SYSTEM$GET_TAG_ON_CURRENT_TABLE and SYSTEM$GET_TAG_ON_CURRENT_COLUMN read a tag of what a policy protects.
 * Outside a policy's body the call is refused while its statement compiles — over a table, in a WHERE clause,
 * in a block and in a view or a routine that only stores it — each in its own sentence; the tag's name is
 * judged first, a column and a NULL being no constants and a number no string. The table's function is a
 * masking policy's and a row access policy's to call and the column's a masking policy's and a projection
 * policy's: any other kind of policy is refused as it is created, and so is a tag that is not there
 * (live-verified).
 */
public class PolicyObjectTagFunctionsTest extends MeasuredCellsTestSupport {

    @Test
    public void outsideAPolicy() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        answer(
            "CREATE OR REPLACE TAG tg2");
        assertAnswer(
            "REFUSED Invalid system$get_tag_on_current_table function invocation.It can only be invoked from a "
                + "masking policy/row access policy.",
            "SELECT SYSTEM$GET_TAG_ON_CURRENT_TABLE('tg2')");
        assertAnswer(
            "REFUSED Invalid system$get_tag_on_current_column function invocation. It can only be invoked from a "
                + "masking or projection policy.",
            "SELECT SYSTEM$GET_TAG_ON_CURRENT_COLUMN('tg2')");
        assertAnswer(
            "REFUSED Invalid system$get_tag_on_current_table function invocation.It can only be invoked from a "
                + "masking policy/row access policy.",
            "SELECT SYSTEM$GET_TAG_ON_CURRENT_TABLE('nosuch_tag')");
        assertAnswer(
            "REFUSED Invalid system$get_tag_on_current_column function invocation. It can only be invoked from a "
                + "masking or projection policy.",
            "SELECT SYSTEM$GET_TAG_ON_CURRENT_COLUMN('nosuch_tag')");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function SYSTEM$GET_TAG_ON_CURRENT_TABLE needs to be "
                + "constant, found 'null'",
            "SELECT SYSTEM$GET_TAG_ON_CURRENT_TABLE(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function SYSTEM$GET_TAG_ON_CURRENT_COLUMN needs to be "
                + "constant, found 'null'",
            "SELECT SYSTEM$GET_TAG_ON_CURRENT_COLUMN(NULL)");
        assertAnswer(
            "REFUSED Invalid system$get_tag_on_current_table function invocation.It can only be invoked from a "
                + "masking policy/row access policy.",
            "SELECT SYSTEM$GET_TAG_ON_CURRENT_TABLE('tg2') FROM t");
        assertAnswer(
            "REFUSED Invalid system$get_tag_on_current_column function invocation. It can only be invoked from a "
                + "masking or projection policy.",
            "SELECT a FROM t WHERE SYSTEM$GET_TAG_ON_CURRENT_COLUMN('tg2') IS NULL");
        assertAnswer(
            "REFUSED Invalid system$get_tag_on_current_table function invocation.It can only be invoked from a "
                + "masking policy/row access policy.",
            "BEGIN RETURN SYSTEM$GET_TAG_ON_CURRENT_TABLE('tg2'); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[SYSTEM$GET_TAG_ON_CURRENT_TABLE()], expected 1, got 0",
            "SELECT SYSTEM$GET_TAG_ON_CURRENT_TABLE()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[SYSTEM$GET_TAG_ON_CURRENT_COLUMN('tg2', 'x')] expected 1, got 2",
            "SELECT SYSTEM$GET_TAG_ON_CURRENT_COLUMN('tg2', 'x')");
    }

    @Test
    public void inAViewAndAFunction() {
        try {
            answer(
                "CREATE OR REPLACE TABLE t (a INT, b INT)");
            answer(
                "INSERT INTO t VALUES (1, 2), (3, 4)");
            answer(
                "CREATE OR REPLACE TAG tg2");
            assertAnswer(
                "REFUSED Invalid system$get_tag_on_current_table function invocation.It can only be invoked from "
                    + "a masking policy/row access policy.",
                "CREATE OR REPLACE VIEW vt AS SELECT SYSTEM$GET_TAG_ON_CURRENT_TABLE('tg2') AS x");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'VT' does not exist or not authorized. Your primary role "
                    + "<ROLE> must have at least one privilege granted on TABLE VT.",
                "SELECT * FROM vt");
            assertAnswer(
                "REFUSED Invalid system$get_tag_on_current_column function invocation. It can only be invoked "
                    + "from a masking or projection policy.",
                "CREATE OR REPLACE FUNCTION ft() RETURNS VARCHAR AS 'SYSTEM$GET_TAG_ON_CURRENT_COLUMN(''tg2'')'");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function FT.",
                "SELECT ft()");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS ft()");
            answer(
                "DROP VIEW IF EXISTS vt");
        }
    }

    @Test
    public void inABlock() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        answer(
            "CREATE OR REPLACE TAG tg2");
        assertAnswer(
            "REFUSED Invalid system$get_tag_on_current_table function invocation.It can only be invoked from a "
                + "masking policy/row access policy.",
            "BEGIN IF (FALSE) THEN RETURN SYSTEM$GET_TAG_ON_CURRENT_TABLE('tg2'); END IF; RETURN 1; END;");
        assertAnswer(
            "REFUSED Invalid system$get_tag_on_current_column function invocation. It can only be invoked from a "
                + "masking or projection policy.",
            "BEGIN RETURN SYSTEM$GET_TAG_ON_CURRENT_COLUMN('tg2'); EXCEPTION WHEN OTHER THEN RETURN 'caught'; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 0 to function SYSTEM$GET_TAG_ON_CURRENT_TABLE needs to be "
                + "constant, found 'null'",
            "BEGIN RETURN SYSTEM$GET_TAG_ON_CURRENT_TABLE(NULL); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 61 | Bind variable null not set.",
            "BEGIN LET n := 'tg2'; RETURN SYSTEM$GET_TAG_ON_CURRENT_TABLE(:n); END;");
        assertAnswer(
            "REFUSED Invalid system$get_tag_on_current_table function invocation.It can only be invoked from a "
                + "masking policy/row access policy.",
            "BEGIN LET r := (SELECT SYSTEM$GET_TAG_ON_CURRENT_TABLE('tg2')); RETURN r; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [N] for function 'SYSTEM$GET_TAG_ON_CURRENT_TABLE', "
                + "parameter 0: constant arguments expected",
            "BEGIN LET n := 'tg2'; RETURN SYSTEM$GET_TAG_ON_CURRENT_TABLE(n); END;");
    }

    @Test
    public void inARoutinesBodyAndAView() {
        try {
            answer(
                "CREATE OR REPLACE TABLE t (a INT, b INT)");
            answer(
                "INSERT INTO t VALUES (1, 2), (3, 4)");
            answer(
                "CREATE OR REPLACE TAG tg2");
            assertAnswer(
                "REFUSED Invalid system$get_tag_on_current_table function invocation.It can only be invoked from "
                    + "a masking policy/row access policy.",
                "CREATE OR REPLACE PROCEDURE pt2() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                    + "SYSTEM$GET_TAG_ON_CURRENT_TABLE(''tg2''); END'");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function PT2.",
                "CALL pt2()");
            assertAnswer(
                "REFUSED Invalid system$get_tag_on_current_table function invocation.It can only be invoked from "
                    + "a masking policy/row access policy.",
                "CREATE OR REPLACE FUNCTION ft3() RETURNS VARCHAR AS 'SELECT "
                    + "SYSTEM$GET_TAG_ON_CURRENT_TABLE(''tg2'')'");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$GET_TAG_ON_CURRENT_COLUMN needs "
                    + "to be constant, found 'null'",
                "CREATE OR REPLACE FUNCTION ft4() RETURNS VARCHAR AS "
                    + "'UPPER(SYSTEM$GET_TAG_ON_CURRENT_COLUMN(NULL))'");
            assertAnswer(
                "REFUSED Invalid system$get_tag_on_current_table function invocation.It can only be invoked from "
                    + "a masking policy/row access policy.",
                "CREATE OR REPLACE FUNCTION ft5() RETURNS VARCHAR AS "
                    + "'UPPER(SYSTEM$GET_TAG_ON_CURRENT_TABLE(''tg2''))'");
            assertAnswer(
                "REFUSED Invalid system$get_tag_on_current_column function invocation. It can only be invoked "
                    + "from a masking or projection policy.",
                "CREATE OR REPLACE VIEW vt2 AS SELECT SYSTEM$GET_TAG_ON_CURRENT_COLUMN('tg2') AS x FROM t");
            assertAnswer(
                "REFUSED Invalid system$get_tag_on_current_table function invocation.It can only be invoked from "
                    + "a masking policy/row access policy.",
                "CREATE OR REPLACE VIEW vt3 AS SELECT a FROM t WHERE SYSTEM$GET_TAG_ON_CURRENT_TABLE('tg2') IS "
                    + "NULL");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS pt2()");
            answer(
                "DROP FUNCTION IF EXISTS ft3()");
            answer(
                "DROP FUNCTION IF EXISTS ft4()");
            answer(
                "DROP FUNCTION IF EXISTS ft5()");
            answer(
                "DROP VIEW IF EXISTS vt2");
            answer(
                "DROP VIEW IF EXISTS vt3");
        }
    }

    @Test
    public void theCountAndTheKindOfTheArgument() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        answer(
            "CREATE OR REPLACE TAG tg2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[SYSTEM$GET_TAG_ON_CURRENT_TABLE('tg2', 'x')] expected 1, got 2",
            "SELECT SYSTEM$GET_TAG_ON_CURRENT_TABLE('tg2', 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[SYSTEM$GET_TAG_ON_CURRENT_COLUMN()], expected 1, got 0",
            "SELECT SYSTEM$GET_TAG_ON_CURRENT_COLUMN()");
        assertAnswer(
            "REFUSED SQL compilation error: | argument needs to be a string: 'SYSTEM$GET_TAG_ON_CURRENT_TABLE'",
            "SELECT SYSTEM$GET_TAG_ON_CURRENT_TABLE(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid value [A] for function 'SYSTEM$GET_TAG_ON_CURRENT_COLUMN', "
                + "parameter 0: constant arguments expected",
            "SELECT SYSTEM$GET_TAG_ON_CURRENT_COLUMN(a) FROM t");
        assertAnswer(
            "REFUSED Invalid system$get_tag_on_current_table function invocation.It can only be invoked from a "
                + "masking policy/row access policy.",
            "SELECT SYSTEM$GET_TAG_ON_CURRENT_TABLE(UPPER('tg2'))");
    }

    @Test
    public void aPolicyReadsTheTagOfWhatItProtects() {
        try {
            answer(
                "CREATE OR REPLACE TABLE t (a INT, b INT)");
            answer(
                "INSERT INTO t VALUES (1, 2), (3, 4)");
            answer(
                "CREATE OR REPLACE TAG tg2");
            assertAnswer(
                "Masking policy MP1 successfully created.",
                "CREATE OR REPLACE MASKING POLICY mp1 AS (v INT) RETURNS INT -> "
                    + "IFF(SYSTEM$GET_TAG_ON_CURRENT_COLUMN('tg2') = 'x', v, NULL)");
            assertAnswer(
                "Masking policy MP2 successfully created.",
                "CREATE OR REPLACE MASKING POLICY mp2 AS (v INT) RETURNS INT -> "
                    + "IFF(SYSTEM$GET_TAG_ON_CURRENT_TABLE('tg2') = 'x', v, NULL)");
            assertAnswer(
                "Row access policy 'RP1' is successfully created.",
                "CREATE OR REPLACE ROW ACCESS POLICY rp1 AS (v INT) RETURNS BOOLEAN -> "
                    + "SYSTEM$GET_TAG_ON_CURRENT_TABLE('tg2') = 'x'");
            assertAnswer(
                "REFUSED Invalid system$get_tag_on_current_column function invocation. It can only be invoked "
                    + "from a masking or projection policy.",
                "CREATE OR REPLACE ROW ACCESS POLICY rp2 AS (v INT) RETURNS BOOLEAN -> "
                    + "SYSTEM$GET_TAG_ON_CURRENT_COLUMN('tg2') = 'x'");
            assertAnswer(
                "Projection policy 'PP1' is successfully created.",
                "CREATE OR REPLACE PROJECTION POLICY pp1 AS () RETURNS PROJECTION_CONSTRAINT -> "
                    + "IFF(SYSTEM$GET_TAG_ON_CURRENT_COLUMN('tg2') = 'x', PROJECTION_CONSTRAINT(ALLOW => TRUE), "
                    + "PROJECTION_CONSTRAINT(ALLOW => FALSE))");
            assertAnswer(
                "REFUSED Invalid system$get_tag_on_current_table function invocation.It can only be invoked from "
                    + "a masking policy/row access policy.",
                "CREATE OR REPLACE PROJECTION POLICY pp2 AS () RETURNS PROJECTION_CONSTRAINT -> "
                    + "IFF(SYSTEM$GET_TAG_ON_CURRENT_TABLE('tg2') = 'x', PROJECTION_CONSTRAINT(ALLOW => TRUE), "
                    + "PROJECTION_CONSTRAINT(ALLOW => FALSE))");
            assertAnswer(
                "Table TM successfully created.",
                "CREATE OR REPLACE TABLE tm (a INT, b INT)");
            assertAnswer(
                "1",
                "INSERT INTO tm VALUES (1, 2)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE tm MODIFY COLUMN a SET MASKING POLICY mp1");
            assertAnswer(
                "null|2",
                "SELECT a, b FROM tm");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE tm MODIFY COLUMN a SET TAG tg2 = 'x'");
            assertAnswer(
                "1|2",
                "SELECT a, b FROM tm");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE tm MODIFY COLUMN a UNSET MASKING POLICY");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE tm MODIFY COLUMN a SET MASKING POLICY mp2");
            assertAnswer(
                "null|2",
                "SELECT a, b FROM tm");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE tm SET TAG tg2 = 'x'");
            assertAnswer(
                "1|2",
                "SELECT a, b FROM tm");
            assertAnswer(
                "Table TR successfully created.",
                "CREATE OR REPLACE TABLE tr (a INT)");
            assertAnswer(
                "1",
                "INSERT INTO tr VALUES (1)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE tr ADD ROW ACCESS POLICY rp1 ON (a)");
            assertAnswer(
                "0",
                "SELECT COUNT(*) FROM tr");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE tr SET TAG tg2 = 'x'");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM tr");
            assertAnswer(
                "Table TP successfully created.",
                "CREATE OR REPLACE TABLE tp (a INT, b INT)");
            assertAnswer(
                "1",
                "INSERT INTO tp VALUES (1, 2)");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE tp MODIFY COLUMN a SET PROJECTION POLICY pp1");
            assertAnswer(
                "REFUSED SQL compilation error: The following columns are restricted by a Projection Policy. "
                    + "Please remove them from the list of projected columns: |  |  A",
                "SELECT a FROM tp");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER TABLE tp MODIFY COLUMN a SET TAG tg2 = 'x'");
            answer(
                "SELECT a FROM tp");
        } finally {
            answer(
                "DROP TABLE IF EXISTS tm");
            answer(
                "DROP TABLE IF EXISTS tr");
            answer(
                "DROP TABLE IF EXISTS tp");
            answer(
                "DROP MASKING POLICY IF EXISTS mp1");
            answer(
                "DROP MASKING POLICY IF EXISTS mp2");
            answer(
                "DROP ROW ACCESS POLICY IF EXISTS rp1");
            answer(
                "DROP ROW ACCESS POLICY IF EXISTS rp2");
            answer(
                "DROP PROJECTION POLICY IF EXISTS pp1");
            answer(
                "DROP PROJECTION POLICY IF EXISTS pp2");
        }
    }

    @Test
    public void theKindsOfPolicyAndTheTagsTheyName() {
        try {
            answer(
                "CREATE OR REPLACE TABLE t (a INT, b INT)");
            answer(
                "INSERT INTO t VALUES (1, 2), (3, 4)");
            answer(
                "CREATE OR REPLACE TAG tg2");
            assertAnswer(
                "REFUSED Invalid system$get_tag_on_current_table function invocation.It can only be invoked from "
                    + "a masking policy/row access policy.",
                "CREATE OR REPLACE AGGREGATION POLICY ap1 AS () RETURNS AGGREGATION_CONSTRAINT -> "
                    + "IFF(SYSTEM$GET_TAG_ON_CURRENT_TABLE('tg2') = 'x', NO_AGGREGATION_CONSTRAINT(), "
                    + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 2))");
            assertAnswer(
                "REFUSED Invalid system$get_tag_on_current_column function invocation. It can only be invoked "
                    + "from a masking or projection policy.",
                "CREATE OR REPLACE AGGREGATION POLICY ap2 AS () RETURNS AGGREGATION_CONSTRAINT -> "
                    + "IFF(SYSTEM$GET_TAG_ON_CURRENT_COLUMN('tg2') = 'x', NO_AGGREGATION_CONSTRAINT(), "
                    + "AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 2))");
            assertAnswer(
                "Row access policy 'RP3' is successfully created.",
                "CREATE OR REPLACE ROW ACCESS POLICY rp3 AS (v INT) RETURNS BOOLEAN -> TRUE");
            assertAnswer(
                "REFUSED Invalid system$get_tag_on_current_column function invocation. It can only be invoked "
                    + "from a masking or projection policy.",
                "ALTER ROW ACCESS POLICY rp3 SET BODY -> SYSTEM$GET_TAG_ON_CURRENT_COLUMN('tg2') = 'x'");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER ROW ACCESS POLICY rp3 SET BODY -> SYSTEM$GET_TAG_ON_CURRENT_TABLE('tg2') = 'x'");
            assertAnswer(
                "Masking policy MP3 successfully created.",
                "CREATE OR REPLACE MASKING POLICY mp3 AS (v INT) RETURNS INT -> v");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER MASKING POLICY mp3 SET BODY -> IFF(SYSTEM$GET_TAG_ON_CURRENT_COLUMN('tg2') = 'x', v, NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: | argument 0 to function SYSTEM$GET_TAG_ON_CURRENT_COLUMN needs "
                    + "to be constant, found 'null'",
                "CREATE OR REPLACE MASKING POLICY mp4 AS (v INT) RETURNS INT -> "
                    + "IFF(SYSTEM$GET_TAG_ON_CURRENT_COLUMN(NULL) = 'x', v, NULL)");
            assertAnswer(
                "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
                "CREATE OR REPLACE MASKING POLICY mp5 AS (v INT) RETURNS INT -> "
                    + "IFF(SYSTEM$GET_TAG_ON_CURRENT_COLUMN('nosuch_tag') = 'x', v, NULL)");
        } finally {
            answer(
                "DROP AGGREGATION POLICY IF EXISTS ap1");
            answer(
                "DROP AGGREGATION POLICY IF EXISTS ap2");
            answer(
                "DROP ROW ACCESS POLICY IF EXISTS rp3");
            answer(
                "DROP MASKING POLICY IF EXISTS mp3");
            answer(
                "DROP MASKING POLICY IF EXISTS mp4");
            answer(
                "DROP MASKING POLICY IF EXISTS mp5");
        }
    }
}
