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

package dev.frostlake.procedural;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A value given to a block's OBJECT or ARRAY variable is read as TO_OBJECT and TO_ARRAY read it: a date, a
 * time, a timestamp or a text given to an OBJECT is refused with the cast's echo, and a time or a text given to an
 * ARRAY is its one element, ["[1, 2]"] - assigned, as a DEFAULT, by a typed LET, as a text expression or
 * selected INTO, where the refusal echoes the selected value under identity. A CALL's answer is held as the
 * CALL handed it back (all live-verified).
 */
public class ContainerVariableAssignmentTest extends MeasuredCellsTestSupport {

    @Test
    public void containerAssignments() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL "
                + "compilation error: | invalid type [CAST(TO_TIME('10:00:00') AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN o := '10:00:00'::TIME; RETURN o; END;");
        answer("DECLARE o OBJECT; BEGIN o := '2024-01-02 03:04:05'::TIMESTAMP_NTZ; RETURN o; END;");
        answer("DECLARE o OBJECT; BEGIN o := '2024-01-02 03:04:05 +05:00'::TIMESTAMP_TZ; RETURN o; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL "
                + "compilation error: | invalid type [CAST(TO_DATE('2024-01-02') AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN o := '2024-01-02'::DATE; RETURN o; END;");
        assertAnswer("[\"10:00:00\"]", "DECLARE a ARRAY; BEGIN a := '10:00:00'::TIME; RETURN a; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL "
                + "compilation error: | invalid type [CAST('{\"a\": 1}' AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN o := '{\"a\": 1}'; RETURN o; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL "
                + "compilation error: | invalid type [CAST('x' AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN o := 'x'; RETURN o; END;");
        assertAnswer("[\"[1, 2]\"]", "DECLARE a ARRAY; BEGIN a := '[1, 2]'; RETURN a; END;");
        assertAnswer("[\"x\"]", "DECLARE a ARRAY; BEGIN a := 'x'; RETURN a; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL "
                + "compilation error: | invalid type [CAST('{\"a\": 1}' AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN o := '{\"a\": 1}'; RETURN TYPEOF(o); END;");
        assertAnswer("1", "DECLARE a ARRAY; BEGIN a := '[1, 2]'; RETURN ARRAY_SIZE(a); END;");
        assertAnswer("REFUSED Declared return type 'VARIANT' is incompatible with actual return type 'DATE'",
            "CREATE OR REPLACE FUNCTION fv55() RETURNS VARIANT AS 'DATE ''2024-01-02'''");
        assertAnswer("REFUSED SQL compilation error: | Unknown functions FV55, FV55.", "SELECT TYPEOF(fv55()), fv55()");
        assertAnswer("REFUSED Declared return type 'VARIANT' is incompatible with actual return type "
                + "'TIMESTAMP_NTZ(9)'",
            "CREATE OR REPLACE FUNCTION fv55() RETURNS VARIANT AS 'TO_TIMESTAMP_NTZ(''2024-01-02 03:04:05'')'");
        assertAnswer("REFUSED SQL compilation error: | Unknown functions FV55, FV55.", "SELECT TYPEOF(fv55()), fv55()");
        assertAnswer("REFUSED Declared return type 'VARIANT' is incompatible with actual return type "
                + "'BINARY(67108864)'",
            "CREATE OR REPLACE FUNCTION fv55() RETURNS VARIANT AS 'X''AB''::BINARY'");
        assertAnswer("REFUSED SQL compilation error: | Unknown functions FV55, FV55.", "SELECT TYPEOF(fv55()), fv55()");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void otherWrittenRoutes() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        engine.execute("CREATE OR REPLACE PROCEDURE pv58(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "s; END; $$");
        assertAnswer("[\"x\"]", "DECLARE a ARRAY DEFAULT 'x'; BEGIN RETURN a; END;");
        assertAnswer("[\"[1, 2]\"]", "DECLARE a ARRAY DEFAULT '[1, 2]'; BEGIN RETURN a; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL "
                + "compilation error: | invalid type [CAST('x' AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT DEFAULT 'x'; BEGIN RETURN o; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL "
                + "compilation error: | invalid type [CAST('{\"a\": 1}' AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT DEFAULT '{\"a\": 1}'; BEGIN RETURN o; END;");
        assertAnswer("[\"x\"]", "BEGIN LET a ARRAY := 'x'; RETURN a; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL "
                + "compilation error: | invalid type [CAST('x' AS OBJECT)] for parameter 'TO_OBJECT'",
            "BEGIN LET o OBJECT := 'x'; RETURN o; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL "
                + "compilation error: | invalid type [CAST('{\"a\": 1}' AS OBJECT)] for parameter 'TO_OBJECT'",
            "BEGIN LET o OBJECT := '{\"a\": 1}'; RETURN o; END;");
        assertAnswer("[\"x\"]", "DECLARE s VARCHAR DEFAULT 'x'; a ARRAY; BEGIN a := s; RETURN a; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 59 : SQL "
                + "compilation error: | invalid type [CAST('{\"a\": 1}' AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE s VARCHAR DEFAULT '{\"a\": 1}'; o OBJECT; BEGIN o := s; RETURN o; END;");
        assertAnswer("[\"x\"]", "DECLARE a ARRAY; BEGIN SELECT 'x' INTO :a; RETURN a; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : SQL "
                + "compilation error: | invalid type [CAST(identity('{\"a\": 1}') AS OBJECT)] for parameter "
                + "'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN SELECT '{\"a\": 1}' INTO :o; RETURN o; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : SQL "
                + "compilation error: | invalid type [CAST(identity('x') AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN SELECT 'x' INTO :o; RETURN o; END;");
        assertAnswer("x", "DECLARE a ARRAY; BEGIN a := (CALL pv58('x')); RETURN a; END;");
        assertAnswer("x", "DECLARE o OBJECT; BEGIN o := (CALL pv58('x')); RETURN o; END;");
        assertAnswer("[\"xy\"]", "DECLARE a ARRAY; BEGIN a := 'x' || 'y'; RETURN a; END;");
        assertAnswer("[\"5\"]", "DECLARE a ARRAY; BEGIN a := TO_VARCHAR(5); RETURN a; END;");
        assertAnswer("[\"x\"]", "DECLARE a ARRAY; BEGIN a := (SELECT 'x'); RETURN a; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL "
                + "compilation error: | invalid type [CAST('x' || 'y' AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN o := 'x' || 'y'; RETURN o; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL "
                + "compilation error: | invalid type [CAST((SELECT '{\"a\": 1}' AS \"'{\"A\": 1}'\" FROM (VALUES "
                + "(NULL)) DUAL) AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN o := (SELECT '{\"a\": 1}'); RETURN o; END;");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void selectedIntoContainers() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        engine.execute("CREATE OR REPLACE TABLE dd59 (c VARCHAR, v VARIANT)");
        engine.execute("INSERT INTO dd59 SELECT 'x', PARSE_JSON('\"y\"')");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : SQL "
                + "compilation error: | invalid type [CAST(identity('x') AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN SELECT c INTO :o FROM dd59; RETURN o; END;");
        assertAnswer("[\"x\"]", "DECLARE a ARRAY; BEGIN SELECT c INTO :a FROM dd59; RETURN a; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : SQL "
                + "compilation error: | invalid type [CAST(identity('xy') AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN SELECT 'x' || 'y' INTO :o; RETURN o; END;");
        assertAnswer("null", "DECLARE o OBJECT; BEGIN SELECT c INTO :o FROM dd59 WHERE FALSE; RETURN o; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : Failed to cast "
                + "variant value \"y\" to OBJECT",
            "DECLARE o OBJECT; BEGIN SELECT v INTO :o FROM dd59; RETURN o; END;");
        assertAnswer("[\"y\"]", "DECLARE a ARRAY; BEGIN SELECT v INTO :a FROM dd59; RETURN a; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : SQL "
                + "compilation error: | invalid type [CAST(identity('x') AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN SELECT c AS k INTO :o FROM dd59; RETURN o; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 34 : SQL "
                + "compilation error: | invalid type [CAST(identity('x') AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE n NUMBER; o OBJECT; BEGIN SELECT 1, 'x' INTO :n, :o; RETURN o; END;");
        assertAnswer("null", "DECLARE a ARRAY; BEGIN SELECT 'x' INTO :a WHERE FALSE; RETURN a; END;");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST((SELECT '{\"a\": 1}' AS \"'{\"A\": 1}'\" "
                + "FROM (VALUES (NULL)) DUAL) AS OBJECT)] for parameter 'TO_OBJECT'",
            "SELECT (SELECT '{\"a\": 1}')::OBJECT");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST((SELECT '{\"a\": 1}' AS \"'{\"A\": 1}'\", 2 "
                + "AS \"2\" FROM (VALUES (NULL)) DUAL) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            "SELECT (SELECT '{\"a\": 1}', 2)::INT");
        assertAnswer("REFUSED SQL compilation error: | invalid type [CAST((SELECT 'x' AS \"K\" FROM (VALUES (NULL)) "
                + "DUAL) AS OBJECT)] for parameter 'TO_OBJECT'",
            "SELECT (SELECT 'x' AS k)::OBJECT");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void variantIntoContainers() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : Failed to cast "
                + "variant value \"y\" to OBJECT",
            "DECLARE o OBJECT; BEGIN o := PARSE_JSON('\"y\"'); RETURN o; END;");
        assertAnswer("[\"y\"]", "DECLARE a ARRAY; BEGIN a := PARSE_JSON('\"y\"'); RETURN a; END;");
        assertAnswer("[1]", "DECLARE a ARRAY; BEGIN a := PARSE_JSON('[1]'); RETURN a; END;");
        assertAnswer("{\"k\":1}", "DECLARE o OBJECT; BEGIN o := PARSE_JSON('{\"k\": 1}'); RETURN o; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : Failed to cast "
                + "variant value 5 to OBJECT",
            "DECLARE o OBJECT; BEGIN o := PARSE_JSON('5'); RETURN o; END;");
        assertAnswer("[5]", "DECLARE a ARRAY; BEGIN a := PARSE_JSON('5'); RETURN a; END;");
        assertAnswer("null", "DECLARE a ARRAY; BEGIN a := PARSE_JSON('null'); RETURN a; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : Failed to cast "
                + "variant value [1] to OBJECT",
            "DECLARE o OBJECT; BEGIN o := PARSE_JSON('[1]'); RETURN o; END;");
        assertAnswer("[{\"k\":1}]", "DECLARE a ARRAY; BEGIN a := PARSE_JSON('{\"k\": 1}'); RETURN a; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : Failed to cast "
                + "variant value 5 to OBJECT",
            "DECLARE o OBJECT; BEGIN SELECT PARSE_JSON('5') INTO :o; RETURN o; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : Failed to cast "
                + "variant value [1] to OBJECT",
            "DECLARE o OBJECT; BEGIN SELECT PARSE_JSON('[1]') INTO :o; RETURN o; END;");
        assertAnswer("[{\"k\":1}]", "DECLARE a ARRAY; BEGIN SELECT PARSE_JSON('{\"k\": 1}') INTO :a; RETURN a; END;");
        assertAnswer("2", "DECLARE a ARRAY; BEGIN SELECT PARSE_JSON('[1, 2]') INTO :a; RETURN ARRAY_SIZE(a); END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : Failed to cast "
                + "variant value \"y\" to OBJECT",
            "DECLARE o OBJECT DEFAULT PARSE_JSON('\"y\"'); BEGIN RETURN o; END;");
        assertAnswer("[\"y\"]", "DECLARE a ARRAY DEFAULT PARSE_JSON('\"y\"'); BEGIN RETURN a; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 66 : Failed to cast "
                + "variant value \"y\" to OBJECT",
            "DECLARE v VARIANT DEFAULT PARSE_JSON('\"y\"'); o OBJECT; BEGIN o := v; RETURN o; END;");
        assertAnswer("[\"it's\"]", "DECLARE a ARRAY; BEGIN SELECT 'it''s' INTO :a; RETURN a; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : SQL "
                + "compilation error: | invalid type [CAST(identity('it''s') AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN SELECT 'it''s' INTO :o; RETURN o; END;");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
