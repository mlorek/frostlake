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
package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The expression a Snowscript UDF's refusal names is printed from the tree as parsed, before a name is resolved
 * or a type checked. With no query and no window call in it, an operator stands between its operands, and an
 * operand stands in parentheses of its own unless it is a name, a literal or a call of no arguments:
 * {@code (A > 1) AND (A < 3)}, {@code (NOSUCH(1)) + 1}, {@code F2() + 1}; an IS NULL brackets nothing. A number
 * reads by its value, {@code 1.50} as 1.5 and {@code 1e2} as 100; a text keeps its doubled quote. A negated
 * predicate is the NOT of the plain one, a BETWEEN and a CASE are calls of their own names, a simple CASE with
 * its subject first and a missing ELSE a null. A cast spells its target in full - {@code a::INT} is
 * {@code CAST(A AS NUMBER(38,0))}, a text of no width VARCHAR(134217728) - and a TRY_CAST names none. A typed
 * literal is its conversion function's call, an interval INTERVAL_LITERAL over its parts as written, an array and
 * an object literal their constructors, and a path a GET of each of its steps. A keyword that reads as a call
 * keeps the form it was written in, {@code CURRENT_DATE} and {@code CURRENT_DATE()} alike. A membership list
 * gives a computed member a list of its own. Every answer is live-verified.
 */
public class SnowscriptUdfEchoOperatorsTest extends MeasuredCellsTestSupport {

    @Test
    public void literalsAndOperatorsStandAsParsed() {
        engine.execute(
            "CREATE OR REPLACE TABLE rc_t (a INT, b VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS $$ 1 $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1.5)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1.50); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(-1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(-1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(100)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1e2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH('it''s')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch('it''s'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TRUE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TRUE); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(null)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NULL); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(A); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TO_DATE('2020-01-02'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATE '2020-01-02'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(INTERVAL_LITERAL('day', '1'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(INTERVAL '1 day'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(ARRAY_CONSTRUCT(1, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch([1, 2]); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(OBJECT_CONSTRUCT('k', 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch({'k': 1}); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(ARRAY_CONSTRUCT(1, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(ARRAY_CONSTRUCT(1, 2)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(OBJECT_CONSTRUCT('k', 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(OBJECT_CONSTRUCT('k', "
                + "1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(X'0A')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(X'0A'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(0.5)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(.5); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1.0); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(100000000000000000000)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(100000000000000000000); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A + 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A - 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a - 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A * 2)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a * 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A / 2)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a / 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A % 2)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a % 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NEGATE(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(-a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(UNARY PLUS(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(+a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A || 'x')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a || 'x'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A = 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a = 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A <> 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a <> 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A != 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a != 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A < 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a < 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A >= 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a >= 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A > 1) AND (A < 3))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a > 1 AND a < 3); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A > 1) OR (A < 3))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a > 1 OR a < 3); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(A > 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NOT (a > 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(A > 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NOT a > 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IS NULL)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS NULL); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IS NOT NULL)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS NOT NULL); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (1, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1, 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(A IN (1, 2)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT IN (1, 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(BETWEEN(A, 1, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a BETWEEN 1 AND 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(BETWEEN(A, 1, 2)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT BETWEEN 1 AND 2); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A LIKE 'x%')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a LIKE 'x%'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(A LIKE 'x%'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT LIKE 'x%'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A ILIKE 'x')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a ILIKE 'x'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A LIKE 'x' LIKE '!')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a LIKE 'x' ESCAPE '!'); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A RLIKE 'x')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a RLIKE 'x'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(EQUAL_NULL(A, 1)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS DISTINCT FROM 1); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(EQUAL_NULL(A, 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS NOT DISTINCT FROM "
                + "1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A + 1) * 2)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((a + 1) * 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A + (1 * 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1 * 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A + 1) + 2)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1 + 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(('a' || 'b') || 'c')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch('a' || 'b' || 'c'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(((A = 1) AND (A = 2)) AND (A = 3))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a = 1 AND a = 2 AND a = "
                + "3); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A + 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(((a + 1))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A + -1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + (-1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A - -1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a - -1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1 + A)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1 + a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A < 1) = TRUE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a < 1 = TRUE); END $$");
    }

