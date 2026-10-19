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
 * A lateral item keeps the columns its source declares, whatever its arguments would make of a NULL that no
 * row holds. A FLATTEN, a SPLIT_TO_TABLE or a STRTOK_SPLIT_TO_TABLE whose argument calls a handler that is
 * not null-safe, parses a text or divides by a column answers its own columns and the rows of the real
 * inputs - to a comma join, a JOIN LATERAL, a TABLE(...) item and a nested lateral item alike - and so does a
 * lateral subquery and a SQL table function. A handler in the argument is called for the rows alone, never
 * an extra time for the item's shape. A name the argument cannot resolve is the statement's compilation
 * error, and what a row's own value raises is the statement's fault in its own words. Every answer is
 * live-verified.
 */
public class LateralItemShapeTest extends MeasuredCellsTestSupport {

    @Test
    public void aLateralTableFunctionKeepsItsColumns() {
        assertAnswer(
            "Table W successfully created.",
            "CREATE OR REPLACE TABLE w (id INT, s VARCHAR)");
        assertAnswer(
            "2",
            "INSERT INTO w VALUES (1, 'a,b'), (2, 'c')");
        assertAnswer(
            "Function JSP successfully created.",
            "CREATE OR REPLACE FUNCTION jsp(s VARCHAR) RETURNS ARRAY LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H "
                + "{ public static String[] f(String s) { return s.split(\",\"); } } $$");
        assertAnswer(
            "1|a ;; 1|b ;; 2|c",
            "SELECT w.id, f.value FROM w, LATERAL FLATTEN(INPUT => jsp(w.s)) f ORDER BY 1, 2");
        assertShape(
            "ID:NUMBER(38,0), S:VARCHAR(16777216), SEQ:NUMBER(38,0), KEY:VARCHAR(134217728), "
                + "PATH:VARCHAR(134217728), INDEX:NUMBER(38,0), VALUE:VARIANT, THIS:VARIANT",
            "SELECT * FROM w, LATERAL FLATTEN(INPUT => jsp(w.s)) f ORDER BY 1, f.index");
        assertAnswer(
            "1|a ;; 1|b ;; 2|c",
            "SELECT w.id, f.value FROM w, TABLE(FLATTEN(INPUT => jsp(w.s))) f ORDER BY 1, 2");
        assertAnswer(
            "1|a ;; 1|b ;; 2|c",
            "SELECT w.id, f.value FROM w JOIN LATERAL FLATTEN(INPUT => jsp(w.s)) f ORDER BY 1, 2");
        assertAnswer(
            "1|a ;; 1|b ;; 2|c",
            "SELECT w.id, f.value FROM w LEFT JOIN LATERAL FLATTEN(INPUT => jsp(w.s)) f ORDER BY 1, 2");
        assertAnswer(
            "1|a ;; 1|b ;; 2|c",
            "SELECT w.id, f.value FROM w, LATERAL FLATTEN(INPUT => jsp(w.s), OUTER => TRUE) f ORDER BY 1, 2");
        assertAnswer(
            "1|a|a ;; 1|a|z ;; 1|b|b ;; 1|b|z ;; 2|c|c ;; 2|c|z",
            "SELECT w.id, f.value, g.value FROM w, LATERAL FLATTEN(INPUT => jsp(w.s)) f, LATERAL FLATTEN(INPUT => "
                + "jsp(f.value::VARCHAR || ',z')) g ORDER BY 1, 2, 3");
        assertAnswer(
            "1|2 ;; 2|1",
            "SELECT w.id, COUNT(*) FROM w, LATERAL FLATTEN(INPUT => jsp(w.s)) f GROUP BY w.id ORDER BY 1");
        assertAnswer(
            "1|a ;; 2|c",
            "SELECT w.id, f.value FROM w, LATERAL FLATTEN(INPUT => jsp(w.s)) f WHERE f.index = 0 ORDER BY 1");
    }

