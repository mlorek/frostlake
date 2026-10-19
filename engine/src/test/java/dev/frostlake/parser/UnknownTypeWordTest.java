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
 * A word standing where a data type belongs that names none. The account's grammar takes any name there, and a
 * single bare word with parameters after it, and judges the names once the statement has parsed: a fault of
 * the syntax anywhere in the statement is told instead, and so is the width of a type that is known. A bare
 * word is then a name - the last part of a dotted one, upper-cased, a quoted one as written - and a word with
 * parameters is echoed as it was written, {@code Unsupported data type 'NoSuch(5, 2)'.}, in its turn among the
 * widths. A dotted or quoted name takes no parameters: the parenthesis after it is a syntax error. A column, a
 * cast, a routine's parameter and its return type are read alike. Every answer is live-verified.
 */
public class UnknownTypeWordTest extends MeasuredCellsTestSupport {

    @Test
    public void aWordInAColumnList() {
        answer(
            "CREATE OR REPLACE TABLE nf_p (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 30 | Invalid character length: 0. Must be "
                + "between 1 and 134,217,728.",
            "CREATE TABLE nf_p1 (v VARCHAR(0), n nosuchtype)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 44 | Invalid character length: 0. Must be "
                + "between 1 and 134,217,728.",
            "CREATE TABLE nf_p1 (n nosuchtype, v VARCHAR(0))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE TABLE nf_p1 (n nosuchtype)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCH'.",
            "CREATE TABLE nf_p1 (n x.nosuch)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5)'.",
            "CREATE TABLE nf_p1 (n nosuchtype(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuch'.",
            "CREATE TABLE nf_p1 (n \"nosuch\")");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '('.",
            "CREATE TABLE nf_p1 (n \"nosuch\"(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE TABLE nf_p1 (n nosuchtype, m othertype)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NoSuchType(5, 2)'.",
            "CREATE TABLE nf_q37 (n NoSuchType(5, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '('.",
            "CREATE TABLE nf_q38 (n x.nosuch(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'no such'.",
            "CREATE TABLE nf_q39 (n \"no such\")");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'y'.",
            "CREATE TABLE nf_q40 (n \"x\".\"y\")");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'VARCHAR'.",
            "CREATE TABLE nf_q41 (n \"VARCHAR\")");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'varchar'.",
            "CREATE TABLE nf_q42 (n \"varchar\")");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NoSuch'.",
            "CREATE TABLE nf_q43 (n \"NoSuch\")");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE TABLE nf_q44 (a ARRAY(nosuchtype))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE TABLE nf_q45 (a OBJECT(k nosuchtype))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE TABLE nf_q46 (a MAP(VARCHAR, nosuchtype))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuch(5)'.",
            "CREATE TABLE nf_q47 (a ARRAY(nosuch(5)))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuch'.",
            "CREATE TABLE nf_q48 (a ARRAY(\"nosuch\"))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCH'.",
            "CREATE TABLE nf_q49 (a ARRAY(x.nosuch))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '<EOF>'.",
            "CREATE TABLE nf_q50 (n nosuchtype) nosuchword");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ')'.",
            "CREATE TABLE nf_q51 (n nosuchtype,)");
        answer(
            "CREATE TABLE nf_q52 (n nosuchtype, v VARCHAR(0), m other)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 31 | Invalid character length: 0. Must be "
                + "between 1 and 134,217,728.",
            "CREATE TABLE nf_q53 (v VARCHAR(0), n nosuchtype, w VARCHAR(0))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5)'.",
            "CREATE TABLE nf_q54 (n nosuchtype(5), m othertype)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'othertype(5)'.",
            "CREATE TABLE nf_q55 (n nosuchtype, m othertype(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE TABLE nf_q56 (n nosuchtype, n INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE TABLE nf_q57 (n nosuchtype NOT NULL DEFAULT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 30 | Invalid number precision: 99. Must be "
                + "between 0 and 38.",
            "CREATE TABLE nf_q58 (n NUMBER(99), m nosuchtype)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 44 | Invalid number precision: 99. Must be "
                + "between 0 and 38.",
            "CREATE TABLE nf_q59 (n nosuchtype, m NUMBER(99))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 37 | Invalid timestamp scale: 12. Must be "
                + "between 0 and 9.",
            "CREATE TABLE nf_q60 (n TIMESTAMP_NTZ(12), m nosuchtype)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuch(5)'.",
            "ALTER TABLE nf_p ADD COLUMN y nosuch(5)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuch'.",
            "ALTER TABLE nf_p ADD COLUMN y \"nosuch\"");
        answer(
            "ALTER TABLE nf_p ADD COLUMN y nosuch, z other");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '('.",
            "ALTER TABLE nf_p ADD COLUMN y x.nosuch(5)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 38 | Invalid character length: 0. Must be "
                + "between 1 and 134,217,728.",
            "ALTER TABLE nf_p ADD COLUMN x VARCHAR(0), y nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 48 | Invalid character length: 0. Must be "
                + "between 1 and 134,217,728.",
            "ALTER TABLE nf_p ADD COLUMN y nosuch, x VARCHAR(0)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuch(5)'.",
            "ALTER TABLE nf_p ALTER COLUMN a SET DATA TYPE nosuch(5)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuch'.",
            "ALTER TABLE nf_p ALTER COLUMN a SET DATA TYPE \"nosuch\"");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCH'.",
            "ALTER TABLE nosuch_table ADD COLUMN y nosuch");
    }

    @Test
    public void aWordInACastOrARoutine() {
        answer(
            "CREATE OR REPLACE TABLE nf_p (a INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5)'.",
            "SELECT CAST(1 AS nosuchtype(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5)'.",
            "SELECT 1::nosuchtype(5)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "SELECT 1::nosuchtype");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "SELECT 1::NoSuchType");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuch'.",
            "SELECT 1::\"nosuch\"");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '5'.",
            "SELECT 1::\"nosuch\"(5)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '5'.",
            "SELECT 1::x.nosuch(5)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCH'.",
            "SELECT 1::x.nosuch");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuch'.",
            "SELECT CAST(1 AS \"nosuch\")");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '('. | syntax error "
                + "line 1 at position 26 unexpected '5'.",
            "SELECT CAST(1 AS x.nosuch(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5)'.",
            "SELECT TRY_CAST('1' AS nosuchtype(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "SELECT 1::nosuchtype, 2::othertype");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "SELECT 1::ARRAY(nosuchtype)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '<EOF>'.",
            "SELECT CAST(1 AS nosuchtype(5)), 1 +");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION nf_f(x nosuchtype) RETURNS INT AS '1'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5)'.",
            "CREATE OR REPLACE FUNCTION nf_f(x nosuchtype(5)) RETURNS INT AS '1'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5)'.",
            "CREATE OR REPLACE FUNCTION nf_f(x INT) RETURNS nosuchtype(5) AS '1'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuch'.",
            "CREATE OR REPLACE FUNCTION nf_f(x INT) RETURNS \"nosuch\" AS '1'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCHTYPE'.",
            "CREATE OR REPLACE FUNCTION nf_f(x INT) RETURNS nosuchtype AS '1'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5)'.",
            "CREATE OR REPLACE FUNCTION nf_f(x INT) RETURNS TABLE (a nosuchtype(5)) AS 'SELECT 1'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5)'.",
            "CREATE OR REPLACE PROCEDURE nf_pr(x nosuchtype(5)) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5)'.",
            "CREATE TABLE nf_r39 (n nosuchtype(5), v VARCHAR(0))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 31 | Invalid character length: 0. Must be "
                + "between 1 and 134,217,728.",
            "CREATE TABLE nf_r40 (v VARCHAR(0), n nosuchtype(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5)'.",
            "CREATE TABLE nf_r41 (n nosuchtype(5), m othertype(6))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuch'.",
            "CREATE TABLE nf_r42 (n \"nosuch\", m othertype)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCH'.",
            "CREATE TABLE nf_r43 (n x.nosuch, m \"other\")");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'othertype(5)'.",
            "CREATE TABLE nf_r44 (n \"nosuch\", m othertype(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'MAP with key type UNKNOWN'.",
            "CREATE TABLE nf_r45 (a MAP(nosuchtype, VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuchtype(5)'.",
            "CREATE TABLE nf_r46 (a OBJECT(k nosuchtype(5)))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'nosuch'.",
            "CREATE TABLE nf_r47 (a OBJECT(k \"nosuch\"))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'NOSUCH'.",
            "CREATE TABLE nf_r48 (a OBJECT(k x.nosuch))");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'othertype(5)'.",
            "CREATE TABLE nf_r49 (a ARRAY(nosuchtype), b othertype(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '('.",
            "CREATE TABLE nf_r50 (n nosuchtype) nosuchword (x)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'othertype(5)'.",
            "SELECT 1::\"nosuch\", 2::othertype(5)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'othertype(5)'.",
            "SELECT 1::othertype(5), 2::\"nosuch\"");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 25 | Invalid character length: 0. Must be "
                + "between 1 and 134,217,728.",
            "SELECT CAST(1 AS VARCHAR(0)), 2::nosuchtype");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 40 | Invalid character length: 0. Must be "
                + "between 1 and 134,217,728.",
            "SELECT 2::nosuchtype, CAST(1 AS VARCHAR(0))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '('. | syntax error "
                + "line 1 at position 26 unexpected '5'.",
            "SELECT CAST(1 AS \"nosuch\"(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '5'.",
            "SELECT 1::BOOLEAN(5)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '('. | syntax error "
                + "line 1 at position 25 unexpected '5'.",
            "SELECT CAST(1 AS BOOLEAN(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '('.",
            "CREATE TABLE nf_rb (b BOOLEAN(5))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('.",
            "CREATE TABLE nf_rc (b DATE(5), n nosuchtype)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '('.",
            "CREATE TABLE nf_rd (b VARIANT(5))");
    }
}