    @Test
    public void aCastSpellsItsTargetAndAConditionalKeepsItsName() {
        engine.execute(
            "CREATE OR REPLACE TABLE rc_t (a INT, b VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS $$ 1 $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS NUMBER(38,0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::INT); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS VARCHAR(134217728)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::VARCHAR); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS VARCHAR(5)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::VARCHAR(5)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS NUMBER(10,2)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::NUMBER(10,2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS FLOAT))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CAST(a AS FLOAT)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TRY_CAST('1'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TRY_CAST('1' AS INT)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS DATE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::DATE); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS VARCHAR(134217728)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::STRING); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS VARCHAR(134217728)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::TEXT); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS BOOLEAN))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::BOOLEAN); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST('1' AS NUMBER(38,0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch('1'::INTEGER); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS TIMESTAMP_NTZ(9)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::TIMESTAMP); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS VARIANT))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::VARIANT); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS ARRAY))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::ARRAY); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS NUMBER(38,0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::NUMBER); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS NUMBER(5,0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::DECIMAL(5)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS NUMBER(38,0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::BIGINT); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS FLOAT))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::DOUBLE); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS VARCHAR(1)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::CHAR); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS BINARY(67108864)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::BINARY); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS TIMESTAMP_LTZ(3)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::TIMESTAMP_LTZ(3)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS TIME(9)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::TIME); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(CAST(A AS NUMBER(38,0)) AS "
                + "VARCHAR(134217728)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::INT::VARCHAR); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS VARCHAR(3)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CAST(a AS VARCHAR(3))); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TRY_CAST('x'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TRY_CAST('x' AS DATE)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS OBJECT))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::OBJECT); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TO_NUMBER(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TO_NUMBER(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TO_NUMBER('1', 10, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TO_NUMBER('1', 10, 2)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TO_VARCHAR(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TO_VARCHAR(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TO_DATE('2020-01-01'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TO_DATE('2020-01-01')); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TO_CHAR(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TO_CHAR(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TO_BOOLEAN(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TO_BOOLEAN(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TO_DOUBLE(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TO_DOUBLE(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TO_TIMESTAMP('2020-01-01'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN "
                + "nosuch(TO_TIMESTAMP('2020-01-01')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TO_VARIANT(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TO_VARIANT(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS FLOAT))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::REAL); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS NUMBER(3,1)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::NUMERIC(3,1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(IFF(A > 1, 2, 3))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(IFF(a > 1, 2, 3)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(IFF(TRUE, 2, 3))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(IFF(TRUE, 2, 3)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(IFF(A, 2, 3))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(IFF(a, 2, 3)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CASE(A > 1, 2, 3))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CASE WHEN a > 1 THEN 2 "
                + "ELSE 3 END); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CASE(A > 1, 2, A > 0, 1, 3))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CASE WHEN a > 1 THEN 2 "
                + "WHEN a > 0 THEN 1 ELSE 3 END); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CASE(A > 1, 2, null))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CASE WHEN a > 1 THEN 2 "
                + "END); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CASE(A, 1, 2, 3))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CASE a WHEN 1 THEN 2 ELSE "
                + "3 END); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CASE(A, 1, 2, 2, 3, null))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CASE a WHEN 1 THEN 2 WHEN "
                + "2 THEN 3 END); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CASE(A > 1, 'x', null))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CASE WHEN a > 1 THEN 'x' "
                + "ELSE NULL END); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(COALESCE(A, 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(COALESCE(a, 0)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(COALESCE(A, 0, 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(COALESCE(a, 0, 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NVL(A, 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NVL(a, 0)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(IFNULL(A, 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(IFNULL(a, 0)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NVL2(A, 1, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NVL2(a, 1, 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NULLIF(A, 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NULLIF(a, 0)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(ZEROIFNULL(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(ZEROIFNULL(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NULLIFZERO(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NULLIFZERO(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(DECODE(A, 1, 'x', 'y'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DECODE(a, 1, 'x', 'y')); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GREATEST(A, 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(GREATEST(a, 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(LEAST(A, 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(LEAST(a, 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(COALESCE(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(COALESCE(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(EQUAL_NULL(A, 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(EQUAL_NULL(a, 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(BOOLAND(A, 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(BOOLAND(a, 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CASE(A IS NULL, 1, A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CASE WHEN a IS NULL THEN "
                + "1 ELSE a END); END $$");
    }

