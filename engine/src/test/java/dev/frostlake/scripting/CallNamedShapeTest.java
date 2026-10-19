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
 * The shape of a CALL's named arguments is judged before the procedure is looked for, so an unknown
 * procedure's CALL is refused for its shape first, and for a CALL a block holds while the block compiles, on a
 * branch that never runs too: a name written twice at its second writing, then a mix of named and positional
 * arguments at the procedure's name, the procedure named as the CALL writes it. A name no parameter has is
 * judged when the CALL runs (live-verified).
 */
public class CallNamedShapeTest extends MeasuredCellsTestSupport {

    @Test
    public void inABlockAndAtTheTopLevel() {
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
                "REFUSED SQL compilation error: error line 1 at position 38 | duplicate named argument N for "
                    + "function PN",
                "DECLARE d DATE; BEGIN CALL pn(n => 1, n => 2); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 22 | duplicate named argument N for "
                    + "function PN",
                "BEGIN CALL pn(n => 1, n => 2); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 16 | duplicate named argument N for "
                    + "function PN",
                "CALL pn(n => 1, n => 2)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 38 | duplicate named argument N for "
                    + "function PN",
                "BEGIN IF (FALSE) THEN CALL pn(n => 1, n => 2); END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 0 at position -1 | too many arguments for function [PN(1, 2)] expected 1, "
                    + "got 2",
                "BEGIN CALL pn(n => 1, m => 2); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 11 | illegal mixing of named and "
                    + "positional arguments for function PN",
                "BEGIN CALL pn(1, n => 2); RETURN 1; END;");
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
    public void anUnknownProcedureAndAnAssignedCall() {
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
                "REFUSED SQL compilation error: error line 1 at position 30 | duplicate named argument A for "
                    + "function NOSUCH_ZQ7",
                "BEGIN CALL nosuch_zq7(a => 1, a => 2); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 11 | illegal mixing of named and "
                    + "positional arguments for function NOSUCH_ZQ7",
                "BEGIN CALL nosuch_zq7(1, a => 2); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 23 | duplicate named argument N for "
                    + "function ABS",
                "BEGIN CALL abs(n => 1, n => 2); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 22 | duplicate named argument N for "
                    + "function PN",
                "BEGIN CALL pn(n => 1, \"N\" => 2); RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 0 at position -1 | too many arguments for function [PN(1, 2)] expected 1, "
                    + "got 2",
                "BEGIN CALL pn(n => 1, \"n\" => 2); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 24 | duplicate named argument A for "
                    + "function NOSUCH_ZQ7",
                "CALL nosuch_zq7(a => 1, a => 2)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 5 | illegal mixing of named and "
                    + "positional arguments for function NOSUCH_ZQ7",
                "CALL nosuch_zq7(1, a => 2)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 46 | duplicate named argument N for "
                    + "function PN",
                "DECLARE x NUMBER; BEGIN x := (CALL pn(n => 1, n => 2)); RETURN x; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 35 | illegal mixing of named and "
                    + "positional arguments for function PN",
                "DECLARE x NUMBER; BEGIN x := (CALL pn(1, n => 2)); RETURN x; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 40 | duplicate named argument N for "
                    + "function PN",
                "DECLARE x NUMBER; BEGIN CALL pn(n => 1, n => 2) INTO :x; RETURN x; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 22 | duplicate named argument N for "
                    + "function PN",
                "BEGIN CALL pn(n => 1, n => 2, 3); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 25 | duplicate named argument N for "
                    + "function PN",
                "BEGIN CALL pn(1, n => 2, n => 3); RETURN 1; END;");
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
    public void theProcedureAsTheCallWritesIt() {
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
                "REFUSED SQL compilation error: error line 1 at position 27 | duplicate named argument A for "
                    + "function NOSUCH_ZQ7",
                "CALL nosuch_zq7(1, a => 2, a => 3)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 36 | duplicate named argument A for "
                    + "function TEST_SCHEMA.NOSUCH_ZQ7",
                "CALL test_schema.nosuch_zq7(a => 1, a => 2)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 31 | duplicate named argument A for "
                    + "function NOSUCH_DB_ZQ7.S.P",
                "CALL nosuch_db_zq7.s.p(a => 1, a => 2)");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown function NOSUCH_ZQ7.",
                "CALL nosuch_zq7(a => 1, b => 2)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 5 | illegal mixing of named and "
                    + "positional arguments for function \"nosuch_zq7\"",
                "CALL \"nosuch_zq7\"(1, a => 2)");
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
