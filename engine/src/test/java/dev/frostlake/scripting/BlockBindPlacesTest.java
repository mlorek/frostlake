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
 * A block's binds: a refusal is placed in the statement as the block wrote it, a user-defined function's
 * arguments are judged by the declared types inside a block as outside it, and a variable holding NULL picks an
 * overload by its declaration (live-verified).
 */
public class BlockBindPlacesTest extends MeasuredCellsTestSupport {

    @Test
    public void aRefusalIsPlacedInTheWrittenStatement() {
        try {
            answer(
                "CREATE OR REPLACE FUNCTION fd(d DATE) RETURNS VARCHAR AS $$ 'date' $$");
            answer(
                "CREATE OR REPLACE FUNCTION fo(n NUMBER) RETURNS VARCHAR AS $$ 'num' $$");
            answer(
                "CREATE OR REPLACE FUNCTION fo(v VARCHAR) RETURNS VARCHAR AS $$ 'var' $$");
            answer(
                "ALTER SESSION SET USE_CACHED_RESULT = FALSE");
            answer(
                "DECLARE s VARCHAR(134217728); res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s) AS c25); "
                    + "RETURN TABLE(res); END;");
            answer(
                "DECLARE s VARCHAR(134217728); t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:s) AS c27 INTO :t; RETURN "
                    + "t; END;");
            answer(
                "DECLARE s VARCHAR(134217728); res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(UPPER(:s)) AS "
                    + "c29); RETURN TABLE(res); END;");
            answer(
                "DECLARE s VARCHAR(134217728); res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(COALESCE(:s, "
                    + "'ab')) AS c30); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 47 : SQL compilation "
                    + "error: error line 1 at position 8 | Invalid argument types for function 'ABS': (BOOLEAN)",
                "DECLARE s BOOLEAN; res RESULTSET; BEGIN res := (SELECT ABS(:s) AS c62); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 44 : SQL compilation "
                    + "error: error line 1 at position 8 | Invalid argument types for function 'ABS': (DATE)",
                "DECLARE s DATE; res RESULTSET; BEGIN res := (SELECT ABS(:s) AS c63); RETURN TABLE(res); END;");
            assertAnswer(
                "VARCHAR(3)[LOB]",
                "DECLARE s VARCHAR(10); res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(fo(:s)) AS c64); RETURN "
                    + "TABLE(res); END;");
            assertAnswer(
                "VARCHAR(3)[LOB]",
                "DECLARE s NUMBER(10,2); res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(fo(:s)) AS c65); "
                    + "RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 50 : SQL compilation "
                    + "error: error line 1 at position 11 | Invalid argument types for function '+': (BINARY(10), "
                    + "NUMBER(1,0))",
                "DECLARE s BINARY(10); res RESULTSET; BEGIN res := (SELECT :s + 1 AS c68); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 50 : SQL compilation "
                    + "error: error line 1 at position 8 | Invalid argument types for function 'NEGATE': (BINARY(10))",
                "DECLARE s BINARY(10); res RESULTSET; BEGIN res := (SELECT -:s AS c72); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 50 : SQL compilation "
                    + "error: error line 1 at position 11 | Invalid argument types for function '*': (BINARY(10), "
                    + "NUMBER(1,0))",
                "DECLARE s BINARY(10); res RESULTSET; BEGIN res := (SELECT :s * 2 AS c73); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 19 | Invalid argument types for function "
                    + "'+': (ARRAY, NULL)",
                "SELECT NULL::ARRAY + NULL AS q18");
        } finally {
            answer(
                "ALTER SESSION UNSET USE_CACHED_RESULT");
        }
    }

