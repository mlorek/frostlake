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
 * With a query or a window call anywhere in the expression a Snowscript UDF's refusal names, every operator is
 * printed as a call of its own name: {@code "+"(A, 1)}, {@code AND(x, y)}, {@code "IS NULL"(A)},
 * {@code LIKE(A, 'x')}, and a membership in a query is the comparison it stands for,
 * {@code "="(A, ANY(SELECT ...))}, a NOT IN {@code "!="(A, ALL(SELECT ...))}. A window call names its
 * PARTITION BY keys and its ORDER BY keys, each with its direction and where its nulls sort, and neither its
 * frame nor its null treatment; COUNT(*) is the call {@code "COUNT(*)"()}, an AVG over an ordered window the
 * SUM over the COUNT, and a RATIO_TO_REPORT its value over the window's SUM. A query is printed with no name
 * resolved: each item with its name - its alias, or its own text upper-cased -, each table with its alias, a
 * FROM-less SELECT over {@code (VALUES (null)) DUAL}, a join's kind in full with its ON in parentheses, and the
 * clauses in the tree's order - WHERE, GROUP BY, ORDER BY, HAVING, QUALIFY, the row limit as LIMIT n OFFSET m. A
 * set operation selects everything from its operands, each named OB$n. Every answer is live-verified.
 */
public class SnowscriptUdfEchoQueriesTest extends MeasuredCellsTestSupport {