    @Test
    public void anArgumentThatFailsForANullNoRowHolds() {
        answer(
            "CREATE OR REPLACE TABLE w (id INT, s VARCHAR)");
        answer(
            "INSERT INTO w VALUES (1, 'a,b'), (2, 'c')");
        answer(
            "CREATE OR REPLACE FUNCTION jsp(s VARCHAR) RETURNS ARRAY LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H "
                + "{ public static String[] f(String s) { return s.split(\",\"); } } $$");
        assertAnswer(
            "Table J successfully created.",
            "CREATE OR REPLACE TABLE j (id INT, s VARCHAR)");
        assertAnswer(
            "1",
            "INSERT INTO j VALUES (1, '[1,2]')");
        assertAnswer(
            "1|1 ;; 1|2",
            "SELECT j.id, f.value FROM j, LATERAL FLATTEN(INPUT => PARSE_JSON(COALESCE(j.s, '{'))) f ORDER BY 1, 2");
        assertShape(
            "ID:NUMBER(38,0), S:VARCHAR(16777216), SEQ:NUMBER(38,0), KEY:VARCHAR(134217728), "
                + "PATH:VARCHAR(134217728), INDEX:NUMBER(38,0), VALUE:VARIANT, THIS:VARIANT",
            "SELECT * FROM j, LATERAL FLATTEN(INPUT => PARSE_JSON(COALESCE(j.s, '{'))) f ORDER BY 1, f.index");
        assertAnswer(
            "1|10 ;; 2|5",
            "SELECT w.id, f.value FROM w, LATERAL FLATTEN(INPUT => ARRAY_CONSTRUCT(10 / COALESCE(w.id, 0))) f "
                + "ORDER BY 1");
        assertAnswer(
            "1|a ;; 1|b ;; 2|c",
            "SELECT w.id, t.value FROM w, LATERAL SPLIT_TO_TABLE(SUBSTR(w.s, 1 / COALESCE(w.id, 0)), ',') t ORDER "
                + "BY 1, 2");
        assertShape(
            "ID:NUMBER(38,0), S:VARCHAR(16777216), SEQ:NUMBER(38,0), INDEX:NUMBER(38,0), VALUE:VARCHAR(134217728)",
            "SELECT * FROM w, LATERAL SPLIT_TO_TABLE(SUBSTR(w.s, 1 / COALESCE(w.id, 0)), ',') t ORDER BY 1, "
                + "t.index");
        assertAnswer(
            "1|a ;; 1|b ;; 2|c",
            "SELECT w.id, t.value FROM w, LATERAL STRTOK_SPLIT_TO_TABLE(SUBSTR(w.s, 1 / COALESCE(w.id, 0)), ',') "
                + "t ORDER BY 1, 2");
        assertAnswer(
            "1|10.000000 ;; 2|5.000000",
            "SELECT w.id, q.x FROM w, LATERAL (SELECT 10 / COALESCE(w.id, 0) AS x) q ORDER BY 1");
        assertShape(
            "ID:NUMBER(38,0), S:VARCHAR(16777216), X:NUMBER(8,6), Y:VARCHAR(16777216)",
            "SELECT * FROM w, LATERAL (SELECT 10 / COALESCE(w.id, 0) AS x, w.s AS y) q ORDER BY 1");
        assertAnswer(
            "1|a,b|10.000000|a,b ;; 2|c|5.000000|c",
            "SELECT * FROM w, LATERAL (SELECT 10 / COALESCE(w.id, 0) AS x, w.s AS y) q ORDER BY 1");
        assertAnswer(
            "1|a ;; 2|c",
            "SELECT w.id, q.x FROM w, LATERAL (SELECT jsp(w.s)[0]::VARCHAR AS x) q ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated",
            "SELECT w.id, q.n FROM w, LATERAL (SELECT COUNT(*) AS n FROM TABLE(FLATTEN(INPUT => jsp(w.s)))) q "
                + "ORDER BY 1");
    }

