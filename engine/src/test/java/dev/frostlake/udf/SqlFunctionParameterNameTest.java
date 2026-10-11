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
 * What a SQL function's parameter stands for in its body: a value, read only where a column could be read by its
 * bare name. A function, a table, a common table expression, an alias and a list of column names spelled like
 * the parameter stay what they are - {@code upper(upper)} calls UPPER over the argument, {@code FROM people}
 * reads the table for a parameter named people - as do a named argument's name, a path's field, the unit of an
 * EXTRACT, a type and a name written with its qualifier. Wherever a name is written bare, the parameter comes
 * before a column of its name: in a select list, a WHERE, an aggregate, a join's ON, a QUALIFY and a subquery,
 * so {@code MAX(id)} over a parameter id is the argument. A parameter in a GROUP BY or an ORDER BY is its value,
 * which a number names a position with. A parameter is named in upper case however it was declared: a quoted
 * {@code "X"} reads the parameter x and a quoted {@code "x"} is no name of the body. Every parameter is bound
 * at once, so one named like a word of another's value - number, date, varchar - changes nothing of it. Every
 * answer is live-verified.
 */
public class SqlFunctionParameterNameTest extends MeasuredCellsTestSupport {

    @Test
    public void functionsTablesAliasesAndColumns() {
        assertAnswer(
            "Function C_FN successfully created.",
            "CREATE OR REPLACE FUNCTION c_fn(upper VARCHAR) RETURNS VARCHAR AS 'upper(upper)'");
        assertAnswer(
            "ABC",
            "SELECT c_fn('abc')");
        assertAnswer(
            "Table PEOPLE successfully created.",
            "CREATE OR REPLACE TABLE people (id NUMBER, name VARCHAR)");
        assertAnswer(
            "2",
            "INSERT INTO people VALUES (1, 'ann'), (2, 'bob')");
        assertAnswer(
            "Function C_TBL successfully created.",
            "CREATE OR REPLACE FUNCTION c_tbl(people NUMBER) RETURNS NUMBER AS 'SELECT COUNT(*) FROM people'");
        assertAnswer(
            "2",
            "SELECT c_tbl(1)");
        assertAnswer(
            "Function C_CTE successfully created.",
            "CREATE OR REPLACE FUNCTION c_cte(c NUMBER) RETURNS NUMBER AS 'WITH c AS (SELECT 5 AS z) SELECT z + 0 "
                + "FROM c'");
        assertAnswer(
            "5",
            "SELECT c_cte(1)");
        assertAnswer(
            "Function C_ALIAS successfully created.",
            "CREATE OR REPLACE FUNCTION c_alias(v NUMBER) RETURNS NUMBER AS 'SELECT w FROM (SELECT 10 AS v, 20 AS "
                + "w)'");
        assertAnswer(
            "20",
            "SELECT c_alias(1)");
        assertAnswer(
            "Function C_NAMED successfully created.",
            "CREATE OR REPLACE FUNCTION c_named(input VARCHAR) RETURNS NUMBER AS $$ SELECT COUNT(*) FROM "
                + "TABLE(FLATTEN(input => PARSE_JSON(input))) $$");
        assertAnswer(
            "3",
            "SELECT c_named('[1,2,3]')");
        assertAnswer(
            "Function C_COL successfully created.",
            "CREATE OR REPLACE FUNCTION c_col(name VARCHAR) RETURNS NUMBER AS 'SELECT COUNT(*) FROM people WHERE "
                + "people.name = name'");
        assertAnswer(
            "1",
            "SELECT c_col('ann')");
        assertAnswer(
            "Function C_COL2 successfully created.",
            "CREATE OR REPLACE FUNCTION c_col2(id NUMBER) RETURNS NUMBER AS 'SELECT MAX(id) FROM people'");
        assertAnswer(
            "99",
            "SELECT c_col2(99)");
        assertAnswer(
            "Function C_COL3 successfully created.",
            "CREATE OR REPLACE FUNCTION c_col3(id NUMBER) RETURNS NUMBER AS 'SELECT COUNT(*) FROM people WHERE id "
                + "= 1'");
        assertAnswer(
            "2|0",
            "SELECT c_col3(1), c_col3(7)");
        assertAnswer(
            "Function C_COL4 successfully created.",
            "CREATE OR REPLACE FUNCTION c_col4(id NUMBER) RETURNS TABLE (x NUMBER) AS 'SELECT id FROM people'");
        assertAnswer(
            "99 ;; 99",
            "SELECT x FROM TABLE(c_col4(99)) ORDER BY 1");
        assertAnswer(
            "Function C_SEL successfully created.",
            "CREATE OR REPLACE FUNCTION c_sel(v NUMBER) RETURNS NUMBER AS 'SELECT v FROM (SELECT 10 AS v)'");
        assertAnswer(
            "1",
            "SELECT c_sel(1)");
        assertAnswer(
            "Function C_AS successfully created.",
            "CREATE OR REPLACE FUNCTION c_as(v NUMBER) RETURNS NUMBER AS 'SELECT 5 AS v'");
        assertAnswer(
            "5",
            "SELECT c_as(1)");
        assertAnswer(
            "Function C_TAL successfully created.",
            "CREATE OR REPLACE FUNCTION c_tal(p NUMBER) RETURNS NUMBER AS 'SELECT COUNT(*) FROM people p WHERE "
                + "p.id > 0'");
        assertAnswer(
            "2",
            "SELECT c_tal(1)");
        assertAnswer(
            "Function C_TAL2 successfully created.",
            "CREATE OR REPLACE FUNCTION c_tal2(p NUMBER) RETURNS NUMBER AS 'SELECT COUNT(*) FROM people p WHERE "
                + "p.id > p'");
        assertAnswer(
            "1",
            "SELECT c_tal2(1)");
        assertAnswer(
            "Function C_ORD successfully created.",
            "CREATE OR REPLACE FUNCTION c_ord(name VARCHAR) RETURNS VARCHAR AS 'SELECT MIN(name) FROM people'");
        assertAnswer(
            "zed",
            "SELECT c_ord('zed')");
        assertAnswer(
            "Function C_LEFT successfully created.",
            "CREATE OR REPLACE FUNCTION c_left(left VARCHAR) RETURNS VARCHAR AS 'left(left, 2)'");
        assertAnswer(
            "ab",
            "SELECT c_left('abcdef')");
        assertAnswer(
            "Function C_EXT successfully created.",
            "CREATE OR REPLACE FUNCTION c_ext(year DATE) RETURNS NUMBER AS 'EXTRACT(year FROM year)'");
        assertAnswer(
            "2024",
            "SELECT c_ext('2024-02-01'::DATE)");
        assertAnswer(
            "Function C_KEY successfully created.",
            "CREATE OR REPLACE FUNCTION c_key(name VARCHAR) RETURNS VARCHAR AS 'OBJECT_CONSTRUCT(''name'', "
                + "name):name::VARCHAR'");
        assertAnswer(
            "ann",
            "SELECT c_key('ann')");
        assertAnswer(
            "Function C_OBJ successfully created.",
            "CREATE OR REPLACE FUNCTION c_obj(k VARCHAR) RETURNS VARCHAR AS 'TO_JSON({''k'': k})'");
        assertAnswer(
            "{\"k\":\"v\"}",
            "SELECT c_obj('v')");
        assertAnswer(
            "Function C_GRP successfully created.",
            "CREATE OR REPLACE FUNCTION c_grp(g NUMBER) RETURNS NUMBER AS 'SELECT COUNT(*) FROM (SELECT id AS g "
                + "FROM people GROUP BY g)'");
        assertAnswer(
            "REFUSED SQL compilation error: | [5] is not a valid group by expression",
            "SELECT c_grp(5)");
        assertAnswer(
            "Function C_TYP successfully created.",
            "CREATE OR REPLACE FUNCTION c_typ(number VARCHAR) RETURNS VARCHAR AS 'CAST(number AS VARCHAR) || "
                + "''!'''");
        assertAnswer(
            "n!",
            "SELECT c_typ('n')");
        assertAnswer(
            "Function C_TNM successfully created.",
            "CREATE OR REPLACE FUNCTION c_tnm(people NUMBER) RETURNS TABLE (n NUMBER) AS 'SELECT COUNT(*) FROM "
                + "people'");
        assertAnswer(
            "2",
            "SELECT n FROM TABLE(c_tnm(1))");
        assertAnswer(
            "Function C_TNA successfully created.",
            "CREATE OR REPLACE FUNCTION c_tna(input VARCHAR) RETURNS TABLE (n NUMBER) AS 'SELECT COUNT(*) FROM "
                + "TABLE(FLATTEN(input => PARSE_JSON(input)))'");
        assertAnswer(
            "2",
            "SELECT n FROM TABLE(c_tna('[1,2]'))");
        assertAnswer(
            "Function C_TWICE successfully created.",
            "CREATE OR REPLACE FUNCTION c_twice(x NUMBER) RETURNS NUMBER AS 'x + (SELECT MAX(x) FROM (SELECT 3 AS "
                + "x))'");
        assertAnswer(
            "20",
            "SELECT c_twice(10)");
    }

