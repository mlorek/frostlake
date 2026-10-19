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
 * A name nothing declares, written inside a call's argument, is refused while the block compiles: bare, at the
 * name's own place, on a branch that never runs, in a nested call, in a condition, a loop's bound, a handler and
 * a DECLARE's default, and before a later name outside the call. A lambda's parameter is no name of the
 * block's, so its body is refused for it. The word standing where a function reads a date or time unit is left
 * to the call: one that names no unit is found when the statement runs, as the name nothing declares, and a
 * variable there gives its value as the unit (live-verified).
 */
public class CallArgumentNameTest extends MeasuredCellsTestSupport {

    @Test
    public void aNameInsideAnArgument() {
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 17 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN NVL(nosuch_var_x9, 'x'); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN UPPER(nosuch_var_x9) || SQLERRM; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN LET x := ABS(nosuch_var_x9); RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 33 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN IF (FALSE) THEN RETURN NVL(nosuch_var_x9, 'x'); END IF; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 29 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN DATEADD(day, 1, nosuch_var_x9); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 0 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN DATEADD(nosuch_var_x9, 1, CURRENT_DATE()); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 44 | invalid identifier 'X'",
            "BEGIN RETURN ARRAY_SIZE(TRANSFORM([1], x -> x + nosuch_var_x9)); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 25 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN COALESCE(1, nosuch_var_x9); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN IFF(TRUE, 1, nosuch_var_x9); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 19 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN (SELECT NVL(nosuch_var_x9, 'x')); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 32 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN LET v := 1; RETURN NVL(v, nosuch_var_x9); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN nosuch_fn_x9(nosuch_var_x9); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN UPPER(nosuch_var_x9.y); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN CAST(nosuch_var_x9 AS INT); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN nosuch_var_x9::INT; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN -nosuch_var_x9; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN CASE WHEN nosuch_var_x9 THEN 1 END; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN [nosuch_var_x9]; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN {'a': nosuch_var_x9}; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN nosuch_var_x9 IS NULL; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN nosuch_var_x9 IN (1, 2); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN 1 IN (nosuch_var_x9); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN nosuch_var_x9 BETWEEN 1 AND 2; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 25 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN UPPER(LOWER(nosuch_var_x9)); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 22 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN NVL('x', nosuch_var_x9) || NVL(nosuch_var_y9, 'y'); END;");
        assertAnswer(
            "1",
            "BEGIN LET v := 1; RETURN NVL(v, 2); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | invalid identifier 'SQLERRM'",
            "BEGIN RETURN UPPER(sqlerrm); END;");
        assertAnswer(
            "null",
            "BEGIN RETURN LEAST(1, sqlcode); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN LET c := UPPER(nosuch_var_x9); RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN IF (NVL(nosuch_var_x9, TRUE)) THEN RETURN 1; END IF; RETURN 2; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN FOR i IN 1 TO NVL(nosuch_var_x9, 2) DO NULL; END FOR; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN TRIM(nosuch_var_x9, 'x'); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier 'NOSUCH_VAR_X9'",
            "BEGIN RETURN DATE_TRUNC(month, nosuch_var_x9); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 17 | invalid identifier 'MONTH'",
            "BEGIN RETURN NVL(month, 1); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier 'MONTH'",
            "BEGIN RETURN DATEADD(month, 1, month); END;");
    }

    @Test
    public void lambdasContextWordsAndUnitPlaces() {
        try {
            answer(
                "CREATE OR REPLACE TABLE nb_t (a INT)");
            answer(
                "INSERT INTO nb_t VALUES (1)");
            answer(
                "CREATE OR REPLACE SEQUENCE nb_seq");
            answer(
                "CREATE OR REPLACE PROCEDURE nb_p1(a INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN NVL(a, "
                    + "nosuch_var_x9); END $$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 44 | invalid identifier 'Y'",
                "BEGIN RETURN ARRAY_SIZE(FILTER([1, 2], y -> y > 1)); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 43 | invalid identifier 'ACC'",
                "BEGIN RETURN REDUCE([1, 2], 0, (acc, v) -> acc + v); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 50 | invalid identifier 'X'",
                "BEGIN RETURN ARRAY_SIZE(TRANSFORM([1], (x INT) -> x + 1)); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 17 | invalid identifier 'CURRENT_DATE'",
                "BEGIN RETURN NVL(CURRENT_DATE, CURRENT_DATE) IS NOT NULL; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 19 | invalid identifier 'SYSDATE'",
                "BEGIN RETURN UPPER(SYSDATE) IS NOT NULL; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                    + "error: error line 1 at position 0 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN DATEDIFF(nosuch_var_x9, CURRENT_DATE(), CURRENT_DATE()); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                    + "error: error line 1 at position 0 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN DATE_PART(nosuch_var_x9, CURRENT_DATE()); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 22 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN LAST_DAY(nosuch_var_x9, month); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 35 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN TRUNC(CURRENT_DATE(), nosuch_var_x9); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 48 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN TIME_SLICE(CURRENT_TIMESTAMP(), 1, nosuch_var_x9) IS NULL; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                    + "error: | invalid value [nosuch_var_x9] for parameter 'EXTRACT date/time part'",
                "BEGIN RETURN EXTRACT(nosuch_var_x9 FROM CURRENT_DATE()); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN EXTRACT(year FROM nosuch_var_x9); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                    + "error: error line 1 at position 0 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN DATE_TRUNC(nosuch_var_x9, CURRENT_DATE()); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN DATEADD(day, nosuch_var_x9, CURRENT_DATE()); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                    + "error: error line 1 at position 0 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN TIMESTAMPADD(nosuch_var_x9, 1, CURRENT_TIMESTAMP()); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                    + "error: error line 1 at position 0 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN TIMEDIFF(nosuch_var_x9, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN nosuch_fn_x9(a => nosuch_var_x9); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 30 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN ARRAY_SIZE(SPLIT(nosuch_var_x9, ',')); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN SYSTEM$TYPEOF(nosuch_var_x9); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 34 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN LET x NUMBER := 0; x := NVL(nosuch_var_x9, 1); RETURN x; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 18 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN CASE (UPPER(nosuch_var_x9)) WHEN 'A' THEN RETURN 1; ELSE RETURN 2; END CASE; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 71 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN RETURN UPPER(sqlerrm) || NVL(nosuch_var_x9, 'x'); END;");
            assertAnswer(
                "Aa",
                "BEGIN LET v := 'a'; RETURN UPPER(v) || LOWER(v); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 45 | invalid identifier 'R.A'",
                "BEGIN FOR r IN (SELECT 1 AS a) DO RETURN NVL(r.a, 0); END FOR; RETURN 9; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 45 | invalid identifier 'R.NOSUCH'",
                "BEGIN FOR r IN (SELECT 1 AS a) DO RETURN NVL(r.nosuch, 0); END FOR; RETURN 9; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 39 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN FOR i IN 1 TO 2 DO RETURN NVL(i, nosuch_var_x9); END FOR; RETURN 9; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier 'NOSUCH_VAR_X9'",
                "DECLARE v INT DEFAULT NVL(nosuch_var_x9, 1); BEGIN RETURN v; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 23 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN LET v INT := ABS(nosuch_var_x9); RETURN v; END;");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS nb_p1(INT)");
            answer(
                "DROP FUNCTION IF EXISTS nb_f1(INT)");
            answer(
                "DROP SEQUENCE IF EXISTS nb_seq");
        }
    }

    @Test
    public void sequencesContainersBindsAndCalls() {
        try {
            answer(
                "CREATE OR REPLACE TABLE nb_t (a INT)");
            answer(
                "INSERT INTO nb_t VALUES (1)");
            answer(
                "CREATE OR REPLACE SEQUENCE nb_seq");
            answer(
                "CREATE OR REPLACE PROCEDURE nb_p1(a INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN NVL(a, "
                    + "nosuch_var_x9); END $$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 17 | invalid identifier 'NB_SEQ'",
                "BEGIN RETURN NVL(nb_seq.NEXTVAL, 0) > 0; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'NB_SEQ'",
                "BEGIN RETURN nb_seq.NEXTVAL > 0; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 29 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN ARRAY_CONSTRUCT(nosuch_var_x9); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 35 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN OBJECT_CONSTRUCT('a', nosuch_var_x9); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 17 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN GET(nosuch_var_x9, 'a'); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN nosuch_var_x9:a; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN TO_CHAR(nosuch_var_x9, 'YYYY'); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 22 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN CAST(NVL(nosuch_var_x9, 1) AS INT); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 17 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN NVL(nosuch_var_x9, 1)::INT; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 19 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN COUNT(nosuch_var_x9); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 17 | invalid identifier '\"nosuch_Var\"'",
                "BEGIN RETURN NVL(\"nosuch_Var\", 1); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 17 | invalid identifier 'nosuch_var_x9'",
                "BEGIN RETURN NVL(:nosuch_var_x9, 1); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 30 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN EXECUTE IMMEDIATE UPPER(nosuch_var_x9); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                    + "error: error line 1 at position 15 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN CALL nb_p1(NVL(nosuch_var_x9, 1)); RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 21 | invalid identifier 'NOSUCH_VAR_X9'",
                "CALL nb_p1(1)");
            assertAnswer(
                "Function NB_F1 successfully created.",
                "CREATE OR REPLACE FUNCTION nb_f1(a INT) RETURNS INT AS $$ BEGIN RETURN NVL(nosuch_var_x9, a); "
                    + "END $$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 18 | invalid identifier 'NOSUCH_VAR_X9'",
                "SELECT nb_f1(1)");
            assertAnswer(
                "2024-01-02",
                "BEGIN LET d := 'day'; RETURN DATEADD(d, 1, '2024-01-01'::DATE); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                    + "error: ['nosuch'] is not a valid date/time component for function DATEADD.",
                "BEGIN RETURN DATEADD('nosuch', 1, '2024-01-01'::DATE); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 17 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN NVL(nosuch_var_x9.NEXTVAL, 0); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 17 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN IFF(nosuch_var_x9 IS NULL, 1, 2); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 25 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN ZEROIFNULL(-nosuch_var_x9); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 19 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN UPPER(nosuch_var_x9 || 'a'); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                    + "error: error line 1 at position 21 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN UPPER((SELECT nosuch_var_x9)); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 20 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN [UPPER(nosuch_var_x9)]; END;");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "BEGIN RETURN 2 ^ 2; END;");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "BEGIN IF (FALSE) THEN RETURN 2 ^ 2; END IF; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 20 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN ABS(1, nosuch_var_x9); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                    + "error: error line 1 at position 0 | not enough arguments for function [ABS()], expected 1, "
                    + "got 0",
                "BEGIN RETURN ABS(); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 25 | invalid identifier 'NOSUCH_VAR_X9'",
                "BEGIN RETURN NVL(1, 2) + nosuch_var_x9; END;");
        } finally {
            answer(
                "DROP PROCEDURE IF EXISTS nb_p1(INT)");
            answer(
                "DROP FUNCTION IF EXISTS nb_f1(INT)");
            answer(
                "DROP SEQUENCE IF EXISTS nb_seq");
        }
    }
}