    @Test
    public void keywordCallsDatePartsAndAggregates() {
        engine.execute(
            "CREATE OR REPLACE TABLE rc_t (a INT, b VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS $$ 1 $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_DATE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_DATE); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_DATE())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_DATE()); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_TIMESTAMP)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_TIMESTAMP); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_TIMESTAMP(3))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_TIMESTAMP(3)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_TIME)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_TIME); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_USER)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_USER); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_USER())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_USER()); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_ROLE())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_ROLE()); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(LOCALTIMESTAMP)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(LOCALTIMESTAMP); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(LOCALTIME)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(LOCALTIME); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(SYSDATE())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(SYSDATE()); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(EXTRACT(from 'year', CURRENT_DATE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(EXTRACT(year FROM "
                + "CURRENT_DATE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(DATE_PART('year', CURRENT_DATE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATE_PART('year', "
                + "CURRENT_DATE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(DATE_PART(YEAR, CURRENT_DATE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATE_PART(year, "
                + "CURRENT_DATE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(DATEADD(DAY, 1, CURRENT_DATE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATEADD(day, 1, "
                + "CURRENT_DATE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(DATEADD('day', 1, CURRENT_DATE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATEADD('day', 1, "
                + "CURRENT_DATE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(DATEDIFF('day', CURRENT_DATE, "
                + "CURRENT_DATE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATEDIFF('day', "
                + "CURRENT_DATE, CURRENT_DATE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(DATEDIFF(DAY, CURRENT_DATE, CURRENT_DATE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATEDIFF(day, "
                + "CURRENT_DATE, CURRENT_DATE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(DATE_TRUNC('month', CURRENT_DATE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATE_TRUNC('month', "
                + "CURRENT_DATE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(YEAR(CURRENT_DATE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(YEAR(CURRENT_DATE)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(POSITION('a', 'abc'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(POSITION('a' IN 'abc')); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(POSITION('a', 'abc'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(POSITION('a', 'abc')); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(SUBSTR('abc', 1, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(SUBSTR('abc', 1, 2)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(SUBSTRING('abc', 1, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(SUBSTRING('abc', 1, 2)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(UPPER('x'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(UPPER('x')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(ABS(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(ABS(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(LENGTH('abc'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(LENGTH('abc')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CONCAT('a', 'b'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CONCAT('a', 'b')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(ROUND(A, 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(ROUND(a, 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(MOD(A, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(MOD(a, 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TRIM('  x '))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TRIM('  x ')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(LTRIM('xa', 'x'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(LTRIM('xa', 'x')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GETDATE())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(GETDATE()); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_SCHEMA())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_SCHEMA()); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(UUID_STRING())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(UUID_STRING()); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(RANDOM())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(RANDOM()); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CHARINDEX('a', 'abc'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CHARINDEX('a', 'abc')); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(SPLIT_PART('a,b', ',', 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(SPLIT_PART('a,b', ',', "
                + "1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH('a' REGEXP_LIKE 'a')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(REGEXP_LIKE('a', 'a')); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH('a' REGEXP 'a')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch('a' REGEXP 'a'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CONTAINS('abc', 'a'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CONTAINS('abc', 'a')); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(SQUARE(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(SQUARE(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TRUNC(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TRUNC(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TRUNC(A, 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TRUNC(a, 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TO_CHAR(CURRENT_DATE, 'YYYY'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TO_CHAR(CURRENT_DATE, "
                + "'YYYY')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_DATE + 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_DATE + 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_DATE + (INTERVAL_LITERAL('day', "
                + "'1')))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_DATE + INTERVAL "
                + "'1 day'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_TIMESTAMP - "
                + "(INTERVAL_LITERAL('hours', '2', 'minutes', '3')))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_TIMESTAMP - "
                + "INTERVAL '2 hours, 3 minutes'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(SUM(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(SUM(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(COUNT(*))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(COUNT(*)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(COUNT(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(COUNT(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(COUNT(DISTINCT A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(COUNT(DISTINCT a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AVG(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(AVG(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(MAX(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(MAX(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(LISTAGG(A, ','))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(LISTAGG(a, ',')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(LISTAGG(A, ','))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(LISTAGG(a, ',') WITHIN "
                + "GROUP (ORDER BY a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(ARRAY_AGG(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(ARRAY_AGG(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(MEDIAN(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(MEDIAN(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(MIN(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(MIN(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(COUNT_IF(A > 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(COUNT_IF(a > 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(SUM(DISTINCT A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(SUM(DISTINCT a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(ANY_VALUE(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(ANY_VALUE(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(STDDEV(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(STDDEV(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(PERCENTILE_CONT(0.5))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(PERCENTILE_CONT(0.5) "
                + "WITHIN GROUP (ORDER BY a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(ARRAY_AGG(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(ARRAY_AGG(a) WITHIN GROUP "
                + "(ORDER BY a DESC)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(COUNT(1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(COUNT(1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(COUNT(A, A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(COUNT(a, a)); END $$");
    }

