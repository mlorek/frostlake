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
 * An error that leaves several bodies carries one uncaught-exception sentence for each. The body it starts
 * in names its own kind and place - a statement, an expression, a declared exception - and every procedure
 * or block that called it adds a STATEMENT_ERROR at the calling statement: a CALL, a CALL INTO, an EXECUTE
 * IMMEDIATE of one. A handler of the calling body catches it as STATEMENT_ERROR, whatever was raised inside,
 * with the inner code and state and the inner sentence as its message; RAISE in that handler lets it out as
 * the calling statement's again. Every answer is live-verified.
 */
public class NestedCallUncaughtExceptionTest extends MeasuredCellsTestSupport {

    @Test
    public void eachBodyAnErrorLeavesAddsItsSentence() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_l3() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  SELECT * FROM "
                + "nosuch_t;\n  RETURN 'x';\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_l2() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  CALL p_l3();\n  "
                + "RETURN 'y';\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_l1() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  CALL p_l2();\n  "
                + "RETURN 'z';\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_r3() RETURNS VARCHAR LANGUAGE SQL AS $$\nDECLARE\n  e EXCEPTION "
                + "(-20001, 'boom');\nBEGIN\n  RAISE e;\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_r2() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  CALL p_r3();\n  "
                + "RETURN 'y';\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_h() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  CALL p_r3();\n  "
                + "RETURN 'no';\nEXCEPTION\n  WHEN STATEMENT_ERROR THEN\n    RETURN 'stmt ' || SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || SQLERRM;\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_h2() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  CALL p_l3();\n  "
                + "RETURN 'no';\nEXCEPTION\n  WHEN STATEMENT_ERROR THEN\n    RETURN 'stmt ' || SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || SQLERRM;\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_h3() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  CALL p_r3();\n  "
                + "RETURN 'no';\nEXCEPTION\n  WHEN OTHER THEN\n    RETURN 'other ' || SQLCODE || ' ' || SQLSTATE || "
                + "' ' || SQLERRM;\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_h4() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  CALL p_l2();\n  "
                + "RETURN 'no';\nEXCEPTION\n  WHEN OTHER THEN\n    RETURN 'other ' || SQLCODE || ' ' || SQLSTATE || "
                + "' ' || SQLERRM;\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_e3() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  LET x := 1/0;\n  "
                + "RETURN x;\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_e2() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  CALL p_e3();\n  "
                + "RETURN 'y';\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_rr() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  CALL p_r3();\n  "
                + "RETURN 'no';\nEXCEPTION\n  WHEN OTHER THEN\n    RAISE;\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_c3() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$\nBEGIN\n  "
                + "SELECT * FROM nosuch_t;\n  RETURN 'x';\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_c2() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$\nBEGIN\n  "
                + "CALL p_c3();\n  RETURN 'y';\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_n3() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  BEGIN\n    SELECT * "
                + "FROM nosuch_t;\n  END;\n  RETURN 'x';\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_n2() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  IF (TRUE) THEN\n    "
                + "CALL p_n3();\n  END IF;\n  RETURN 'y';\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_i2() RETURNS VARCHAR LANGUAGE SQL AS $$\nDECLARE\n  r "
                + "VARCHAR;\nBEGIN\n  CALL p_l3() INTO :r;\n  RETURN r;\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_x2() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  EXECUTE IMMEDIATE "
                + "'CALL p_l3()';\n  RETURN 'y';\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_ok() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  RETURN "
                + "'ok';\nEND;\n$$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_o2() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  CALL p_ok();\n  "
                + "SELECT * FROM nosuch_t;\n  RETURN 'y';\nEND;\n$$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation "
                + "error: | Object 'NOSUCH_T' does not exist or not authorized. This executable runs with owner's "
                + "rights. The owner role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "CALL p_l3()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: | Object 'NOSUCH_T' does "
                + "not exist or not authorized. This executable runs with owner's rights. The owner role <ROLE> must "
                + "have at least one privilege granted on TABLE NOSUCH_T.",
            "CALL p_l2()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 3 at position 2 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 3 at position 2 : SQL compilation error: | Object 'NOSUCH_T' does not exist or not "
                + "authorized. This executable runs with owner's rights. The owner role <ROLE> must have at least "
                + "one privilege granted on TABLE NOSUCH_T.",
            "CALL p_l1()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'E' on line 5 at position 2 : boom",
            "CALL p_r3()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : Uncaught exception of "
                + "type 'E' on line 5 at position 2 : boom",
            "CALL p_r2()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: | Object 'NOSUCH_T' does "
                + "not exist or not authorized. This executable runs with owner's rights. The owner role <ROLE> must "
                + "have at least one privilege granted on TABLE NOSUCH_T.",
            "BEGIN CALL p_l3(); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 3 at position 2 : Uncaught exception of type 'STATEMENT_ERROR' on "
                + "line 3 at position 2 : SQL compilation error: | Object 'NOSUCH_T' does not exist or not "
                + "authorized. This executable runs with owner's rights. The owner role <ROLE> must have at least "
                + "one privilege granted on TABLE NOSUCH_T.",
            "BEGIN CALL p_l2(); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Uncaught exception of "
                + "type 'E' on line 5 at position 2 : boom",
            "BEGIN CALL p_r3(); END;");
        assertAnswer(
            "stmt -20001 P0001 Uncaught exception of type 'E' on line 5 at position 2 : boom",
            "CALL p_h()");
        assertAnswer(
            "stmt 2003 42S02 Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL "
                + "compilation error: | Object 'NOSUCH_T' does not exist or not authorized. This executable runs "
                + "with owner's rights. The owner role <ROLE> must have at least one privilege granted on TABLE "
                + "NOSUCH_T.",
            "CALL p_h2()");
        assertAnswer(
            "other -20001 P0001 Uncaught exception of type 'E' on line 5 at position 2 : boom",
            "CALL p_h3()");
        assertAnswer(
            "other 2003 42S02 Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : Uncaught "
                + "exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: | Object "
                + "'NOSUCH_T' does not exist or not authorized. This executable runs with owner's rights. The owner "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "CALL p_h4()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 3 at position 11 : Division by zero",
            "CALL p_e3()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : Uncaught exception of "
                + "type 'EXPRESSION_ERROR' on line 3 at position 11 : Division by zero",
            "CALL p_e2()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : Uncaught exception of "
                + "type 'E' on line 5 at position 2 : boom",
            "CALL p_rr()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation "
                + "error: | Object 'NOSUCH_T' does not exist or not authorized. Your primary role <ROLE> must have "
                + "at least one privilege granted on TABLE NOSUCH_T.",
            "CALL p_c3()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: | Object 'NOSUCH_T' does "
                + "not exist or not authorized. Your primary role <ROLE> must have at least one privilege granted on "
                + "TABLE NOSUCH_T.",
            "CALL p_c2()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 4 at position 4 : SQL compilation "
                + "error: | Object 'NOSUCH_T' does not exist or not authorized. This executable runs with owner's "
                + "rights. The owner role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "CALL p_n3()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 4 at position 4 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 4 at position 4 : SQL compilation error: | Object 'NOSUCH_T' does "
                + "not exist or not authorized. This executable runs with owner's rights. The owner role <ROLE> must "
                + "have at least one privilege granted on TABLE NOSUCH_T.",
            "CALL p_n2()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 5 at position 2 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: | Object 'NOSUCH_T' does "
                + "not exist or not authorized. This executable runs with owner's rights. The owner role <ROLE> must "
                + "have at least one privilege granted on TABLE NOSUCH_T.",
            "CALL p_i2()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: | Object 'NOSUCH_T' does "
                + "not exist or not authorized. This executable runs with owner's rights. The owner role <ROLE> must "
                + "have at least one privilege granted on TABLE NOSUCH_T.",
            "CALL p_x2()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 4 at position 2 : SQL compilation "
                + "error: | Object 'NOSUCH_T' does not exist or not authorized. This executable runs with owner's "
                + "rights. The owner role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "CALL p_o2()");
        assertAnswer(
            "other 2003 42S02 Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL "
                + "compilation error: | Object 'NOSUCH_T' does not exist or not authorized. This executable runs "
                + "with owner's rights. The owner role <ROLE> must have at least one privilege granted on TABLE "
                + "NOSUCH_T.",
            "BEGIN\n  CALL p_l3();\n  RETURN 1;\nEXCEPTION\n  WHEN OTHER THEN\n    RETURN 'other ' || SQLCODE || "
                + "' ' || SQLSTATE || ' ' || SQLERRM;\nEND;");
        assertAnswer(
            "stmt 2003 42S02 Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : Uncaught "
                + "exception of type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: | Object "
                + "'NOSUCH_T' does not exist or not authorized. This executable runs with owner's rights. The owner "
                + "role <ROLE> must have at least one privilege granted on TABLE NOSUCH_T.",
            "BEGIN\n  CALL p_l2();\n  RETURN 1;\nEXCEPTION\n  WHEN STATEMENT_ERROR THEN\n    RETURN 'stmt ' || "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM;\nEND;");
        assertAnswer(
            "other -20001 P0001 Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : Uncaught "
                + "exception of type 'E' on line 5 at position 2 : boom",
            "BEGIN\n  CALL p_r2();\n  RETURN 1;\nEXCEPTION\n  WHEN OTHER THEN\n    RETURN 'other ' || SQLCODE || "
                + "' ' || SQLSTATE || ' ' || SQLERRM;\nEND;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 4 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: | Object 'NOSUCH_T' does "
                + "not exist or not authorized. This executable runs with owner's rights. The owner role <ROLE> must "
                + "have at least one privilege granted on TABLE NOSUCH_T.",
            "BEGIN\n  BEGIN\n    CALL p_l3();\n  END;\n  RETURN 1;\nEND;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 4 at position 2 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: | Object 'NOSUCH_T' does "
                + "not exist or not authorized. This executable runs with owner's rights. The owner role <ROLE> must "
                + "have at least one privilege granted on TABLE NOSUCH_T.",
            "DECLARE\n  r VARCHAR;\nBEGIN\n  CALL p_l3() INTO :r;\n  RETURN r;\nEND;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 2 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: | Object 'NOSUCH_T' does "
                + "not exist or not authorized. This executable runs with owner's rights. The owner role <ROLE> must "
                + "have at least one privilege granted on TABLE NOSUCH_T.",
            "BEGIN\n  EXECUTE IMMEDIATE 'CALL p_l3()';\n  RETURN 1;\nEND;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 2 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 3 at position 2 : SQL compilation error: | Object 'NOSUCH_T' does "
                + "not exist or not authorized. This executable runs with owner's rights. The owner role <ROLE> must "
                + "have at least one privilege granted on TABLE NOSUCH_T.",
            "EXECUTE IMMEDIATE 'CALL p_l2()'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 2 at position 2 |  variable 'X' cannot have its type "
                + "inferred from initializer",
            "BEGIN\n  LET x := (SELECT COUNT(*) FROM nosuch_t);\n  RETURN x;\nEND;");
    }
}
