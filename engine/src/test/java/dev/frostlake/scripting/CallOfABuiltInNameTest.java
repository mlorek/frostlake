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
 * A CALL whose name has no schema before it and is a built-in function's — a scalar's or an aggregate's,
 * written bare or between quotes in any case — calls the function, whatever procedure the schema holds under
 * the name: its answer, and its own refusals of a count or of named arguments, placed nowhere. With a schema
 * before it the name is the procedure's. A block's CALL, a CALL assigned to a variable and CALL ... INTO read
 * the name alike (live-verified).
 */
public class CallOfABuiltInNameTest extends MeasuredCellsTestSupport {

    @Test
    public void aProcedureNamedAsABuiltIn() {
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
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                    + "error: error line 0 at position -1 | too many arguments for function [PI(:i)] expected 0, got "
                    + "1",
                "DECLARE r NUMBER DEFAULT 0; BEGIN FOR i IN 1 TO 3 DO CALL pi(:i) INTO :r; END FOR; RETURN r; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                    + "error: error line 0 at position -1 | too many arguments for function [PI(:r)] expected 0, got "
                    + "1",
                "DECLARE r NUMBER DEFAULT 0; BEGIN FOR i IN 1 TO 3 DO CALL pi(:r) INTO :r; END FOR; RETURN r; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | too many arguments for function "
                    + "[PI(1)] expected 0, got 1",
                "CALL pi(1)");
            assertAnswer(
                "3.141592653589793",
                "CALL pi()");
            assertAnswer(
                "1",
                "CALL abs(1)");
            assertAnswer(
                "3",
                "CALL abs(-3)");
            assertAnswer(
                "A",
                "CALL upper('a')");
            assertAnswer(
                "2",
                "CALL test_schema.pi(1)");
            assertAnswer(
                "7",
                "CALL test_db.test_schema.abs(1)");
            assertAnswer(
                "1",
                "BEGIN CALL abs(-3); RETURN 1; END;");
            assertAnswer(
                "3",
                "DECLARE r NUMBER; BEGIN CALL abs(-3) INTO :r; RETURN r; END;");
            assertAnswer(
                "2",
                "DECLARE r NUMBER; BEGIN CALL pn(1) INTO :r; RETURN r; END;");
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
    public void scalarsAggregatesAndQuotedNames() {
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
                "1",
                "CALL sum(1)");
            assertAnswer(
                "a",
                "CALL lower('A')");
            assertAnswer(
                "2",
                "CALL greatest(1, 2)");
            assertAnswer(
                "2",
                "CALL nvl(NULL, 2)");
            assertAnswer(
                "7",
                "CALL zq_plain(1)");
            assertAnswer(
                "3",
                "CALL ABS(-3)");
            assertAnswer(
                "3",
                "CALL \"ABS\"(-3)");
            assertAnswer(
                "3",
                "CALL \"abs\"(-3)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | function ABS does not support named "
                    + "arguments",
                "CALL abs(n => -3)");
            assertAnswer(
                "REFUSED Numeric value 'x' is not recognized",
                "CALL abs('x')");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                    + "[ABS()], expected 1, got 0",
                "CALL abs()");
            assertAnswer(
                "REFUSED SQL compilation error: error line 0 at position -1 | too many arguments for function "
                    + "[ABS(1, 2)] expected 1, got 2",
                "CALL abs(1, 2)");
            assertAnswer(
                "7",
                "CALL test_schema.sum(1)");
            assertAnswer(
                "1",
                "CALL count(1)");
            assertAnswer(
                "3",
                "DECLARE x NUMBER; BEGIN x := (CALL abs(-3)); RETURN x; END;");
            assertAnswer(
                "3",
                "DECLARE r RESULTSET; BEGIN r := (CALL abs(-3)); RETURN TABLE(r); END;");
            assertAnswer(
                "1",
                "BEGIN CALL sum(1); RETURN 1; END;");
            assertAnswer(
                "1",
                "DECLARE x NUMBER; BEGIN CALL sum(1) INTO :x; RETURN x; END;");
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
    public void quotedNamesInAnyCase() {
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
                "A",
                "CALL \"upper\"('a')");
            assertAnswer(
                "A",
                "CALL \"UPPER\"('a')");
            assertAnswer(
                "A",
                "CALL \"Upper\"('a')");
            assertAnswer(
                "a",
                "CALL \"lower\"('A')");
            assertAnswer(
                "a",
                "CALL lower('A')");
            assertAnswer(
                "proc",
                "CALL test_schema.\"lower\"('A')");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 0 at position -1 | function ABS does not support named arguments",
                "BEGIN CALL abs(n => -3); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 5 | illegal mixing of named and "
                    + "positional arguments for function ABS",
                "CALL abs(1, n => 2)");
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