    @Test
    public void aHandlerIsCalledForTheRowsAlone() {
        answer(
            "CREATE OR REPLACE TABLE w (id INT, s VARCHAR)");
        answer(
            "INSERT INTO w VALUES (1, 'a,b'), (2, 'c')");
        assertAnswer(
            "Function JCN successfully created.",
            "CREATE OR REPLACE FUNCTION jcn(x NUMBER) RETURNS ARRAY LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H { "
                + "static int n = 0; public static String[] f(int x) { n = n + 1; return new String[] "
                + "{String.valueOf(n)}; } } $$");
        assertAnswer(
            "2",
            "SELECT MAX(f.value::NUMBER) FROM w, LATERAL FLATTEN(INPUT => jcn(w.id)) f");
        assertAnswer(
            "2",
            "SELECT MAX(q.x::NUMBER) FROM w, LATERAL (SELECT jcn(w.id)[0] AS x) q");
    }

    @Test
    public void aSqlTableFunctionAndTheFaultsOfNamesAndRows() {
        answer(
            "CREATE OR REPLACE TABLE w (id INT, s VARCHAR)");
        answer(
            "INSERT INTO w VALUES (1, 'a,b'), (2, 'c')");
        answer(
            "CREATE OR REPLACE FUNCTION jsp(s VARCHAR) RETURNS ARRAY LANGUAGE JAVA HANDLER = 'H.f' AS $$ class H "
                + "{ public static String[] f(String s) { return s.split(\",\"); } } $$");
        assertAnswer(
            "Function TSQ successfully created.",
            "CREATE OR REPLACE FUNCTION tsq(n NUMBER) RETURNS TABLE (a NUMBER, b VARCHAR) AS $$ SELECT 10 / n, "
                + "'r' || n $$");
        assertAnswer(
            "1|10|r1 ;; 2|5|r2",
            "SELECT w.id, t.a, t.b FROM w, TABLE(tsq(w.id)) t ORDER BY 1");
        assertShape(
            "ID:NUMBER(38,0), S:VARCHAR(16777216), A:NUMBER(38,0), B:VARCHAR(134217728)",
            "SELECT * FROM w, TABLE(tsq(w.id)) t ORDER BY 1");
        assertAnswer(
            "Function TSZ successfully created.",
            "CREATE OR REPLACE FUNCTION tsz(n NUMBER) RETURNS TABLE (a NUMBER) AS $$ SELECT 10 / COALESCE(n, 0) $$");
        assertAnswer(
            "1|10 ;; 2|5",
            "SELECT w.id, t.a FROM w, TABLE(tsz(w.id)) t ORDER BY 1");
        assertShape(
            "ID:NUMBER(38,0), S:VARCHAR(16777216), A:NUMBER(38,0)",
            "SELECT * FROM w, TABLE(tsz(w.id)) t ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 58 | invalid identifier 'W.NOPE'",
            "SELECT w.id, f.value FROM w, LATERAL FLATTEN(INPUT => jsp(w.nope)) f");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'F.NOPE'",
            "SELECT w.id, f.nope FROM w, LATERAL FLATTEN(INPUT => jsp(w.s)) f");
        assertAnswer(
            "1",
            "INSERT INTO w VALUES (3, NULL)");
        assertAnswer(
            "REFUSED User Error Report:  | Java Stack Trace: | java.lang.NullPointerException | \tat "
                + "function_handler_0//H.f(InlineCode.java:1) |  in function JSP with handler H.f",
            "SELECT w.id, f.value FROM w, LATERAL FLATTEN(INPUT => jsp(w.s)) f ORDER BY 1, 2");
        assertAnswer(
            "REFUSED Error parsing JSON: unknown keyword \"a\", pos 2",
            "SELECT w.id, f.value FROM w, LATERAL FLATTEN(INPUT => PARSE_JSON(COALESCE(w.s, '{'))) f ORDER BY 1, 2");
    }
}