    @Test
    public void windowCallsAndQueriesPrintOperatorsAsCalls() {
        engine.execute(
            "CREATE OR REPLACE TABLE rc_t (a INT, b VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS $$ 1 $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"COUNT(*)\"() OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN COUNT(*) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(A) OVER (PARTITION BY A ORDER BY A ASC NULLS "
                + "LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a) OVER (PARTITION BY a "
                + "ORDER BY a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: ROW_NUMBER() OVER (ORDER BY A DESC NULLS FIRST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN ROW_NUMBER() OVER (ORDER BY a "
                + "DESC); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: LAG(A) OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN LAG(a) OVER (ORDER BY a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(A) OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a) OVER (ORDER BY a ROWS "
                + "BETWEEN 1 PRECEDING AND CURRENT ROW); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: COUNT(A) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN COUNT(a) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: MAX(A) OVER (PARTITION BY A)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN MAX(a) OVER (PARTITION BY a); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: COUNT(DISTINCT A) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN COUNT(DISTINCT a) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: RANK() OVER (PARTITION BY A, \"+\"(A, 1) ORDER BY "
                + "A ASC NULLS FIRST, A DESC NULLS FIRST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN RANK() OVER (PARTITION BY a, a + "
                + "1 ORDER BY a NULLS FIRST, a DESC); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: LEAD(A, 2, 0) OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN LEAD(a, 2, 0) OVER (ORDER BY a); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: FIRST_VALUE(A) OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN FIRST_VALUE(a) IGNORE NULLS OVER "
                + "(ORDER BY a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"COUNT(*)\"() OVER (PARTITION BY A)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN COUNT(*) OVER (PARTITION BY a); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(A) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(A) OVER (ORDER BY A ASC NULLS LAST) / "
                + "COUNT(A) OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN AVG(a) OVER (ORDER BY a RANGE "
                + "BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NTILE(2) OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN NTILE(2) OVER (ORDER BY a); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"+\"(\"COUNT(*)\"() OVER (), 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN COUNT(*) OVER () + 1; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: ABS(\"COUNT(*)\"() OVER ())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN ABS(COUNT(*) OVER ()); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: MIN(A) OVER (ORDER BY 1 ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN MIN(a) OVER (ORDER BY 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: LISTAGG(A) OVER (PARTITION BY A)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN LISTAGG(a) OVER (PARTITION BY "
                + "a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: COUNT(1) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN COUNT(1) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"COUNT(*)\"() OVER ())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(COUNT(*) OVER ()); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(SUM(A) OVER (PARTITION BY A ORDER BY A ASC "
                + "NULLS LAST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(SUM(a) OVER (PARTITION BY "
                + "a ORDER BY a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(ROW_NUMBER() OVER (ORDER BY A DESC NULLS "
                + "FIRST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(ROW_NUMBER() OVER (ORDER "
                + "BY a DESC)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(LAG(A) OVER (ORDER BY A ASC NULLS LAST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(LAG(a) OVER (ORDER BY "
                + "a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(SUM(A) OVER (ORDER BY A ASC NULLS LAST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(SUM(a) OVER (ORDER BY a "
                + "ROWS BETWEEN 1 PRECEDING AND CURRENT ROW)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(COUNT(A) OVER ())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(COUNT(a) OVER ()); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(MAX(A) OVER (PARTITION BY A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(MAX(a) OVER (PARTITION BY "
                + "a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(COUNT(DISTINCT A) OVER ())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(COUNT(DISTINCT a) OVER "
                + "()); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT 1 AS \"1\" FROM (VALUES (null)) "
                + "DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"+\"((SELECT 1 AS \"1\" FROM (VALUES (null)) "
                + "DUAL), NOSUCH(1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN (SELECT 1) + nosuch(1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"+\"(NOSUCH(1), (SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1) + (SELECT 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"=\"((SELECT 1 AS \"1\" FROM (VALUES (null)) "
                + "DUAL), NOSUCH(1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN (SELECT 1) = nosuch(1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"=\"(NOSUCH(1), ANY(SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1) IN (SELECT 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"=\"(1, ANY(SELECT NOSUCH(1) AS \"NOSUCH(1)\" "
                + "FROM (VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN 1 IN (SELECT nosuch(1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"!=\"(NOSUCH(1), ALL(SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1) NOT IN (SELECT 1); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: EXISTS(SELECT NOSUCH(1) AS \"NOSUCH(1)\" FROM "
                + "(VALUES (null)) DUAL)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN EXISTS (SELECT nosuch(1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(EXISTS(SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(EXISTS (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT EXISTS(SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NOT EXISTS (SELECT 1)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT \"COUNT(*)\"() AS \"COUNT(*)\" "
                + "FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT COUNT(*) FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT MAX(A) AS \"MAX(A)\" FROM RC_T AS "
                + "RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT MAX(a) FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "\"=\"(B, 'x')))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "b = 'x')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT T.A AS \"T.A\" FROM RC_T AS T "
                + "INNER JOIN RC_T AS U ON (\"=\"(T.A, U.A))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT t.a FROM rc_t t "
                + "JOIN rc_t u ON t.a = u.a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T  "
                + "ORDER BY A ASC NULLS LAST LIMIT 1 OFFSET 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t ORDER "
                + "BY a LIMIT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT SUM(A) AS \"SUM(A)\" FROM RC_T AS "
                + "RC_T GROUP BY B))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT SUM(a) FROM rc_t "
                + "GROUP BY b)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: AND(\">\"((SELECT 1 AS \"1\" FROM (VALUES (null)) "
                + "DUAL), 0), \">\"(NOSUCH(1), 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN (SELECT 1) > 0 AND nosuch(1) > "
                + "0; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"=\"(NOSUCH(1), ANY(SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1) = ANY (SELECT 1); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \">\"(NOSUCH(1), ALL(SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1) > ALL (SELECT 1); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"=\"(1, ANY(SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1 IN (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) + 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(1, (SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1 + (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"=\"((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) = 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"||\"((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) || (SELECT "
                + "2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NEGATE((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(-(SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"IS NULL\"((SELECT A AS \"A\" FROM RC_T "
                + "AS RC_T)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t) IS "
                + "NULL); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(ABS((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(ABS((SELECT 1))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT 1 AS \"1\" FROM (VALUES (null)) "
                + "DUAL), 2)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1), 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(BETWEEN((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 0, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) BETWEEN 0 AND "
                + "2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(BETWEEN(1, (SELECT 0 AS \"0\" FROM (VALUES "
                + "(null)) DUAL), 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1 BETWEEN (SELECT 0) AND "
                + "2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CASE(\"=\"((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 1), 2, null))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CASE WHEN (SELECT 1) = 1 "
                + "THEN 2 END); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(IFF(\"=\"((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 1), 2, 3))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(IFF((SELECT 1) = 1, 2, "
                + "3)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(((SELECT 1 AS \"1\" FROM (VALUES (null)) "
                + "DUAL)) IN (1, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) IN (1, 2)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(COALESCE((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(COALESCE((SELECT 1), 0)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL) AS VARCHAR(134217728)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1)::VARCHAR); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "\"=\"(A, (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a = (SELECT 1))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT DISTINCT A AS \"A\" FROM RC_T AS "
                + "RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT DISTINCT a FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"Z\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a AS z FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT COUNT(A) AS \"COUNT(A)\", MAX(B) "
                + "AS \"MAX(B)\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT COUNT(a), MAX(b) "
                + "FROM rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL) OB$0 UNION ALL (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) OB$1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1 UNION ALL "
                + "SELECT 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT \"COUNT(*)\"() AS \"COUNT(*)\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 3))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT COUNT(*) FROM "
                + "TABLE(GENERATOR(ROWCOUNT => 3)))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM (SELECT A AS \"A\" "
                + "FROM RC_T AS RC_T) D))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM (SELECT a "
                + "FROM rc_t) d)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T  "
                + "HAVING \">\"(\"COUNT(*)\"(), 0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t "
                + "HAVING COUNT(*) > 0)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T "
                + "QUALIFY \"=\"(ROW_NUMBER() OVER (ORDER BY A ASC NULLS LAST), 1)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t "
                + "QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT X AS \"X\" FROM C AS C))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((WITH c AS (SELECT 1 AS "
                + "x) SELECT x FROM c)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: AND((SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL),"
                + " NOSUCH(1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN (SELECT 1) AND nosuch(1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: AND(NOSUCH(1), (SELECT TRUE AS \"TRUE\" FROM "
                + "(VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1) AND (SELECT TRUE); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: OR(NOT((SELECT TRUE AS \"TRUE\" FROM (VALUES "
                + "(null)) DUAL)), \"=\"(NOSUCH(1), 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN NOT (SELECT TRUE) OR nosuch(1) = "
                + "1; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"*\"((SELECT 1 AS \"1\" FROM (VALUES (null)) "
                + "DUAL), NOSUCH(1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN (SELECT 1) * nosuch(1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"||\"((SELECT 1 AS \"1\" FROM (VALUES (null)) "
                + "DUAL), NOSUCH(1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN (SELECT 1) || nosuch(1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"<\"((SELECT 1 AS \"1\" FROM (VALUES (null)) "
                + "DUAL), NOSUCH(1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN (SELECT 1) < nosuch(1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"-\"(\"-\"(NOSUCH(1), (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)), 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1) - (SELECT 1) - 1; END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"+\"(\"+\"((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 1), NOSUCH(1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN ((SELECT 1) + 1) + nosuch(1); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"+\"(\"+\"((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 1), NOSUCH(1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN (SELECT 1) + 1 + nosuch(1); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"+\"(\"+\"(NOSUCH(1), 1), (SELECT 1 AS \"1\" "
                + "FROM (VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1) + 1 + (SELECT 1); END "
                + "$$");
    }

    @Test
    public void everyOperatorIsACallBesideAQuery() {
        engine.execute(
            "CREATE OR REPLACE TABLE rc_t (a INT, b VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS $$ 1 $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS $$ x $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"IS NOT NULL\"((SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) IS NOT NULL); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(LIKE((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 'x'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) LIKE 'x'); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(LIKE((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 'x')))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) NOT LIKE 'x'); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(ILIKE((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 'x'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) ILIKE 'x'); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(LIKE((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 'x', '!'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) LIKE 'x' "
                + "ESCAPE '!'); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(RLIKE((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 'x'))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) RLIKE 'x'); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL)) IN (1, 2)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) NOT IN (1, "
                + "2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"<>\"((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) <> 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"!=\"((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) != 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\">=\"((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) >= 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"<=\"((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) <= 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"/\"((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) / 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"%\"((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) % 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"UNARY PLUS\"((SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(+(SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(EQUAL_NULL((SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL), 1)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) IS DISTINCT "
                + "FROM 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(EQUAL_NULL((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) IS NOT "
                + "DISTINCT FROM 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(\"=\"((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 1)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NOT ((SELECT 1) = 1)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(BETWEEN((SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL), 0, 2)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) NOT BETWEEN 0 "
                + "AND 2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(EXISTS(SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), \">\"(A, 1)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(EXISTS (SELECT 1) AND a > "
                + "1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(OR(\"=\"(A, ANY(SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)), \"=\"(A, 2)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (SELECT 1) OR a = "
                + "2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(\"IS NULL\"(A), EXISTS(SELECT 1 AS "
                + "\"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS NULL AND EXISTS "
                + "(SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(\"IS NOT NULL\"(A), EXISTS(SELECT 1 AS "
                + "\"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS NOT NULL AND EXISTS "
                + "(SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(A IN (1, 2), EXISTS(SELECT 1 AS \"1\" "
                + "FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1, 2) AND EXISTS "
                + "(SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(NOT(A IN (1, 2)), EXISTS(SELECT 1 AS "
                + "\"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT IN (1, 2) AND "
                + "EXISTS (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (1, (SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1, (SELECT 1))); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((\"+\"(A, 1)) IN (1, (SELECT 1 AS \"1\" "
                + "FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1 IN (1, (SELECT "
                + "1))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\">\"(\"+\"(A, 1), (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1 > (SELECT 1)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(ABS(\"+\"(A, 1)), (SELECT 1 AS \"1\" "
                + "FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(ABS(a + 1) + (SELECT 1)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CASE(A, (SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), 2, null))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CASE a WHEN (SELECT 1) "
                + "THEN 2 END); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CASE(\">\"(A, 1), (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL), \"+\"(A, 1)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CASE WHEN a > 1 THEN "
                + "(SELECT 1) ELSE a + 1 END); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(CAST(A AS NUMBER(38,0)), (SELECT 1 "
                + "AS \"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::INT + (SELECT 1)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(BETWEEN(A, 1, 2), EXISTS(SELECT 1 AS "
                + "\"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a BETWEEN 1 AND 2 AND "
                + "EXISTS (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(LIKE(A, 'x'), EXISTS(SELECT 1 AS \"1\" "
                + "FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a LIKE 'x' AND EXISTS "
                + "(SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(NOT(LIKE(A, 'x')), EXISTS(SELECT 1 AS "
                + "\"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT LIKE 'x' AND EXISTS "
                + "(SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(RLIKE(A, 'x'), EXISTS(SELECT 1 AS "
                + "\"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a RLIKE 'x' AND EXISTS "
                + "(SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(ARRAY_CONSTRUCT(1, (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch([1, (SELECT 1)]); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(OBJECT_CONSTRUCT('k', (SELECT 1 AS \"1\" "
                + "FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch({'k': (SELECT 1)}); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(NEGATE(A), (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(-a + (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(\"+\"(A, -1), (SELECT 1 AS \"1\" "
                + "FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + -1 + (SELECT 1)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(CURRENT_DATE, (SELECT 1 AS \"1\" "
                + "FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_DATE + (SELECT "
                + "1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\">\"(TO_DATE('2020-01-01'), (SELECT "
                + "CURRENT_DATE() AS \"CURRENT_DATE\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATE '2020-01-01' > "
                + "(SELECT CURRENT_DATE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\">\"(\"+\"(CURRENT_DATE, "
                + "INTERVAL_LITERAL('day', '1')), (SELECT CURRENT_DATE() AS \"CURRENT_DATE\" FROM (VALUES (null)) "
                + "DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_DATE + INTERVAL "
                + "'1 day' > (SELECT CURRENT_DATE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(OR(\"IS NULL\"((SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)), \"IS NOT NULL\"(A)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) IS NULL OR a "
                + "IS NOT NULL); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(\"=\"(A, ANY(SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)), \"<>\"(A, ALL(SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a = ANY (SELECT 1) AND a "
                + "<> ALL (SELECT 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"!=\"(A, ALL(SELECT 2 AS \"2\" FROM "
                + "(VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a != ALL (SELECT 2)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"<\"(A, ANY(SELECT 2 AS \"2\" FROM "
                + "(VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a < ANY (SELECT 2)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\">=\"(A, ANY(SELECT 2 AS \"2\" FROM "
                + "(VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a >= ANY (SELECT 2)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"<=\"(A, ALL(SELECT 2 AS \"2\" FROM "
                + "(VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a <= ALL (SELECT 2)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"<>\"(A, ANY(SELECT 2 AS \"2\" FROM "
                + "(VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a <> ANY (SELECT 2)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"=\"(ROW(A, 1), ANY(SELECT 1 AS \"1\", 2 "
                + "AS \"2\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((a, 1) IN (SELECT 1, 2)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(OR(NOT EXISTS(SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL), NOT(\"=\"(A, ANY(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NOT EXISTS (SELECT 1) OR "
                + "NOT (a IN (SELECT 1))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(\"!=\"(A, ALL(SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)), TRUE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT IN (SELECT 1) AND "
                + "TRUE); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(IFF(\"=\"(A, ANY(SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)), 1, 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(IFF(a IN (SELECT 1), 1, "
                + "2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(\"COUNT(*)\"(), (SELECT 1 AS \"1\" "
                + "FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(COUNT(*) + (SELECT 1)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(SUM(A), (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(SUM(a) + (SELECT 1)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(COUNT(DISTINCT A), (SELECT 1 AS "
                + "\"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(COUNT(DISTINCT a) + "
                + "(SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"||\"(LISTAGG(A, ','), (SELECT 'x' AS "
                + "\"'X'\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(LISTAGG(a, ',') WITHIN "
                + "GROUP (ORDER BY a) || (SELECT 'x')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(A, 1), (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1, (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(A, 1), ABS((SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1, ABS((SELECT 1))); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(NOT(EQUAL_NULL(A, 1)), EXISTS(SELECT 1 "
                + "AS \"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS DISTINCT FROM 1 AND "
                + "EXISTS (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"=\"(\"||\"(A, 'x'), (SELECT 'x' AS "
                + "\"'X'\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a || 'x' = (SELECT 'x')); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(OR(AND(\">\"(A, 1), \"<\"(A, 3)), "
                + "EXISTS(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a > 1 AND a < 3 OR EXISTS "
                + "(SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(NOT(A), EXISTS(SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NOT a AND EXISTS (SELECT "
                + "1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(F2(), (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(f2() + (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(F1(A), (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(f1(a) + (SELECT 1)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(COALESCE(A, 0), (SELECT 1 AS \"1\" "
                + "FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(COALESCE(a, 0) + (SELECT "
                + "1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\">\"(TO_DATE('2020-01-01'), (SELECT "
                + "CURRENT_DATE() AS \"CURRENT_DATE\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TO_DATE('2020-01-01') > "
                + "(SELECT CURRENT_DATE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"||\"(CAST(A AS VARCHAR(134217728)), "
                + "(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::VARCHAR || (SELECT "
                + "1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(TRY_CAST('1'), (SELECT 1 AS \"1\" "
                + "FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TRY_CAST('1' AS INT) + "
                + "(SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(EXTRACT('year', CURRENT_DATE), "
                + "(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(EXTRACT(year FROM "
                + "CURRENT_DATE) + (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"=\"(\"=\"(A, (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)), TRUE))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a = (SELECT 1) = TRUE); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"=\"((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), ANY(SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) IN (SELECT "
                + "2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"=\"((SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), ANY(SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1) = ANY (SELECT "
                + "2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(EXISTS(SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NOT (NOT EXISTS (SELECT "
                + "1))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(OR(EXISTS(SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL), EXISTS(SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(EXISTS (SELECT 1) OR "
                + "EXISTS (SELECT 2)); END $$");
    }

    @Test
    public void aWindowCallNamesItsKeysAndDropsItsFrame() {
        engine.execute(
            "CREATE OR REPLACE TABLE rc_t (a INT, b VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS $$ 1 $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS $$ x $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"+\"(SUM(A) OVER (ORDER BY A ASC NULLS LAST), 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a) OVER (ORDER BY a) + 1; "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"+\"(1, ROW_NUMBER() OVER (ORDER BY A ASC NULLS "
                + "LAST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN 1 + ROW_NUMBER() OVER (ORDER BY "
                + "a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: AVG(A) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN AVG(a) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"+\"(AVG(A) OVER (PARTITION BY A), 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN AVG(a) OVER (PARTITION BY a) + "
                + "1; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AVG(A) OVER ())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(AVG(a) OVER ()); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: ABS(AVG(A) OVER ())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN ABS(AVG(a) OVER ()); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"COUNT(*)\"() OVER (ORDER BY A DESC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN COUNT(*) OVER (ORDER BY a DESC "
                + "NULLS LAST); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(A) OVER (ORDER BY A ASC NULLS FIRST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a) OVER (ORDER BY a ASC "
                + "NULLS FIRST); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(\"+\"(A, 1)) OVER (PARTITION BY \"%\"(A, 2) "
                + "ORDER BY NEGATE(A) ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a + 1) OVER (PARTITION BY a "
                + "% 2 ORDER BY -a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: LAG(A, 1) OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN LAG(a, 1) OVER (ORDER BY a); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: LAST_VALUE(A) OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN LAST_VALUE(a) OVER (ORDER BY a "
                + "ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NTH_VALUE(A, 2) OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN NTH_VALUE(a, 2) OVER (ORDER BY "
                + "a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: DENSE_RANK() OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN DENSE_RANK() OVER (ORDER BY a); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: PERCENT_RANK() OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN PERCENT_RANK() OVER (ORDER BY "
                + "a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: CUME_DIST() OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN CUME_DIST() OVER (ORDER BY a); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: A / (IFF(SUM(A) OVER () = 0, null, SUM(A) OVER "
                + "()))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN RATIO_TO_REPORT(a) OVER (); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: STDDEV(A) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN STDDEV(a) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: MEDIAN(A) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN MEDIAN(a) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: COUNT_IF(\">\"(A, 1)) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN COUNT_IF(a > 1) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: MIN(A) OVER (PARTITION BY A ORDER BY A ASC NULLS "
                + "LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN MIN(a) OVER (PARTITION BY a "
                + "ORDER BY a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: LISTAGG(A, ',') OVER (PARTITION BY A)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN LISTAGG(a, ',') WITHIN GROUP "
                + "(ORDER BY a) OVER (PARTITION BY a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: ARRAY_AGG(A) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN ARRAY_AGG(a) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: FIRST_VALUE(A) OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN FIRST_VALUE(a) RESPECT NULLS "
                + "OVER (ORDER BY a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: CONDITIONAL_TRUE_EVENT(\">\"(A, 1)) OVER (ORDER "
                + "BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN CONDITIONAL_TRUE_EVENT(a > 1) "
                + "OVER (ORDER BY a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: F2() OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN f2() OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: AND(\">\"(SUM(A) OVER (ORDER BY A ASC NULLS LAST),"
                + " 1), \">\"(A, 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a) OVER (ORDER BY a) > 1 AND "
                + "a > 0; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"IS NULL\"(SUM(A) OVER (ORDER BY A ASC NULLS "
                + "LAST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a) OVER (ORDER BY a) IS "
                + "NULL; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(A) OVER (ORDER BY A ASC NULLS LAST) IN (1, 2)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a) OVER (ORDER BY a) IN (1, "
                + "2); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: BETWEEN(SUM(A) OVER (ORDER BY A ASC NULLS LAST), "
                + "1, 2)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a) OVER (ORDER BY a) BETWEEN "
                + "1 AND 2; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: CAST(SUM(A) OVER (ORDER BY A ASC NULLS LAST) AS "
                + "VARCHAR(134217728))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a) OVER (ORDER BY "
                + "a)::VARCHAR; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: CASE(\">\"(SUM(A) OVER (), 1), 1, null)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN CASE WHEN SUM(a) OVER () > 1 "
                + "THEN 1 END; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: IFF(\">\"(A, 1), ROW_NUMBER() OVER (ORDER BY A "
                + "ASC NULLS LAST), \"+\"(A, 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN IFF(a > 1, ROW_NUMBER() OVER "
                + "(ORDER BY a), a + 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"+\"(\"COUNT(*)\"() OVER (PARTITION BY A ORDER "
                + "BY A ASC NULLS LAST), \"COUNT(*)\"())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN COUNT(*) OVER (PARTITION BY a "
                + "ORDER BY a) + COUNT(*); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(A) OVER (PARTITION BY A, A ORDER BY A ASC "
                + "NULLS LAST, A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a) OVER (PARTITION BY a, a "
                + "ORDER BY a, a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: AVG(DISTINCT A) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN AVG(DISTINCT a) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: VAR_POP(A) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN VAR_POP(a) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(A) OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a) OVER (ORDER BY a NULLS "
                + "LAST); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(A) OVER (ORDER BY A DESC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a) OVER (ORDER BY a DESC "
                + "NULLS LAST); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: MAX(A) OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN MAX(a) OVER (ORDER BY a ASC); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(A) OVER (PARTITION BY 'x' ORDER BY \"+\"(A, "
                + "1) DESC NULLS FIRST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a) OVER (PARTITION BY 'x' "
                + "ORDER BY a + 1 DESC); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: COUNT(A, A) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN COUNT(a, a) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: ROW_NUMBER() OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN ROW_NUMBER() OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(NOSUCH(A)) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(nosuch(a)) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(A) OVER (PARTITION BY NOSUCH(A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a) OVER (PARTITION BY "
                + "nosuch(a)); END $$");
    }

    @Test
    public void aQuerysClausesStandInTheTreesOrder() {
        engine.execute(
            "CREATE OR REPLACE TABLE rc_t (a INT, b VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS $$ 1 $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS $$ x $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT \"+\"(A, 1) AS \"A + 1\" FROM RC_T "
                + "AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a + 1 FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\", B AS \"B\" FROM RC_T "
                + "AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a, b FROM rc_t)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"z z\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a AS \"z z\" FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"Z\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a AS \"Z\" FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"q\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a \"q\" FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT RC_T.A AS \"RC_T.A\" FROM RC_T AS "
                + "RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT rc_t.a FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT T.* AS \"T.*\" FROM RC_T AS T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT t.* FROM rc_t "
                + "t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM RC_T AS T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * FROM rc_t t)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t AS "
                + "t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM TEST_SCHEMA.RC_T "
                + "AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM "
                + "test_schema.rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM "
                + "TEST_DB.TEST_SCHEMA.RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM "
                + "test_db.test_schema.rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "AND(\">\"(A, 1), \"=\"(B, 'x'))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a > 1 AND b = 'x')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "A IN (1, 2)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a IN (1, 2))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "\"IS NULL\"(A)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a IS NULL)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "LIKE(B, 'x%')))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "b LIKE 'x%')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "BETWEEN(A, 1, 2)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a BETWEEN 1 AND 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "NOT(\"=\"(A, 1))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "NOT a = 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T  "
                + "ORDER BY A DESC NULLS FIRST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t ORDER "
                + "BY a DESC)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T  "
                + "ORDER BY A ASC NULLS LAST, B DESC NULLS LAST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t ORDER "
                + "BY a, b DESC NULLS LAST)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T LIMIT "
                + "1 OFFSET 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t LIMIT "
                + "1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T LIMIT "
                + "1 OFFSET 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t LIMIT "
                + "1 OFFSET 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T  "
                + "ORDER BY A ASC NULLS LAST LIMIT 3 OFFSET 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t ORDER "
                + "BY a LIMIT 3 OFFSET 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T LIMIT "
                + "1 OFFSET 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT TOP 1 a FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T LIMIT "
                + "1 OFFSET 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t FETCH "
                + "FIRST 1 ROWS ONLY)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT B AS \"B\", SUM(A) AS \"SUM(A)\" "
                + "FROM RC_T AS RC_T GROUP BY B  HAVING \">\"(SUM(A), 1)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT b, SUM(a) FROM "
                + "rc_t GROUP BY b HAVING SUM(a) > 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT B AS \"B\", SUM(A) AS \"SUM(A)\" "
                + "FROM RC_T AS RC_T GROUP BY B  ORDER BY B ASC NULLS LAST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT b, SUM(a) FROM "
                + "rc_t GROUP BY b ORDER BY b)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT B AS \"B\", SUM(A) AS \"SUM(A)\" "
                + "FROM RC_T AS RC_T GROUP BY 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT b, SUM(a) FROM "
                + "rc_t GROUP BY 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\", B AS \"B\" FROM RC_T "
                + "AS RC_T GROUP BY A, B))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a, b FROM rc_t "
                + "GROUP BY a, b)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT B AS \"B\" FROM RC_T AS RC_T GROUP "
                + "BY ALL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT b FROM rc_t GROUP "
                + "BY ALL)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT T.A AS \"T.A\" FROM RC_T AS T LEFT "
                + "OUTER JOIN RC_T AS U ON (\"=\"(T.A, U.A))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT t.a FROM rc_t t "
                + "LEFT JOIN rc_t u ON t.a = u.a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT T.A AS \"T.A\" FROM RC_T AS T LEFT "
                + "OUTER JOIN RC_T AS U ON (AND(\"=\"(T.A, U.A), \"=\"(T.B, U.B)))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT t.a FROM rc_t t "
                + "LEFT OUTER JOIN rc_t u ON t.a = u.a AND t.b = u.b)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT T.A AS \"T.A\" FROM RC_T AS T "
                + "RIGHT OUTER JOIN RC_T AS U ON (\"=\"(T.A, U.A))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT t.a FROM rc_t t "
                + "RIGHT JOIN rc_t u ON t.a = u.a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT T.A AS \"T.A\" FROM RC_T AS T FULL "
                + "OUTER JOIN RC_T AS U ON (\"=\"(T.A, U.A))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT t.a FROM rc_t t "
                + "FULL JOIN rc_t u ON t.a = u.a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT T.A AS \"T.A\" FROM RC_T AS T "
                + "INNER JOIN RC_T AS U))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT t.a FROM rc_t t "
                + "CROSS JOIN rc_t u)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT T.A AS \"T.A\" FROM RC_T AS T "
                + "INNER JOIN RC_T AS U))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT t.a FROM rc_t t, "
                + "rc_t u)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT T.A AS \"T.A\" FROM RC_T AS T "
                + "INNER JOIN RC_T AS U WHERE \"=\"(T.A, U.A)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT t.a FROM rc_t t, "
                + "rc_t u WHERE t.a = u.a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT T.A AS \"T.A\" FROM RC_T AS T "
                + "INNER JOIN RC_T AS U))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT t.a FROM rc_t t "
                + "JOIN rc_t u USING (a))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT T.A AS \"T.A\" FROM RC_T AS T "
                + "INNER JOIN RC_T AS U))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT t.a FROM rc_t t "
                + "NATURAL JOIN rc_t u)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT T.A AS \"T.A\" FROM RC_T AS T "
                + "INNER JOIN RC_T AS U ON (\"=\"(T.A, U.A)) INNER JOIN RC_T AS W ON (\"=\"(U.A, W.A))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT t.a FROM rc_t t "
                + "INNER JOIN rc_t u ON t.a = u.a INNER JOIN rc_t w ON u.a = w.a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL) OB$0 UNION (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) OB$1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1 UNION SELECT "
                + "2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL) OB$0 INTERSECT (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) OB$1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1 INTERSECT "
                + "SELECT 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL) OB$0 MINUS (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) OB$1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1 EXCEPT SELECT "
                + "2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL) OB$0 MINUS (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) OB$1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1 MINUS SELECT "
                + "2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL) OB$0 UNION ALL (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) OB$1 UNION ALL "
                + "(SELECT 3 AS \"3\" FROM (VALUES (null)) DUAL) OB$2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1 UNION ALL "
                + "SELECT 2 UNION ALL SELECT 3)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT A AS \"A\" FROM "
                + "RC_T AS RC_T) OB$0 UNION ALL (SELECT A AS \"A\" FROM RC_T AS RC_T) OB$1  ORDER BY 1 ASC NULLS "
                + "LAST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t UNION "
                + "ALL SELECT a FROM rc_t ORDER BY 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT 1 AS \"X\" FROM (VALUES (null)) "
                + "DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1 AS x)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT 1 AS \"1\", 2 AS \"2\" FROM "
                + "(VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1, 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT 'x' AS \"'X'\" FROM (VALUES "
                + "(null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 'x')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT null AS \"NULL\" FROM (VALUES "
                + "(null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT NULL)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT (SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL) AS \"(SELECT 1)\" FROM (VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT (SELECT 1))); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "EXISTS(SELECT 1 AS \"1\" FROM RC_T AS U WHERE \"=\"(U.A, RC_T.A))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "EXISTS (SELECT 1 FROM rc_t u WHERE u.a = rc_t.a))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "\"=\"(A, ANY(SELECT A AS \"A\" FROM RC_T AS RC_T))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a IN (SELECT a FROM rc_t))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT \"COUNT(*)\"() AS \"COUNT(*)\" "
                + "FROM (VALUES (1), (2)) V))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT COUNT(*) FROM "
                + "(VALUES (1), (2)) v(x))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT X AS \"X\" FROM (VALUES (1), (2)) "
                + "V))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT x FROM (VALUES "
                + "(1), (2)) AS v(x))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT COLUMN1 AS \"COLUMN1\" FROM "
                + "(VALUES (1), (2)) \"VALUES\"))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT column1 FROM "
                + "VALUES (1), (2))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM (SELECT A AS \"A\" "
                + "FROM RC_T AS RC_T) values))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM (SELECT a "
                + "FROM rc_t))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT D.A AS \"D.A\" FROM (SELECT A AS "
                + "\"A\" FROM RC_T AS RC_T) D))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT d.a FROM (SELECT "
                + "a FROM rc_t) AS d)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM E AS E))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((WITH c AS (SELECT a FROM "
                + "rc_t), e AS (SELECT a FROM c) SELECT a FROM e)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT VALUE AS \"VALUE\" FROM TABLE "
                + "(FLATTEN(INPUT => PARSE_JSON('[1]')))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT value FROM "
                + "TABLE(FLATTEN(INPUT => PARSE_JSON('[1]'))))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT F.VALUE AS \"F.VALUE\" FROM RC_T "
                + "AS RC_T, TABLE (FLATTEN(INPUT => PARSE_JSON('[1]')))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT f.value FROM rc_t,"
                + " LATERAL FLATTEN(INPUT => PARSE_JSON('[1]')) f)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t "
                + "SAMPLE (10))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT CASE(\">\"(A, 1), 1, 0) AS \"CASE "
                + "WHEN A > 1 THEN 1 ELSE 0 END\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT CASE WHEN a > 1 "
                + "THEN 1 ELSE 0 END FROM rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT CAST(A AS VARCHAR(134217728)) AS "
                + "\"A::VARCHAR\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a::VARCHAR FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT COUNT(DISTINCT A) AS "
                + "\"COUNT(DISTINCT A)\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT COUNT(DISTINCT a) "
                + "FROM rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT SUM(A) OVER (ORDER BY A ASC NULLS "
                + "LAST) AS \"SUM(A) OVER (ORDER BY A)\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT SUM(a) OVER "
                + "(ORDER BY a) FROM rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "OR(\"=\"(A, 1), \"=\"(B, 'x'))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a = 1 OR b = 'x')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T  "
                + "ORDER BY 1 ASC NULLS LAST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t ORDER "
                + "BY 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT DISTINCT A AS \"A\", B AS \"B\" "
                + "FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT DISTINCT a, b "
                + "FROM rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT ALL a FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT $1 AS \"$1\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT $1 FROM rc_t)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT NOSUCH(A) AS \"N\" FROM RC_T AS "
                + "RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT nosuch(a) AS n "
                + "FROM rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT \"a\" AS \"a\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT \"a\" FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM \"rc_t\" AS rc_t))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM "
                + "\"rc_t\")); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS T x))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t \"T "
                + "x\")); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM NOSUCH_TABLE_X9 AS "
                + "NOSUCH_TABLE_X9))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM "
                + "nosuch_table_x9)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM "
                + "IDENTIFIER('rc_t'))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT ZZ AS \"ZZ\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT zz FROM rc_t)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "\"=\"(ZZ, 1)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "zz = 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T GROUP "
                + "BY A  ORDER BY A ASC NULLS LAST  HAVING \">\"(\"COUNT(*)\"(), 1) LIMIT 1 OFFSET 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t GROUP "
                + "BY a HAVING COUNT(*) > 1 ORDER BY a LIMIT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "\"=\"(A, 1) GROUP BY A))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a = 1 GROUP BY a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "\"=\"(A, 1)  ORDER BY A ASC NULLS LAST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a = 1 ORDER BY a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "\"=\"(A, 1) LIMIT 1 OFFSET 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a = 1 LIMIT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T GROUP "
                + "BY A LIMIT 1 OFFSET 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t GROUP "
                + "BY a LIMIT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "\"=\"(A, 1)  HAVING \">\"(\"COUNT(*)\"(), 0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a = 1 HAVING COUNT(*) > 0)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T GROUP "
                + "BY A QUALIFY \">\"(\"COUNT(*)\"() OVER (), 0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t GROUP "
                + "BY a QUALIFY COUNT(*) OVER () > 0)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T  "
                + "ORDER BY A DESC NULLS FIRST, B ASC NULLS FIRST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t ORDER "
                + "BY a DESC NULLS FIRST, b ASC NULLS FIRST)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT \"+\"(MAX(A), 1) AS \"M\" FROM "
                + "RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT MAX(a) + 1 AS m "
                + "FROM rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\", 'x' AS \"C\", \"+\"(A, "
                + "1) AS \"A + 1\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a, 'x' AS c, a + "
                + "1 FROM rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS T WHERE "
                + "\"=\"(T.A, 1)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t t "
                + "WHERE t.a = 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT A AS \"A\" FROM "
                + "RC_T AS RC_T) OB$0 UNION ALL (SELECT A AS \"A\" FROM RC_T AS RC_T) OB$1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t UNION "
                + "ALL SELECT a FROM rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT A AS \"A\" FROM "
                + "RC_T AS RC_T) OB$0 UNION (SELECT A AS \"A\" FROM RC_T AS RC_T) OB$1 LIMIT 1 OFFSET 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t UNION "
                + "SELECT a FROM rc_t LIMIT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL) OB$0 UNION ALL (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) OB$1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(((SELECT 1) UNION ALL "
                + "(SELECT 2))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "\"=\"(A, (SELECT MAX(A) AS \"MAX(A)\" FROM RC_T AS RC_T))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a = (SELECT MAX(a) FROM rc_t))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T  "
                + "ORDER BY \"+\"(A, 1) ASC NULLS LAST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t ORDER "
                + "BY a + 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T GROUP "
                + "BY A, \"+\"(A, 1)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t GROUP "
                + "BY a, a + 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T GROUP "
                + "BY GROUPING SETS ((), (A))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t GROUP "
                + "BY ROLLUP (a))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T LIMIT "
                + "5 OFFSET 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t LIMIT "
                + "5 OFFSET 0)); END $$");
    }

    @Test
    public void predicatesAreCallsBesideAQuery() {
        engine.execute(
            "CREATE OR REPLACE TABLE rc_t (a INT, b VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS $$ 1 $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS $$ x $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(LIKE_ANY(A, null, 'x', 'y'), "
                + "EXISTS(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a LIKE ANY "
                + "('x', 'y') AND EXISTS (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(A IN (ZZ, 1), EXISTS(SELECT 1 AS \"1\" "
                + "FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (zz, 1) "
                + "AND EXISTS (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(A IN (\"+\"(1, 1)) IN (2), "
                + "EXISTS(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1 + 1, "
                + "2) AND EXISTS (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(T, (SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(t.a + (SELECT "
                + "1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"=\"(T, (SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(t.a = (SELECT "
                + "1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"=\"(\"t\", (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(\"t\".\"a\" = "
                + "(SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(\"=\"(A, ANY(SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL))))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(NOT (a = ANY "
                + "(SELECT 1))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(\"=\"(A, ANY(SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL))))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(NOT (a IN "
                + "(SELECT 1))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(CASE(\">\"(A, 1), 2, null), (SELECT "
                + "1 AS \"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(CASE WHEN a > "
                + "1 THEN 2 ELSE NULL END + (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(REGEXP_LIKE('a', 'a'), EXISTS(SELECT 1 "
                + "AS \"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN "
                + "nosuch(REGEXP_LIKE('a', 'a') AND EXISTS (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(REGEXP('a', 'a'), EXISTS(SELECT 1 AS "
                + "\"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch('a' REGEXP 'a' "
                + "AND EXISTS (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(NOT(RLIKE(A, 'x')), EXISTS(SELECT 1 AS "
                + "\"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT RLIKE "
                + "'x' AND EXISTS (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(ILIKE(A, 'x', '!'), EXISTS(SELECT 1 AS "
                + "\"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a ILIKE 'x' "
                + "ESCAPE '!' AND EXISTS (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"=\"(GET(V, 'k'), (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(v:k = (SELECT "
                + "1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"=\"(GET(V, 0), (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(v[0] = (SELECT "
                + "1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\">\"(TO_TIME('01:02:03'), (SELECT "
                + "CURRENT_TIME() AS \"CURRENT_TIME\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(TIME "
                + "'01:02:03' > (SELECT CURRENT_TIME)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(\"=\"(ROW(A, 1), ROW(1, 2)), "
                + "EXISTS(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch((a, 1) = (1, "
                + "2) AND EXISTS (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(ROW(A, 1) IN (ROW(1, 2), ROW(3, 4)), "
                + "EXISTS(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch((a, 1) IN ((1, "
                + "2), (3, 4)) AND EXISTS (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"!=\"(ROW(A, 1), ALL(SELECT 1 AS \"1\", 2 "
                + "AS \"2\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch((a, 1) NOT IN "
                + "(SELECT 1, 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"=\"(FILTER(ARRAY_CONSTRUCT(1, 2), (X -> "
                + "\">\"(X, 1)), (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(FILTER([1, 2], "
                + "x -> x > 1) = (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"=\"(\"+\"(A, null), (SELECT 1 AS \"1\" "
                + "FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + NULL = "
                + "(SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"=\"(\"COUNT(*)\"(), (SELECT 1 AS \"1\" "
                + "FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(COUNT(*) = "
                + "(SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"=\"(\"+\"(\"COUNT(*)\"(), COUNT(A)), "
                + "(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(COUNT(*) + "
                + "COUNT(a) = (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(\"UNARY PLUS\"(A), (SELECT 1 AS "
                + "\"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(+a + (SELECT "
                + "1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(NEGATE(\"+\"(A, 1)), (SELECT 1 AS "
                + "\"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(-(a + 1) + "
                + "(SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(NOT(\">\"(A, 1)), EXISTS(SELECT 1 AS "
                + "\"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(NOT (a > 1) "
                + "AND EXISTS (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"=\"(EXTRACT('year', CURRENT_DATE), "
                + "(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(EXTRACT('year',"
                + " CURRENT_DATE) = (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"=\"(DATE_PART(YEAR, CURRENT_DATE), "
                + "(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATE_PART(year,"
                + " CURRENT_DATE) = (SELECT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\">\"(DATEADD(DAY, 1, CURRENT_DATE), "
                + "(SELECT CURRENT_DATE() AS \"CURRENT_DATE\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATEADD(day, 1,"
                + " CURRENT_DATE) > (SELECT CURRENT_DATE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\">\"(CURRENT_TIMESTAMP, (SELECT "
                + "CURRENT_TIMESTAMP() AS \"CURRENT_TIMESTAMP\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN "
                + "nosuch(CURRENT_TIMESTAMP > (SELECT CURRENT_TIMESTAMP)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"=\"(CURRENT_USER, (SELECT CURRENT_USER() "
                + "AS \"CURRENT_USER\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_USER = "
                + "(SELECT CURRENT_USER)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\">\"(LOCALTIME, (SELECT LOCALTIME() AS "
                + "\"LOCALTIME\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(LOCALTIME > "
                + "(SELECT LOCALTIME)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\">\"(CURRENT_DATE(), (SELECT "
                + "CURRENT_DATE() AS \"CURRENT_DATE()\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(CURRENT_DATE() "
                + "> (SELECT CURRENT_DATE())); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\">\"(SYSDATE(), (SELECT SYSDATE() AS "
                + "\"SYSDATE()\" FROM (VALUES (null)) DUAL)))",
            "CREATE OR REPLACE FUNCTION eu(a INT, v VARIANT) RETURNS INT AS $$ BEGIN RETURN nosuch(SYSDATE() > "
                + "(SELECT SYSDATE())); END $$");
    }

    @Test
    public void averagesAndRatiosOverAWindow() {
        engine.execute(
            "CREATE OR REPLACE TABLE rc_t (a INT, b VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS $$ 1 $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS $$ x $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(A) OVER (ORDER BY A ASC NULLS LAST) / "
                + "COUNT(A) OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN AVG(a) OVER (ORDER BY a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(A) OVER (PARTITION BY A ORDER BY A ASC NULLS "
                + "LAST) / COUNT(A) OVER (PARTITION BY A ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN AVG(a) OVER (PARTITION BY a "
                + "ORDER BY a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: AVG(A) OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN AVG(a) OVER (ORDER BY a ROWS "
                + "BETWEEN 1 PRECEDING AND CURRENT ROW); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"+\"(SUM(A) OVER (ORDER BY A ASC NULLS LAST) / "
                + "COUNT(A) OVER (ORDER BY A ASC NULLS LAST), 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN AVG(a) OVER (ORDER BY a) + 1; "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(SUM(A) OVER (ORDER BY A ASC NULLS LAST) / "
                + "COUNT(A) OVER (ORDER BY A ASC NULLS LAST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(AVG(a) OVER (ORDER BY "
                + "a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(\"+\"(A, 1)) OVER (ORDER BY A ASC NULLS LAST) "
                + "/ COUNT(\"+\"(A, 1)) OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN AVG(a + 1) OVER (ORDER BY a); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: AVG(A) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN AVG(a) OVER (ORDER BY a ROWS "
                + "BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: (\"+\"(A, 1)) / (IFF(SUM(\"+\"(A, 1)) OVER "
                + "(PARTITION BY A) = 0, null, SUM(\"+\"(A, 1)) OVER (PARTITION BY A)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN RATIO_TO_REPORT(a + 1) OVER "
                + "(PARTITION BY a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"+\"(A / (IFF(SUM(A) OVER () = 0, null, SUM(A) "
                + "OVER ())), 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN RATIO_TO_REPORT(a) OVER () + 1; "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A / (IFF(SUM(A) OVER () = 0, null, SUM(A) "
                + "OVER ())))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(RATIO_TO_REPORT(a) OVER "
                + "()); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: A / (IFF(SUM(A) OVER (PARTITION BY A, \"+\"(A, "
                + "1)) = 0, null, SUM(A) OVER (PARTITION BY A, \"+\"(A, 1))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN RATIO_TO_REPORT(a) OVER "
                + "(PARTITION BY a, a + 1); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"+\"(SUM(A) OVER (ORDER BY A ASC NULLS LAST), "
                + "SUM(A) OVER (ORDER BY A ASC NULLS LAST) / COUNT(A) OVER (ORDER BY A ASC NULLS LAST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a) OVER (ORDER BY a) + "
                + "AVG(a) OVER (ORDER BY a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"+\"(\"COUNT(*)\"() OVER (ORDER BY A ASC NULLS "
                + "LAST), \"COUNT(*)\"() OVER ())",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN COUNT(*) OVER (ORDER BY a) + "
                + "COUNT(*) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: STDDEV(A) OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN STDDEV(a) OVER (ORDER BY a); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: VARIANCE(A) OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN VARIANCE(a) OVER (ORDER BY a); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: MEDIAN(A) OVER (PARTITION BY A)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN MEDIAN(a) OVER (PARTITION BY a); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(DISTINCT A) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(DISTINCT a) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: COUNT(DISTINCT A) OVER (PARTITION BY A)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN COUNT(DISTINCT a) OVER "
                + "(PARTITION BY a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: LAG(A) OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN LAG(a) IGNORE NULLS OVER (ORDER "
                + "BY a); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: LEAD(\"+\"(A, 1), 2) OVER (PARTITION BY A ORDER "
                + "BY A DESC NULLS FIRST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN LEAD(a + 1, 2) OVER (PARTITION "
                + "BY a ORDER BY a DESC); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"=\"(ROW_NUMBER() OVER (PARTITION BY A ORDER BY "
                + "A ASC NULLS LAST), 1)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN ROW_NUMBER() OVER (PARTITION BY "
                + "a ORDER BY a) = 1; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: BETWEEN(RANK() OVER (ORDER BY A ASC NULLS LAST), "
                + "1, 2)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN RANK() OVER (ORDER BY a) BETWEEN "
                + "1 AND 2; END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(A) OVER (ORDER BY A ASC NULLS LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a) OVER (ORDER BY a ROWS "
                + "UNBOUNDED PRECEDING); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(A) OVER (PARTITION BY T ORDER BY T ASC NULLS "
                + "LAST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a) OVER (PARTITION BY t.a "
                + "ORDER BY t.b); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: SUM(A) OVER (ORDER BY A DESC NULLS FIRST) / "
                + "COUNT(A) OVER (ORDER BY A DESC NULLS FIRST)",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN AVG(a) OVER (ORDER BY a DESC); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: BOOLAND_AGG(A) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN BOOLAND_AGG(a) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: ANY_VALUE(A) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN ANY_VALUE(a) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: OBJECT_AGG(A, A) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN OBJECT_AGG(a, a) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: MODE(A) OVER ()",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN MODE(a) OVER (); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: \"||\"(SUM(A) OVER (ORDER BY A ASC NULLS LAST), "
                + "(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN SUM(a) OVER (ORDER BY a) || "
                + "(SELECT 1); END $$");
    }

    @Test
    public void itemNamesJoinsSetOperationsAndRowLimits() {
        engine.execute(
            "CREATE OR REPLACE TABLE rc_t (a INT, b VARCHAR)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS $$ 1 $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS $$ x $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T  "
                + "ORDER BY A ASC NULLS LAST QUALIFY \"=\"(ROW_NUMBER() OVER (ORDER BY A ASC NULLS LAST), 1) LIMIT 1 "
                + "OFFSET 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t "
                + "QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1 ORDER BY a LIMIT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T GROUP "
                + "BY A  ORDER BY A ASC NULLS LAST  HAVING \">\"(\"COUNT(*)\"(), 0) QUALIFY \">\"(\"COUNT(*)\"() "
                + "OVER (), 0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t GROUP "
                + "BY a HAVING COUNT(*) > 0 QUALIFY COUNT(*) OVER () > 0 ORDER BY a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "\">\"(A, 0) GROUP BY A  ORDER BY A ASC NULLS LAST  HAVING \">\"(\"COUNT(*)\"(), 0) QUALIFY "
                + "\">\"(\"COUNT(*)\"() OVER (), 0) LIMIT 2 OFFSET 1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a > 0 GROUP BY a HAVING COUNT(*) > 0 QUALIFY COUNT(*) OVER () > 0 ORDER BY a LIMIT 2 OFFSET 1)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T  "
                + "HAVING \">\"(\"COUNT(*)\"(), 0) QUALIFY \">\"(\"COUNT(*)\"() OVER (), 0)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t "
                + "HAVING COUNT(*) > 0 QUALIFY COUNT(*) OVER () > 0)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T "
                + "QUALIFY \">\"(\"COUNT(*)\"() OVER (), 0) LIMIT 1 OFFSET 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t "
                + "QUALIFY COUNT(*) OVER () > 0 LIMIT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT \"+\"(A, 1) AS \"A  +  1\" FROM "
                + "RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a  +  1 FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT \"+\"(\"a\", 1) AS \"\"A\" + 1\" "
                + "FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT \"a\" + 1 FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"a\"b\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a AS \"a\"\"b\" "
                + "FROM rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT T.\"a\" AS \"T.\"A\"\" FROM RC_T "
                + "AS T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT t.\"a\" FROM rc_t "
                + "t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT \"+\"(1, 1) AS \"1 + 1\" FROM "
                + "(VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1 + 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT -1 AS \"-1\" FROM (VALUES (null)) "
                + "DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT -1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT \"+\"(A, 1) AS \"A /* C */ + 1\" "
                + "FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a /* c */ + 1 "
                + "FROM rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT RC_T.* AS \"RC_T.*\" FROM RC_T AS "
                + "RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT rc_t.* FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT * EXCLUDE (b) "
                + "FROM rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\", * FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a, * FROM rc_t)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT CURRENT_TIMESTAMP() AS "
                + "\"CURRENT_TIMESTAMP\" FROM (VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT "
                + "CURRENT_TIMESTAMP)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT CURRENT_USER() AS \"CURRENT_USER\" "
                + "FROM (VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT CURRENT_USER)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT LOCALTIME() AS \"LOCALTIME\" FROM "
                + "(VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT LOCALTIME)); END "
                + "$$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT CURRENT_DATE() AS "
                + "\"CURRENT_DATE()\" FROM (VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT CURRENT_DATE())); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT EXTRACT('year', CURRENT_DATE()) AS "
                + "\"EXTRACT(YEAR FROM CURRENT_DATE)\" FROM (VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT EXTRACT(year FROM "
                + "CURRENT_DATE))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT \"+\"(\"COUNT(*)\"(), 1) AS "
                + "\"COUNT(*) + 1\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT COUNT(*) + 1 FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT \"IS NULL\"(A) AS \"A IS NULL\" "
                + "FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a IS NULL FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT NEGATE(A) AS \"-A\" FROM RC_T AS "
                + "RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT -a FROM rc_t)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT \"UNARY PLUS\"(A) AS \"+A\" FROM "
                + "RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT +a FROM rc_t)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT NOT(A) AS \"NOT A\" FROM RC_T AS "
                + "RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT NOT a FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT ARRAY_CONSTRUCT(1, 2) AS \"[1, "
                + "2]\" FROM (VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT [1, 2])); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT OBJECT_CONSTRUCT('k', 1) AS "
                + "\"{'K': 1}\" FROM (VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT {'k': 1})); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT GET(B, 'k') AS \"B:K\" FROM RC_T "
                + "AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT b:k FROM rc_t)); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT CASE(A, 1, 2, null) AS \"CASE A "
                + "WHEN 1 THEN 2 END\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT CASE a WHEN 1 "
                + "THEN 2 END FROM rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT TRY_CAST(B) AS \"TRY_CAST(B AS "
                + "INT)\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT TRY_CAST(b AS "
                + "INT) FROM rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT CAST(A AS NUMBER(38,0)) AS "
                + "\"A::INT\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a::INT FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT INTERVAL_LITERAL('day', '1') AS "
                + "\"INTERVAL '1 DAY'\" FROM (VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT INTERVAL '1 "
                + "day')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT TO_DATE('2020-01-01') AS \"DATE "
                + "'2020-01-01'\" FROM (VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT DATE "
                + "'2020-01-01')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "A IN (A, 1)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a IN (a, 1))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "A IN (\"+\"(1, 1)) IN (2)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a IN (1 + 1, 2))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "LIKE_ANY(B, null, 'x', 'y')))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "b LIKE ANY ('x', 'y'))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "NOT(EQUAL_NULL(A, 1))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a IS DISTINCT FROM 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "\"=\"(ROW(A, B), ROW(1, 'x'))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "(a, b) = (1, 'x'))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "NOT(A IN (1, 2))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a NOT IN (1, 2))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "NOT(BETWEEN(A, 1, 2))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a NOT BETWEEN 1 AND 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "NOT(LIKE(B, 'x'))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "b NOT LIKE 'x')); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "AND(\"IS NOT NULL\"(A), NOT EXISTS(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a IS NOT NULL AND NOT EXISTS (SELECT 1))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT T.A AS \"T.A\" FROM RC_T AS T "
                + "INNER JOIN (SELECT A AS \"A\" FROM RC_T AS RC_T) D ON (\"=\"(T.A, D.A))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT t.a FROM rc_t t "
                + "JOIN (SELECT a FROM rc_t) d ON t.a = d.a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT T.A AS \"T.A\" FROM RC_T AS T "
                + "INNER JOIN RC_T AS U ON (\"=\"(T.A, U.A))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT t.a FROM (rc_t t "
                + "JOIN rc_t u ON t.a = u.a))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT T.A AS \"T.A\" FROM RC_T AS T "
                + "INNER JOIN RC_T AS U INNER JOIN RC_T AS W))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT t.a FROM rc_t t, "
                + "rc_t u, rc_t w)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT T.A AS \"T.A\" FROM RC_T AS T LEFT "
                + "OUTER JOIN RC_T AS U))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT t.a FROM rc_t t "
                + "LEFT JOIN rc_t u USING (a))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT T.A AS \"T.A\" FROM RC_T AS T LEFT "
                + "OUTER JOIN RC_T AS U))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT t.a FROM rc_t t "
                + "NATURAL LEFT JOIN rc_t u)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT F.VALUE AS \"F.VALUE\" FROM TABLE "
                + "(FLATTEN(INPUT => PARSE_JSON('[1]')))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT f.value FROM "
                + "TABLE(FLATTEN(INPUT => PARSE_JSON('[1]'))) f)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT F.VALUE AS \"F.VALUE\" FROM RC_T "
                + "AS T, TABLE (FLATTEN(INPUT => PARSE_JSON('[1]')))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT f.value FROM rc_t "
                + "t, TABLE(FLATTEN(INPUT => PARSE_JSON('[1]'))) f)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t AS "
                + "\"T\")); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM "
                + "TEST_SCHEMA.\"rc_t\" AS rc_t))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM "
                + "test_schema.\"rc_t\")); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS T WHERE "
                + "\"=\"(A, 1)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t t "
                + "SAMPLE (10) WHERE a = 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT X AS \"X\" FROM (VALUES (1, 'a'), "
                + "(2, 'b')) V))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT x FROM VALUES (1, "
                + "'a'), (2, 'b') AS v(x, y))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT COLUMN1 AS \"COLUMN1\" FROM "
                + "(VALUES (\"+\"(1, 1))) \"VALUES\"))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT column1 FROM "
                + "(VALUES (1 + 1)))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT X AS \"X\" FROM C AS Z))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((WITH c AS (SELECT 1 AS "
                + "x) SELECT x FROM c z)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT Z.X AS \"Z.X\" FROM C AS Z INNER "
                + "JOIN C AS C ON (TRUE)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((WITH c AS (SELECT 1 AS "
                + "x) SELECT z.x FROM c AS z JOIN c ON TRUE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS T INNER "
                + "JOIN RC_T AS U WHERE \"=\"(T.A, U.A)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t t "
                + "CROSS JOIN rc_t u WHERE t.a = u.a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS T INNER "
                + "JOIN RC_T AS U))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t t "
                + "INNER JOIN rc_t u)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS T INNER "
                + "JOIN RC_T AS U ON (\"=\"(T.A, U.A)) LEFT OUTER JOIN RC_T AS W ON (\"=\"(W.A, U.A)) WHERE \"IS "
                + "NULL\"(W.A)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t t "
                + "JOIN rc_t u ON t.a = u.a LEFT JOIN rc_t w ON w.a = u.a WHERE w.a IS NULL)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS T INNER "
                + "JOIN RC_T AS U ON (AND(\"=\"(T.A, U.A), \"=\"(U.B, ANY(SELECT B AS \"B\" FROM RC_T AS RC_T))))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t t "
                + "JOIN rc_t u ON t.a = u.a AND u.b IN (SELECT b FROM rc_t))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS T LEFT "
                + "OUTER JOIN (SELECT U.A AS \"U.A\" FROM RC_T AS U WHERE \"=\"(U.A, T.A)) L ON (TRUE)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t t "
                + "LEFT JOIN LATERAL (SELECT u.a FROM rc_t u WHERE u.a = t.a) l ON TRUE)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT A AS \"A\" FROM "
                + "RC_T AS RC_T) OB$0 UNION ALL (SELECT A AS \"A\" FROM RC_T AS RC_T) OB$1  ORDER BY A ASC NULLS "
                + "LAST LIMIT 2 OFFSET 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t UNION "
                + "ALL SELECT a FROM rc_t ORDER BY a LIMIT 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL) OB$0 UNION ALL (SELECT * FROM (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) "
                + "OB$1 UNION (SELECT 3 AS \"3\" FROM (VALUES (null)) DUAL) OB$2) OB$3))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1 UNION ALL "
                + "(SELECT 2 UNION SELECT 3))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL) OB$0 UNION ALL BY NAME (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) OB$1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1 UNION ALL BY "
                + "NAME SELECT 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT A AS \"A\" FROM "
                + "RC_T AS RC_T  ORDER BY A ASC NULLS LAST LIMIT 1 OFFSET 0) OB$0 UNION ALL (SELECT 2 AS \"2\" FROM "
                + "(VALUES (null)) DUAL) OB$1))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(((SELECT a FROM rc_t "
                + "ORDER BY a LIMIT 1) UNION ALL SELECT 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL) OB$0 UNION (SELECT * FROM (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) "
                + "OB$1 INTERSECT (SELECT 3 AS \"3\" FROM (VALUES (null)) DUAL) OB$2) OB$3))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1 UNION SELECT 2 "
                + "INTERSECT SELECT 3)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT * FROM (SELECT * FROM (SELECT 1 AS "
                + "\"1\" FROM (VALUES (null)) DUAL) OB$0 MINUS (SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL) OB$1) "
                + "OB$2 UNION ALL (SELECT 3 AS \"3\" FROM (VALUES (null)) DUAL) OB$3))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1 EXCEPT SELECT 2 "
                + "UNION ALL SELECT 3)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM (SELECT * FROM "
                + "(SELECT 1 AS \"A\" FROM (VALUES (null)) DUAL) OB$0 UNION ALL (SELECT 2 AS \"2\" FROM (VALUES "
                + "(null)) DUAL) OB$1) D))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM (SELECT 1 "
                + "AS a UNION ALL SELECT 2) d)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T LIMIT "
                + "null OFFSET 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t LIMIT "
                + "NULL)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T LIMIT "
                + "3 OFFSET 2))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t "
                + "OFFSET 2 ROWS FETCH NEXT 3 ROWS ONLY)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T  "
                + "ORDER BY A DESC NULLS FIRST LIMIT 2 OFFSET 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT TOP 2 a FROM rc_t "
                + "ORDER BY a DESC)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T  "
                + "ORDER BY A ASC NULLS LAST LIMIT 2 OFFSET 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t ORDER "
                + "BY a FETCH FIRST 2 ROWS ONLY)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T GROUP "
                + "BY GROUPING SETS ((), (A), (B), (A, B))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t GROUP "
                + "BY CUBE (a, b))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T GROUP "
                + "BY GROUPING SETS ((A), (B))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t GROUP "
                + "BY GROUPING SETS ((a), (b)))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T GROUP "
                + "BY GROUPING SETS ((), (A), (A, B))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t GROUP "
                + "BY ROLLUP (a, b))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT B AS \"B\" FROM RC_T AS RC_T GROUP "
                + "BY ALL  ORDER BY 1 ASC NULLS LAST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT b FROM rc_t GROUP "
                + "BY ALL ORDER BY 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T GROUP "
                + "BY GROUPING SETS ((A), (A, B))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t GROUP "
                + "BY a, ROLLUP (b))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT DISTINCT A AS \"A\" FROM RC_T AS "
                + "RC_T  ORDER BY A ASC NULLS LAST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT DISTINCT a FROM "
                + "rc_t ORDER BY a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT DISTINCT A AS \"A\" FROM RC_T AS "
                + "RC_T LIMIT 1 OFFSET 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT DISTINCT TOP 1 a "
                + "FROM rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "\"=\"(A, null)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a = :a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T  "
                + "ORDER BY A ASC NULLS LAST, B ASC NULLS LAST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t ORDER "
                + "BY a ASC, b)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"X\", \"+\"(X, 1) AS \"X + "
                + "1\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a AS x, x + 1 "
                + "FROM rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a AS a FROM "
                + "rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT 1 AS \"A\" FROM (VALUES (null)) "
                + "DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1 a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT 1 AS \"a b\" FROM (VALUES (null)) "
                + "DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1 AS \"a b\")); "
                + "END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT \"COUNT(*)\"() AS \"C\" FROM RC_T "
                + "AS RC_T))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT COUNT(*) AS c "
                + "FROM rc_t)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "\">\"(A, (SELECT AVG(A) AS \"AVG(A)\" FROM RC_T AS U WHERE \"=\"(U.B, RC_T.B)))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a > (SELECT AVG(a) FROM rc_t u WHERE u.b = rc_t.b))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT \"+\"((SELECT MAX(A) AS \"MAX(A)\" "
                + "FROM RC_T AS RC_T), 1) AS \"(SELECT MAX(A) FROM RC_T) + 1\" FROM (VALUES (null)) DUAL))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT (SELECT MAX(a) "
                + "FROM rc_t) + 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T WHERE "
                + "\"=\"(A, ANY(SELECT A AS \"A\" FROM RC_T AS RC_T WHERE \"=\"(B, ANY(SELECT B AS \"B\" FROM RC_T "
                + "AS RC_T))))))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t WHERE "
                + "a IN (SELECT a FROM rc_t WHERE b IN (SELECT b FROM rc_t)))); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T  "
                + "ORDER BY A ASC NULLS FIRST LIMIT 1 OFFSET 0))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t ORDER "
                + "BY a NULLS FIRST LIMIT 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T  "
                + "ORDER BY 1 DESC NULLS FIRST, 2 ASC NULLS LAST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t ORDER "
                + "BY 1 DESC, 2)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T  "
                + "ORDER BY ABS(A) DESC NULLS FIRST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t ORDER "
                + "BY ABS(a) DESC)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T  "
                + "ORDER BY T.A ASC NULLS LAST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t ORDER "
                + "BY t.a)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T GROUP "
                + "BY A  ORDER BY \"COUNT(*)\"() DESC NULLS FIRST))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t GROUP "
                + "BY a ORDER BY COUNT(*) DESC)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT A AS \"A\" FROM RC_T AS RC_T GROUP "
                + "BY A  HAVING \">\"(A, 1)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT a FROM rc_t GROUP "
                + "BY a HAVING a > 1)); END $$");
        assertAnswer(
            "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT MAX(A) AS \"MAX(A)\" FROM RC_T AS "
                + "RC_T  ORDER BY 1 ASC NULLS LAST  HAVING \">\"(MAX(A), 1)))",
            "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT MAX(a) FROM rc_t "
                + "HAVING MAX(a) > 1 ORDER BY 1)); END $$");
    }
}
