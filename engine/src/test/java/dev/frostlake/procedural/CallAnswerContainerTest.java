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
 * A CALL's answer given to an OBJECT, an ARRAY or a VARIANT variable: held as the CALL hands it back and read as
 * JSON by an expression, a JSON null there being SQL NULL for an OBJECT or an ARRAY; given INTO one, judged by the
 * answer's static type as a SELECT ... INTO judges a selected value, so an ARRAY makes a text its one element and
 * an OBJECT refuses a text, NULL among them, and a whole number (all live-verified).
 */
public class CallAnswerContainerTest extends MeasuredCellsTestSupport {

    @Test
    public void heldAnswers() {
        engine.execute("CREATE OR REPLACE PROCEDURE pv58(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "s; END; $$");
        assertAnswer("x", "DECLARE a ARRAY; BEGIN a := (CALL pv58('x')); RETURN a; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : Error parsing "
                + "JSON: unknown keyword \"x\", pos 2",
            "DECLARE a ARRAY; BEGIN a := (CALL pv58('x')); RETURN ARRAY_SIZE(a); END;");
        assertAnswer("x", "DECLARE o OBJECT; BEGIN o := (CALL pv58('x')); RETURN o; END;");
        assertAnswer("OBJECT", "DECLARE o OBJECT; BEGIN o := (CALL pv58('{\"a\": 1}')); RETURN TYPEOF(o); END;");
        assertAnswer("2", "DECLARE a ARRAY; BEGIN a := (CALL pv58('[1,2]')); RETURN ARRAY_SIZE(a); END;");
        assertAnswer("ARRAY", "DECLARE a ARRAY; BEGIN a := (CALL pv58('[1,2]')); RETURN TYPEOF(a); END;");
        assertAnswer("1", "DECLARE o OBJECT; BEGIN o := (CALL pv58('{\"a\": 1}')); RETURN o:a; END;");
        assertAnswer("{\"a\": 1}", "DECLARE o OBJECT; BEGIN o := (CALL pv58('{\"a\": 1}')); RETURN o; END;");
        assertAnswer("OBJECT", "DECLARE v VARIANT; BEGIN v := (CALL pv58('{\"a\": 1}')); RETURN TYPEOF(v); END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : Error parsing "
                + "JSON: unknown keyword \"x\", pos 2",
            "DECLARE o OBJECT; BEGIN o := (CALL pv58('x')); RETURN TYPEOF(o); END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 56 : Failed to cast "
                + "variant value [1] to OBJECT",
            "DECLARE o OBJECT; BEGIN o := (CALL pv58('[1]')); RETURN TYPEOF(o); END;");
        assertAnswer("ARRAY", "DECLARE a ARRAY; BEGIN a := (CALL pv58('{\"a\": 1}')); RETURN TYPEOF(a); END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : SQL "
                + "compilation error: | invalid type [CAST(identity('{\"a\": 1}') AS OBJECT)] for parameter "
                + "'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN CALL pv58('{\"a\": 1}') INTO :o; RETURN TYPEOF(o); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'pv58'.",
            "BEGIN LET o OBJECT := (CALL pv58('{\"a\": 1}')); RETURN TYPEOF(o); END;");
        assertAnswer("2", "DECLARE o OBJECT; BEGIN o := (CALL pv58('{\"a\": 1}')); RETURN o:a::INT + 1; END;");
        assertAnswer("2", "DECLARE a ARRAY; BEGIN a := (CALL pv58('[1,2]')); RETURN a[1]; END;");
    }

    @Test
    public void intoAndHeld() {
        engine.execute("CREATE OR REPLACE PROCEDURE pv58(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "s; END; $$");
        assertAnswer("1", "DECLARE a ARRAY; BEGIN CALL pv58('[1,2]') INTO :a; RETURN ARRAY_SIZE(a); END;");
        assertAnswer("[\"[1,2]\"]", "DECLARE a ARRAY; BEGIN CALL pv58('[1,2]') INTO :a; RETURN a; END;");
        assertAnswer("VARCHAR", "DECLARE v VARIANT; BEGIN CALL pv58('{\"a\": 1}') INTO :v; RETURN TYPEOF(v); END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : SQL "
                + "compilation error: | invalid type [CAST(identity('x') AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN CALL pv58('x') INTO :o; RETURN o; END;");
        assertAnswer("null", "DECLARE a ARRAY; BEGIN a := (CALL pv58(NULL)); RETURN ARRAY_SIZE(a); END;");
        assertAnswer("null", "DECLARE o OBJECT; BEGIN o := (CALL pv58('')); RETURN TYPEOF(o); END;");
        assertAnswer("null", "DECLARE o OBJECT; BEGIN o := (CALL pv58('null')); RETURN TYPEOF(o); END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : Error parsing "
                + "JSON: unknown keyword \"x\", pos 2",
            "DECLARE v VARIANT; BEGIN v := (CALL pv58('x')); RETURN TYPEOF(v); END;");
        assertAnswer("x", "DECLARE v VARIANT; BEGIN v := (CALL pv58('x')); RETURN v; END;");
        assertAnswer("[1,2]", "DECLARE a ARRAY; BEGIN a := (CALL pv58('[1,2]')); RETURN a; END;");
        assertAnswer("[\"a\"]", "DECLARE o OBJECT; BEGIN o := (CALL pv58('{\"a\": 1}')); RETURN OBJECT_KEYS(o); END;");
    }

    @Test
    public void jsonNullsAndAnswerTypes() {
        engine.execute("CREATE OR REPLACE PROCEDURE pv58(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "s; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pv59(s VARCHAR) RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "PARSE_JSON(s); END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pv60() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE a ARRAY; BEGIN "
            + "CALL pv58('[1,2]') INTO :a; RETURN a::VARCHAR; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pv61() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE o OBJECT; "
            + "BEGIN CALL pv58('x') INTO :o; RETURN 'r'; END; $$");
        assertAnswer("null", "DECLARE a ARRAY; BEGIN a := (CALL pv58('null')); RETURN TYPEOF(a); END;");
        assertAnswer("NULL_VALUE", "DECLARE v VARIANT; BEGIN v := (CALL pv58('null')); RETURN TYPEOF(v); END;");
        assertAnswer("true", "DECLARE o OBJECT; BEGIN o := (CALL pv58('null')); RETURN o IS NULL; END;");
        assertAnswer("null", "DECLARE o OBJECT; BEGIN o := (CALL pv58(' null ')); RETURN TYPEOF(o); END;");
        assertAnswer("null", "DECLARE a ARRAY; BEGIN a := (CALL pv58('null')); RETURN ARRAY_SIZE(a); END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : SQL "
                + "compilation error: | invalid type [CAST(identity('null') AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN CALL pv58('null') INTO :o; RETURN TYPEOF(o); END;");
        assertAnswer("null", "DECLARE a ARRAY; BEGIN CALL pv58(NULL) INTO :a; RETURN TYPEOF(a); END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : SQL "
                + "compilation error: | invalid type [CAST(identity(NULL) AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN CALL pv58(NULL) INTO :o; RETURN TYPEOF(o); END;");
        assertAnswer("2", "DECLARE a ARRAY; BEGIN CALL pv59('[1,2]') INTO :a; RETURN ARRAY_SIZE(a); END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : Failed to cast "
                + "variant value 5 to OBJECT",
            "DECLARE o OBJECT; BEGIN CALL pv59('5') INTO :o; RETURN TYPEOF(o); END;");
        assertAnswer("[5]", "DECLARE a ARRAY; BEGIN CALL pv59('5') INTO :a; RETURN a; END;");
        assertAnswer("[\"[1,2]\"]", "CALL pv60()");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL "
                + "compilation error: | invalid type [CAST(identity('x') AS OBJECT)] for parameter 'TO_OBJECT'",
            "CALL pv61()");
        assertAnswer("VARCHAR", "DECLARE v VARIANT; BEGIN CALL pv58('null') INTO :v; RETURN TYPEOF(v); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'pv58'.",
            "BEGIN LET a ARRAY := (CALL pv58('null')); RETURN TYPEOF(a); END;");
        assertAnswer("[\"\"]", "DECLARE a ARRAY; BEGIN CALL pv58('') INTO :a; RETURN a; END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : SQL "
                + "compilation error: | invalid type [CAST(identity('') AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN CALL pv58('') INTO :o; RETURN TYPEOF(o); END;");
    }

    @Test
    public void nullTextsAndNumbers() {
        engine.execute("CREATE OR REPLACE PROCEDURE pv58(s VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "s; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pv62() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN NULL; END; "
            + "$$");
        engine.execute("CREATE OR REPLACE PROCEDURE pv63() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 5; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : SQL "
                + "compilation error: | invalid type [CAST(identity(NULL) AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN SELECT NULL::VARCHAR INTO :o; RETURN TYPEOF(o); END;");
        assertAnswer("null", "DECLARE a ARRAY; BEGIN SELECT NULL::VARCHAR INTO :a; RETURN TYPEOF(a); END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : SQL "
                + "compilation error: | invalid type [CAST(identity(NULL) AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN SELECT NULL INTO :o; RETURN TYPEOF(o); END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : SQL "
                + "compilation error: | invalid type [CAST(TO_NUMBER(NULL, 0, 0) AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN CALL pv62() INTO :o; RETURN TYPEOF(o); END;");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : SQL "
                + "compilation error: | invalid type [CAST(TO_NUMBER('5') AS OBJECT)] for parameter 'TO_OBJECT'",
            "DECLARE o OBJECT; BEGIN CALL pv63() INTO :o; RETURN TYPEOF(o); END;");
        assertAnswer("null", "DECLARE o OBJECT; BEGIN o := (CALL pv58(NULL)); RETURN TYPEOF(o); END;");
        assertAnswer("null", "DECLARE a ARRAY; BEGIN a := (CALL pv58('null')); RETURN a; END;");
        assertAnswer("null", "DECLARE o OBJECT; BEGIN o := (CALL pv58('null')); RETURN o; END;");
        assertAnswer("false", "DECLARE v VARIANT; BEGIN v := (CALL pv58('null')); RETURN v IS NULL; END;");
        assertAnswer("[5]", "DECLARE a ARRAY; BEGIN CALL pv63() INTO :a; RETURN a; END;");
        assertAnswer("null", "DECLARE a ARRAY; BEGIN CALL pv62() INTO :a; RETURN TYPEOF(a); END;");
    }
}
