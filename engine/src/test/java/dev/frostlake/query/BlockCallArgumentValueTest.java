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
 * A call in a block's own expression is handed its arguments' values: a FLOAT stays a FLOAT, NaN and the
 * infinities included, a BINARY literal is the BINARY it spells, and an argument that reads no name and
 * folds to NULL is judged as written, so a call that needs a constant refuses the NULL an operator or a
 * conversion made where it takes the NULL literal itself. A SQL function's body reads a non-finite argument
 * as the value it is. Every answer is live-verified.
 */
public class BlockCallArgumentValueTest extends MeasuredCellsTestSupport {

    @Test
    public void theArgumentsOfAReturnedCall() {
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
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(NaN AS NUMBER(18,"
                + "0))'",
            "BEGIN RETURN LAST_QUERY_ID('NaN'::FLOAT); END;");
        assertAnswer(
            "inf",
            "BEGIN RETURN TO_VARCHAR('inf'::FLOAT * 2); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN TO_VARCHAR('inf'::FLOAT * 2); END;");
        assertAnswer(
            "NaN",
            "BEGIN RETURN TO_VARCHAR(TO_DOUBLE('NaN')); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN TO_VARCHAR(TO_DOUBLE('NaN')); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | argument 1 to function RANDOM needs to be constant, found "
                + "'(SYSTEM$NULL_TO_FIXED(IFNULL(CAST(CAST(null AS NULL) AS NULL), null))) + 1'",
            "BEGIN RETURN RANDOM(COALESCE(NULL, NULL) + 1); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | argument 1 to function RANDOM needs to be constant, found '(SYSTEM$NULL_TO_FIXED(null)) "
                + "+ 1'",
            "BEGIN RETURN RANDOM(NULL + 1); END;");
        assertAnswer(
            "NaN",
            "BEGIN RETURN 'NaN'::FLOAT; END;");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "BEGIN RETURN SYSTEM$TYPEOF(1.5::FLOAT); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN SYSTEM$TYPEOF(1.5::FLOAT); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function RANDOM needs to be constant, found "
                + "'(SYSTEM$NULL_TO_FIXED(null)) + 1'",
            "SELECT RANDOM(NULL + 1)");
        assertAnswer(
            "inf|NaN",
            "SELECT TO_VARCHAR('inf'::FLOAT * 2), TO_VARCHAR(TO_DOUBLE('NaN'))");
        assertAnswer(
            "NaN",
            "BEGIN RETURN ABS('NaN'::FLOAT); END;");
        assertAnswer(
            "-inf",
            "BEGIN RETURN TO_VARCHAR('-inf'::FLOAT); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN TO_VARCHAR('-inf'::FLOAT); END;");
        assertAnswer(
            "3",
            "BEGIN LET x FLOAT := 1.5; RETURN TO_VARCHAR(x * 2); END;");
        assertAnswer(
            "1.5",
            "BEGIN RETURN TO_VARCHAR(1.5::FLOAT); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN TO_VARCHAR(1.5::FLOAT); END;");
        assertAnswer(
            "null",
            "BEGIN RETURN LENGTH(NULL); END;");
        assertShape(
            "anonymous block:NUMBER(18,0)",
            "BEGIN RETURN LENGTH(NULL); END;");
        assertAnswer(
            "null",
            "BEGIN RETURN UPPER(NULL); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN UPPER(NULL); END;");
        assertAnswer(
            "null",
            "BEGIN RETURN CONCAT('a', NULL); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN CONCAT('a', NULL); END;");
        assertAnswer(
            "2020-01-01",
            "BEGIN RETURN TO_VARCHAR(TO_DATE('2020-01-01')); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN TO_VARCHAR(TO_DATE('2020-01-01')); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN TO_VARCHAR('2020-01-01 10:00:00'::TIMESTAMP_NTZ); END;");
        assertAnswer(
            "2020-01-02",
            "BEGIN RETURN DATEADD(day, 1, '2020-01-01'::DATE); END;");
        assertShape(
            "anonymous block:DATE",
            "BEGIN RETURN DATEADD(day, 1, '2020-01-01'::DATE); END;");
        assertAnswer(
            "2",
            "BEGIN RETURN ARRAY_SIZE(ARRAY_CONSTRUCT(1, 2)); END;");
        assertShape(
            "anonymous block:NUMBER(9,0)",
            "BEGIN RETURN ARRAY_SIZE(ARRAY_CONSTRUCT(1, 2)); END;");
        assertAnswer(
            "{\"a\":1}",
            "BEGIN RETURN TO_JSON(OBJECT_CONSTRUCT('a', 1)); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN TO_JSON(OBJECT_CONSTRUCT('a', 1)); END;");
        assertAnswer(
            "4",
            "BEGIN RETURN LENGTH('it''s'); END;");
        assertShape(
            "anonymous block:NUMBER(18,0)",
            "BEGIN RETURN LENGTH('it''s'); END;");
        assertAnswer(
            "4",
            "BEGIN RETURN LENGTH('a\\\\nb'); END;");
        assertShape(
            "anonymous block:NUMBER(18,0)",
            "BEGIN RETURN LENGTH('a\\\\nb'); END;");
        assertAnswer(
            "true",
            "BEGIN RETURN TO_VARCHAR(TRUE); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN TO_VARCHAR(TRUE); END;");
        assertAnswer(
            "1e+30",
            "BEGIN RETURN TO_VARCHAR(1e30::FLOAT); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN TO_VARCHAR(1e30::FLOAT); END;");
        assertAnswer(
            "X",
            "BEGIN LET s VARCHAR := 'x'; RETURN UPPER(s); END;");
        assertAnswer(
            "Wed",
            "BEGIN LET d DATE := '2020-01-01'; RETURN DAYNAME(d); END;");
        assertAnswer(
            "10",
            "BEGIN LET ts TIMESTAMP_NTZ := '2020-01-01 10:00:00'; RETURN HOUR(ts); END;");
        assertAnswer(
            "{\"a\":1}",
            "BEGIN LET v VARIANT := PARSE_JSON('{\"a\":1}'); RETURN TO_JSON(v); END;");
        assertAnswer(
            "2",
            "BEGIN LET a ARRAY := ARRAY_CONSTRUCT(1, 2); RETURN ARRAY_SIZE(a); END;");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "BEGIN LET f FLOAT := 1.5; RETURN SYSTEM$TYPEOF(f); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Invalid parameter "
                + "value: NULL. Reason: seed must not be NULL",
            "BEGIN RETURN RANDOM(NULL); END;");
        assertAnswer(
            "null",
            "BEGIN RETURN LAST_QUERY_ID(NULL); END;");
        assertAnswer(
            "NaN",
            "BEGIN RETURN TO_VARCHAR('NaN'::FLOAT + 1); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN TO_VARCHAR('NaN'::FLOAT + 1); END;");
        assertAnswer(
            "NaN",
            "BEGIN RETURN GREATEST('NaN'::FLOAT, 1); END;");
        assertAnswer(
            "a",
            "BEGIN RETURN IFF('NaN'::FLOAT > 1, 'a', 'b'); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN IFF('NaN'::FLOAT > 1, 'a', 'b'); END;");
        assertAnswer(
            "-inf",
            "BEGIN RETURN TO_VARCHAR(-'inf'::FLOAT); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN TO_VARCHAR(-'inf'::FLOAT); END;");
        assertAnswer(
            "NaN",
            "CALL pf('NaN'::FLOAT)");
        assertAnswer(
            "-inf",
            "CALL pf('-inf'::FLOAT)");
        assertAnswer(
            "41",
            "BEGIN RETURN TO_VARCHAR(X'41'); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN TO_VARCHAR(X'41'); END;");
        assertAnswer(
            "61",
            "BEGIN RETURN HEX_ENCODE('a'); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN HEX_ENCODE('a'); END;");
        assertAnswer(
            "12345678901234567890123456789012345678",
            "BEGIN RETURN TO_VARCHAR(12345678901234567890123456789012345678); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN TO_VARCHAR(12345678901234567890123456789012345678); END;");
        assertAnswer(
            "0.000000001",
            "BEGIN RETURN TO_VARCHAR(0.000000001); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN TO_VARCHAR(0.000000001); END;");
        assertAnswer(
            "1",
            "BEGIN RETURN TO_VARCHAR(1.0); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN TO_VARCHAR(1.0); END;");
        assertAnswer(
            "INTEGER",
            "BEGIN RETURN TYPEOF(TO_VARIANT(1.0)); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN TYPEOF(TO_VARIANT(1.0)); END;");
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "BEGIN RETURN SYSTEM$TYPEOF(1.0); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN SYSTEM$TYPEOF(1.0); END;");
        assertAnswer(
            "VARCHAR(1)[LOB]",
            "BEGIN RETURN SYSTEM$TYPEOF('a'); END;");
        assertShape(
            "anonymous block:VARCHAR(134217728)",
            "BEGIN RETURN SYSTEM$TYPEOF('a'); END;");
        assertAnswer(
            "NUMBER(38,0)[SB16]",
            "BEGIN LET x INT := 5; RETURN SYSTEM$TYPEOF(x + 1); END;");
    }

    @Test
    public void theArgumentsThatReadAVariable() {
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
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : Invalid parameter "
                + "value: NULL. Reason: seed must not be NULL",
            "BEGIN LET s INT := NULL; RETURN RANDOM(s); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : Invalid parameter "
                + "value: NULL. Reason: seed must not be NULL",
            "BEGIN LET s INT := NULL; RETURN RANDOM(s + 1); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : Invalid parameter "
                + "value: NULL. Reason: seed must not be NULL",
            "BEGIN LET s INT := NULL; RETURN RANDOM(:s + 1); END;");
        assertAnswer(
            "true",
            "BEGIN LET s INT := 1; RETURN RANDOM(s + 1) IS NOT NULL; END;");
        assertAnswer(
            "null",
            "BEGIN LET s INT := NULL; RETURN LAST_QUERY_ID(s); END;");
        assertAnswer(
            "null",
            "BEGIN LET s INT := NULL; RETURN LAST_QUERY_ID(s + 1); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Division by zero",
            "BEGIN RETURN UPPER(TO_VARCHAR(1/0)); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Division by zero",
            "BEGIN RETURN 1 + LENGTH(TO_VARCHAR(1/0)); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Division by zero",
            "BEGIN RETURN LENGTH(1/0); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : Division by zero",
            "BEGIN LET z INT := 0; RETURN UPPER(TO_VARCHAR(1/z)); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Numeric value 'a' "
                + "is not recognized",
            "BEGIN RETURN UPPER(TO_NUMBER('a')); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Numeric value 'a' "
                + "is not recognized",
            "BEGIN RETURN 'x' || UPPER(TO_NUMBER('a')); END;");
        assertAnswer(
            "NaN",
            "BEGIN LET x FLOAT := 'NaN'::FLOAT; RETURN GREATEST(x, 1); END;");
        assertAnswer(
            "-inf",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; RETURN TO_VARCHAR(-x); END;");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; RETURN SYSTEM$TYPEOF(x); END;");
        assertAnswer(
            "inf",
            "BEGIN LET x FLOAT := 'inf'::FLOAT; RETURN fsq(x); END;");
        assertAnswer(
            "NaN",
            "BEGIN RETURN fsq('NaN'::FLOAT); END;");
        assertAnswer(
            "NUMBER(10,2)[SB8]",
            "BEGIN LET n NUMBER(10,2) := 1.50; RETURN SYSTEM$TYPEOF(n); END;");
        assertAnswer(
            "1.5",
            "BEGIN RETURN TO_VARCHAR(1.50); END;");
        assertAnswer(
            "NUMBER(10,2)[SB2]",
            "BEGIN RETURN SYSTEM$TYPEOF(1.50::NUMBER(10,2)); END;");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(ROUND('inf'::FLOAT))");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "BEGIN RETURN SYSTEM$TYPEOF(1e30::FLOAT); END;");
        assertAnswer(
            "41",
            "BEGIN LET b BINARY := X'41'; RETURN TO_VARCHAR(b); END;");
        assertAnswer(
            "BINARY(1)[LOB]",
            "BEGIN RETURN SYSTEM$TYPEOF(X'41'); END;");
        assertAnswer(
            "1000000000000000000000000000000",
            "BEGIN RETURN TO_VARCHAR(1e30); END;");
        assertAnswer(
            "NUMBER(31,0)[SB16]",
            "BEGIN RETURN SYSTEM$TYPEOF(1e30); END;");
        assertAnswer(
            "NUMBER(2,1)[SB1]",
            "BEGIN RETURN SYSTEM$TYPEOF(1.5); END;");
        assertAnswer(
            "NUMBER(2,1)[SB1]",
            "BEGIN RETURN SYSTEM$TYPEOF(1.10); END;");
        assertAnswer(
            "NUMBER(3,0)[SB1]",
            "BEGIN RETURN SYSTEM$TYPEOF(100); END;");
        assertAnswer(
            "VARCHAR(4)[LOB]",
            "BEGIN RETURN SYSTEM$TYPEOF('abc' || 'd'); END;");
        assertAnswer(
            "NUMBER(2,0)[SB1]",
            "BEGIN RETURN SYSTEM$TYPEOF(1 + 1); END;");
        assertAnswer(
            "NULL[LOB]",
            "BEGIN RETURN SYSTEM$TYPEOF(NULL); END;");
        assertAnswer(
            "BOOLEAN[SB1]",
            "BEGIN RETURN SYSTEM$TYPEOF(TRUE); END;");
        assertAnswer(
            "DATE[SB4]",
            "BEGIN RETURN SYSTEM$TYPEOF('2020-01-01'::DATE); END;");
        assertAnswer(
            "NUMBER(18,0)[SB8]",
            "BEGIN RETURN SYSTEM$TYPEOF(LENGTH('abc')); END;");
        assertAnswer(
            "NUMBER(3,1)[SB1]",
            "BEGIN RETURN SYSTEM$TYPEOF(1.5 * 2); END;");
        assertAnswer(
            "NUMBER(8,6)[SB4]",
            "BEGIN RETURN SYSTEM$TYPEOF(10 / 4); END;");
        assertAnswer(
            "0.333333",
            "BEGIN RETURN TO_VARCHAR(1 / 3); END;");
        assertAnswer(
            "NUMBER(38,6)[SB16]",
            "BEGIN LET x INT := 10; RETURN SYSTEM$TYPEOF(x / 4); END;");
        assertAnswer(
            "NUMBER(38,0)[SB16]",
            "BEGIN LET x INT := 10; RETURN SYSTEM$TYPEOF(x); END;");
        assertAnswer(
            "VARCHAR[LOB]",
            "BEGIN LET s VARCHAR := 'abc'; RETURN SYSTEM$TYPEOF(s); END;");
        assertAnswer(
            "VARCHAR[LOB]",
            "BEGIN LET s VARCHAR(10) := 'abc'; RETURN SYSTEM$TYPEOF(s); END;");
        assertAnswer(
            "VARCHAR[LOB]",
            "BEGIN LET s := 'abc'; RETURN SYSTEM$TYPEOF(s); END;");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "BEGIN LET x := 1.50; RETURN SYSTEM$TYPEOF(x); END;");
        assertAnswer(
            "1.5",
            "BEGIN LET x := 1.50; RETURN TO_VARCHAR(x); END;");
        assertAnswer(
            "NUMBER(11,2)[SB8]",
            "BEGIN LET x NUMBER(10,2) := 1.50; RETURN SYSTEM$TYPEOF(x + 1); END;");
        assertAnswer(
            "1.50",
            "BEGIN LET x NUMBER(10,2) := 1.50; RETURN x; END;");
        assertAnswer(
            "DATE[SB4]",
            "BEGIN LET d DATE := '2020-01-01'; RETURN SYSTEM$TYPEOF(d); END;");
        assertAnswer(
            "BOOLEAN[SB1]",
            "BEGIN LET t BOOLEAN := TRUE; RETURN SYSTEM$TYPEOF(t); END;");
        assertAnswer(
            "2",
            "BEGIN LET f FLOAT := 2; RETURN TO_VARCHAR(f); END;");
        assertAnswer(
            "1e+30",
            "BEGIN LET f FLOAT := 1e30; RETURN TO_VARCHAR(f); END;");
        assertAnswer(
            "VARIANT[LOB]",
            "BEGIN LET v VARIANT := 1.50; RETURN SYSTEM$TYPEOF(v); END;");
    }

    @Test
    public void theArgumentsThatFoldToNull() {
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
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '1 "
                + "+ 1'",
            "SELECT LAST_QUERY_ID(1 + 1) IS NOT NULL");
        assertAnswer(
            "true",
            "SELECT LAST_QUERY_ID(-1) IS NOT NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'ABS(-1)'",
            "SELECT LAST_QUERY_ID(ABS(-1)) IS NOT NULL");
        assertAnswer(
            "true",
            "SELECT LAST_QUERY_ID('1') IS NOT NULL");
        assertAnswer(
            "true",
            "SELECT LAST_QUERY_ID(1.0) IS NOT NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(SYSTEM$NULL_TO_FIXED(null)) + 1'",
            "SELECT LAST_QUERY_ID(NULL + 1)");
        assertAnswer(
            "true",
            "BEGIN RETURN LAST_QUERY_ID(-1) IS NOT NULL; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(SYSTEM$NULL_TO_FIXED(null)) + 1'",
            "BEGIN RETURN LAST_QUERY_ID(NULL + 1); END;");
        assertAnswer(
            "true",
            "BEGIN RETURN UNIFORM(1, 10, RANDOM(1 + 1)) > 0; END;");
        assertAnswer(
            "true",
            "BEGIN RETURN RANDOM(1 + 1) IS NOT NULL; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | argument 1 to function RANDOM needs to be constant, found "
                + "'ABS(SYSTEM$NULL_TO_FIXED(null))'",
            "BEGIN RETURN RANDOM(ABS(NULL)); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | argument 1 to function RANDOM needs to be constant, found 'SYSTEM$NULL_TO_FIXED(null)'",
            "BEGIN RETURN RANDOM(CAST(NULL AS INT)); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Invalid parameter "
                + "value: NULL. Reason: seed must not be NULL",
            "BEGIN RETURN RANDOM(COALESCE(NULL, NULL)); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Invalid parameter "
                + "value: NULL. Reason: seed must not be NULL",
            "BEGIN RETURN RANDOM(IFF(TRUE, NULL, NULL)); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : Invalid parameter "
                + "value: NULL. Reason: seed must not be NULL",
            "BEGIN LET s INT := NULL; RETURN RANDOM(ABS(s)); END;");
        assertAnswer(
            "null",
            "BEGIN LET n INT := NULL; RETURN BASE64_ENCODE('a', n + 1); END;");
        assertAnswer(
            "YWJj | ZGVm | Z2g=",
            "BEGIN LET n INT := 4; RETURN BASE64_ENCODE('abcdefgh', n); END;");
    }

    @Test
    public void theValuesNoLiteralSpells() {
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
            "[NaN]",
            "SELECT ARRAY_CONSTRUCT('NaN'::FLOAT)");
        assertAnswer(
            "[Infinity,1.500000000000000e+00]",
            "SELECT ARRAY_CONSTRUCT('inf'::FLOAT, 1.5::FLOAT)");
        assertAnswer(
            "[NaN,-Infinity]",
            "SELECT TO_JSON(ARRAY_CONSTRUCT('NaN'::FLOAT, '-inf'::FLOAT))");
        assertAnswer(
            "{\"a\":NaN}",
            "SELECT OBJECT_CONSTRUCT('a', 'NaN'::FLOAT)");
        assertAnswer(
            "Infinity",
            "SELECT TO_VARIANT('inf'::FLOAT)");
        assertAnswer(
            "FLOAT[DOUBLE]|FLOAT[DOUBLE]|FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(ROUND('inf'::FLOAT)), SYSTEM$TYPEOF(TRUNC('inf'::FLOAT)), "
                + "SYSTEM$TYPEOF(CEIL('NaN'::FLOAT))");
        assertAnswer(
            "NaN|Infinity",
            "SELECT SQRT('NaN'::FLOAT), SQRT('inf'::FLOAT)");
        assertAnswer(
            "REFUSED Invalid floating point operation: log(10,-1)",
            "SELECT LOG(10, -1)");
        assertAnswer(
            "REFUSED Invalid floating point operation: log(10,0)",
            "SELECT LOG(10, 0)");
        assertAnswer(
            "Infinity",
            "SELECT POWER(0, -1)");
        assertAnswer(
            "Infinity",
            "SELECT EXP(1000)");
        assertAnswer(
            "Infinity",
            "SELECT POWER(10, 400)");
        assertAnswer(
            "Infinity",
            "SELECT 1e308::FLOAT * 10");
    }
}
