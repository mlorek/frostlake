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
 * A variable declared VARIANT is a VARIANT to the CALL it is bound to, whatever it holds: a text in it that a
 * number's, a boolean's, a date's or a time's parameter does not read is refused as the variant it is, "Failed
 * to cast variant value "x" to DATE". A text parameter written without a length is held by its body as wide as
 * a block's own VARCHAR variable, an ARRAY parameter refuses a BINARY, and the too-many-arguments sentence
 * echoes a semi-structured variable that holds a value as the JSON the statement binds (live-verified).
 */
public class CallVariantAndWidthBindTest extends MeasuredCellsTestSupport {

    @Test
    public void aVariantThatDoesNotConvert() {
        try {
            answer(
                "CREATE OR REPLACE PROCEDURE pn(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN n + 1; "
                    + "END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pd(d DATE) RETURNS DATE LANGUAGE SQL AS $$ BEGIN RETURN d; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pa(a ARRAY) RETURNS ARRAY LANGUAGE SQL AS $$ BEGIN RETURN a; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pi(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN n + 1; "
                    + "END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE po3(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (TRUE) "
                    + "THEN CALL pa(:s); END IF; RETURN 1; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE po4(s VARCHAR(16777216)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF "
                    + "(TRUE) THEN CALL pa(:s); END IF; RETURN 1; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE po5(s VARCHAR(10)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF "
                    + "(TRUE) THEN CALL pa(:s); END IF; RETURN 1; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE add_dml(so OBJECT, qid VARCHAR) RETURNS OBJECT LANGUAGE SQL AS $$ "
                    + "BEGIN RETURN so; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE abs(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 7; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE upper(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                    + "'proc'; END $$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 37 : Failed to cast "
                    + "variant value \"x\" to DATE",
                "DECLARE v VARIANT DEFAULT 'x'; BEGIN CALL pd(:v); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': "
                    + "(VARCHAR(134217728))",
                "CALL po3(NULL)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': "
                    + "(VARCHAR(16777216))",
                "CALL po4(NULL)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': (VARCHAR(10))",
                "CALL po5(NULL)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 37 : Failed to cast "
                    + "variant value \"x\" to FIXED",
                "DECLARE v VARIANT DEFAULT 'x'; BEGIN CALL pn(:v); RETURN 1; END;");
            assertAnswer(
                "6",
                "DECLARE v VARIANT DEFAULT '5'; r NUMBER; BEGIN CALL pn(:v) INTO :r; RETURN r; END;");
            assertAnswer(
                "6",
                "DECLARE v VARIANT DEFAULT 5; r NUMBER; BEGIN CALL pn(:v) INTO :r; RETURN r; END;");
            assertAnswer(
                "2024-01-02",
                "DECLARE v VARIANT DEFAULT '2024-01-02'; r DATE; BEGIN CALL pd(:v) INTO :r; RETURN r; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 38 : Failed to cast "
                    + "variant value true to DATE",
                "DECLARE v VARIANT DEFAULT TRUE; BEGIN CALL pd(:v); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE v VARIANT DEFAULT 'x'; BEGIN CALL pa(:v); RETURN 1; END;");
            assertAnswer(
                "REFUSED Failed to cast variant value \"x\" to DATE",
                "SELECT 'x'::VARIANT::DATE");
            assertAnswer(
                "REFUSED Failed to cast variant value \"x\" to DATE",
                "SELECT TO_DATE('x'::VARIANT)");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS pn(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS pd(DATE)");
            answer(
                "DROP PROCEDURE IF EXISTS pa(ARRAY)");
            answer(
                "DROP PROCEDURE IF EXISTS pi(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS po3(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS po4(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS po5(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS add_dml(OBJECT, VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS abs(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS upper(VARCHAR)");
        }
    }

    @Test
    public void everyParameterFamily() {
        try {
            answer(
                "CREATE OR REPLACE PROCEDURE pn(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN n + 1; "
                    + "END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pd(d DATE) RETURNS DATE LANGUAGE SQL AS $$ BEGIN RETURN d; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pa(a ARRAY) RETURNS ARRAY LANGUAGE SQL AS $$ BEGIN RETURN a; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pt(t TIME) RETURNS TIME LANGUAGE SQL AS $$ BEGIN RETURN t; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pts(t TIMESTAMP_NTZ) RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ BEGIN "
                    + "RETURN t; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE ptl(t TIMESTAMP_LTZ) RETURNS TIMESTAMP_LTZ LANGUAGE SQL AS $$ BEGIN "
                    + "RETURN t; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pb(b BOOLEAN) RETURNS BOOLEAN LANGUAGE SQL AS $$ BEGIN RETURN b; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE pf(f FLOAT) RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN f; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pv(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN s; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE add_dml(so OBJECT, qid VARCHAR) RETURNS OBJECT LANGUAGE SQL AS $$ "
                    + "BEGIN RETURN so; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE abs(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 7; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE sum(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 7; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE lower(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                    + "'proc'; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE greatest(a NUMBER, b NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
                    + "RETURN 7; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE nvl(a NUMBER, b NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
                    + "RETURN 7; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE zq_plain(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 7; "
                    + "END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE po3(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (TRUE) "
                    + "THEN CALL pa(:s); END IF; RETURN 1; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE po6(s STRING) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (TRUE) "
                    + "THEN CALL pa(:s); END IF; RETURN 1; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE po7(s TEXT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (TRUE) THEN "
                    + "CALL pa(:s); END IF; RETURN 1; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pob(b BINARY) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (TRUE) "
                    + "THEN CALL pa(:b); END IF; RETURN 1; END $$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 37 : Failed to cast "
                    + "variant value \"x\" to TIME",
                "DECLARE v VARIANT DEFAULT 'x'; BEGIN CALL pt(:v); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 37 : Failed to cast "
                    + "variant value \"x\" to TIMESTAMP_NTZ",
                "DECLARE v VARIANT DEFAULT 'x'; BEGIN CALL pts(:v); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 37 : Failed to cast "
                    + "variant value \"x\" to TIMESTAMP_LTZ",
                "DECLARE v VARIANT DEFAULT 'x'; BEGIN CALL ptl(:v); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 37 : Failed to cast "
                    + "variant value \"x\" to BOOLEAN",
                "DECLARE v VARIANT DEFAULT 'x'; BEGIN CALL pb(:v); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 37 : Failed to cast "
                    + "variant value \"x\" to REAL",
                "DECLARE v VARIANT DEFAULT 'x'; BEGIN CALL pf(:v); RETURN 1; END;");
            assertAnswer(
                "x",
                "DECLARE v VARIANT DEFAULT 'x'; r VARCHAR; BEGIN CALL pv(:v) INTO :r; RETURN r; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : Failed to cast "
                    + "variant value 5 to DATE",
                "DECLARE v VARIANT DEFAULT 5; BEGIN CALL pd(:v); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 51 : Failed to cast "
                    + "variant value \"x\" to DATE",
                "DECLARE v VARIANT DEFAULT 'x'; x DATE; BEGIN x := (CALL pd(:v)); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 37 : Failed to cast "
                    + "variant value \"x\" to DATE",
                "DECLARE v VARIANT DEFAULT 'x'; BEGIN CALL pd(d => :v); RETURN 1; END;");
            assertAnswer(
                "12:30",
                "DECLARE v VARIANT DEFAULT '12:30:00'; r TIME; BEGIN CALL pt(:v) INTO :r; RETURN r; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : Failed to cast "
                    + "variant value \"x\" to DATE",
                "DECLARE v VARIANT; BEGIN v := 'x'; CALL pd(:v); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 51 : Failed to cast "
                    + "variant value \"x\" to DATE",
                "DECLARE v VARIANT DEFAULT PARSE_JSON('\"x\"'); BEGIN CALL pd(:v); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 37 : Date 'x' is not "
                    + "recognized",
                "DECLARE v VARIANT DEFAULT 'x'; BEGIN CALL pd(:v || ''); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 37 : Date 'x' is not "
                    + "recognized",
                "DECLARE v VARCHAR DEFAULT 'x'; BEGIN CALL pd(:v); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : Failed to cast "
                    + "variant value \"x\" to DATE",
                "DECLARE v VARIANT DEFAULT 'x'; BEGIN IF (TRUE) THEN CALL pd(:v); END IF; RETURN 1; END;");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS pn(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS pd(DATE)");
            answer(
                "DROP PROCEDURE IF EXISTS pa(ARRAY)");
            answer(
                "DROP PROCEDURE IF EXISTS pt(TIME)");
            answer(
                "DROP PROCEDURE IF EXISTS pts(TIMESTAMP_NTZ)");
            answer(
                "DROP PROCEDURE IF EXISTS ptl(TIMESTAMP_LTZ)");
            answer(
                "DROP PROCEDURE IF EXISTS pb(BOOLEAN)");
            answer(
                "DROP PROCEDURE IF EXISTS pf(FLOAT)");
            answer(
                "DROP PROCEDURE IF EXISTS pv(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS add_dml(OBJECT, VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS abs(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS sum(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS lower(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS greatest(NUMBER, NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS nvl(NUMBER, NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS zq_plain(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS po3(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS po6(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS po7(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS pob(BINARY)");
        }
    }

    @Test
    public void theWidthANullTextBindsAt() {
        try {
            answer(
                "CREATE OR REPLACE PROCEDURE pn(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN n + 1; "
                    + "END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pd(d DATE) RETURNS DATE LANGUAGE SQL AS $$ BEGIN RETURN d; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pa(a ARRAY) RETURNS ARRAY LANGUAGE SQL AS $$ BEGIN RETURN a; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pt(t TIME) RETURNS TIME LANGUAGE SQL AS $$ BEGIN RETURN t; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pts(t TIMESTAMP_NTZ) RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ BEGIN "
                    + "RETURN t; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE ptl(t TIMESTAMP_LTZ) RETURNS TIMESTAMP_LTZ LANGUAGE SQL AS $$ BEGIN "
                    + "RETURN t; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pb(b BOOLEAN) RETURNS BOOLEAN LANGUAGE SQL AS $$ BEGIN RETURN b; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE pf(f FLOAT) RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN f; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pv(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN s; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE add_dml(so OBJECT, qid VARCHAR) RETURNS OBJECT LANGUAGE SQL AS $$ "
                    + "BEGIN RETURN so; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE abs(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 7; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE sum(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 7; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE lower(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                    + "'proc'; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE greatest(a NUMBER, b NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
                    + "RETURN 7; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE nvl(a NUMBER, b NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
                    + "RETURN 7; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE zq_plain(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 7; "
                    + "END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE po3(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (TRUE) "
                    + "THEN CALL pa(:s); END IF; RETURN 1; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE po6(s STRING) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (TRUE) "
                    + "THEN CALL pa(:s); END IF; RETURN 1; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE po7(s TEXT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (TRUE) THEN "
                    + "CALL pa(:s); END IF; RETURN 1; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pob(b BINARY) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (TRUE) "
                    + "THEN CALL pa(:b); END IF; RETURN 1; END $$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': "
                    + "(VARCHAR(134217728))",
                "CALL po3(NULL)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': "
                    + "(VARCHAR(134217728))",
                "CALL po6(NULL)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': "
                    + "(VARCHAR(134217728))",
                "CALL po7(NULL)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': "
                    + "(BINARY(67108864))",
                "CALL pob(NULL)");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': (VARCHAR(3))",
                "CALL po3('abc')");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': "
                    + "(VARCHAR(134217728))",
                "DECLARE s VARCHAR; BEGIN CALL pa(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 24 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': "
                    + "(VARCHAR(134217728))",
                "DECLARE s STRING; BEGIN CALL pa(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': "
                    + "(VARCHAR(16777216))",
                "DECLARE s VARCHAR(16777216); BEGIN CALL pa(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 29 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': (VARCHAR(10))",
                "DECLARE s VARCHAR(10); BEGIN CALL pa(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 29 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': "
                    + "(VARCHAR(134217728))",
                "BEGIN LET s VARCHAR := NULL; CALL pa(:s); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 24 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PA': "
                    + "(BINARY(67108864))",
                "DECLARE b BINARY; BEGIN CALL pa(:b); RETURN 1; END;");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS pn(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS pd(DATE)");
            answer(
                "DROP PROCEDURE IF EXISTS pa(ARRAY)");
            answer(
                "DROP PROCEDURE IF EXISTS pt(TIME)");
            answer(
                "DROP PROCEDURE IF EXISTS pts(TIMESTAMP_NTZ)");
            answer(
                "DROP PROCEDURE IF EXISTS ptl(TIMESTAMP_LTZ)");
            answer(
                "DROP PROCEDURE IF EXISTS pb(BOOLEAN)");
            answer(
                "DROP PROCEDURE IF EXISTS pf(FLOAT)");
            answer(
                "DROP PROCEDURE IF EXISTS pv(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS add_dml(OBJECT, VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS abs(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS sum(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS lower(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS greatest(NUMBER, NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS nvl(NUMBER, NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS zq_plain(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS po3(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS po6(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS po7(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS pob(BINARY)");
        }
    }

    @Test
    public void howABindIsEchoed() {
        try {
            answer(
                "CREATE OR REPLACE PROCEDURE pn(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN n + 1; "
                    + "END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pd(d DATE) RETURNS DATE LANGUAGE SQL AS $$ BEGIN RETURN d; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pa(a ARRAY) RETURNS ARRAY LANGUAGE SQL AS $$ BEGIN RETURN a; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pt(t TIME) RETURNS TIME LANGUAGE SQL AS $$ BEGIN RETURN t; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pts(t TIMESTAMP_NTZ) RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ BEGIN "
                    + "RETURN t; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE ptl(t TIMESTAMP_LTZ) RETURNS TIMESTAMP_LTZ LANGUAGE SQL AS $$ BEGIN "
                    + "RETURN t; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pb(b BOOLEAN) RETURNS BOOLEAN LANGUAGE SQL AS $$ BEGIN RETURN b; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE pf(f FLOAT) RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN f; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pv(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN s; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE add_dml(so OBJECT, qid VARCHAR) RETURNS OBJECT LANGUAGE SQL AS $$ "
                    + "BEGIN RETURN so; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE abs(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 7; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE sum(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 7; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE lower(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                    + "'proc'; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE greatest(a NUMBER, b NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
                    + "RETURN 7; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE nvl(a NUMBER, b NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN "
                    + "RETURN 7; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE zq_plain(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 7; "
                    + "END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE po3(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (TRUE) "
                    + "THEN CALL pa(:s); END IF; RETURN 1; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE po6(s STRING) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (TRUE) "
                    + "THEN CALL pa(:s); END IF; RETURN 1; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE po7(s TEXT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (TRUE) THEN "
                    + "CALL pa(:s); END IF; RETURN 1; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pob(b BINARY) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (TRUE) "
                    + "THEN CALL pa(:b); END IF; RETURN 1; END $$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 61 : SQL compilation "
                    + "error: error line 0 at position -1 | too many arguments for function "
                    + "[ADD_DML(CAST(PARSE_JSON(:stats) AS OBJECT), 'z', 1)] expected 2, got 3",
                "DECLARE stats OBJECT DEFAULT OBJECT_CONSTRUCT('a', 1); BEGIN CALL add_dml(:stats, 'z', 1); "
                    + "RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 50 : SQL compilation "
                    + "error: error line 0 at position -1 | too many arguments for function [PN(CAST(PARSE_JSON(:a) "
                    + "AS ARRAY), 1)] expected 1, got 2",
                "DECLARE a ARRAY DEFAULT ARRAY_CONSTRUCT(1); BEGIN CALL pn(:a, 1); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                    + "error: error line 0 at position -1 | too many arguments for function [PN(PARSE_JSON(:v), 1)] "
                    + "expected 1, got 2",
                "DECLARE v VARIANT DEFAULT 1; BEGIN CALL pn(:v, 1); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 43 : SQL compilation "
                    + "error: error line 0 at position -1 | too many arguments for function [PN(:d, 1)] expected 1, "
                    + "got 2",
                "DECLARE d DATE DEFAULT '2024-01-02'; BEGIN CALL pn(:d, 1); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 37 : SQL compilation "
                    + "error: error line 0 at position -1 | too many arguments for function [PN(:s, 1)] expected 1, "
                    + "got 2",
                "DECLARE s VARCHAR DEFAULT 'q'; BEGIN CALL pn(:s, 1); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 38 : SQL compilation "
                    + "error: error line 0 at position -1 | too many arguments for function [PN(:b, 1)] expected 1, "
                    + "got 2",
                "DECLARE b BOOLEAN DEFAULT TRUE; BEGIN CALL pn(:b, 1); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                    + "error: error line 0 at position -1 | too many arguments for function [PN(:f, 1)] expected 1, "
                    + "got 2",
                "DECLARE f FLOAT DEFAULT 1.5; BEGIN CALL pn(:f, 1); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 61 : SQL compilation "
                    + "error: error line 0 at position -1 | too many arguments for function [PN(:t, 1)] expected 1, "
                    + "got 2",
                "DECLARE t TIMESTAMP_NTZ DEFAULT '2024-01-02 03:04:05'; BEGIN CALL pn(:t, 1); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 61 : SQL compilation "
                    + "error: error line 0 at position -1 | Invalid argument types for function 'PN': (OBJECT)",
                "DECLARE stats OBJECT DEFAULT OBJECT_CONSTRUCT('a', 1); BEGIN CALL pn(:stats); RETURN 1; END;");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS pn(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS pd(DATE)");
            answer(
                "DROP PROCEDURE IF EXISTS pa(ARRAY)");
            answer(
                "DROP PROCEDURE IF EXISTS pt(TIME)");
            answer(
                "DROP PROCEDURE IF EXISTS pts(TIMESTAMP_NTZ)");
            answer(
                "DROP PROCEDURE IF EXISTS ptl(TIMESTAMP_LTZ)");
            answer(
                "DROP PROCEDURE IF EXISTS pb(BOOLEAN)");
            answer(
                "DROP PROCEDURE IF EXISTS pf(FLOAT)");
            answer(
                "DROP PROCEDURE IF EXISTS pv(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS add_dml(OBJECT, VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS abs(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS sum(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS lower(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS greatest(NUMBER, NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS nvl(NUMBER, NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS zq_plain(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS po3(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS po6(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS po7(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS pob(BINARY)");
        }
    }

    @Test
    public void theTypeABodyHoldsAParameterAt() {
        try {
            answer(
                "CREATE OR REPLACE PROCEDURE pn(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN n + 1; "
                    + "END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pd(d DATE) RETURNS DATE LANGUAGE SQL AS $$ BEGIN RETURN d; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pf(f FLOAT) RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN f; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pv(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN s; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE abs(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 7; END "
                    + "$$");
            answer(
                "CREATE OR REPLACE PROCEDURE \"lower\"(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                    + "'proc'; END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pw(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                    + "SYSTEM$TYPEOF(s); END $$");
            answer(
                "CREATE OR REPLACE PROCEDURE pw2(s VARCHAR(16777216)) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
                    + "RETURN SYSTEM$TYPEOF(s); END $$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : SQL compilation "
                    + "error: error line 0 at position -1 | too many arguments for function [PN(:v, 1)] expected 1, "
                    + "got 2",
                "DECLARE v VARIANT; BEGIN CALL pn(:v, 1); RETURN 1; END;");
            assertAnswer(
                "VARCHAR[LOB]",
                "CALL pw(NULL)");
            assertAnswer(
                "VARCHAR[LOB]",
                "CALL pw('abc')");
            assertAnswer(
                "VARCHAR[LOB]",
                "CALL pw2(NULL)");
            assertAnswer(
                "VARCHAR[LOB]",
                "DECLARE s VARCHAR; BEGIN RETURN SYSTEM$TYPEOF(s); END;");
            assertAnswer(
                "VARCHAR[LOB]",
                "DECLARE s VARCHAR DEFAULT 'abc'; BEGIN RETURN SYSTEM$TYPEOF(s); END;");
            assertAnswer(
                "VARCHAR[LOB]",
                "DECLARE s VARCHAR(10); BEGIN RETURN SYSTEM$TYPEOF(s); END;");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS pn(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS pd(DATE)");
            answer(
                "DROP PROCEDURE IF EXISTS pf(FLOAT)");
            answer(
                "DROP PROCEDURE IF EXISTS pv(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS abs(NUMBER)");
            answer(
                "DROP PROCEDURE IF EXISTS \"lower\"(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS pw(VARCHAR)");
            answer(
                "DROP PROCEDURE IF EXISTS pw2(VARCHAR)");
        }
    }
}
