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
 * What a Snowscript UDF's refusal names is the whole expression of the statement that holds the unsupported
 * call: a RETURN's value, a LET's or an assignment's, the condition of an IF, an ELSEIF, a WHILE or a REPEAT, a
 * bound of a FOR, the subject or a WHEN of a CASE statement. A call is named with each part of its name in
 * quotes only where it needs them, {@code "noSuch"(1)} and {@code TEST_SCHEMA.NOSUCH(1)}, and without the
 * names of its arguments; a variable's field is named by the variable, and a bind is the null it holds while the
 * body compiles. A path into a VARIANT is a GET of each of its steps. Every answer is live-verified.
 */
public class SnowscriptUdfEchoStatementsTest extends MeasuredCellsTestSupport {

    @Test
    public void callsNamesPathsAndTheStatementsExpression() {
        engine.execute(
            "CREATE OR REPLACE TABLE rc_t (a INT, b VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS $$ 1 $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a => 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1, 'x')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a => 1, b => 'x'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1, 2)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1, b => 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(*)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(*); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(DISTINCT A)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DISTINCT a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: TEST_SCHEMA.NOSUCH(1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN test_schema.nosuch(1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: TEST_DB.TEST_SCHEMA.NOSUCH(1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN test_db.test_schema.nosuch(1); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"noSuch\"(1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN \"noSuch\"(1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN \"NOSUCH\"(1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: TEST_SCHEMA.\"noSuch\"(1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN test_schema.\"noSuch\"(1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"f2\"()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN \"f2\"(); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: F2()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN f2(); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: F2() + 1",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN f2() + 1; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: F2()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN F2(); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: TEST_SCHEMA.F2()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN test_schema.f2(); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOSUCH(1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(nosuch(1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (NOSUCH(1)) + (NOSUCH(2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1) + nosuch(2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: ABS(NOSUCH(1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN ABS(nosuch(1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: ABS((NOSUCH(1)) + 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN ABS(nosuch(1) + 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(F2())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(f2()); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: CAST(NOSUCH(1) AS NUMBER(38,0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1)::INT; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: CAST(NOSUCH(1) AS VARCHAR(134217728))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1)::VARCHAR; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: BETWEEN(NOSUCH(A), 1, 2)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a) BETWEEN 1 AND 2; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: COALESCE(NOSUCH(A), 0)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN COALESCE(nosuch(a), 0); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: IFF(TRUE, NOSUCH(1), 2)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN IFF(TRUE, nosuch(1), 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: CASE((NOSUCH(1)) = 1, 2, 3)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN CASE WHEN nosuch(1) = 1 THEN 2 "
                + "ELSE 3 END; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CURRENT_DATE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_DATE); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NEGATE(NOSUCH(1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN -nosuch(1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOT(NOSUCH(1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN NOT nosuch(1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1) IS NULL",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1) IS NULL; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (NOSUCH(1)) IN (1, 2)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1) IN (1, 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (NOSUCH(1)) LIKE 'a'",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1) LIKE 'a'; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (NOSUCH(1)) = 1",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1) = 1; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: 1 = (NOSUCH(1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN 1 = nosuch(1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: ((NOSUCH(1)) > 0) AND (A > 0)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1) > 0 AND a > 0; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (A > 0) OR ((NOSUCH(1)) > 0)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN a > 0 OR nosuch(1) > 0; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: IFF((NOSUCH(1)) > 0, 1, 2)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN IFF(nosuch(1) > 0, 1, 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NVL(NOSUCH(1), 0)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN NVL(nosuch(1), 0); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (NOSUCH(1)) || (NOSUCH(2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1) || nosuch(2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN (nosuch(1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch (1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN NOSUCH(1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN NoSuch(1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1, 2, 3)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1, 2, 3); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH('x', null, TRUE)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch('x', NULL, TRUE); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: EU(1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN eu(1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: EU(A - 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN eu(a - 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(NOSUCH(1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(nosuch(1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(SUM(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(SUM(a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: CAST(NOSUCH(1) AS NUMBER(10,2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN CAST(nosuch(1) AS NUMBER(10,2)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: TRY_CAST(NOSUCH(1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN TRY_CAST(nosuch(1) AS INT); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOT(BETWEEN(NOSUCH(1), 1, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1) NOT BETWEEN 1 AND 2; "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOT((NOSUCH(1)) IN (1, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1) NOT IN (1, 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1) IS NOT NULL",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1) IS NOT NULL; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: DECODE(NOSUCH(1), 1, 2, 3)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN DECODE(nosuch(1), 1, 2, 3); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: GREATEST(NOSUCH(1), 2)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN GREATEST(nosuch(1), 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: CONCAT(NOSUCH(1), 'x')",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN CONCAT(nosuch(1), 'x'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GET(V, 'k'))",
            "CREATE OR REPLACE FUNCTION eu(v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(v:k); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GET(GET(V, 'k'), 'j'))",
            "CREATE OR REPLACE FUNCTION eu(v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(v:k.j); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GET(V, 'k'))",
            "CREATE OR REPLACE FUNCTION eu(v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(v['k']); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GET(V, 0))",
            "CREATE OR REPLACE FUNCTION eu(v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(v[0]); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(GET(V, 'k') AS NUMBER(38,0)))",
            "CREATE OR REPLACE FUNCTION eu(v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(v:k::INT); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GET(V, 'k'))",
            "CREATE OR REPLACE FUNCTION eu(v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(GET(v, 'k')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GET_PATH(V, 'k.j'))",
            "CREATE OR REPLACE FUNCTION eu(v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(GET_PATH(v, 'k.j')); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(PARSE_JSON('{}'))",
            "CREATE OR REPLACE FUNCTION eu(v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(PARSE_JSON('{}')); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GET(V, 'k') IS NULL)",
            "CREATE OR REPLACE FUNCTION eu(v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(v:k IS NULL); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GET(GET(GET(V, 'k'), 0), 'j'))",
            "CREATE OR REPLACE FUNCTION eu(v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(v:k[0].j); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(GET(V, 'K k'))",
            "CREATE OR REPLACE FUNCTION eu(v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(v:\"K k\"); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((CAST(GET(V, 'k') AS VARCHAR(134217728))) "
                + "= 'x')",
            "CREATE OR REPLACE FUNCTION eu(v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(v:k::VARCHAR = 'x'); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TO_VARIANT(1))",
            "CREATE OR REPLACE FUNCTION eu(v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(TO_VARIANT(1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(V AS NUMBER(38,0)))",
            "CREATE OR REPLACE FUNCTION eu(v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(v::INT); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(ARRAY_SIZE(V))",
            "CREATE OR REPLACE FUNCTION eu(v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(ARRAY_SIZE(v)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(OBJECT_KEYS(V))",
            "CREATE OR REPLACE FUNCTION eu(v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(OBJECT_KEYS(v)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((GET(V, 'k')) + 1)",
            "CREATE OR REPLACE FUNCTION eu(v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(v:k + 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(FLATTEN(V))",
            "CREATE OR REPLACE FUNCTION eu(v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(FLATTEN(v)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (NOSUCH(1)) + A",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN LET x INT := nosuch(1) + a; RETURN x; "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(X)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN LET x INT := 1; RETURN nosuch(x); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(null)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN LET x INT := 1; RETURN nosuch(:x); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: BETWEEN(NOSUCH(1), 1, 2)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN IF (nosuch(1) BETWEEN 1 AND 2) THEN "
                + "RETURN 1; END IF; RETURN 2; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (COALESCE(NOSUCH(1), 0)) > 0",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN WHILE (COALESCE(nosuch(1), 0) > 0) DO "
                + "RETURN 1; END WHILE; RETURN 2; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(3)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN FOR i IN 1 TO nosuch(3) DO RETURN 1; "
                + "END FOR; RETURN 2; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN CASE (nosuch(1)) WHEN 1 THEN RETURN 1; "
                + "ELSE RETURN 2; END CASE; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (NOSUCH(1)) = 1",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN CASE WHEN nosuch(1) = 1 THEN RETURN 1; "
                + "ELSE RETURN 2; END CASE; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: CASE((NOSUCH(1)) = 1, 2, 3)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN LET x INT := 0; x := CASE WHEN "
                + "nosuch(1) = 1 THEN 2 ELSE 3 END; RETURN x; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: CAST(NOSUCH(1) AS NUMBER(38,0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN LET x INT DEFAULT nosuch(1)::INT; "
                + "RETURN x; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (NOSUCH(A)) + A",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a) + a; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN IF (a > 0) THEN RETURN nosuch(a => 1); "
                + "END IF; RETURN 2; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN LET x := nosuch(1); RETURN 1; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (NOSUCH(1)) IN (1, 2)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN REPEAT RETURN 1; UNTIL (nosuch(1) IN (1,"
                + " 2)) END REPEAT; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"=\"(A, ANY(SELECT NOSUCH(1) AS \"NOSUCH(1)\" "
                + "FROM (VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN IF (a IN (SELECT nosuch(1))) THEN "
                + "RETURN 1; END IF; RETURN 2; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (SELECT NOSUCH(1) AS \"NOSUCH(1)\" FROM (VALUES "
                + "(null)) DUAL)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN LET r INT := (SELECT nosuch(1)); RETURN "
                + "r; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (SELECT NOSUCH(1) AS \"NOSUCH(1)\" FROM (VALUES "
                + "(null)) DUAL)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN (SELECT nosuch(1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (SELECT NOSUCH(A) AS \"NOSUCH(A)\" FROM RC_T AS "
                + "RC_T)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN (SELECT nosuch(a) FROM rc_t); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "\"=\"(NOSUCH(A), 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN (SELECT a FROM rc_t WHERE "
                + "nosuch(a) = 1); END $$");
    }

    @Test
    public void theExpressionOfTheStatementThatHoldsTheCall() {
        engine.execute(
            "CREATE OR REPLACE TABLE rc_t (a INT, b VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS $$ 1 $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS $$ x $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"+\"((SELECT 1 AS \"1\" FROM (VALUES (null)) "
                + "DUAL), 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN LET x := (SELECT 1) + 1; RETURN x; END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: EXISTS(SELECT 1 AS \"1\" FROM (VALUES (null)) "
                + "DUAL)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN IF (EXISTS (SELECT 1)) THEN RETURN 1; "
                + "END IF; RETURN 2; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (SELECT 3 AS \"3\" FROM (VALUES (null)) DUAL)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN FOR i IN 1 TO (SELECT 3) DO RETURN 1; "
                + "END FOR; RETURN 2; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (F1(A + 1)) * 2",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN f1(a + 1) * 2; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: X + F2()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN LET x INT := 1; x := x + f2(); RETURN "
                + "x; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: A > F2()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN WHILE (a > f2()) DO RETURN 1; END "
                + "WHILE; RETURN 2; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: F2() > 0",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN IF (a > 0) THEN RETURN 1; ELSEIF (f2() "
                + "> 0) THEN RETURN 2; END IF; RETURN 3; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: F2()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN CASE a WHEN f2() THEN RETURN 1; END "
                + "CASE; RETURN 2; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: CASE(A > 0, F2(), null)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN CASE WHEN a > 0 THEN f2() END; "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: F2() > 0",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN LOOP IF (f2() > 0) THEN BREAK; END IF; "
                + "END LOOP; RETURN 1; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: F2()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN FOR i IN f2() TO 3 DO RETURN 1; END "
                + "FOR; RETURN 2; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: F1(3)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN FOR i IN REVERSE 1 TO f1(3) DO RETURN "
                + "1; END FOR; RETURN 2; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: X + (F1(X))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN LET x INT := 1; LET y INT := x + f1(x); "
                + "RETURN y; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (F1(A)) + ((F1(A + 1)) * F2())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN f1(a) + f1(a + 1) * f2(); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(X + 1, null)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN LET x INT := 1; RETURN nosuch(x + 1, "
                + ":x); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (F1(null)) + (null)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN LET x INT := 1; RETURN f1(:x) + :x; END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: F2() > 0",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN CASE WHEN a > 0 THEN RETURN 1; WHEN "
                + "f2() > 0 THEN RETURN 2; ELSE RETURN 3; END CASE; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: F2()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN CASE (a) WHEN 1 THEN RETURN f2(); ELSE "
                + "RETURN 2; END CASE; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: F1(A)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN IF (a > 0) THEN LET z INT := f1(a); "
                + "RETURN z; END IF; RETURN 2; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: F2()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN BEGIN RETURN f2(); END; RETURN 1; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: F2()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN (f2()); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: F2() + 1",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN ((f2() + 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: ((F2() + 1) > 0) AND (A > 0)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN f2() + 1 > 0 AND a > 0; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (A > 0) AND (((F2() + 1) > 0) OR (A < 5))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN a > 0 AND (f2() + 1 > 0 OR a < "
                + "5); END $$");
    }
}
