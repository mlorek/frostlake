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
package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The lines a type's parameter list is refused with after its first. A cast names the first token inside an
 * ARRAY's or an OBJECT's parentheses twice - {@code NULL::ARRAY(5)} is '5' and '5' again - and an empty pair
 * inside a CAST once. In a list of definitions - a table's columns, a routine's parameters, an OBJECT's fields
 * - a fault one token would not mend makes the list read on: the comma is taken for the one between two
 * definitions, so the number after it is refused as no name, and the first ')' met ends the list, so that the
 * token after it is refused too. {@code CREATE TABLE t (a VARCHAR(5,2))} is ',', '2' and the list's own ')'.
 * An ALTER TABLE's column stands in no list, and reports the comma and the number alone. Every answer is
 * live-verified.
 */
public class TypeParameterListTailTest extends MeasuredCellsTestSupport {

    @Test
    public void anArraysOrAnObjectsFirstTokenIsNamedTwiceByACast() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '5'. | syntax error "
                + "line 1 at position 19 unexpected '5'.",
            "SELECT NULL::ARRAY(5)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '5'. | syntax error "
                + "line 1 at position 26 unexpected '5'.",
            "SELECT CAST(NULL AS ARRAY(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '5'. | syntax error "
                + "line 1 at position 30 unexpected '5'.",
            "SELECT TRY_CAST(NULL AS ARRAY(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '5'. | syntax error "
                + "line 1 at position 40 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (a ARRAY(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '5'. | syntax error "
                + "line 1 at position 19 unexpected '5'. | syntax error line 1 at position 22 unexpected ')'.",
            "SELECT NULL::ARRAY(5,2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '5'. | syntax error "
                + "line 1 at position 26 unexpected '5'. | syntax error line 1 at position 29 unexpected ')'.",
            "SELECT CAST(NULL AS ARRAY(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '5'. | syntax error "
                + "line 1 at position 30 unexpected '5'. | syntax error line 1 at position 33 unexpected ')'.",
            "SELECT TRY_CAST(NULL AS ARRAY(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '5'. | syntax error "
                + "line 1 at position 40 unexpected '2'. | syntax error line 1 at position 42 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (a ARRAY(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ')'. | syntax error "
                + "line 1 at position 19 unexpected ')'.",
            "SELECT NULL::ARRAY()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ')'.",
            "SELECT CAST(NULL AS ARRAY())");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ')'.",
            "SELECT TRY_CAST(NULL AS ARRAY())");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ')'. | syntax error "
                + "line 1 at position 39 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (a ARRAY())");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '5'. | syntax error "
                + "line 1 at position 20 unexpected '5'.",
            "SELECT NULL::OBJECT(5)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '5'. | syntax error "
                + "line 1 at position 27 unexpected '5'.",
            "SELECT CAST(NULL AS OBJECT(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '5'. | syntax error "
                + "line 1 at position 31 unexpected '5'.",
            "SELECT TRY_CAST(NULL AS OBJECT(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '5'. | syntax error "
                + "line 1 at position 41 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (a OBJECT(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '5'. | syntax error "
                + "line 1 at position 20 unexpected '5'. | syntax error line 1 at position 23 unexpected ')'.",
            "SELECT NULL::OBJECT(5,2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '5'. | syntax error "
                + "line 1 at position 27 unexpected '5'. | syntax error line 1 at position 30 unexpected ')'.",
            "SELECT CAST(NULL AS OBJECT(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '5'. | syntax error "
                + "line 1 at position 31 unexpected '5'. | syntax error line 1 at position 34 unexpected ')'.",
            "SELECT TRY_CAST(NULL AS OBJECT(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '5'. | syntax error "
                + "line 1 at position 41 unexpected '2'. | syntax error line 1 at position 43 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (a OBJECT(5,2))");
        assertAnswer(
            "null",
            "SELECT NULL::OBJECT()");
        assertAnswer(
            "null",
            "SELECT CAST(NULL AS OBJECT())");
        assertAnswer(
            "null",
            "SELECT TRY_CAST(NULL AS OBJECT())");
        assertAnswer(
            "Table TP_T successfully created.",
            "CREATE OR REPLACE TABLE tp_t (a OBJECT())");
        answer(
            "SELECT NULL::ARRAY(5 NOT NULL)");
        answer(
            "SELECT CAST(NULL AS ARRAY(5 NOT NULL))");
        answer(
            "SELECT TRY_CAST(NULL AS ARRAY(5 NOT NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '5'. | syntax error "
                + "line 1 at position 49 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (a ARRAY(5 NOT NULL))");
        answer(
            "SELECT NULL::OBJECT(a)");
        answer(
            "SELECT CAST(NULL AS OBJECT(a))");
        answer(
            "SELECT TRY_CAST(NULL AS OBJECT(a))");
        answer(
            "CREATE OR REPLACE TABLE tp_t (a OBJECT(a))");
        answer(
            "SELECT NULL::OBJECT(a 5)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '5'.",
            "SELECT CAST(NULL AS OBJECT(a 5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '5'.",
            "SELECT TRY_CAST(NULL AS OBJECT(a 5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '5'.",
            "CREATE OR REPLACE TABLE tp_t (a OBJECT(a 5))");
        answer(
            "SELECT NULL::ARRAY(NUMBER, 5)");
        answer(
            "SELECT CAST(NULL AS ARRAY(NUMBER, 5))");
        answer(
            "SELECT TRY_CAST(NULL AS ARRAY(NUMBER, 5))");
        answer(
            "CREATE OR REPLACE TABLE tp_t (a ARRAY(NUMBER, 5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '5'.",
            "SELECT NULL::OBJECT(a NUMBER, 5)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '5'.",
            "SELECT CAST(NULL AS OBJECT(a NUMBER, 5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '5'.",
            "SELECT TRY_CAST(NULL AS OBJECT(a NUMBER, 5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '5'.",
            "CREATE OR REPLACE TABLE tp_t (a OBJECT(a NUMBER, 5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '-'. | syntax error "
                + "line 1 at position 19 unexpected '-'. | syntax error line 1 at position 21 unexpected ')'.",
            "SELECT NULL::ARRAY(-1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '-'. | syntax error "
                + "line 1 at position 26 unexpected '-'. | syntax error line 1 at position 28 unexpected ')'.",
            "SELECT CAST(NULL AS ARRAY(-1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '-'. | syntax error "
                + "line 1 at position 30 unexpected '-'. | syntax error line 1 at position 32 unexpected ')'.",
            "SELECT TRY_CAST(NULL AS ARRAY(-1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '-'. | syntax error "
                + "line 1 at position 41 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (a ARRAY(-1))");
        answer(
            "SELECT NULL::ARRAY('x')");
        answer(
            "SELECT CAST(NULL AS ARRAY('x'))");
        answer(
            "SELECT TRY_CAST(NULL AS ARRAY('x'))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ''x''. | syntax error "
                + "line 1 at position 42 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (a ARRAY('x'))");
        answer(
            "SELECT NULL::ARRAY(NUMBER NUMBER)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'NUMBER'.",
            "SELECT CAST(NULL AS ARRAY(NUMBER NUMBER))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'NUMBER'.",
            "SELECT TRY_CAST(NULL AS ARRAY(NUMBER NUMBER))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'NUMBER'.",
            "CREATE OR REPLACE TABLE tp_t (a ARRAY(NUMBER NUMBER))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '5'.",
            "SELECT NULL::MAP(5)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '5'. | syntax error "
                + "line 1 at position 26 unexpected ')'.",
            "SELECT CAST(NULL AS MAP(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '5'. | syntax error "
                + "line 1 at position 30 unexpected ')'.",
            "SELECT TRY_CAST(NULL AS MAP(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '5'. | syntax error "
                + "line 1 at position 38 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (a MAP(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ')'.",
            "SELECT NULL::MAP()");
        answer(
            "SELECT CAST(NULL AS MAP())");
        answer(
            "SELECT TRY_CAST(NULL AS MAP())");
        answer(
            "CREATE OR REPLACE TABLE tp_t (a MAP())");
        answer(
            "SELECT NULL::MAP(VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ')'. | syntax error "
                + "line 1 at position 32 unexpected ')'.",
            "SELECT CAST(NULL AS MAP(VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ')'. | syntax error "
                + "line 1 at position 36 unexpected ')'.",
            "SELECT TRY_CAST(NULL AS MAP(VARCHAR))");
        answer(
            "CREATE OR REPLACE TABLE tp_t (a MAP(VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '5'.",
            "SELECT NULL::MAP(VARCHAR, 5)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '5'.",
            "SELECT CAST(NULL AS MAP(VARCHAR, 5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '5'.",
            "SELECT TRY_CAST(NULL AS MAP(VARCHAR, 5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '5'.",
            "CREATE OR REPLACE TABLE tp_t (a MAP(VARCHAR, 5))");
        answer(
            "SELECT NULL::MAP(VARCHAR, NUMBER, 5)");
        answer(
            "SELECT CAST(NULL AS MAP(VARCHAR, NUMBER, 5))");
        answer(
            "SELECT TRY_CAST(NULL AS MAP(VARCHAR, NUMBER, 5))");
        answer(
            "CREATE OR REPLACE TABLE tp_t (a MAP(VARCHAR, NUMBER, 5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'. | syntax "
                + "error line 1 at position 19 unexpected '<EOF>'.",
            "SELECT NULL::ARRAY(");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '<EOF>'. | syntax "
                + "error line 1 at position 20 unexpected '<EOF>'.",
            "SELECT NULL::OBJECT(");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '5'. | syntax error "
                + "line 1 at position 19 unexpected '5'.",
            "SELECT NULL::ARRAY(5");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '5'. | syntax error "
                + "line 1 at position 20 unexpected '5'.",
            "SELECT NULL::OBJECT(5");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'. | syntax "
                + "error line 1 at position 26 unexpected '<EOF>'.",
            "SELECT CAST(NULL AS ARRAY(");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '5'. | syntax error "
                + "line 1 at position 27 unexpected '5'.",
            "SELECT CAST(NULL AS OBJECT(5");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '5'. | syntax error "
                + "line 1 at position 19 unexpected '5'.",
            "SELECT NULL::ARRAY(5) FROM dual_none");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '5'. | syntax error "
                + "line 1 at position 19 unexpected '5'.",
            "SELECT NULL::ARRAY(5), 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '5'. | syntax error "
                + "line 1 at position 19 unexpected '5'.",
            "SELECT NULL::ARRAY(5) + 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '5'. | syntax error "
                + "line 1 at position 23 unexpected '5'.",
            "SELECT 1, NULL::OBJECT(5)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '5'. | syntax error "
                + "line 1 at position 19 unexpected '5'.",
            "SELECT NULL::ARRAY(5)::VARCHAR");
        answer(
            "SELECT (NULL::ARRAY(5))");
        answer(
            "SELECT COALESCE(NULL::ARRAY(5), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '5'. | syntax error "
                + "line 1 at position 40 unexpected ','.",
            "CREATE OR REPLACE TABLE tp_t (a ARRAY(5), b INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '5'. | syntax error "
                + "line 1 at position 47 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (b INT, a ARRAY(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ')'. | syntax error "
                + "line 1 at position 40 unexpected 'NOT'.",
            "CREATE OR REPLACE TABLE tp_t (a ARRAY() NOT NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '5'. | syntax error "
                + "line 1 at position 41 unexpected '2'. | syntax error line 1 at position 43 unexpected ','.",
            "CREATE OR REPLACE TABLE tp_t (a OBJECT(5,2), b INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '5'.",
            "ALTER TABLE tp_x ADD COLUMN a ARRAY(5)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '5'. | syntax error "
                + "line 1 at position 42 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION tp_f(x ARRAY(5)) RETURNS INT AS '1'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '5'.",
            "CREATE OR REPLACE FUNCTION tp_f(x INT) RETURNS ARRAY(5) AS '1'");
        assertAnswer(
            "Function TP_F successfully created.",
            "CREATE OR REPLACE FUNCTION tp_f(x OBJECT()) RETURNS INT AS '1'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '5'.",
            "DECLARE x ARRAY(5); BEGIN RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '5'.",
            "BEGIN LET x OBJECT(5) := NULL; RETURN 1; END;");
        answer(
            "SELECT NULL::ARRAY(5) AS a, NULL::OBJECT(5) AS b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '5'.",
            "SELECT NULL::VARIANT(5)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('. | syntax error "
                + "line 1 at position 28 unexpected '5'.",
            "SELECT CAST(NULL AS VARIANT(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '('.",
            "CREATE OR REPLACE TABLE tp_t (a VARIANT(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '5'.",
            "SELECT NULL::BOOLEAN(5)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '5'.",
            "SELECT NULL::DATE(5)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '('.",
            "CREATE OR REPLACE TABLE tp_t (a BOOLEAN(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '('.",
            "CREATE OR REPLACE TABLE tp_t (a DATE(5))");
    }

    @Test
    public void aListOfDefinitionsIsReadOnAfterAFaultInAType() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected '2'. | syntax error line 1 at position 44 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ','. | syntax error "
                + "line 1 at position 40 unexpected '2'. | syntax error line 1 at position 42 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a FLOAT(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 41 unexpected '2'. | syntax error line 1 at position 43 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a BINARY(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ','. | syntax error "
                + "line 1 at position 39 unexpected '2'. | syntax error line 1 at position 41 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a TIME(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected ','. | syntax error "
                + "line 1 at position 48 unexpected '2'. | syntax error line 1 at position 50 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a TIMESTAMP_NTZ(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ','. | syntax error "
                + "line 1 at position 39 unexpected '2'. | syntax error line 1 at position 41 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a CHAR(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 41 unexpected '2'. | syntax error line 1 at position 43 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a STRING(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected ','. | syntax error "
                + "line 1 at position 48 unexpected '2'. | syntax error line 1 at position 50 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a TIMESTAMP_LTZ(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ','. | syntax error "
                + "line 1 at position 43 unexpected '2'. | syntax error line 1 at position 45 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a DATETIME(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ','. | syntax error "
                + "line 1 at position 43 unexpected '3'. | syntax error line 1 at position 45 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a NUMBER(5,2,3))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected '2'. | syntax error line 1 at position 44 unexpected '3'. | "
                + "syntax error line 1 at position 46 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2,3))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a NUMBER(5,))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '('.",
            "CREATE OR REPLACE TABLE tp_a (a NUMBER(,5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '('.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(,))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '2'.",
            "CREATE OR REPLACE TABLE tp_a (a NUMBER(5 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '2'.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '('.",
            "CREATE OR REPLACE TABLE tp_a (a INT(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '('.",
            "CREATE OR REPLACE TABLE tp_a (a INT(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '('.",
            "CREATE OR REPLACE TABLE tp_a (a DOUBLE(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected ','. | syntax error "
                + "line 1 at position 44 unexpected '2'. | syntax error line 1 at position 46 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a TIMESTAMP(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ','. | syntax error "
                + "line 1 at position 39 unexpected '2'. | syntax error line 1 at position 41 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a TEXT(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected ','. | syntax error "
                + "line 1 at position 44 unexpected '2'. | syntax error line 1 at position 46 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a VARBINARY(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected ','. | syntax error "
                + "line 1 at position 44 unexpected '3'. | syntax error line 1 at position 46 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a NUMERIC(5,2,3))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected ','. | syntax error "
                + "line 1 at position 44 unexpected '3'. | syntax error line 1 at position 46 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a DECIMAL(5,2,3))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected ','. | syntax error "
                + "line 1 at position 47 unexpected '2'. | syntax error line 1 at position 49 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a TIMESTAMP_TZ(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected '2'. | syntax error line 1 at position 44 unexpected ','.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2), b INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected '2'. | syntax error line 1 at position 44 unexpected ','.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2), b INT, c INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected ','. | syntax error "
                + "line 1 at position 49 unexpected '2'. | syntax error line 1 at position 51 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (b INT, a VARCHAR(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected ','. | syntax error "
                + "line 1 at position 49 unexpected '2'. | syntax error line 1 at position 51 unexpected ','.",
            "CREATE OR REPLACE TABLE tp_a (b INT, a VARCHAR(5,2), c INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected '2'. | syntax error line 1 at position 45 unexpected 'NOT'.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2) NOT NULL)");
        answer(
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2) DEFAULT 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected '2'. | syntax error line 1 at position 44 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2)) COMMENT = 'c'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected '2'. | syntax error line 1 at position 44 unexpected ','.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2), b VARCHAR(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected '2'. | syntax error line 1 at position 44 unexpected ','.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2), PRIMARY KEY (a))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ','. | syntax error "
                + "line 1 at position 40 unexpected '2'. | syntax error line 1 at position 42 unexpected ','.",
            "CREATE OR REPLACE TABLE tp_a (a FLOAT(5,2), b INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ','. | syntax error "
                + "line 1 at position 39 unexpected '2'. | syntax error line 1 at position 41 unexpected ','.",
            "CREATE OR REPLACE TABLE tp_a (a TIME(5,2), b INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ','. | syntax error "
                + "line 1 at position 43 unexpected '3'. | syntax error line 1 at position 45 unexpected ','.",
            "CREATE OR REPLACE TABLE tp_a (a NUMBER(5,2,3), b INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 12 unexpected ','. | syntax error "
                + "line 2 at position 13 unexpected '2'. | syntax error line 3 at position 0 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (\n a VARCHAR(5,2)\n)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected '2'.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected '2'.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected '<EOF>'.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected ','. | syntax error "
                + "line 1 at position 52 unexpected '2'. | syntax error line 1 at position 54 unexpected ')'.",
            "CREATE OR REPLACE TEMPORARY TABLE tp_a (a VARCHAR(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected ','. | syntax error "
                + "line 1 at position 52 unexpected '2'. | syntax error line 1 at position 54 unexpected ')'.",
            "CREATE OR REPLACE TRANSIENT TABLE tp_a (a VARCHAR(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ','. | syntax error "
                + "line 1 at position 45 unexpected '2'. | syntax error line 1 at position 47 unexpected ')'.",
            "CREATE TABLE IF NOT EXISTS tp_a (a VARCHAR(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected '2'. | syntax error line 1 at position 44 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2)) AS SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ','. | syntax error "
                + "line 1 at position 40 unexpected '2'.",
            "ALTER TABLE tp_x ADD COLUMN a VARCHAR(5,2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ','. | syntax error "
                + "line 1 at position 40 unexpected '2'.",
            "ALTER TABLE tp_x ADD COLUMN a VARCHAR(5,2), b INT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected ','. | syntax error "
                + "line 1 at position 56 unexpected '2'.",
            "ALTER TABLE tp_x ALTER COLUMN a SET DATA TYPE VARCHAR(5,2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected ','. | syntax error "
                + "line 1 at position 44 unexpected '2'. | syntax error line 1 at position 46 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION tp_f(x VARCHAR(5,2)) RETURNS INT AS '1'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 56 unexpected ','.",
            "CREATE OR REPLACE FUNCTION tp_f(x INT) RETURNS VARCHAR(5,2) AS '1'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected ','. | syntax error "
                + "line 1 at position 66 unexpected '2'. | syntax error line 1 at position 68 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION tp_f(x INT) RETURNS TABLE (a VARCHAR(5,2)) AS 'SELECT 1'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ','. | syntax error "
                + "line 1 at position 45 unexpected '2'. | syntax error line 1 at position 47 unexpected ')'.",
            "CREATE OR REPLACE PROCEDURE tp_p(x VARCHAR(5,2)) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; "
                + "$$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','.",
            "DECLARE x VARCHAR(5,2); BEGIN RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected ','. | syntax error "
                + "line 1 at position 59 unexpected ')'.",
            "CREATE OR REPLACE VIEW tp_v (a) AS SELECT NULL::VARCHAR(5,2)");
        answer(
            "CREATE OR REPLACE EXTERNAL TABLE tp_e (a VARCHAR(5,2) AS (value:a::VARCHAR)) LOCATION = @nosuch "
                + "FILE_FORMAT = (TYPE = CSV)");
        answer(
            "CREATE OR REPLACE DYNAMIC TABLE tp_d (a VARCHAR(5,2)) TARGET_LAG = '1 minute' WAREHOUSE = nosuch AS "
                + "SELECT 1");
        answer(
            "CREATE OR REPLACE HYBRID TABLE tp_h (a VARCHAR(5,2) PRIMARY KEY)");
    }

    @Test
    public void moreListsOfDefinitions() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ','. | syntax error "
                + "line 1 at position 43 unexpected '3'. | syntax error line 1 at position 45 unexpected '4'. | "
                + "syntax error line 1 at position 47 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a NUMBER(5,2,3,4))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected '2'. | syntax error line 1 at position 44 unexpected '3'. | "
                + "syntax error line 1 at position 46 unexpected '4'. | syntax error line 1 at position 48 "
                + "unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2,3,4))");
        answer(
            "CREATE OR REPLACE TABLE tp_a (a OBJECT(a VARCHAR(5,2), b INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '5'. | syntax error "
                + "line 1 at position 47 unexpected '2'. | syntax error line 1 at position 49 unexpected '3'. | "
                + "syntax error line 1 at position 51 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a INT, b ARRAY(5,2,3))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected ','. | syntax error "
                + "line 1 at position 44 unexpected '2'. | syntax error line 1 at position 46 unexpected ','.",
            "CREATE OR REPLACE FUNCTION tp_f(x VARCHAR(5,2), y INT) RETURNS INT AS '1'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected '-'. | syntax error line 1 at position 45 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,-2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected ','. | syntax error "
                + "line 1 at position 42 unexpected '2'. | syntax error line 2 at position 0 unexpected ','.",
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2)\n, b INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '5'. | syntax error "
                + "line 1 at position 40 unexpected ','.",
            "CREATE OR REPLACE TABLE tp_a (a ARRAY(5), b ARRAY(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ','. | syntax error "
                + "line 1 at position 43 unexpected '3'. | syntax error line 1 at position 46 unexpected 'NOT'.",
            "CREATE OR REPLACE TABLE tp_a (a NUMBER(5,2,3) NOT NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '5'. | syntax error "
                + "line 1 at position 38 unexpected '2'.",
            "ALTER TABLE tp_x ADD COLUMN a ARRAY(5,2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ','. | syntax error "
                + "line 1 at position 41 unexpected '3'.",
            "ALTER TABLE tp_x ADD COLUMN a NUMBER(5,2,3)");
        answer(
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,x))");
        answer(
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2) COMMENT 'c')");
        answer(
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2) COLLATE 'en')");
        answer(
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2) UNIQUE)");
        answer(
            "CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,2) b)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '5'. | syntax error "
                + "line 1 at position 42 unexpected 'NOT'.",
            "CREATE OR REPLACE TABLE tp_a (a OBJECT(5) NOT NULL, b INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '5'. | syntax error "
                + "line 1 at position 40 unexpected '2'. | syntax error line 1 at position 42 unexpected ','.",
            "CREATE OR REPLACE TABLE tp_a (a ARRAY(5,2), b INT) COMMENT = 'c'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '5'. | syntax error "
                + "line 1 at position 43 unexpected ','.",
            "CREATE OR REPLACE PROCEDURE tp_p(x ARRAY(5), y INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; "
                + "END; $$");
        answer(
            "SELECT NULL::OBJECT(a VARCHAR(5,2), b INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ','. | syntax error "
                + "line 1 at position 33 unexpected '3'. | syntax error line 1 at position 35 unexpected ')'.",
            "SELECT NULL::OBJECT(a NUMBER(5,2,3))");
        answer(
            "SELECT CAST(NULL AS OBJECT(a VARCHAR(5,2)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected ','. | syntax error "
                + "line 1 at position 48 unexpected '2'. | syntax error line 1 at position 50 unexpected ','.",
            "CREATE OR REPLACE TABLE tp_a (a TIMESTAMP_NTZ(5,2), b TIMESTAMP_NTZ(3))");
    }

    @Test
    public void nestedListsPoliciesAndVectors() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '('.",
            "SELECT NULL::MAP(VARCHAR, NUMBER())");
        assertAnswer(
            "null",
            "SELECT NULL::MAP(VARCHAR(), NUMBER)");
        answer(
            "SELECT NULL::MAP(VARCHAR, NUMBER(5,2,3))");
        answer(
            "SELECT NULL::MAP(VARCHAR, VARCHAR(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '('.",
            "SELECT NULL::ARRAY(NUMBER())");
        answer(
            "SELECT NULL::ARRAY(VARCHAR(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '('.",
            "SELECT NULL::OBJECT(a NUMBER())");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ','. | syntax error "
                + "line 1 at position 32 unexpected '2'. | syntax error line 1 at position 34 unexpected ')'.",
            "SELECT NULL::OBJECT(a VARCHAR(5,2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '('.",
            "SELECT NULL::OBJECT(a NUMBER, b NUMBER())");
        answer(
            "SELECT CAST(NULL AS MAP(VARCHAR, NUMBER()))");
        answer(
            "SELECT CAST(NULL AS ARRAY(NUMBER()))");
        answer(
            "CREATE OR REPLACE TABLE tp_t (a MAP(VARCHAR, NUMBER()))");
        answer(
            "CREATE OR REPLACE TABLE tp_t (a ARRAY(NUMBER()))");
        answer(
            "CREATE OR REPLACE MASKING POLICY tp_m2 AS (v NUMBER) RETURNS NUMBER() -> v");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '('.",
            "CREATE OR REPLACE MASKING POLICY tp_m2 AS (v NUMBER()) RETURNS NUMBER -> v");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 71 unexpected ','.",
            "CREATE OR REPLACE MASKING POLICY tp_m2 AS (v NUMBER) RETURNS NUMBER(5,2,3) -> v");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 71 unexpected ','.",
            "CREATE OR REPLACE MASKING POLICY tp_m2 AS (v VARCHAR) RETURNS VARCHAR(5,2) -> v");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected ','. | syntax error "
                + "line 1 at position 55 unexpected '2'. | syntax error line 1 at position 57 unexpected ')'.",
            "CREATE OR REPLACE MASKING POLICY tp_m2 AS (v VARCHAR(5,2)) RETURNS VARCHAR -> v");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '('.",
            "CREATE OR REPLACE ROW ACCESS POLICY tp_r AS (v NUMBER()) RETURNS BOOLEAN -> TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ')'.",
            "SELECT NULL::VECTOR()");
        answer(
            "SELECT CAST(NULL AS VECTOR())");
        answer(
            "SELECT TRY_CAST(NULL AS VECTOR())");
        answer(
            "CREATE OR REPLACE TABLE tp_t (a VECTOR())");
        answer(
            "SELECT NULL::VECTOR(INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ')'. | syntax error "
                + "line 1 at position 31 unexpected ')'.",
            "SELECT CAST(NULL AS VECTOR(INT))");
        answer(
            "CREATE OR REPLACE TABLE tp_t (a VECTOR(INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ')'.",
            "SELECT NULL::VECTOR(INT, )");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '5'.",
            "SELECT NULL::VECTOR(5)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '5'. | syntax error "
                + "line 1 at position 41 unexpected ')'.",
            "CREATE OR REPLACE TABLE tp_t (a VECTOR(5))");
        assertAnswer(
            "Drop statement executed successfully (TP_M2 already dropped).",
            "DROP MASKING POLICY IF EXISTS tp_m2");
        assertAnswer(
            "Drop statement executed successfully (TP_R already dropped).",
            "DROP ROW ACCESS POLICY IF EXISTS tp_r");
        assertAnswer(
            "Drop statement executed successfully (TP_T already dropped).",
            "DROP TABLE IF EXISTS tp_t");
        assertAnswer(
            "Drop statement executed successfully (TP_A already dropped).",
            "DROP TABLE IF EXISTS tp_a");
    }
}
