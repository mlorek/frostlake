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
package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A SQL function's query that reads a table function written bare in its FROM clause, {@code FROM FLATTEN(INPUT =>
 * ...)}, is refused at its SELECT in the frame the body is read in, then where the account reads on through the query's
 * first select item and the call (live-verified): an operand refuses the call's first argument and, by the parentheses
 * open around the query, the call's or the query's closing parenthesis; a star or a list refuses the FROM first; an
 * item that is a call refuses its own first argument, a star argument's closing parenthesis, and inside parentheses the
 * FROM, AS or comma after it.
 */
public class SqlFunctionBareCallLinesTest extends MeasuredCellsTestSupport {

    /** Table function bodies, one parenthesis open. */
    @Test
    public void aTableFunctionsQueryIsReadOnThroughTheCall() {
        engine.execute("CREATE OR REPLACE TABLE ft (a INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]'))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 27 unexpected 'INPUT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value FROM "
                + "FLATTEN(PARSE_JSON('[1]'))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 27 unexpected 'PARSE_JSON'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT * FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]'))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 10 unexpected 'FROM'. | syntax error line 1 "
                + "at position 23 unexpected 'INPUT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]'), OUTER => TRUE)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 27 unexpected 'INPUT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT seq4() AS value FROM "
                + "GENERATOR(ROWCOUNT => 3)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value FROM SPLIT_TO_TABLE('a,b', "
                + "',')$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 34 unexpected ''a,b''."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT v.value FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')) v$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 29 unexpected 'INPUT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value FROM FLATTEN(input => "
                + "PARSE_JSON('[1]'))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 27 unexpected 'input'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value FROM "
                + "FLATTEN(INPUT=>PARSE_JSON('[1]'))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 27 unexpected 'INPUT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value FROM LATERAL FLATTEN(INPUT "
                + "=> PARSE_JSON('[1]'))$$",
                "Function FA successfully created."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value FROM ft, FLATTEN(INPUT => "
                + "ft.a)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')) WHERE value > 0$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 27 unexpected 'INPUT'. | syntax error line 1 "
                + "at position 70 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value\nFROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]'))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 2 at position 13 unexpected 'INPUT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$  SELECT value FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]'))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 3 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 29 unexpected 'INPUT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]'), PATH => 'x')$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 27 unexpected 'INPUT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value FROM FLATTEN(PATH => 'x', "
                + "INPUT => PARSE_JSON('[1]'))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 27 unexpected 'PATH'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value FROM my_udtf(1)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 27 unexpected '1'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')) AS f(seq, key, path, index, value, this)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 27 unexpected 'INPUT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT MAX(value) FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]'))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected 'value'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT COUNT(*) FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]'))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected '*'. | syntax error line 1 at "
                + "position 15 unexpected ')'. | syntax error line 1 at position 30 unexpected 'INPUT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT ABS(1) AS v FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]'))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected '1'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value, 1 FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]'))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 17 unexpected 'FROM'. | syntax error line 1 "
                + "at position 30 unexpected 'INPUT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value AS v FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]'))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 32 unexpected 'INPUT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT seq4() FROM GENERATOR(ROWCOUNT => "
                + "3)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT 1 FROM GENERATOR(ROWCOUNT => 3)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 25 unexpected 'ROWCOUNT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT * FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')) WHERE 1 = 1$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 10 unexpected 'FROM'. | syntax error line 1 "
                + "at position 23 unexpected 'INPUT'. | syntax error line 1 at position 62 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')) f$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 27 unexpected 'INPUT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT MAX(value) FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')) WHERE 1 = 1$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected 'value'. | syntax error line 1 "
                + "at position 71 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value + 1 FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]'))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 18 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT f.value FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')) f WHERE f.value > 0$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 29 unexpected 'INPUT'. | syntax error line 1 "
                + "at position 76 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value FROM ft JOIN FLATTEN(INPUT "
                + "=> PARSE_JSON('[1]')) ON TRUE$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 35 unexpected 'INPUT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT COUNT(DISTINCT value) FROM "
                + "FLATTEN(INPUT => PARSE_JSON('[1]'))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected 'DISTINCT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT * FROM GENERATOR(ROWCOUNT => 3)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 10 unexpected 'FROM'. | syntax error line 1 "
                + "at position 25 unexpected 'ROWCOUNT'."},
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT value FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')) ORDER BY 1$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 27 unexpected 'INPUT'."},
        });
    }