    @Test
    public void positionsListsPathsAndNestedQueries() {
        assertAnswer(
            "Table PEOPLE successfully created.",
            "CREATE OR REPLACE TABLE people (id NUMBER, name VARCHAR)");
        assertAnswer(
            "2",
            "INSERT INTO people VALUES (1, 'ann'), (2, 'bob')");
        assertAnswer(
            "REFUSED SQL compilation error: | [5] is not a valid group by expression",
            "SELECT COUNT(*) FROM people GROUP BY 5");
        assertAnswer(
            "Function C_GRP3 successfully created.",
            "CREATE OR REPLACE FUNCTION c_grp3(g NUMBER) RETURNS NUMBER AS 'SELECT COUNT(*) FROM (SELECT id FROM "
                + "people GROUP BY g)'");
        assertAnswer(
            "2",
            "SELECT c_grp3(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | [2] is not a valid group by expression",
            "SELECT c_grp3(2)");
        assertAnswer(
            "Function C_ORDB successfully created.",
            "CREATE OR REPLACE FUNCTION c_ordb(o NUMBER) RETURNS VARCHAR AS 'SELECT MIN(name) FROM (SELECT name "
                + "FROM people ORDER BY o DESC LIMIT 1)'");
        assertAnswer(
            "bob",
            "SELECT c_ordb(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | [2] is not a valid order by expression",
            "SELECT c_ordb(2)");
        assertAnswer(
            "Function C_Q successfully created.",
            "CREATE OR REPLACE FUNCTION c_q(id NUMBER) RETURNS NUMBER AS 'SELECT MAX(people.id) FROM people'");
        assertAnswer(
            "2",
            "SELECT c_q(99)");
        assertAnswer(
            "Function C_Q2 successfully created.",
            "CREATE OR REPLACE FUNCTION c_q2(id NUMBER) RETURNS NUMBER AS 'SELECT MAX(p.id) FROM people p WHERE "
                + "p.id < id'");
        assertAnswer(
            "1",
            "SELECT c_q2(2)");
        assertAnswer(
            "Function C_AL2 successfully created.",
            "CREATE OR REPLACE FUNCTION c_al2(v NUMBER) RETURNS NUMBER AS 'SELECT v + 1 FROM (SELECT 10 AS v)'");
        assertAnswer(
            "2",
            "SELECT c_al2(1)");
        assertAnswer(
            "Function C_USING successfully created.",
            "CREATE OR REPLACE FUNCTION c_using(id NUMBER) RETURNS NUMBER AS 'SELECT COUNT(*) FROM people a JOIN "
                + "people b USING (id)'");
        assertAnswer(
            "2",
            "SELECT c_using(7)");
        assertAnswer(
            "Function C_LAT2 successfully created.",
            "CREATE OR REPLACE FUNCTION c_lat2(value NUMBER) RETURNS NUMBER AS 'SELECT SUM(value) FROM "
                + "TABLE(FLATTEN(INPUT => PARSE_JSON(''[1,2]'')))'");
        assertAnswer(
            "20",
            "SELECT c_lat2(10)");
        assertAnswer(
            "Function C_PATH successfully created.",
            "CREATE OR REPLACE FUNCTION c_path(k VARCHAR) RETURNS VARCHAR AS 'PARSE_JSON(''{\"k\": "
                + "\"inner\"}''):k::VARCHAR'");
        assertAnswer(
            "inner",
            "SELECT c_path('zz')");
        assertAnswer(
            "Function C_BRK successfully created.",
            "CREATE OR REPLACE FUNCTION c_brk(k VARCHAR) RETURNS VARCHAR AS 'PARSE_JSON(''{\"kk\": "
                + "\"inner\"}'')[k]::VARCHAR'");
        assertAnswer(
            "inner",
            "SELECT c_brk('kk')");
        assertAnswer(
            "Function C_EXC successfully created.",
            "CREATE OR REPLACE FUNCTION c_exc(name VARCHAR) RETURNS NUMBER AS 'SELECT COUNT(*) FROM (SELECT * "
                + "EXCLUDE (name) FROM people)'");
        assertAnswer(
            "2",
            "SELECT c_exc('zz')");
        assertAnswer(
            "Function C_DT successfully created.",
            "CREATE OR REPLACE FUNCTION c_dt(date VARCHAR) RETURNS DATE AS 'date::DATE'");
        assertAnswer(
            "2024-01-02",
            "SELECT c_dt('2024-01-02')");
        assertAnswer(
            "REFUSED Declared return type 'VARCHAR(134217728)' is incompatible with actual return type 'DATE'",
            "CREATE OR REPLACE FUNCTION c_cd(current_date VARCHAR) RETURNS VARCHAR AS 'current_date'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function C_CD.",
            "SELECT c_cd('p')");
        assertAnswer(
            "Function C_GEN successfully created.",
            "CREATE OR REPLACE FUNCTION c_gen(n NUMBER) RETURNS NUMBER AS 'SELECT COUNT(*) FROM "
                + "TABLE(GENERATOR(ROWCOUNT => n))'");
        assertAnswer(
            "3",
            "SELECT c_gen(3)");
        assertAnswer(
            "Function C_VAL successfully created.",
            "CREATE OR REPLACE FUNCTION c_val(v NUMBER) RETURNS NUMBER AS 'SELECT SUM(column1) FROM (VALUES (v), "
                + "(v + 1))'");
        assertAnswer(
            "7",
            "SELECT c_val(3)");
        assertAnswer(
            "Function C_CTECOL successfully created.",
            "CREATE OR REPLACE FUNCTION c_ctecol(z NUMBER) RETURNS NUMBER AS 'WITH q (z) AS (SELECT 5) SELECT "
                + "MAX(q.z) FROM q'");
        assertAnswer(
            "5",
            "SELECT c_ctecol(1)");
        assertAnswer(
            "Function C_CTECOL2 successfully created.",
            "CREATE OR REPLACE FUNCTION c_ctecol2(z NUMBER) RETURNS NUMBER AS 'WITH q (z) AS (SELECT 5) SELECT "
                + "MAX(z) FROM q'");
        assertAnswer(
            "1",
            "SELECT c_ctecol2(1)");
        assertAnswer(
            "Function C_TJ successfully created.",
            "CREATE OR REPLACE FUNCTION c_tj(a NUMBER) RETURNS TABLE (n NUMBER) AS 'SELECT a.id FROM people a "
                + "WHERE a.id = a'");
        assertAnswer(
            "2",
            "SELECT n FROM TABLE(c_tj(2))");
        assertAnswer(
            "Function C_PP successfully created.",
            "CREATE OR REPLACE FUNCTION c_pp(v VARIANT) RETURNS VARCHAR AS 'v:v::VARCHAR'");
        assertAnswer(
            "in",
            "SELECT c_pp(PARSE_JSON('{\"v\": \"in\"}'))");
        assertAnswer(
            "Function C_TWO successfully created.",
            "CREATE OR REPLACE FUNCTION c_two(number NUMBER, date DATE) RETURNS VARCHAR AS 'number || ''/'' || "
                + "date'");
        assertAnswer(
            "1.5/2024-01-02",
            "SELECT c_two(1.5, '2024-01-02'::DATE)");
        assertAnswer(
            "Function C_TWO2 successfully created.",
            "CREATE OR REPLACE FUNCTION c_two2(varchar VARCHAR, binary NUMBER) RETURNS VARCHAR AS 'varchar || "
                + "''/'' || binary'");
        assertAnswer(
            "null",
            "SELECT c_two2(NULL, 7)");
        assertAnswer(
            "Function C_TWO3 successfully created.",
            "CREATE OR REPLACE FUNCTION c_two3(a NUMBER, number NUMBER) RETURNS TABLE (v VARCHAR) AS 'SELECT a || "
                + "''/'' || number'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for function "
                + "'C_TWO3': (NUMBER(2,1), NUMBER(1,0))",
            "SELECT v FROM TABLE(c_two3(1.5, 2))");
        assertAnswer(
            "Function C_CASE successfully created.",
            "CREATE OR REPLACE FUNCTION c_case(x NUMBER) RETURNS VARCHAR AS 'CASE x WHEN 1 THEN ''one'' ELSE "
                + "''other'' END'");
        assertAnswer(
            "one",
            "SELECT c_case(1)");
        assertAnswer(
            "Function C_IN successfully created.",
            "CREATE OR REPLACE FUNCTION c_in(x NUMBER) RETURNS BOOLEAN AS 'x IN (SELECT id FROM people)'");
        assertAnswer(
            "true|false",
            "SELECT c_in(2), c_in(9)");
        assertAnswer(
            "Function C_Q3 successfully created.",
            "CREATE OR REPLACE FUNCTION c_q3(people NUMBER) RETURNS NUMBER AS 'SELECT MAX(people.id) + people "
                + "FROM people'");
        assertAnswer(
            "12",
            "SELECT c_q3(10)");
        assertAnswer(
            "Function C_INS successfully created.",
            "CREATE OR REPLACE FUNCTION c_ins(id NUMBER) RETURNS NUMBER AS 'SELECT COUNT(*) FROM people WHERE id "
                + "IN (id, 0)'");
        assertAnswer(
            "2",
            "SELECT c_ins(1)");
        assertAnswer(
            "Function C_ORD2 successfully created.",
            "CREATE OR REPLACE FUNCTION c_ord2(name VARCHAR) RETURNS VARCHAR AS 'SELECT LISTAGG(p.name, '','') "
                + "WITHIN GROUP (ORDER BY p.name DESC) FROM people p'");
        assertAnswer(
            "bob,ann",
            "SELECT c_ord2('zz')");
    }