    @Test
    public void aUserFunctionsArgumentsAreJudgedInABlock() {
        try {
            answer(
                "CREATE OR REPLACE FUNCTION fd(d DATE) RETURNS VARCHAR AS $$ 'date' $$");
            answer(
                "CREATE OR REPLACE FUNCTION fo(n NUMBER) RETURNS VARCHAR AS $$ 'num' $$");
            answer(
                "CREATE OR REPLACE FUNCTION fo(v VARCHAR) RETURNS VARCHAR AS $$ 'var' $$");
            answer(
                "ALTER SESSION SET USE_CACHED_RESULT = FALSE");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 52 : SQL compilation "
                    + "error: error line 1 at position 8 | Invalid argument types for function 'FD': (NUMBER(11,2))",
                "DECLARE s NUMBER(10,2); res RESULTSET; BEGIN res := (SELECT fd(:s + 1) AS q28); RETURN "
                    + "TABLE(res); END;");
            answer(
                "DECLARE s VARCHAR(10); res RESULTSET; BEGIN res := (SELECT fo(:s, 1) AS q30); RETURN TABLE(res); "
                    + "END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : SQL compilation "
                    + "error: error line 1 at position 8 | Invalid argument types for function 'FD': (BOOLEAN)",
                "DECLARE s BOOLEAN DEFAULT TRUE; res RESULTSET; BEGIN res := (SELECT fd(:s) AS v5); RETURN "
                    + "TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 72 : SQL compilation "
                    + "error: error line 1 at position 8 | Invalid argument types for function 'FD': (ARRAY)",
                "DECLARE s ARRAY DEFAULT ARRAY_CONSTRUCT(1); res RESULTSET; BEGIN res := (SELECT fd(:s) AS v15); "
                    + "RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 72 : SQL compilation "
                    + "error: error line 1 at position 8 | Invalid argument types for function 'FO': (ARRAY)",
                "DECLARE s ARRAY DEFAULT ARRAY_CONSTRUCT(1); res RESULTSET; BEGIN res := (SELECT fo(:s) AS v16); "
                    + "RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 58 : SQL compilation "
                    + "error: error line 1 at position 15 | Invalid argument types for function 'FD': (NUMBER(10,2))",
                "DECLARE s NUMBER(10,2) DEFAULT 1.5; t VARCHAR; BEGIN t := fd(s); RETURN t; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL compilation "
                    + "error: error line 1 at position 15 | Invalid argument types for function 'FD': (BOOLEAN)",
                "DECLARE s BOOLEAN DEFAULT TRUE; t VARCHAR; BEGIN t := fd(s); RETURN t; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 46 : SQL compilation "
                    + "error: error line 1 at position 15 | Invalid argument types for function 'FD': (NUMBER(10,2))",
                "DECLARE s NUMBER(10,2); t VARCHAR; BEGIN t := fd(s); RETURN t; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL compilation "
                    + "error: error line 1 at position 15 | Invalid argument types for function 'FD': (BOOLEAN)",
                "DECLARE s BOOLEAN; t VARCHAR; BEGIN t := fd(s); RETURN t; END;");
            assertAnswer(
                "var",
                "DECLARE s BOOLEAN; t VARCHAR; BEGIN t := fo(s); RETURN t; END;");
            assertAnswer(
                "var",
                "DECLARE s DATE; t VARCHAR; BEGIN t := fo(s); RETURN t; END;");
            assertAnswer(
                "var",
                "DECLARE s VARCHAR(10); t VARCHAR; BEGIN t := fo(s); RETURN t; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 36 : SQL compilation "
                    + "error: error line 1 at position 8 | Invalid argument types for function 'FD': (NUMBER(2,1))",
                "DECLARE res RESULTSET; BEGIN res := (SELECT fd(1.5) AS v36); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 36 : SQL compilation "
                    + "error: error line 1 at position 8 | Invalid argument types for function 'FD': (BOOLEAN)",
                "DECLARE res RESULTSET; BEGIN res := (SELECT fd(TRUE) AS v37); RETURN TABLE(res); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                    + "error: error line 1 at position 7 | Invalid argument types for function 'FD': (NUMBER(2,1))",
                "BEGIN RETURN fd(1.5); END;");
        } finally {
            answer(
                "ALTER SESSION UNSET USE_CACHED_RESULT");
        }
    }
}
