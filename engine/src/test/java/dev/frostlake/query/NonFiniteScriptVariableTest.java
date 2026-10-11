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
 * A FLOAT, DOUBLE or REAL variable of a block holds NaN and the infinities - from a LET, a DECLARE default, an
 * assignment, a text that spells one, a procedure's argument and its RETURN - and an exact one refuses them
 * as the cast does. A statement of the block binds a NaN and refuses an infinity while it compiles, with a
 * code and a state of its own for a handler; the block's own expressions and what EXECUTE IMMEDIATE is USING
 * take both. Every answer is live-verified.
 */
public class NonFiniteScriptVariableTest extends MeasuredCellsTestSupport {

    @Test
    public void theValuesAFloatVariableHolds() {
        engine.execute(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE t (n NUMBER(5,2), n40 NUMBER(4,0), k INT)");
        engine.execute(
            "INSERT INTO t VALUES (1.5, 17, 1), (2.5, 3, 2)");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pf(x FLOAT) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN TO_VARCHAR(x); "
                + "END'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pnan() RETURNS FLOAT LANGUAGE SQL AS 'BEGIN RETURN ''NaN''::FLOAT; END'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pinf() RETURNS FLOAT LANGUAGE SQL AS 'BEGIN LET x FLOAT := "
                + "''inf''::FLOAT; RETURN x; END'");
        assertAnswer(
            "NaN",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; RETURN x; END;");
        assertAnswer(
            "Infinity",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; RETURN x; END;");
        assertAnswer(
            "-Infinity",
            "BEGIN LET x FLOAT := TO_DOUBLE('-inf'); RETURN x; END;");
        assertAnswer(
            "NaN",
            "DECLARE x FLOAT DEFAULT 'NaN'::FLOAT; BEGIN RETURN x; END;");
        assertAnswer(
            "1.5",
            "BEGIN LET x FLOAT := 1.5; RETURN x; END;");
        assertAnswer(
            "NaN",
            "DECLARE x FLOAT; BEGIN x := 'NaN'::FLOAT; RETURN x; END;");
        assertAnswer(
            "Infinity",
            "DECLARE x FLOAT; BEGIN x := 'inf'::FLOAT; RETURN x; END;");
        assertAnswer(
            "NaN",
            "CALL pnan()");
        assertAnswer(
            "Infinity",
            "CALL pinf()");
        assertAnswer(
            "NaN",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; RETURN x + 1; END;");
        assertAnswer(
            "true",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; RETURN x = x; END;");
        assertShape(
            "anonymous block:BOOLEAN",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; RETURN x = x; END;");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; RETURN SYSTEM$TYPEOF(x); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; RETURN SYSTEM$TYPEOF(x); END;");
        assertAnswer(
            "NaN",
            "BEGIN LET y DOUBLE := 'NaN'::FLOAT; RETURN y; END;");
        assertAnswer(
            "Infinity",
            "BEGIN LET z REAL := 'inf'; RETURN z; END;");
        assertAnswer(
            "Infinity",
            "BEGIN LET x FLOAT := 'inf'; RETURN x; END;");
        assertAnswer(
            "NaN",
            "BEGIN LET x FLOAT := 'NaN'; RETURN x; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : Number out of "
                + "representable range: type FIXED[SB16](38,0){not null}, value nan",
            "BEGIN LET n NUMBER := 'NaN'::FLOAT; RETURN n; END;");
        assertShape(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : Number out of "
                + "representable range: type FIXED[SB16](38,0){not null}, value nan",
            "BEGIN LET n NUMBER := 'NaN'::FLOAT; RETURN n; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : Number out of "
                + "representable range: type FIXED[SB16](38,0){not null}, value inf",
            "BEGIN LET n NUMBER := 'inf'::FLOAT; RETURN n; END;");
        assertShape(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : Number out of "
                + "representable range: type FIXED[SB16](38,0){not null}, value inf",
            "BEGIN LET n NUMBER := 'inf'::FLOAT; RETURN n; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 21 : Numeric value 'abc' "
                + "is not recognized",
            "BEGIN LET x FLOAT := 'abc'; RETURN x; END;");
        assertShape(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 21 : Numeric value 'abc' "
                + "is not recognized",
            "BEGIN LET x FLOAT := 'abc'; RETURN x; END;");
        assertAnswer(
            "NaN",
            "BEGIN LET x := 'NaN'::FLOAT; RETURN x; END;");
        assertAnswer(
            "Infinity",
            "BEGIN LET x := 'inf'::FLOAT; RETURN x; END;");
        assertAnswer(
            "Infinity",
            "BEGIN LET x FLOAT := 1e308::FLOAT * 10; RETURN x; END;");
        assertAnswer(
            "NaN",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; LET y FLOAT := x; RETURN y; END;");
        assertAnswer(
            "NaN",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; RETURN TO_VARCHAR(x); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; RETURN TO_VARCHAR(x); END;");
        assertAnswer(
            "true",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; RETURN x > 1; END;");
        assertShape(
            "anonymous block:BOOLEAN",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; RETURN x > 1; END;");
        assertAnswer(
            "big",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; IF (x > 1) THEN RETURN 'big'; END IF; RETURN 'small'; END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; IF (x > 1) THEN RETURN 'big'; END IF; RETURN 'small'; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : SQL compilation "
                + "error: | Invalid bind value (inf) for type (REAL).",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; CREATE OR REPLACE TEMPORARY TABLE tf (f FLOAT); INSERT INTO tf "
                + "VALUES (:x); RETURN (SELECT TO_VARCHAR(f) FROM tf); END;");
        assertShape(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 83 : SQL compilation "
                + "error: | Invalid bind value (inf) for type (REAL).",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; CREATE OR REPLACE TEMPORARY TABLE tf (f FLOAT); INSERT INTO tf "
                + "VALUES (:x); RETURN (SELECT TO_VARCHAR(f) FROM tf); END;");
        assertAnswer(
            "NaN",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; y FLOAT DEFAULT '-inf'::FLOAT; BEGIN RETURN x + y; END;");
        assertShape(
            "anonymous block:ARRAY",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; RETURN ARRAY_CONSTRUCT(x); END;");
        assertAnswer(
            "3.0",
            "BEGIN LET x FLOAT := 1.5; RETURN x * 2; END;");
        assertAnswer(
            "2.0",
            "BEGIN LET x FLOAT := 2; RETURN x; END;");
        assertAnswer(
            "0.333333",
            "BEGIN LET x FLOAT := 1/3; RETURN x; END;");
    }

    @Test
    public void theValuesAProcedureIsCalledWith() {
        engine.execute(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pf(x FLOAT) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN TO_VARCHAR(x); "
                + "END'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pg(x FLOAT) RETURNS FLOAT LANGUAGE SQL AS 'BEGIN RETURN x; END'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pn(x NUMBER(10,2)) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "SYSTEM$TYPEOF(x); END'");
        engine.execute(
            "CREATE OR REPLACE FUNCTION fsq(x FLOAT) RETURNS VARCHAR AS 'TO_VARCHAR(x)'");
        assertAnswer(
            "null",
            "CALL pf(NULL + 1)");
        assertAnswer(
            "inf",
            "CALL pf('inf'::FLOAT * 2)");
        assertAnswer(
            "1e+30",
            "CALL pf(1e30::FLOAT)");
        assertAnswer(
            "NaN",
            "CALL pg('NaN'::FLOAT)");
        assertAnswer(
            "Infinity",
            "CALL pg('inf'::FLOAT)");
        assertAnswer(
            "-Infinity",
            "CALL pg('-inf'::FLOAT)");
        assertAnswer(
            "1.5",
            "CALL pg(1.5)");
        assertAnswer(
            "null",
            "CALL pg(NULL)");
        assertAnswer(
            "NUMBER(10,2)[SB8]",
            "CALL pn(1.5)");
        assertAnswer(
            "NUMBER(10,2)[SB8]",
            "CALL pn(1)");
        assertAnswer(
            "NaN",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; LET r VARCHAR := ''; CALL pf(:x) INTO :r; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 56 : SQL compilation "
                + "error: | Invalid bind value (inf) for type (REAL).",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; LET r VARCHAR := ''; CALL pf(:x) INTO :r; RETURN r; END;");
        assertAnswer(
            "NaN",
            "BEGIN LET r VARCHAR := ''; CALL pf('NaN'::FLOAT) INTO :r; RETURN r; END;");
        assertAnswer(
            "-inf",
            "BEGIN LET r VARCHAR := ''; CALL pf('-inf'::FLOAT) INTO :r; RETURN r; END;");
        assertAnswer(
            "1.5",
            "BEGIN LET r VARCHAR := ''; CALL pf(1.5) INTO :r; RETURN r; END;");
        assertAnswer(
            "null",
            "BEGIN LET r VARCHAR := ''; CALL pf(NULL) INTO :r; RETURN r; END;");
        assertAnswer(
            "1e+30",
            "BEGIN LET r VARCHAR := ''; CALL pf(1e30::FLOAT) INTO :r; RETURN r; END;");
        assertAnswer(
            "1",
            "BEGIN CALL pf('NaN'::FLOAT); RETURN 1; END;");
        assertAnswer(
            "1",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; CALL pf(:x); RETURN 1; END;");
        assertAnswer(
            "NaN",
            "BEGIN LET r FLOAT := 0; CALL pg('NaN'::FLOAT) INTO :r; RETURN r; END;");
        assertAnswer(
            "inf",
            "BEGIN LET r FLOAT := 0; CALL pg('inf'::FLOAT) INTO :r; RETURN TO_VARCHAR(r); END;");
        assertAnswer(
            "NUMBER(10,2)[SB8]",
            "BEGIN LET r VARCHAR := ''; CALL pn(1.5) INTO :r; RETURN r; END;");
        assertAnswer(
            "NUMBER(10,2)[SB8]",
            "BEGIN LET n NUMBER(10,2) := 1.5; LET r VARCHAR := ''; CALL pn(:n) INTO :r; RETURN r; END;");
        assertAnswer(
            "6",
            "DECLARE c INT DEFAULT 0; BEGIN FOR i IN 1 TO 3 DO c := c + i; END FOR; RETURN c; END;");
        assertAnswer(
            "2",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; c INT DEFAULT 0; BEGIN WHILE (x > 1 AND c < 2) DO c := c + 1; "
                + "END WHILE; RETURN c; END;");
        assertAnswer(
            "gt",
            "DECLARE x FLOAT DEFAULT 'NaN'::FLOAT; BEGIN CASE WHEN x > 1 THEN RETURN 'gt'; ELSE RETURN 'le'; END "
                + "CASE; END;");
        assertAnswer(
            "NaN",
            "DECLARE x FLOAT DEFAULT 'NaN'::FLOAT; BEGIN RETURN x || ''; END;");
        assertAnswer(
            "inf",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; BEGIN RETURN x || ''; END;");
        assertAnswer(
            "inf",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; BEGIN RETURN x::VARCHAR; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 51 : Number out of "
                + "representable range: type FIXED[SB16](38,0){not null}, value inf",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; BEGIN RETURN x::INT; END;");
        assertAnswer(
            "false",
            "DECLARE x FLOAT DEFAULT 'NaN'::FLOAT; BEGIN RETURN x IS NULL; END;");
        assertAnswer(
            "NaN",
            "DECLARE x FLOAT DEFAULT 'NaN'::FLOAT; BEGIN RETURN -x; END;");
        assertAnswer(
            "-Infinity",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; BEGIN RETURN -x; END;");
        assertAnswer(
            "NaN",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; BEGIN RETURN x * 0; END;");
        assertAnswer(
            "NaN",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; BEGIN RETURN x / x; END;");
        assertAnswer(
            "0.0",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; BEGIN RETURN 1 / x; END;");
    }

    @Test
    public void theValuesAStatementBinds() {
        engine.execute(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE tf (f FLOAT)");
        engine.execute(
            "INSERT INTO tf VALUES (1.5)");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE pf(x FLOAT) RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN TO_VARCHAR(x); "
                + "END'");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 56 : SQL compilation "
                + "error: | Invalid bind value (inf) for type (REAL).",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; LET r VARCHAR := ''; SELECT TO_VARCHAR(:x) INTO :r; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 57 : SQL compilation "
                + "error: | Invalid bind value (-inf) for type (REAL).",
            "BEGIN LET x FLOAT := '-inf'::FLOAT; LET r VARCHAR := ''; SELECT TO_VARCHAR(:x) INTO :r; RETURN r; "
                + "END;");
        assertAnswer(
            "NaN",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; LET r VARCHAR := ''; SELECT TO_VARCHAR(:x) INTO :r; RETURN r; END;");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; LET r VARCHAR := ''; SELECT SYSTEM$TYPEOF(:x) INTO :r; RETURN r; "
                + "END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 51 : SQL compilation "
                + "error: | Invalid bind value (inf) for type (REAL).",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; LET n INT := 0; SELECT COUNT(*) INTO :n FROM fz WHERE id < :x; "
                + "RETURN n; END;");
        assertAnswer(
            "2",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; LET n INT := 0; SELECT COUNT(*) INTO :n FROM fz WHERE id < :x; "
                + "RETURN n; END;");
        assertAnswer(
            "Infinity",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; RETURN (SELECT :x); END;");
        assertAnswer(
            "inf",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; RETURN (SELECT TO_VARCHAR(:x)); END;");
        assertAnswer(
            "NaN",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; RETURN (SELECT TO_VARCHAR(:x)); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 42 : SQL compilation "
                + "error: error line 1 at position 26 | invalid identifier 'X'",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; RETURN (SELECT TO_VARCHAR(x)); END;");
        assertAnswer(
            "inf",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; LET y FLOAT := (SELECT :x * 2); RETURN TO_VARCHAR(y); END;");
        assertAnswer(
            "statement 2086 22000 SQL compilation error: | Invalid bind value (inf) for type (REAL).",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; LET r VARCHAR := ''; SELECT TO_VARCHAR(:x) INTO :r; RETURN r; "
                + "EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 'statement ' || SQLCODE || ' ' || SQLSTATE || ' ' || "
                + "SQLERRM; WHEN OTHER THEN RETURN 'other ' || SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 36 : SQL compilation "
                + "error: | Invalid bind value (-inf) for type (REAL).",
            "BEGIN LET x FLOAT := '-inf'::FLOAT; CALL pf(:x); RETURN 1; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                + "error: | Invalid bind value (inf) for type (REAL).",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; INSERT INTO tf VALUES (:x); RETURN 1; END;");
        assertAnswer(
            "1",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; INSERT INTO tf VALUES (:x); RETURN (SELECT COUNT(*) FROM tf WHERE "
                + "f = 'NaN'::FLOAT); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                + "error: | Invalid bind value (inf) for type (REAL).",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; DELETE FROM tf WHERE f = :x; RETURN 1; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 35 : SQL compilation "
                + "error: | Invalid bind value (inf) for type (REAL).",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; UPDATE tf SET f = :x WHERE f = 99; RETURN 1; END;");
        assertAnswer(
            "1",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; EXECUTE IMMEDIATE 'SELECT ?' USING (x); RETURN 1; END;");
        assertAnswer(
            "1",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; EXECUTE IMMEDIATE 'SELECT ?' USING (x); RETURN 1; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 50 : SQL compilation "
                + "error: | Invalid bind value (inf) for type (REAL).",
            "BEGIN LET x := 'inf'::FLOAT; LET r VARCHAR := ''; SELECT TO_VARCHAR(:x) INTO :r; RETURN r; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 65 : SQL compilation "
                + "error: | Invalid bind value (inf) for type (REAL).",
            "DECLARE x FLOAT DEFAULT 'inf'::FLOAT; BEGIN LET rs RESULTSET := (SELECT :x AS v); RETURN TABLE(rs); "
                + "END;");
    }
}