    /** Scalar function bodies, in one, two or three parentheses. */
    @Test
    public void aScalarFunctionsQueryIsReadOnByItsParentheses() {
        engine.execute("CREATE OR REPLACE TABLE ft (a INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT COUNT(*) FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 15 unexpected '*'. | syntax error line 1 at "
                + "position 16 unexpected ')'. | syntax error line 1 at position 18 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT MAX(a) FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected 'a'. | syntax error line 1 at "
                + "position 16 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT a FROM FLATTEN(INPUT => PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 24 unexpected 'INPUT'. | syntax error line 1 "
                + "at position 50 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT COUNT(x) FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 15 unexpected 'x'. | syntax error line 1 at "
                + "position 18 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT 1 FROM FLATTEN(INPUT => PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 24 unexpected 'INPUT'. | syntax error line 1 "
                + "at position 50 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT COUNT(*) FROM GENERATOR(ROWCOUNT => 3))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 15 unexpected '*'. | syntax error line 1 at "
                + "position 16 unexpected ')'. | syntax error line 1 at position 18 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$1 + (SELECT COUNT(*) FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 6 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 19 unexpected '*'. | syntax error line 1 at "
                + "position 20 unexpected ')'. | syntax error line 1 at position 22 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT COUNT(*) FROM FLATTEN(PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 15 unexpected '*'. | syntax error line 1 at "
                + "position 16 unexpected ')'. | syntax error line 1 at position 18 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$SELECT COUNT(*) FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]'))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected '*'. | syntax error line 1 at "
                + "position 15 unexpected ')'. | syntax error line 1 at position 30 unexpected 'INPUT'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT COUNT(*) AS n FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 15 unexpected '*'. | syntax error line 1 at "
                + "position 16 unexpected ')'. | syntax error line 1 at position 18 unexpected 'AS'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT COUNT(*), 1 FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 15 unexpected '*'. | syntax error line 1 at "
                + "position 16 unexpected ')'. | syntax error line 1 at position 17 unexpected ','."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT SUM(a) FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected 'a'. | syntax error line 1 at "
                + "position 16 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT COUNT(DISTINCT a) FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 15 unexpected 'DISTINCT'. | syntax error "
                + "line 1 at position 27 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT ABS(1) FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected '1'. | syntax error line 1 at "
                + "position 16 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT COUNT(*) FROM ft)$$",
                "Function FS successfully created."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT COUNT(*) FROM ft, FLATTEN(INPUT => ft.a))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 15 unexpected '*'. | syntax error line 1 at "
                + "position 16 unexpected ')'. | syntax error line 1 at position 18 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT MAX(a) FROM SPLIT_TO_TABLE('a,b', ','))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected 'a'. | syntax error line 1 at "
                + "position 16 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$( SELECT COUNT(*) FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')) )$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 3 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 16 unexpected '*'. | syntax error line 1 at "
                + "position 17 unexpected ')'. | syntax error line 1 at position 19 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT COUNT(*)\nFROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 15 unexpected '*'. | syntax error line 1 at "
                + "position 16 unexpected ')'. | syntax error line 2 at position 0 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$((SELECT COUNT(*) FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]'))))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 3 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 16 unexpected '*'. | syntax error line 1 at "
                + "position 17 unexpected ')'. | syntax error line 1 at position 19 unexpected 'FROM'. | syntax error "
                + "line 1 at position 32 unexpected 'INPUT'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT * FROM FLATTEN(INPUT => PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 11 unexpected 'FROM'. | syntax error line 1 "
                + "at position 24 unexpected 'INPUT'. | syntax error line 1 at position 50 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT a FROM FLATTEN(INPUT => PARSE_JSON('[1]')) "
                + "WHERE a > 0)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 24 unexpected 'INPUT'. | syntax error line 1 "
                + "at position 50 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT a FROM ft, FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT a, 1 FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected 'FROM'. | syntax error line 1 "
                + "at position 27 unexpected 'INPUT'. | syntax error line 1 at position 53 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT seq4() FROM GENERATOR(ROWCOUNT => 3))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 31 unexpected 'ROWCOUNT'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT MAX(a) + 1 FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected 'a'. | syntax error line 1 at "
                + "position 20 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT COUNT(*) FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')) WHERE 1 = 1)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 15 unexpected '*'. | syntax error line 1 at "
                + "position 16 unexpected ')'. | syntax error line 1 at position 18 unexpected 'FROM'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$SELECT MAX(a) FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]'))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected 'a'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$SELECT a FROM FLATTEN(INPUT => PARSE_JSON('[1]'))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 23 unexpected 'INPUT'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$SELECT * FROM FLATTEN(INPUT => PARSE_JSON('[1]'))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 10 unexpected 'FROM'. | syntax error line 1 "
                + "at position 23 unexpected 'INPUT'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$((SELECT a FROM FLATTEN(INPUT => PARSE_JSON('[1]'))))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 3 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 25 unexpected 'INPUT'. | syntax error line 1 "
                + "at position 52 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$((SELECT MAX(a) FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]'))))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 3 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 14 unexpected 'a'. | syntax error line 1 at "
                + "position 17 unexpected 'FROM'. | syntax error line 1 at position 30 unexpected 'INPUT'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$((SELECT * FROM FLATTEN(INPUT => PARSE_JSON('[1]'))))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 3 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 12 unexpected 'FROM'. | syntax error line 1 "
                + "at position 25 unexpected 'INPUT'. | syntax error line 1 at position 52 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT a AS b FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 29 unexpected 'INPUT'. | syntax error line 1 "
                + "at position 55 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT a.b FROM FLATTEN(INPUT => PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 26 unexpected 'INPUT'. | syntax error line 1 "
                + "at position 52 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT 'x' FROM FLATTEN(INPUT => PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 26 unexpected 'INPUT'. | syntax error line 1 "
                + "at position 52 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT a FROM FLATTEN(INPUT => PARSE_JSON('[1]')) f)$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 24 unexpected 'INPUT'. | syntax error line 1 "
                + "at position 50 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$SELECT a FROM FLATTEN(INPUT => PARSE_JSON('[1]')) WHERE "
                + "a > 0$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 23 unexpected 'INPUT'. | syntax error line 1 "
                + "at position 62 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION fs() RETURNS INT AS $$(SELECT MAX(a), 1 FROM FLATTEN(INPUT => "
                + "PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 13 unexpected 'a'. | syntax error line 1 at "
                + "position 15 unexpected ','."},
        });
    }
}
