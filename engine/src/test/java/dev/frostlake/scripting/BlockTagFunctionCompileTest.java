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
package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * SYSTEM$GET_TAG in a block's own expression is answered while the block compiles: a tag, an object or a
 * domain that is missing is the block's refusal — bare, on a branch that never runs, in a handler's statement
 * and past every handler — in a RETURN, a LET, a condition, a DECLARE default and a query such an expression
 * reads alike. A :name among the arguments is a bind nothing set, refused where it stands; a variable written
 * by its bare name leaves the call to run with its value, and inside an argument has no value yet. A
 * procedure and a function whose body holds such a call are refused as they are created (live-verified).
 */
public class BlockTagFunctionCompileTest extends MeasuredCellsTestSupport {

    @Test
    public void aMissingTagIsTheBlocksRefusal() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        answer(
            "CREATE OR REPLACE TAG tg2");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "BEGIN RETURN SYSTEM$GET_TAG('nosuch_tag', 't', 'table'); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "BEGIN LET x := SYSTEM$GET_TAG('nosuch_tag', 't', 'table'); RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "BEGIN IF (FALSE) THEN RETURN SYSTEM$GET_TAG('nosuch_tag', 't', 'table'); END IF; RETURN 1; END;");
        assertAnswer(
            "null",
            "BEGIN RETURN SYSTEM$GET_TAG('tg2', 't', 'table'); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'NOSUCH_TABLE' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_TABLE.",
            "BEGIN RETURN SYSTEM$GET_TAG('tg2', 'nosuch_table', 'table'); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "BEGIN RETURN SYSTEM$GET_TAG('nosuch_tag', 't', 'table'); EXCEPTION WHEN OTHER THEN RETURN 'caught'; "
                + "END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN RETURN SYSTEM$GET_TAG('nosuch_tag', 't', 'table'); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 56 | Bind variable null not set.",
            "BEGIN LET n := 't'; RETURN SYSTEM$GET_TAG('nosuch_tag', :n, 'table'); END;");
        assertAnswer(
            "REFUSED Unknown domain: nosuch_domain.",
            "BEGIN RETURN SYSTEM$GET_TAG('tg2', 't', 'nosuch_domain'); END;");
        assertAnswer(
            "null",
            "BEGIN RETURN SYSTEM$GET_TAG('tg2', 't.a', 'column'); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Column 'TEST_DB.TEST_SCHEMA.T.NOSUCH' does not exist or not "
                + "authorized.",
            "BEGIN RETURN SYSTEM$GET_TAG('tg2', 't.nosuch', 'column'); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "SELECT SYSTEM$GET_TAG('nosuch_tag', 't', 'table')");
        assertAnswer(
            "null",
            "SELECT SYSTEM$GET_TAG('tg2', 't', 'table')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary role <ROLE> must have at "
                + "least one privilege granted on TAG NOSUCH_TAG.",
            "BEGIN RETURN SYSTEM$GET_TAG_ALLOWED_VALUES('nosuch_tag'); END;");
    }

    @Test
    public void whereverTheExpressionStands() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        answer(
            "CREATE OR REPLACE TAG tg2");
        assertAnswer(
            "null",
            "BEGIN LET n := 't'; RETURN SYSTEM$GET_TAG('tg2', n, 'table'); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 49 | Bind variable null not set.",
            "BEGIN LET n := 't'; RETURN SYSTEM$GET_TAG('tg2', :n, 'table'); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 44 | Bind variable null not set.",
            "BEGIN LET n := 'tg2'; RETURN SYSTEM$GET_TAG(:n, 't', 'table'); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 55 | Bind variable null not set.",
            "BEGIN LET n := 't'; RETURN SYSTEM$GET_TAG('tg2', UPPER(:n), 'table'); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "BEGIN RETURN SYSTEM$GET_TAG('nosuch_tag', UPPER('t'), 'table'); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "BEGIN RETURN SYSTEM$GET_TAG('nosuch_tag', 't', 'table') || 'x'; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "BEGIN RETURN UPPER(SYSTEM$GET_TAG('nosuch_tag', 't', 'table')); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "BEGIN LET r := (SELECT SYSTEM$GET_TAG('nosuch_tag', 't', 'table')); RETURN r; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "BEGIN IF (FALSE) THEN LET r := (SELECT SYSTEM$GET_TAG('nosuch_tag', 't', 'table')); END IF; RETURN "
                + "1; END;");
        assertAnswer(
            "1",
            "BEGIN IF (FALSE) THEN SELECT SYSTEM$GET_TAG('nosuch_tag', 't', 'table'); END IF; RETURN 1; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                + "error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary role <ROLE> must have at "
                + "least one privilege granted on TAG NOSUCH_TAG.",
            "BEGIN SELECT SYSTEM$GET_TAG('nosuch_tag', 't', 'table'); RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "BEGIN IF (SYSTEM$GET_TAG('nosuch_tag', 't', 'table') IS NULL) THEN RETURN 1; END IF; RETURN 2; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "BEGIN WHILE (FALSE) DO LET x := SYSTEM$GET_TAG('nosuch_tag', 't', 'table'); END WHILE; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "DECLARE x VARCHAR DEFAULT SYSTEM$GET_TAG('nosuch_tag', 't', 'table'); BEGIN RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "BEGIN BEGIN RETURN SYSTEM$GET_TAG('nosuch_tag', 't', 'table'); END; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
            "BEGIN LET x VARCHAR := 'a'; x := SYSTEM$GET_TAG('nosuch_tag', 't', 'table'); RETURN x; END;");
    }

    @Test
    public void aVariableForAnArgument() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        answer(
            "CREATE OR REPLACE TAG tg2");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$GET_TAG needs to be constant, found "
                + "'null'",
            "BEGIN LET n := 't'; RETURN SYSTEM$GET_TAG('tg2', UPPER(n), 'table'); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 |  variable 'R' cannot have its type "
                + "inferred from initializer",
            "BEGIN LET n := 't'; LET r := (SELECT SYSTEM$GET_TAG('tg2', :n, 'table')); RETURN r; END;");
        assertAnswer(
            "1",
            "BEGIN LET n := 't'; LET x := 1; IF (x = 2) THEN RETURN SYSTEM$GET_TAG('nosuch_tag', n, 'table'); END "
                + "IF; RETURN 1; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 27 : SQL compilation "
                + "error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary role <ROLE> must have at "
                + "least one privilege granted on TAG NOSUCH_TAG.",
            "BEGIN LET n := 't'; RETURN SYSTEM$GET_TAG('nosuch_tag', n, 'table'); END;");
        assertAnswer(
            "caught",
            "BEGIN LET n := 't'; RETURN SYSTEM$GET_TAG('nosuch_tag', n, 'table'); EXCEPTION WHEN OTHER THEN "
                + "RETURN 'caught'; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: | Table 'NOSUCH_TABLE' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE NOSUCH_TABLE.",
            "BEGIN LET n := 'nosuch_table'; RETURN SYSTEM$GET_TAG('tg2', n, 'table'); END;");
    }

    @Test
    public void aRoutinesBody() {
        try {
            answer(
                "CREATE OR REPLACE TABLE t (a INT, b INT)");
            answer(
                "INSERT INTO t VALUES (1, 2), (3, 4)");
            answer(
                "CREATE OR REPLACE TAG tg2");
            assertAnswer(
                "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
                "CREATE OR REPLACE PROCEDURE pt() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                    + "SYSTEM$GET_TAG(''nosuch_tag'', ''t'', ''table''); END'");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function PT.",
                "CALL pt()");
            assertAnswer(
                "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
                "CREATE OR REPLACE FUNCTION ft2() RETURNS VARCHAR AS 'SYSTEM$GET_TAG(''nosuch_tag'', ''t'', "
                    + "''table'')'");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function FT2.",
                "SELECT ft2()");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS pt()");
            answer(
                "DROP FUNCTION IF EXISTS ft2()");
        }
    }

    @Test
    public void aRoutinesParameterForAnArgument() {
        try {
            answer(
                "CREATE OR REPLACE TABLE t (a INT, b INT)");
            answer(
                "INSERT INTO t VALUES (1, 2), (3, 4)");
            answer(
                "CREATE OR REPLACE TAG tg2");
            assertAnswer(
                "Function PT3 successfully created.",
                "CREATE OR REPLACE PROCEDURE pt3(n VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                    + "SYSTEM$GET_TAG(''tg2'', n, ''table''); END'");
            assertAnswer(
                "null",
                "CALL pt3('t')");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                    + "error: | Table 'NOSUCH_TABLE' does not exist or not authorized. This executable runs with "
                    + "owner's rights. The owner role <ROLE> must have at least one privilege granted on TABLE "
                    + "NOSUCH_TABLE.",
                "CALL pt3('nosuch_table')");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 35 | Bind variables not allowed in view "
                    + "and UDF definitions.",
                "CREATE OR REPLACE PROCEDURE pt4(n VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                    + "SYSTEM$GET_TAG(''tg2'', :n, ''table''); END'");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function PT4.",
                "CALL pt4('t')");
            assertAnswer(
                "REFUSED SQL compilation error: | Invalid value [CORRELATION(null)] for function 'SYSTEM$GET_TAG',"
                    + " parameter 1: constant arguments expected",
                "CREATE OR REPLACE FUNCTION ft6(n VARCHAR) RETURNS VARCHAR AS 'SYSTEM$GET_TAG(''tg2'', n, "
                    + "''table'')'");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function FT6.",
                "SELECT ft6('t')");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS pt3(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS pt4(VARCHAR)");
            answer(
                "DROP FUNCTION IF EXISTS ft6(VARCHAR)");
        }
    }
}