    @Test
    public void wordsOfAnotherParametersValue() {
        assertAnswer(
            "Table PEOPLE successfully created.",
            "CREATE OR REPLACE TABLE people (id NUMBER, name VARCHAR)");
        assertAnswer(
            "2",
            "INSERT INTO people VALUES (1, 'ann'), (2, 'bob')");
        assertAnswer(
            "Function C_CHAIN successfully created.",
            "CREATE OR REPLACE FUNCTION c_chain(a NUMBER(10,2), number NUMBER) RETURNS VARCHAR AS 'a || ''/'' || "
                + "number'");
        assertAnswer(
            "1.00/2",
            "SELECT c_chain(1, 2)");
        assertAnswer(
            "Function C_CHAIN2 successfully created.",
            "CREATE OR REPLACE FUNCTION c_chain2(d DATE, date NUMBER) RETURNS VARCHAR AS 'd || ''/'' || date'");
        assertAnswer(
            "2024-01-02/5",
            "SELECT c_chain2('2024-01-02', 5)");
        assertAnswer(
            "Function C_CHAIN3 successfully created.",
            "CREATE OR REPLACE FUNCTION c_chain3(t TIMESTAMP_NTZ, timestamp_ntz VARCHAR) RETURNS VARCHAR AS 't || "
                + "''/'' || timestamp_ntz'");
        assertAnswer(
            "2024_01_02 03;04;05.000/w",
            "SELECT REPLACE(REPLACE(c_chain3('2024-01-02 03:04:05'::TIMESTAMP_NTZ, 'w'), '-', '_'), ':', ';')");
        assertAnswer(
            "Function C_CHAIN4 successfully created.",
            "CREATE OR REPLACE FUNCTION c_chain4(a VARCHAR, varchar NUMBER) RETURNS VARCHAR AS 'NVL(a, ''none'') "
                + "|| ''/'' || varchar'");
        assertAnswer(
            "none/7",
            "SELECT c_chain4(NULL, 7)");
        assertAnswer(
            "Function C_CHAIN5 successfully created.",
            "CREATE OR REPLACE FUNCTION c_chain5(a NUMBER(10,2), number NUMBER) RETURNS TABLE (v VARCHAR) AS "
                + "'SELECT a || ''/'' || number'");
        assertAnswer(
            "1.25/2",
            "SELECT v FROM TABLE(c_chain5(1.25, 2))");
        assertAnswer(
            "Function C_FN2 successfully created.",
            "CREATE OR REPLACE FUNCTION c_fn2(parse_json VARCHAR) RETURNS VARIANT AS 'parse_json(parse_json)'");
        assertAnswer(
            "[1]",
            "SELECT c_fn2('[1]')");
        assertAnswer(
            "Function C_QUO successfully created.",
            "CREATE OR REPLACE FUNCTION c_quo(x NUMBER) RETURNS NUMBER AS '\"X\" + 1'");
        assertAnswer(
            "2",
            "SELECT c_quo(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 1 | invalid identifier '\"x\"'",
            "CREATE OR REPLACE FUNCTION c_quo2(x NUMBER) RETURNS NUMBER AS '\"x\" + 1'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function C_QUO2.",
            "SELECT c_quo2(1)");
        assertAnswer(
            "Function C_ALIST successfully created.",
            "CREATE OR REPLACE FUNCTION c_alist(a NUMBER) RETURNS NUMBER AS 'SELECT MAX(t.a) FROM (SELECT 5) AS "
                + "t(a)'");
        assertAnswer(
            "5",
            "SELECT c_alist(1)");
        assertAnswer(
            "Function C_ALIST2 successfully created.",
            "CREATE OR REPLACE FUNCTION c_alist2(a NUMBER) RETURNS NUMBER AS 'SELECT MAX(a) FROM (SELECT 5) AS "
                + "t(a)'");
        assertAnswer(
            "1",
            "SELECT c_alist2(1)");
        assertAnswer(
            "Function C_WINP successfully created.",
            "CREATE OR REPLACE FUNCTION c_winp(p NUMBER) RETURNS NUMBER AS 'SELECT MAX(r) FROM (SELECT "
                + "ROW_NUMBER() OVER (PARTITION BY p ORDER BY id) AS r FROM people)'");
        assertAnswer(
            "2",
            "SELECT c_winp(1)");
        assertAnswer(
            "Function C_ON successfully created.",
            "CREATE OR REPLACE FUNCTION c_on(id NUMBER) RETURNS NUMBER AS 'SELECT COUNT(*) FROM people a JOIN "
                + "people b ON a.id = id'");
        assertAnswer(
            "2",
            "SELECT c_on(1)");
        assertAnswer(
            "Function C_ROOT successfully created.",
            "CREATE OR REPLACE FUNCTION c_root(v VARIANT) RETURNS VARCHAR AS 'v:a.v::VARCHAR'");
        assertAnswer(
            "deep",
            "SELECT c_root(PARSE_JSON('{\"a\": {\"v\": \"deep\"}}'))");
        assertAnswer(
            "Function C_FL successfully created.",
            "CREATE OR REPLACE FUNCTION c_fl(f VARCHAR) RETURNS NUMBER AS 'SELECT SUM(f.value::NUMBER) FROM "
                + "TABLE(FLATTEN(INPUT => PARSE_JSON(f))) f'");
        assertAnswer(
            "3",
            "SELECT c_fl('[1,2]')");
        assertAnswer(
            "Function C_SUB successfully created.",
            "CREATE OR REPLACE FUNCTION c_sub(id NUMBER) RETURNS NUMBER AS 'SELECT COUNT(*) FROM people o WHERE "
                + "EXISTS (SELECT 1 FROM people i WHERE i.id = id)'");
        assertAnswer(
            "2|0",
            "SELECT c_sub(1), c_sub(9)");
        assertAnswer(
            "Function C_STAR successfully created.",
            "CREATE OR REPLACE FUNCTION c_star(id NUMBER) RETURNS NUMBER AS 'SELECT COUNT(id) FROM (SELECT * FROM "
                + "people)'");
        assertAnswer(
            "0",
            "SELECT c_star(NULL)");
        assertAnswer(
            "Function C_POS successfully created.",
            "CREATE OR REPLACE FUNCTION c_pos(x NUMBER) RETURNS NUMBER AS 'SELECT $1 + x FROM (SELECT 5)'");
        assertAnswer(
            "6",
            "SELECT c_pos(1)");
        assertAnswer(
            "Function C_LIKE successfully created.",
            "CREATE OR REPLACE FUNCTION c_like(name VARCHAR) RETURNS NUMBER AS 'SELECT COUNT(*) FROM people p "
                + "WHERE p.name LIKE name || ''%'''");
        assertAnswer(
            "1",
            "SELECT c_like('a')");
        assertAnswer(
            "Function C_BTW successfully created.",
            "CREATE OR REPLACE FUNCTION c_btw(id NUMBER) RETURNS NUMBER AS 'SELECT COUNT(*) FROM people p WHERE "
                + "p.id BETWEEN id AND id + 1'");
        assertAnswer(
            "2|1",
            "SELECT c_btw(1), c_btw(2)");
    }
}
