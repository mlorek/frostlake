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
 * What a VARIANT, ARRAY or OBJECT variable of a block holds: the JSON of the value it is given, read back. A
 * DATE, a TIME, a timestamp, a BINARY and a UUID stored in one - by a DECLARE default, an assignment, a typed
 * LET, a SELECT INTO, a FETCH, a CALL's argument or its INTO target, a field of a loop's record - are the text
 * they print as, whole or as a member of an array or an object, so TYPEOF answers VARCHAR where the value read
 * straight from a query keeps its type. A number keeps its family - a DOUBLE stays a DOUBLE in a call, a
 * DECIMAL a DECIMAL, an INTEGER an INTEGER - though a DOUBLE is the number its sixteen digits of JSON say. A
 * date given to an ARRAY variable is its one element and to an OBJECT variable is refused. A FLOAT variable or
 * parameter stays a FLOAT when an expression hands it to a function, and a call written as another call's
 * argument hands on what it answers untouched: a DATE inside an array it built is a DATE to the call around
 * it. Every answer is live-verified.
 */
public class SemiStructuredVariableTest extends MeasuredCellsTestSupport {

    @Test
    public void whatAVariantVariableHolds() {
        assertAnswer(
            "DOUBLE | DATE",
            "SELECT TYPEOF(TO_VARIANT(3.5::FLOAT)) || ' | ' || TYPEOF(TO_VARIANT('2024-01-02'::DATE))");
        assertAnswer(
            "Function TV successfully created.",
            "CREATE OR REPLACE PROCEDURE tv(v VARIANT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN TYPEOF(v); "
                + "END; $$");
        assertAnswer(
            "Function TV2 successfully created.",
            "CREATE OR REPLACE PROCEDURE tv2(v VARIANT) RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; "
                + "BEGIN SELECT TYPEOF(:v) INTO :r; RETURN r; END; $$");
        assertAnswer(
            "Function TV3 successfully created.",
            "CREATE OR REPLACE PROCEDURE tv3(v VARIANT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN TYPEOF(v) "
                + "|| ' / ' || NVL(TO_JSON(v), 'null') || ' / ' || NVL(v::VARCHAR, 'null'); END; $$");
        assertAnswer(
            "DOUBLE",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(3.5::FLOAT); RETURN TYPEOF(v); END; $$");
        assertAnswer(
            "DOUBLE",
            "CALL tv(TO_VARIANT(3.5::FLOAT))");
        assertAnswer(
            "DOUBLE",
            "CALL tv2(TO_VARIANT(3.5::FLOAT))");
        assertAnswer(
            "DOUBLE / 3.500000000000000e+00 / 3.5",
            "CALL tv3(TO_VARIANT(3.5::FLOAT))");
        assertAnswer(
            "VARCHAR",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT('2024-01-02'::DATE); RETURN TYPEOF(v); "
                + "END; $$");
        assertAnswer(
            "VARCHAR",
            "CALL tv(TO_VARIANT('2024-01-02'::DATE))");
        assertAnswer(
            "VARCHAR",
            "CALL tv2(TO_VARIANT('2024-01-02'::DATE))");
        assertAnswer(
            "VARCHAR / \"2024-01-02\" / 2024-01-02",
            "CALL tv3(TO_VARIANT('2024-01-02'::DATE))");
        assertAnswer(
            "VARCHAR",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT('2024-01-02 10:00:00'::TIMESTAMP_NTZ); "
                + "RETURN TYPEOF(v); END; $$");
        assertAnswer(
            "VARCHAR",
            "CALL tv(TO_VARIANT('2024-01-02 10:00:00'::TIMESTAMP_NTZ))");
        assertAnswer(
            "VARCHAR",
            "CALL tv2(TO_VARIANT('2024-01-02 10:00:00'::TIMESTAMP_NTZ))");
        answer(
            "CALL tv3(TO_VARIANT('2024-01-02 10:00:00'::TIMESTAMP_NTZ))");
        assertAnswer(
            "VARCHAR",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(TO_BINARY('AB', 'HEX')); RETURN "
                + "TYPEOF(v); END; $$");
        assertAnswer(
            "VARCHAR",
            "CALL tv(TO_VARIANT(TO_BINARY('AB', 'HEX')))");
        assertAnswer(
            "VARCHAR",
            "CALL tv2(TO_VARIANT(TO_BINARY('AB', 'HEX')))");
        assertAnswer(
            "VARCHAR / \"AB\" / AB",
            "CALL tv3(TO_VARIANT(TO_BINARY('AB', 'HEX')))");
        assertAnswer(
            "VARCHAR",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT('10:11:12'::TIME); RETURN TYPEOF(v); "
                + "END; $$");
        assertAnswer(
            "VARCHAR",
            "CALL tv(TO_VARIANT('10:11:12'::TIME))");
        assertAnswer(
            "VARCHAR",
            "CALL tv2(TO_VARIANT('10:11:12'::TIME))");
        assertAnswer(
            "VARCHAR / \"10:11:12\" / 10:11:12",
            "CALL tv3(TO_VARIANT('10:11:12'::TIME))");
        assertAnswer(
            "INTEGER",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(7); RETURN TYPEOF(v); END; $$");
        assertAnswer(
            "INTEGER",
            "CALL tv(TO_VARIANT(7))");
        assertAnswer(
            "INTEGER",
            "CALL tv2(TO_VARIANT(7))");
        assertAnswer(
            "INTEGER / 7 / 7",
            "CALL tv3(TO_VARIANT(7))");
        assertAnswer(
            "DECIMAL",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(1.50); RETURN TYPEOF(v); END; $$");
        assertAnswer(
            "DECIMAL",
            "CALL tv(TO_VARIANT(1.50))");
        assertAnswer(
            "DECIMAL",
            "CALL tv2(TO_VARIANT(1.50))");
        assertAnswer(
            "DECIMAL / 1.5 / 1.5",
            "CALL tv3(TO_VARIANT(1.50))");
        assertAnswer(
            "VARCHAR",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT('abc'); RETURN TYPEOF(v); END; $$");
        assertAnswer(
            "VARCHAR",
            "CALL tv(TO_VARIANT('abc'))");
        assertAnswer(
            "VARCHAR",
            "CALL tv2(TO_VARIANT('abc'))");
        assertAnswer(
            "VARCHAR / \"abc\" / abc",
            "CALL tv3(TO_VARIANT('abc'))");
        assertAnswer(
            "BOOLEAN",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(TRUE); RETURN TYPEOF(v); END; $$");
        assertAnswer(
            "BOOLEAN",
            "CALL tv(TO_VARIANT(TRUE))");
        assertAnswer(
            "BOOLEAN",
            "CALL tv2(TO_VARIANT(TRUE))");
        assertAnswer(
            "BOOLEAN / true / true",
            "CALL tv3(TO_VARIANT(TRUE))");
        assertAnswer(
            "ARRAY",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(ARRAY_CONSTRUCT(1, 2)); RETURN "
                + "TYPEOF(v); END; $$");
        assertAnswer(
            "ARRAY",
            "CALL tv(TO_VARIANT(ARRAY_CONSTRUCT(1, 2)))");
        assertAnswer(
            "ARRAY",
            "CALL tv2(TO_VARIANT(ARRAY_CONSTRUCT(1, 2)))");
        assertAnswer(
            "ARRAY / [1,2] / [1,2]",
            "CALL tv3(TO_VARIANT(ARRAY_CONSTRUCT(1, 2)))");
        assertAnswer(
            "OBJECT",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(OBJECT_CONSTRUCT('a', 1.5::FLOAT)); "
                + "RETURN TYPEOF(v); END; $$");
        assertAnswer(
            "OBJECT",
            "CALL tv(TO_VARIANT(OBJECT_CONSTRUCT('a', 1.5::FLOAT)))");
        assertAnswer(
            "OBJECT",
            "CALL tv2(TO_VARIANT(OBJECT_CONSTRUCT('a', 1.5::FLOAT)))");
        assertAnswer(
            "OBJECT / {\"a\":1.500000000000000e+00} / {\"a\":1.500000000000000e+00}",
            "CALL tv3(TO_VARIANT(OBJECT_CONSTRUCT('a', 1.5::FLOAT)))");
        assertAnswer(
            "NULL_VALUE",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := PARSE_JSON('null'); RETURN TYPEOF(v); END; $$");
        assertAnswer(
            "NULL_VALUE",
            "CALL tv(PARSE_JSON('null'))");
        assertAnswer(
            "NULL_VALUE",
            "CALL tv2(PARSE_JSON('null'))");
        assertAnswer(
            "NULL_VALUE / null / null",
            "CALL tv3(PARSE_JSON('null'))");
        assertAnswer(
            "VARCHAR",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT('2024-01-02 10:00:00 "
                + "+01:00'::TIMESTAMP_TZ); RETURN TYPEOF(v); END; $$");
        assertAnswer(
            "VARCHAR",
            "CALL tv(TO_VARIANT('2024-01-02 10:00:00 +01:00'::TIMESTAMP_TZ))");
        assertAnswer(
            "VARCHAR",
            "CALL tv2(TO_VARIANT('2024-01-02 10:00:00 +01:00'::TIMESTAMP_TZ))");
        answer(
            "CALL tv3(TO_VARIANT('2024-01-02 10:00:00 +01:00'::TIMESTAMP_TZ))");
        assertAnswer(
            "VARCHAR",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT('2024-01-02'::DATE); LET y := "
                + "TYPEOF(v); RETURN y; END; $$");
        assertAnswer(
            "no date",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; r VARCHAR; BEGIN v := TO_VARIANT('2024-01-02'::DATE); IF "
                + "(IS_DATE(v)) THEN r := 'a date'; ELSE r := 'no date'; END IF; RETURN r; END; $$");
        answer(
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(3.5::FLOAT); RETURN IS_DOUBLE(v) || ' "
                + "' || IS_DECIMAL(v); END; $$");
        assertAnswer(
            "2024-01-03",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT('2024-01-02'::DATE); RETURN "
                + "DATEADD(day, 1, v::DATE); END; $$");
        assertAnswer(
            "2024-01-02",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT('2024-01-02'::DATE); RETURN v; END; $$");
        assertAnswer(
            "3.5",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(3.5::FLOAT); RETURN v; END; $$");
        assertAnswer(
            "DOUBLE",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; w VARIANT; BEGIN v := TO_VARIANT(3.5::FLOAT); w := v; RETURN "
                + "TYPEOF(w); END; $$");
        assertAnswer(
            "DOUBLE",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT DEFAULT TO_VARIANT(3.5::FLOAT); BEGIN RETURN TYPEOF(v); END; "
                + "$$");
        assertAnswer(
            "DOUBLE",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(3.5::FLOAT); RETURN "
                + "TYPEOF(ARRAY_CONSTRUCT(v)[0]); END; $$");
        assertAnswer(
            "null",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(TO_BINARY('AB', 'HEX')); RETURN "
                + "AS_BINARY(v)::VARCHAR; END; $$");
        assertAnswer(
            "VARCHAR",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN SELECT TO_VARIANT('2024-01-02'::DATE) INTO :v; RETURN "
                + "TYPEOF(v); END; $$");
        assertAnswer(
            "DATE",
            "EXECUTE IMMEDIATE $$ DECLARE d DATE DEFAULT '2024-01-02'; BEGIN RETURN TYPEOF(TO_VARIANT(d)); END; $$");
        assertAnswer(
            "DOUBLE",
            "EXECUTE IMMEDIATE $$ DECLARE f FLOAT DEFAULT 3.5; BEGIN RETURN TYPEOF(TO_VARIANT(f)); END; $$");
    }

    @Test
    public void datesTimesAndBinariesInContainers() {
        assertAnswer(
            "VARCHAR / \"2024_01_02 10;00;00.123\"",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT('2024-01-02 "
                + "10:00:00.123'::TIMESTAMP_NTZ); RETURN TYPEOF(v) || ' / ' || REPLACE(REPLACE(TO_JSON(v), '-', '_'),"
                + " ':', ';'); END; $$");
        assertAnswer(
            "VARCHAR / \"2024_01_02 10;00;00.000 +0100\"",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT('2024-01-02 10:00:00 "
                + "+01:00'::TIMESTAMP_TZ); RETURN TYPEOF(v) || ' / ' || REPLACE(REPLACE(TO_JSON(v), '-', '_'), ':', "
                + "';'); END; $$");
        answer(
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT('2024-01-02 10:00:00'::TIMESTAMP_LTZ); "
                + "RETURN TYPEOF(v) || ' / ' || REPLACE(REPLACE(TO_JSON(v), '-', '_'), ':', ';'); END; $$");
        assertAnswer(
            "VARCHAR / \"10;11;12\"",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT('10:11:12.5'::TIME); RETURN TYPEOF(v) "
                + "|| ' / ' || REPLACE(REPLACE(TO_JSON(v), '-', '_'), ':', ';'); END; $$");
        assertAnswer(
            "VARCHAR / \"2024_01_02\"",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT('2024-01-02'::DATE); RETURN TYPEOF(v) "
                + "|| ' / ' || REPLACE(REPLACE(TO_JSON(v), '-', '_'), ':', ';'); END; $$");
        assertAnswer(
            "VARCHAR / \"AB\"",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(TO_BINARY('AB', 'HEX')); RETURN "
                + "TYPEOF(v) || ' / ' || REPLACE(REPLACE(TO_JSON(v), '-', '_'), ':', ';'); END; $$");
        assertAnswer(
            "VARCHAR / \"68656C6C6F\"",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(TO_BINARY('hello', 'UTF-8')); RETURN "
                + "TYPEOF(v) || ' / ' || REPLACE(REPLACE(TO_JSON(v), '-', '_'), ':', ';'); END; $$");
        assertAnswer(
            "VARCHAR DOUBLE VARCHAR DECIMAL INTEGER / [\"2024-01-02\",3.500000000000000e+00,\"AB\",1.5,7]",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := ARRAY_CONSTRUCT('2024-01-02'::DATE, 3.5::FLOAT, "
                + "TO_BINARY('AB', 'HEX'), 1.50, 7); RETURN TYPEOF(v[0]) || ' ' || TYPEOF(v[1]) || ' ' || "
                + "TYPEOF(v[2]) || ' ' || TYPEOF(v[3]) || ' ' || TYPEOF(v[4]) || ' / ' || TO_JSON(v); END; $$");
        assertAnswer(
            "VARCHAR DOUBLE / [\"2024-01-02\",3.500000000000000e+00]",
            "EXECUTE IMMEDIATE $$ DECLARE v ARRAY; BEGIN v := ARRAY_CONSTRUCT('2024-01-02'::DATE, 3.5::FLOAT); "
                + "RETURN TYPEOF(v[0]) || ' ' || TYPEOF(v[1]) || ' / ' || TO_JSON(v); END; $$");
        assertAnswer(
            "VARCHAR DOUBLE / {\"d\":\"2024-01-02\",\"f\":3.500000000000000e+00}",
            "EXECUTE IMMEDIATE $$ DECLARE v OBJECT; BEGIN v := OBJECT_CONSTRUCT('d', '2024-01-02'::DATE, 'f', "
                + "3.5::FLOAT); RETURN TYPEOF(v:d) || ' ' || TYPEOF(v:f) || ' / ' || TO_JSON(v); END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 |  variable 'V' cannot have its type "
                + "inferred from initializer",
            "EXECUTE IMMEDIATE $$ BEGIN LET v := TO_VARIANT('2024-01-02'::DATE); RETURN TYPEOF(v); END; $$");
        assertAnswer(
            "VARCHAR",
            "EXECUTE IMMEDIATE $$ BEGIN LET v VARIANT := TO_VARIANT('2024-01-02'::DATE); RETURN TYPEOF(v); END; $$");
        assertAnswer(
            "DOUBLE FLOAT[DOUBLE]",
            "EXECUTE IMMEDIATE $$ DECLARE f FLOAT DEFAULT 3.5; BEGIN RETURN TYPEOF(TO_VARIANT(f * 2)) || ' ' || "
                + "SYSTEM$TYPEOF(f); END; $$");
        assertAnswer(
            "DECIMAL NUMBER(10,2)[SB8]",
            "EXECUTE IMMEDIATE $$ DECLARE n NUMBER(10,2) DEFAULT 3.5; BEGIN RETURN TYPEOF(TO_VARIANT(n)) || ' ' "
                + "|| SYSTEM$TYPEOF(n); END; $$");
        assertAnswer(
            "DOUBLE FLOAT[DOUBLE]",
            "EXECUTE IMMEDIATE $$ BEGIN LET f := 3.5::FLOAT; RETURN TYPEOF(TO_VARIANT(f)) || ' ' || "
                + "SYSTEM$TYPEOF(f); END; $$");
        assertAnswer(
            "DOUBLE 3.000000000000000e+00",
            "EXECUTE IMMEDIATE $$ DECLARE f FLOAT DEFAULT 3; BEGIN RETURN TYPEOF(TO_VARIANT(f)) || ' ' || "
                + "TO_JSON(TO_VARIANT(f)); END; $$");
        assertAnswer(
            "3.5",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(3.5::FLOAT); RETURN v; END; $$");
        assertAnswer(
            "DOUBLE / 3.000000000000000e+00",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(3::FLOAT); RETURN TYPEOF(v) || ' / ' "
                + "|| TO_JSON(v); END; $$");
        assertAnswer(
            "VARCHAR",
            "EXECUTE IMMEDIATE $$ DECLARE d DATE DEFAULT '2024-01-02'; v VARIANT; BEGIN v := d; RETURN TYPEOF(v); "
                + "END; $$");
        assertAnswer(
            "2024-01-03",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT('2024-01-02'::DATE); RETURN v::DATE + "
                + "1; END; $$");
        assertAnswer(
            "Function TA successfully created.",
            "CREATE OR REPLACE PROCEDURE ta(v ARRAY) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN TYPEOF(v[0]) "
                + "|| ' ' || TYPEOF(v[1]) || ' / ' || TO_JSON(v); END; $$");
        assertAnswer(
            "VARCHAR DOUBLE / [\"2024-01-02\",3.500000000000000e+00]",
            "CALL ta(ARRAY_CONSTRUCT('2024-01-02'::DATE, 3.5::FLOAT))");
        assertAnswer(
            "Function TF successfully created.",
            "CREATE OR REPLACE PROCEDURE tf(f FLOAT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "TYPEOF(TO_VARIANT(f)); END; $$");
        assertAnswer(
            "DOUBLE",
            "CALL tf(3.5)");
        assertAnswer(
            "DOUBLE",
            "CALL tf(3)");
        assertAnswer(
            "DATE VARCHAR",
            "SELECT TYPEOF(TO_VARIANT('2024-01-02'::DATE)) || ' ' || "
                + "TYPEOF(PARSE_JSON(TO_JSON(TO_VARIANT('2024-01-02'::DATE))))");
    }

    @Test
    public void fetchedReturnedAndNestedValues() {
        assertAnswer(
            "rounded 3.000000000000000e-01",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(0.1::FLOAT + 0.2::FLOAT); RETURN "
                + "IFF(v::FLOAT = 0.3::FLOAT, 'rounded', 'kept') || ' ' || TO_JSON(v); END; $$");
        assertAnswer(
            "kept 3.000000000000000e-01",
            "SELECT IFF(TO_VARIANT(0.1::FLOAT + 0.2::FLOAT)::FLOAT = 0.3::FLOAT, 'rounded', 'kept') || ' ' || "
                + "TO_JSON(TO_VARIANT(0.1::FLOAT + 0.2::FLOAT))");
        assertAnswer(
            "3 / 3",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(3::FLOAT); RETURN v::VARCHAR || ' / ' "
                + "|| TO_VARCHAR(v); END; $$");
        assertAnswer(
            "3.0 / 3.0",
            "SELECT TO_VARIANT(3::FLOAT)::VARCHAR || ' / ' || TO_VARCHAR(TO_VARIANT(3::FLOAT))");
        assertAnswer(
            "VARCHAR",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := "
                + "TO_VARIANT('01234567-89ab-cdef-0123-456789abcdef'::UUID); RETURN TYPEOF(v); END; $$");
        assertAnswer(
            "UUID",
            "SELECT TYPEOF(TO_VARIANT('01234567-89ab-cdef-0123-456789abcdef'::UUID))");
        assertAnswer(
            "DATE",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN TYPEOF(GET(ARRAY_CONSTRUCT('2024-01-02'::DATE), 0)); END; $$");
        assertAnswer(
            "DATE",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN TYPEOF(ARRAY_CONSTRUCT('2024-01-02'::DATE)[0]); END; $$");
        assertAnswer(
            "DATE",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN TYPEOF(TO_VARIANT('2024-01-02'::DATE)); END; $$");
        assertAnswer(
            "BINARY DOUBLE",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN TYPEOF(GET(ARRAY_CONSTRUCT(TO_BINARY('AB', 'HEX'), 3.5::FLOAT), "
                + "0)) || ' ' || TYPEOF(GET(ARRAY_CONSTRUCT(TO_BINARY('AB', 'HEX'), 3.5::FLOAT), 1)); END; $$");
        assertAnswer(
            "VARCHAR [\"2024-01-02\"]",
            "EXECUTE IMMEDIATE $$ DECLARE a ARRAY; d DATE DEFAULT '2024-01-02'; BEGIN a := d; RETURN TYPEOF(a[0]) "
                + "|| ' ' || TO_JSON(a); END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 59 : SQL compilation "
                + "error: | invalid type [CAST(TO_DATE('2024-01-02') AS OBJECT)] for parameter 'TO_OBJECT'",
            "EXECUTE IMMEDIATE $$ DECLARE o OBJECT; d DATE DEFAULT '2024-01-02'; BEGIN o := d; RETURN 'held'; "
                + "END; $$");
        assertAnswer(
            "VARCHAR DOUBLE",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT TO_VARIANT('2024-01-02'::DATE), "
                + "TO_VARIANT(3.5::FLOAT); v VARIANT; w VARIANT; BEGIN OPEN c; FETCH c INTO v, w; CLOSE c; RETURN "
                + "TYPEOF(v) || ' ' || TYPEOF(w); END; $$");
        assertAnswer(
            "VARCHAR DOUBLE",
            "EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT TO_VARIANT('2024-01-02'::DATE) AS x, "
                + "TO_VARIANT(3.5::FLOAT) AS f; r VARCHAR; BEGIN FOR rec IN c DO r := TYPEOF(rec.x) || ' ' || "
                + "TYPEOF(rec.f); END FOR; RETURN r; END; $$");
        assertAnswer(
            "Function RV successfully created.",
            "CREATE OR REPLACE PROCEDURE rv() RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN "
                + "TO_VARIANT('2024-01-02'::DATE); END; $$");
        assertAnswer(
            "VARCHAR",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN CALL rv() INTO :v; RETURN TYPEOF(v); END; $$");
        assertAnswer(
            "VARCHAR",
            "EXECUTE IMMEDIATE $$ DECLARE t VARCHAR; BEGIN CALL rv(); SELECT TYPEOF($1) INTO :t FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN t; END; $$");
        assertAnswer(
            "DOUBLE 3.500000000000000e+00",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := 3.5::FLOAT; RETURN TYPEOF(v) || ' ' || "
                + "TO_JSON(v); END; $$");
        assertAnswer(
            "DECIMAL 3.5",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := 3.5; RETURN TYPEOF(v) || ' ' || TO_JSON(v); END; "
                + "$$");
        assertAnswer(
            "INTEGER 2",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := 2; RETURN TYPEOF(v) || ' ' || TO_JSON(v); END; $$");
        assertAnswer(
            "VARCHAR \"abc\"",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := 'abc'; RETURN TYPEOF(v) || ' ' || TO_JSON(v); "
                + "END; $$");
        assertAnswer(
            "DOUBLE 1.000000000000000e+300",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT(1e300::FLOAT); RETURN TYPEOF(v) || ' ' "
                + "|| TO_JSON(v); END; $$");
        assertAnswer(
            "DOUBLE NaN",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT('NaN'::FLOAT); RETURN TYPEOF(v) || ' ' "
                + "|| TO_JSON(v); END; $$");
        assertAnswer(
            "VARCHAR VARCHAR",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; w VARIANT; BEGIN v := TO_VARIANT('2024-01-02'::DATE); w := "
                + "ARRAY_CONSTRUCT(v, '2024-01-03'::DATE); RETURN TYPEOF(w[0]) || ' ' || TYPEOF(w[1]); END; $$");
        assertAnswer(
            "DATE",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := TO_VARIANT('2024-01-02'::DATE); RETURN "
                + "TYPEOF(ARRAY_CONSTRUCT(v, '2024-01-03'::DATE)[1]); END; $$");
        assertAnswer(
            "VARCHAR {\"t\":\"10:11:12\",\"x\":1.500000000000000e+00}",
            "EXECUTE IMMEDIATE $$ DECLARE v VARIANT; BEGIN v := OBJECT_CONSTRUCT('t', '10:11:12.5'::TIME, 'n', "
                + "NULL, 'x', 1.5::FLOAT); RETURN TYPEOF(v:t) || ' ' || TO_JSON(v); END; $$");
    }
}