    @Test
    public void whichOperandsStandBare() {
        engine.execute(
            "CREATE OR REPLACE TABLE rc_t (a INT, b VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS $$ 1 $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS $$ x $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (F1(1)) + 1",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN f1(1) + 1; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (F1(A)) + F2()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN f1(a) + f2(); END $$");
        assertAnswer(
            "Function EU successfully created.",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN RANDOM() + 1; END $$");
        assertAnswer(
            "Function EU successfully created.",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN CURRENT_DATE() + 1; END $$");
        assertAnswer(
            "Function EU successfully created.",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN ABS(1) + 1; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH() + 1",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch() + 1; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((CAST(A AS NUMBER(38,0))) + 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::INT + 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((NEGATE(A)) + 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(-a + 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((NOT(A)) AND TRUE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NOT a AND TRUE); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A IS NULL) AND TRUE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS NULL AND TRUE); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A + 1 IS NULL)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1 IS NULL); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A IN (1, 2)) AND TRUE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1, 2) AND TRUE); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A + 1) IN (1, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1 IN (1, 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (1 + 1) IN (2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1 + 1, 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((BETWEEN(A, 1, 2)) OR TRUE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a BETWEEN 1 AND 2 OR "
                + "TRUE); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A LIKE 'x') AND TRUE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a LIKE 'x' AND TRUE); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A + 1) LIKE 'x')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1 LIKE 'x'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A LIKE ('x' || 'y'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a LIKE 'x' || 'y'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((CASE(A > 1, 2, null)) + 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CASE WHEN a > 1 THEN 2 "
                + "END + 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((CAST(CAST(A AS NUMBER(38,0)) AS "
                + "VARCHAR(134217728))) || 'x')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::INT::VARCHAR || 'x'); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GET(ARRAY_CONSTRUCT(1, 2), 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch([1, 2][0]); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A = 1) = TRUE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a = 1 = TRUE); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((1 + (2 * 3)) - 4)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1 + 2 * 3 - 4); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NEGATE(A + 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(-(a + 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(-1.5)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(-1.5); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(-1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(-(1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(-1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(- 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(UNARY PLUS(1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(+1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(TRUE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NOT TRUE); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(NOT(A)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NOT NOT a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(A ILIKE 'x'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT ILIKE 'x'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(A RLIKE 'x'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT RLIKE 'x'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(A REGEXP 'x'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT REGEXP 'x'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A LIKE_ANY null LIKE_ANY 'x' LIKE_ANY 'y')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a LIKE ANY ('x', 'y')); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A ILIKE_ANY null ILIKE_ANY 'x' ILIKE_ANY "
                + "'y')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a ILIKE ANY ('x', 'y')); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A LIKE_ALL null LIKE_ALL 'x' LIKE_ALL 'y')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a LIKE ALL ('x', 'y')); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A ILIKE 'x' ILIKE '!')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a ILIKE 'x' ESCAPE '!'); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN ('x'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN ('x')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(A IN (1)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT IN (1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(ROW(A, 1) IN (ROW(1, 2), ROW(3, 4)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((a, 1) IN ((1, 2), (3, "
                + "4))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(ROW(A, 1) = ROW(1, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((a, 1) = (1, 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(EQUAL_NULL(A, null))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS NOT DISTINCT FROM "
                + "NULL); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH('x' || (CAST(A AS VARCHAR(134217728))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch('x' || a::VARCHAR); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((TRUE AND FALSE) OR TRUE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TRUE AND FALSE OR TRUE); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A > 1) AND ((A < 3) OR (A = 5)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a > 1 AND (a < 3 OR a = "
                + "5)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SUM(A)) + 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(SUM(a) + 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(COUNT(*) + 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(COUNT(*) + 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A IS NULL) = TRUE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((a IS NULL) = TRUE); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A + 1) = 2)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1 = 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A = 1) OR ((A = 2) AND (A = 3)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a = 1 OR a = 2 AND a = "
                + "3); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((NEGATE(A)) * 2)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(-a * 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A * (NEGATE(A)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a * -a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(ZZ + 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(zz + 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(T + 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(t.a + 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"a b\" + 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(\"a b\" + 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A + \"a\")",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(\"A\" + \"a\"); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH($1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch($1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(BETWEEN(A + 1, 1, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1 BETWEEN 1 AND 2); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(BETWEEN(A, 1 + 1, 2 * 3))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a BETWEEN 1 + 1 AND 2 * "
                + "3); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((CAST(A AS VARCHAR(134217728))) LIKE 'x')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::VARCHAR LIKE 'x'); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A IS NULL) OR (A IS NOT NULL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS NULL OR a IS NOT "
                + "NULL); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A IN (1, 2)) = TRUE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1, 2) = TRUE); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(A IN (1, 2)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NOT a IN (1, 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(A IS NULL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NOT (a IS NULL)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(A LIKE 'x'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NOT a LIKE 'x'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(ARRAY_AGG(DISTINCT A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(ARRAY_AGG(DISTINCT a)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(LISTAGG(DISTINCT A, ','))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(LISTAGG(DISTINCT a, ',"
                + "')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A < 1, A > 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a < 1, a > 1); END $$");
    }

    @Test
    public void castTargetsLiteralsAndDateParts() {
        engine.execute(
            "CREATE OR REPLACE TABLE rc_t (a INT, b VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS $$ 1 $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS $$ x $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS NUMBER(38,0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::NUMBER(38, 0)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS NUMBER(10,0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::NUMBER(10)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS VARCHAR(16777216)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::VARCHAR(16777216)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS FLOAT))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::FLOAT4); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS NUMBER(38,0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::TINYINT); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS TIMESTAMP_NTZ(9)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::TIMESTAMP_NTZ); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS TIMESTAMP_TZ(9)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::TIMESTAMP_TZ); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS TIMESTAMP_NTZ(9)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::DATETIME); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS TIME(3)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::TIME(3)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS BINARY(10)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::BINARY(10)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS BINARY(67108864)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::VARBINARY); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS VARCHAR(3)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::NCHAR(3)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS UNKNOWN))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::GEOGRAPHY); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS VECTOR(INT, 3)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::VECTOR(INT, 3)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS ARRAY(NUMBER(38,0))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::ARRAY(INT)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS OBJECT(k NUMBER(38,0))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::OBJECT(k INT)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS MAP(VARCHAR(134217728), "
                + "NUMBER(38,0))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::MAP(VARCHAR, INT)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TRY_CAST(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TRY_CAST(a AS "
                + "VARCHAR(5))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS FLOAT))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::DOUBLE PRECISION); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS VARCHAR(5)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::CHARACTER VARYING(5)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS NUMBER(38,0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::INTEGER); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS NUMBER(38,0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::SMALLINT); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS NUMBER(38,0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::BYTEINT); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS FLOAT))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::FLOAT8); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS TIMESTAMP_LTZ(9)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::TIMESTAMP_LTZ); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS TIMESTAMP_NTZ(0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::TIMESTAMP_NTZ(0)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS VARCHAR(7)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::NVARCHAR(7)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS UNKNOWN))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::GEOMETRY); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS NUMBER(4,1)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::DEC(4, 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS TIMESTAMP_NTZ(4)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::TIMESTAMP(4)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS TIMESTAMP_NTZ(2)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::DATETIME(2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS VARCHAR(4)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::CHAR(4)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS NUMBER(38,0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::NUMERIC); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS FLOAT))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::FLOAT(5)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS TIMESTAMP_TZ(9)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::TIMESTAMPTZ); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS TIMESTAMP_LTZ(9)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::TIMESTAMPLTZ); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS TIMESTAMP_NTZ(9)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::TIMESTAMPNTZ); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TO_TIME('01:02:03'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TIME '01:02:03'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(INTERVAL_LITERAL('year', '1', 'months', "
                + "'2'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(INTERVAL '1 year, 2 "
                + "months'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(INTERVAL_LITERAL('SECOND', '3'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(INTERVAL '3'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(INTERVAL_LITERAL('d', '1'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(INTERVAL '1 d'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(INTERVAL_LITERAL('DAY', '1'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(INTERVAL '1 DAY'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(INTERVAL_LITERAL('day', '-1'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(INTERVAL '-1 day'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(INTERVAL_LITERAL('hours', '1.5'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(INTERVAL '1.5 hours'); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1.5)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1.5e0); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(0.01)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1E-2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(0.1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(0.10); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(0)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(-0.0); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1.); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH('a\\\\nb')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch('a\\\\nb'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH('\u00fcn\u00ef')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch('\\u00fcn\\u00ef'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(FALSE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(FALSE); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TRUE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(true); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(12345678901234567890.123)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN "
                + "nosuch(12345678901234567890.123); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(Infinity)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1e400); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(0.000000001)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(0.000000001); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH('')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(''); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH('a b')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch('a b'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TO_TIME(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TO_TIME(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TO_TIMESTAMP_NTZ('2020-01-01'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN "
                + "nosuch(TO_TIMESTAMP_NTZ('2020-01-01')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((TO_DATE('2020-01-02')) + 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATE '2020-01-02' + 1); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CEIL(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CEIL(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(EXTRACT(from 'month', CURRENT_TIMESTAMP))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(EXTRACT(month FROM "
                + "CURRENT_TIMESTAMP)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(EXTRACT(from 'year', CURRENT_DATE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(EXTRACT('year', "
                + "CURRENT_DATE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(COLLATE(CAST(A AS VARCHAR(134217728)), "
                + "'en'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::VARCHAR COLLATE 'en'); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TIMESTAMPADD(DAY, 1, CURRENT_DATE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TIMESTAMPADD(day, 1, "
                + "CURRENT_DATE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(DATEDIFF(MONTH, CURRENT_DATE, "
                + "CURRENT_DATE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATEDIFF(month, "
                + "CURRENT_DATE, CURRENT_DATE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(DATE_TRUNC(MONTH, CURRENT_DATE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATE_TRUNC(month, "
                + "CURRENT_DATE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TRUNC(CURRENT_DATE, 'MM'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TRUNC(CURRENT_DATE, "
                + "'MM')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(LAST_DAY(CURRENT_DATE, 'month'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(LAST_DAY(CURRENT_DATE, "
                + "'month')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(LAST_DAY(CURRENT_DATE, MONTH))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(LAST_DAY(CURRENT_DATE, "
                + "month)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CONVERT_TIMEZONE('UTC', CURRENT_TIMESTAMP))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CONVERT_TIMEZONE('UTC', "
                + "CURRENT_TIMESTAMP)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(FILTER(ARRAY_CONSTRUCT(1, 2), (X -> X > 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(FILTER([1, 2], x -> x > "
                + "1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TRANSFORM(ARRAY_CONSTRUCT(1), (X -> X + 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TRANSFORM([1], x INT -> x "
                + "+ 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(DATEADD(MONTH, -1, CURRENT_DATE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATEADD(MONTH, -1, "
                + "CURRENT_DATE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(DATEADD(DAY, 1, CURRENT_DATE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATEADD(\"DAY\", 1, "
                + "CURRENT_DATE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(MONTH(CURRENT_DATE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(MONTH(CURRENT_DATE)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(DAYOFWEEK(CURRENT_DATE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DAYOFWEEK(CURRENT_DATE)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(HOUR(CURRENT_TIMESTAMP))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(HOUR(CURRENT_TIMESTAMP)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(WEEK(CURRENT_DATE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(WEEK(CURRENT_DATE)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(DATE_PART(EPOCH_SECOND, CURRENT_TIMESTAMP))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATE_PART(epoch_second, "
                + "CURRENT_TIMESTAMP)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TIMEDIFF(HOUR, CURRENT_TIMESTAMP, "
                + "CURRENT_TIMESTAMP))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TIMEDIFF(hour, "
                + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_TIMESTAMP())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_TIMESTAMP()); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_TIME(2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_TIME(2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(LOCALTIME())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(LOCALTIME()); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(LOCALTIMESTAMP(3))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(LOCALTIMESTAMP(3)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(SYSDATE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(SYSDATE); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_ROLE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_ROLE); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_DATABASE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_DATABASE); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_SCHEMA)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_SCHEMA); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_WAREHOUSE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_WAREHOUSE); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_SESSION)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_SESSION); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_ACCOUNT)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_ACCOUNT); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GETDATE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(GETDATE); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(SYSTIMESTAMP())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(SYSTIMESTAMP()); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_REGION())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_REGION()); END $$");
    }

    @Test
    public void membershipListsPathsAndConditionals() {
        engine.execute(
            "CREATE OR REPLACE TABLE rc_t (a INT, b VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS $$ 1 $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS $$ x $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (1 IN (1 + 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1, 1 + "
                + "1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (1 + 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1 + 1)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (1 + 1) IN (2, 3))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1 + 1, 2,"
                + " 3)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (1, 2 IN (1 + 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1, 2, 1 "
                + "+ 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (A, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (a, 2)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (ABS(1)) IN (2))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (ABS(1), "
                + "2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (-1, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (-1, 2)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(A IN (1 + 1) IN (2)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT IN (1 + "
                + "1, 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (1, null))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1, "
                + "NULL)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN ('x', 'y'))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN ('x', "
                + "'y')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (A))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (a)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (ZZ, YY))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (zz, "
                + "yy)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (1, 2, 3))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1, 2, "
                + "3)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (1 + 1) IN (2 + 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1 + 1, 2 "
                + "+ 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (F2(), 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (f2(), "
                + "2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (1, F2()))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1, "
                + "f2())); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (CAST(1 AS NUMBER(38,0))) IN (2))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1::INT, "
                + "2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A + 1) IN (A, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1 IN (a, "
                + "2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (CURRENT_DATE, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN "
                + "(CURRENT_DATE, 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (TRUE, FALSE))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (TRUE, "
                + "FALSE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (1.5, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1.5, "
                + "2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (TO_DATE('2020-01-01')) IN (2))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (DATE "
                + "'2020-01-01', 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (NEGATE(A)) IN (2))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (-a, 2)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (1, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN ((1), "
                + "(2))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH('a' REGEXP_LIKE 'a' REGEXP_LIKE 'i')",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN "
                + "nosuch(REGEXP_LIKE('a', 'a', 'i')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(('a' REGEXP_LIKE 'a') AND TRUE)",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN "
                + "nosuch(REGEXP_LIKE('a', 'a') AND TRUE); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH('a' RLIKE 'a')",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(RLIKE('a', "
                + "'a')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A + null)",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + NULL); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A = null)",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a = NULL); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(null IS NULL)",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(NULL IS NULL); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A + 1 IS NOT NULL)",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1 IS NOT "
                + "NULL); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A > 1 IS NULL)",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch((a > 1) IS "
                + "NULL); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A > (1 IS NULL))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a > 1 IS "
                + "NULL); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A || 'x') LIKE 'y')",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a || 'x' LIKE "
                + "'y'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A LIKE_ANY null LIKE_ANY 'x')",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a LIKE ANY "
                + "('x')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A LIKE_ANY '!' LIKE_ANY 'x' LIKE_ANY 'y')",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a LIKE ANY "
                + "('x', 'y') ESCAPE '!'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A + 1) LIKE_ANY null LIKE_ANY 'x' "
                + "LIKE_ANY 'y')",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1 LIKE ANY "
                + "('x', 'y')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(T)",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(t.a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(T + 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(t.a + 1); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOSUCH_T)",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(nosuch_t.b); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"t\")",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(\"t\".\"a\"); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(T IS NULL)",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(t.a IS NULL); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(ABS(T))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(ABS(t.a)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A)",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(EU)",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(eu.a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GET(GET(V, 'k'), A + 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(v:k[a + 1]); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GET(V, A))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(v[a]); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GET(GET(V, 'k'), 'j'))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(v:k:j); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GET(GET(V, 'k'), 'j'))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(v['k']['j']); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((CAST(GET(GET(V, 'k'), 'j') AS NUMBER(38,"
                + "0))) + 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(v:k.j::INT + "
                + "1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GET(ARRAY_CONSTRUCT(1), 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN "
                + "nosuch(ARRAY_CONSTRUCT(1)[0]); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GET(OBJECT_CONSTRUCT('k', 1), 'k'))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN "
                + "nosuch(OBJECT_CONSTRUCT('k', 1):k); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GET(PARSE_JSON('{}'), 'k'))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN "
                + "nosuch(PARSE_JSON('{}'):k); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(ARRAY_CONSTRUCT())",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch([]); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(OBJECT_CONSTRUCT())",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch({}); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(OBJECT_CONSTRUCT('a', 1, 'b', "
                + "ARRAY_CONSTRUCT(1, OBJECT_CONSTRUCT('c', 2))))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch({'a': 1, 'b': "
                + "[1, {'c': 2}]}); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((CASE(A, 1, 2, 2, 3, 4)) + 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(CASE a WHEN 1 "
                + "THEN 2 WHEN 2 THEN 3 ELSE 4 END + 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CASE(A > 1, 2, null))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(CASE WHEN a > "
                + "1 THEN 2 ELSE NULL END); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CASE(A + 1, 1, 2, null))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(CASE a + 1 "
                + "WHEN 1 THEN 2 END); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CASE((A > 1) AND (A < 3), A + 1, A - 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(CASE WHEN a > "
                + "1 AND a < 3 THEN a + 1 ELSE a - 1 END); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(IFF((A > 1) AND (A < 3), A + 1, A - 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(IFF(a > 1 AND "
                + "a < 3, a + 1, a - 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(COALESCE(A + 1, A * 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(COALESCE(a + 1,"
                + " a * 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((ABS(A + 1)) + (ABS(A - 1)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(ABS(a + 1) + "
                + "ABS(a - 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((CAST(A AS VARCHAR(134217728))) || (CAST(A "
                + "AS VARCHAR(134217728))))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::VARCHAR || "
                + "a::VARCHAR); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NEGATE(CAST(A AS NUMBER(38,0))))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(-a::INT); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NEGATE(CAST(A AS NUMBER(38,0))))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(-(a::INT)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(CAST(A AS BOOLEAN)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(NOT "
                + "a::BOOLEAN); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS NUMBER(38,0)) IS NULL)",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::INT IS "
                + "NULL); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((CAST(A AS NUMBER(38,0))) IN (1, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::INT IN (1, "
                + "2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(BETWEEN(CAST(A AS NUMBER(38,0)), 1, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::INT BETWEEN "
                + "1 AND 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A + 1 AS NUMBER(38,0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch((a + 1)::INT); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A + (CAST(1 AS NUMBER(38,0))))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1::INT); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((TRY_CAST(A)) + 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(TRY_CAST(a AS "
                + "INT) + 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A + 1 AS NUMBER(38,0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(CAST(a + 1 AS "
                + "INT)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((INTERVAL_LITERAL('day', '1')) + "
                + "CURRENT_DATE)",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(INTERVAL '1 "
                + "day' + CURRENT_DATE); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_DATE - (INTERVAL_LITERAL('day', "
                + "'1', 'hours', '2')))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_DATE - "
                + "INTERVAL '1 day, 2 hours'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((TO_DATE('2020-01-01')) = CURRENT_DATE)",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATE "
                + "'2020-01-01' = CURRENT_DATE); END $$");
    }
}
