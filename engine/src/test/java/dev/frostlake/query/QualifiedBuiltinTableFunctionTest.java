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
 * A built-in table function runs whatever schema its name is written under. A schema may hold a table
 * function named FLATTEN, SPLIT_TO_TABLE, GENERATOR, RESULT_SCAN or STRTOK_SPLIT_TO_TABLE - the account
 * creates it and lists it - and a call qualified with that schema, with its database too, or with a schema
 * that does not exist still runs the built-in: its columns, its argument names and its refusals. A scalar
 * function of a built-in's name is reached through its schema. A refusal of FLATTEN or SPLIT_TO_TABLE names
 * the call by the first part of its name as written - {@code [fq_s]}, {@code [flatten]}, {@code ["fq_n"]}
 * - and FLATTEN takes one to five arguments by position. Every answer is live-verified.
 */
public class QualifiedBuiltinTableFunctionTest extends MeasuredCellsTestSupport {

    @Test
    public void theBuiltInRunsUnderAnySchema() {
        try {
            assertAnswer(
                "Schema FQ_S successfully created.",
                "CREATE OR REPLACE SCHEMA fq_s");
            answer(
                "USE SCHEMA test_schema");
            assertAnswer(
                "Function FLATTEN successfully created.",
                "CREATE OR REPLACE FUNCTION fq_s.flatten(x VARIANT) RETURNS TABLE (v VARIANT) AS 'SELECT x'");
            assertAnswer(
                "1|null|[0]|0|1|[1,2] ;; 1|null|[1]|1|2|[1,2]",
                "SELECT * FROM TABLE(fq_s.flatten(PARSE_JSON('[1,2]')))");
            assertShape(
                "SEQ:NUMBER(38,0), KEY:VARCHAR(134217728), PATH:VARCHAR(134217728), INDEX:NUMBER(38,0), "
                    + "VALUE:VARIANT, THIS:VARIANT",
                "SELECT * FROM TABLE(fq_s.flatten(PARSE_JSON('[1,2]')))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 20 | invalid argument for function "
                    + "[fq_s] unexpected argument [X] at position 1,",
                "SELECT * FROM TABLE(fq_s.flatten(x => PARSE_JSON('[1,2]')))");
            assertAnswer(
                "1|null|[0]|0|1|[1,2] ;; 1|null|[1]|1|2|[1,2]",
                "SELECT * FROM TABLE(test_db.fq_s.flatten(PARSE_JSON('[1,2]')))");
            assertShape(
                "SEQ:NUMBER(38,0), KEY:VARCHAR(134217728), PATH:VARCHAR(134217728), INDEX:NUMBER(38,0), "
                    + "VALUE:VARIANT, THIS:VARIANT",
                "SELECT * FROM TABLE(flatten(PARSE_JSON('[1,2]')))");
            assertAnswer(
                "1|null|[0]|0|1|[1,2] ;; 1|null|[1]|1|2|[1,2]",
                "SELECT * FROM TABLE(fq_s.flatten(INPUT => PARSE_JSON('[1,2]')))");
            assertAnswer(
                "Function SPLIT_TO_TABLE successfully created.",
                "CREATE OR REPLACE FUNCTION fq_s.split_to_table(s VARCHAR, d VARCHAR) RETURNS TABLE (mine "
                    + "VARCHAR) AS 'SELECT ''mine:'' || s'");
            assertAnswer(
                "1|1|a ;; 1|2|b",
                "SELECT * FROM TABLE(fq_s.split_to_table('a,b', ','))");
            assertShape(
                "SEQ:NUMBER(38,0), INDEX:NUMBER(38,0), VALUE:VARCHAR(134217728)",
                "SELECT * FROM TABLE(fq_s.split_to_table('a,b', ','))");
            assertAnswer(
                "Function GENERATOR successfully created.",
                "CREATE OR REPLACE FUNCTION fq_s.generator(n INT) RETURNS TABLE (g INT) AS 'SELECT n'");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 0 | SELECT with no columns",
                "SELECT * FROM TABLE(fq_s.generator(7))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 0 | SELECT with no columns",
                "SELECT * FROM TABLE(fq_s.generator(n => 7))");
            assertAnswer(
                "2",
                "SELECT COUNT(*) FROM TABLE(fq_s.generator(ROWCOUNT => 2))");
            assertAnswer(
                "Function UPPER successfully created.",
                "CREATE OR REPLACE FUNCTION fq_s.upper(s VARCHAR) RETURNS VARCHAR AS '''mine'''");
            assertAnswer(
                "mine|A",
                "SELECT fq_s.upper('a') q, upper('a') u");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'T.V'",
                "SELECT t.v FROM (SELECT PARSE_JSON('[1,2]') AS j) s, TABLE(fq_s.flatten(s.j)) t");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'T.V'",
                "SELECT t.v FROM (SELECT PARSE_JSON('[1,2]') AS j) s, LATERAL fq_s.flatten(s.j) t");
            assertAnswer(
                "1 ;; 2",
                "SELECT t.value FROM (SELECT PARSE_JSON('[1,2]') AS j) s, LATERAL FLATTEN(s.j) t ORDER BY 1");
            assertAnswer(
                "Function RESULT_SCAN successfully created.",
                "CREATE OR REPLACE FUNCTION fq_s.result_scan(x VARCHAR) RETURNS TABLE (r VARCHAR) AS 'SELECT "
                    + "''mine:'' || x'");
            assertAnswer(
                "REFUSED Statement a not found",
                "SELECT * FROM TABLE(fq_s.result_scan('a'))");
            assertAnswer(
                "Function STRTOK_SPLIT_TO_TABLE successfully created.",
                "CREATE OR REPLACE FUNCTION fq_s.strtok_split_to_table(s VARCHAR) RETURNS TABLE (r VARCHAR) AS "
                    + "'SELECT ''mine:'' || s'");
            assertAnswer(
                "1|1|a ;; 1|2|b",
                "SELECT * FROM TABLE(fq_s.strtok_split_to_table('a b'))");
            assertAnswer(
                "Function FLATTEN successfully created.",
                "CREATE OR REPLACE FUNCTION flatten(x VARIANT) RETURNS TABLE (v VARIANT) AS 'SELECT x'");
            assertShape(
                "SEQ:NUMBER(38,0), KEY:VARCHAR(134217728), PATH:VARCHAR(134217728), INDEX:NUMBER(38,0), "
                    + "VALUE:VARIANT, THIS:VARIANT",
                "SELECT * FROM TABLE(flatten(PARSE_JSON('[1,2]')))");
            assertAnswer(
                "1|null|[0]|0|1|[1,2] ;; 1|null|[1]|1|2|[1,2]",
                "SELECT * FROM TABLE(test_schema.flatten(PARSE_JSON('[1,2]')))");
            assertAnswer(
                "1|null|[0]|0|1|[1,2] ;; 1|null|[1]|1|2|[1,2]",
                "SELECT * FROM TABLE(test_db.test_schema.flatten(PARSE_JSON('[1,2]')))");
            assertAnswer(
                "Statement executed successfully.",
                "USE SCHEMA fq_s");
            assertShape(
                "SEQ:NUMBER(38,0), KEY:VARCHAR(134217728), PATH:VARCHAR(134217728), INDEX:NUMBER(38,0), "
                    + "VALUE:VARIANT, THIS:VARIANT",
                "SELECT * FROM TABLE(flatten(PARSE_JSON('[1,2]')))");
            assertShape(
                "SEQ:NUMBER(38,0), INDEX:NUMBER(38,0), VALUE:VARCHAR(134217728)",
                "SELECT * FROM TABLE(split_to_table('a,b', ','))");
            assertAnswer(
                "A",
                "SELECT upper('a')");
            assertAnswer(
                "REFUSED Statement a not found",
                "SELECT * FROM TABLE(result_scan('a'))");
            answer(
                "USE SCHEMA test_schema");
            answer(
                "SHOW USER FUNCTIONS IN SCHEMA fq_s");
            assertAnswer(
                "FLATTEN|Y|FLATTEN(VARIANT) RETURN TABLE (V VARIANT) ;; GENERATOR|Y|GENERATOR(NUMBER) RETURN "
                    + "TABLE (G NUMBER) ;; RESULT_SCAN|Y|RESULT_SCAN(VARCHAR) RETURN TABLE (R VARCHAR) ;; "
                    + "SPLIT_TO_TABLE|Y|SPLIT_TO_TABLE(VARCHAR, VARCHAR) RETURN TABLE (MINE VARCHAR) ;; "
                    + "STRTOK_SPLIT_TO_TABLE|Y|STRTOK_SPLIT_TO_TABLE(VARCHAR) RETURN TABLE (R VARCHAR) ;; "
                    + "UPPER|N|UPPER(VARCHAR) RETURN VARCHAR",
                "SELECT \"name\", \"is_table_function\", \"arguments\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) "
                    + "ORDER BY 1");
            assertAnswer(
                "signature|(X VARIANT) ;; returns|TABLE (V VARIANT) ;; language|SQL ;; body|SELECT x",
                "DESCRIBE FUNCTION fq_s.flatten(VARIANT)");
            assertAnswer(
                "FLATTEN|TABLE (V VARIANT) ;; GENERATOR|TABLE (G NUMBER) ;; RESULT_SCAN|TABLE (R VARCHAR) ;; "
                    + "SPLIT_TO_TABLE|TABLE (MINE VARCHAR) ;; STRTOK_SPLIT_TO_TABLE|TABLE (R VARCHAR) ;; "
                    + "UPPER|VARCHAR(134217728)",
                "SELECT function_name, data_type FROM information_schema.functions WHERE function_schema = 'FQ_S' "
                    + "ORDER BY 1");
            assertAnswer(
                "REFUSED SQL compilation error: | Unknown user-defined table function FQ_S.NOSUCH",
                "SELECT * FROM TABLE(fq_s.nosuch(1))");
            assertAnswer(
                "1|null|[0]|0|1|[1,2] ;; 1|null|[1]|1|2|[1,2]",
                "SELECT * FROM TABLE(nosuch_schema.flatten(PARSE_JSON('[1,2]')))");
            assertAnswer(
                "1|null|[0]|0|1|[1,2] ;; 1|null|[1]|1|2|[1,2]",
                "SELECT * FROM TABLE(information_schema.flatten(PARSE_JSON('[1,2]')))");
            assertAnswer(
                "1|null|[0]|0|1|[1,2] ;; 1|null|[1]|1|2|[1,2]",
                "SELECT * FROM TABLE(snowflake.core.flatten(PARSE_JSON('[1,2]')))");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter '1'",
                "SELECT * FROM TABLE(fq_s.flatten('x'))");
            assertAnswer(
                "FLATTEN successfully dropped.",
                "DROP FUNCTION fq_s.flatten(VARIANT)");
            assertAnswer(
                "1|null|[0]|0|1|[1,2] ;; 1|null|[1]|1|2|[1,2]",
                "SELECT * FROM TABLE(fq_s.flatten(PARSE_JSON('[1,2]')))");
            assertAnswer(
                "FQ_S successfully dropped.",
                "DROP SCHEMA fq_s");
            assertAnswer(
                "TEST_SCHEMA",
                "SELECT CURRENT_SCHEMA()");
        } finally {
            answer(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void aRefusalNamesTheCallAsWritten() {
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | invalid argument for function [test_db] "
                + "unexpected argument [X] at position 1,",
            "SELECT * FROM TABLE(test_db.fq_n.flatten(x => 1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | invalid argument for function [fq_n] "
                + "unexpected argument [X] at position 1,",
            "SELECT * FROM TABLE(fq_n.flatten(x => 1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | invalid argument for function [flatten] "
                + "unexpected argument [X] at position 1,",
            "SELECT * FROM TABLE(flatten(x => 1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | invalid argument for function [FQ_N] "
                + "unexpected argument [X] at position 1,",
            "SELECT * FROM TABLE(FQ_N.FLATTEN(X => 1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | invalid argument for function "
                + "[\"fq_n\"] unexpected argument [X] at position 1,",
            "SELECT * FROM TABLE(\"fq_n\".flatten(x => 1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | invalid argument for function [fq_n] "
                + "unexpected argument [A] at position 1,",
            "SELECT * FROM TABLE(fq_n.split_to_table(a => 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | invalid argument for function "
                + "[split_to_table] unexpected argument [A] at position 1,",
            "SELECT * FROM TABLE(split_to_table(a => 'x'))");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM TABLE(fq_n.generator(nosuch => 1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | invalid argument for function [fq_n] "
                + "unexpected argument [X] at position 2,",
            "SELECT * FROM TABLE(fq_n.flatten(INPUT => PARSE_JSON('[1]'), x => 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter '1'",
            "SELECT * FROM TABLE(fq_n.flatten('x'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter '1'",
            "SELECT * FROM TABLE(flatten('x'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'INPUT'",
            "SELECT * FROM TABLE(fq_n.flatten(INPUT => 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'INPUT'",
            "SELECT * FROM TABLE(flatten(INPUT => 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter '1'",
            "SELECT * FROM TABLE(fq_n.split_to_table(1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter '1'",
            "SELECT * FROM TABLE(split_to_table(1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | not enough arguments for function "
                + "[fq_n], expected 1, got 0",
            "SELECT * FROM TABLE(fq_n.flatten())");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | not enough arguments for function "
                + "[flatten], expected 1, got 0",
            "SELECT * FROM TABLE(flatten())");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | too many arguments for function [fq_n] "
                + "expected 5, got 6",
            "SELECT * FROM TABLE(fq_n.flatten(PARSE_JSON('[1]'), 'a', TRUE, TRUE, 'BOTH', 6))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | too many arguments for function "
                + "[flatten] expected 5, got 6",
            "SELECT * FROM TABLE(flatten(PARSE_JSON('[1]'), 'a', TRUE, TRUE, 'BOTH', 6))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[SPLIT('a,b' AS \"1\")], expected 2, got 1",
            "SELECT * FROM TABLE(test_db.fq_n.split_to_table('a,b'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[SPLIT('a,b' AS \"1\")], expected 2, got 1",
            "SELECT * FROM TABLE(split_to_table('a,b'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | missing required argument [INPUT] for "
                + "function [fq_n]",
            "SELECT * FROM TABLE(fq_n.flatten(PATH => 'a'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | missing required argument [INPUT] for "
                + "function [flatten]",
            "SELECT * FROM TABLE(flatten(PATH => 'a'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'ROWCOUNT'",
            "SELECT COUNT(*) FROM TABLE(fq_n.generator(ROWCOUNT => 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'ROWCOUNT'",
            "SELECT COUNT(*) FROM TABLE(generator(ROWCOUNT => 'x'))");
        answer(
            "SELECT * FROM TABLE(a.b.c.flatten(PARSE_JSON('[1]')))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 57 | invalid argument for function [fq_n] "
                + "unexpected argument [X] at position 1,",
            "SELECT * FROM (SELECT PARSE_JSON('[1]') AS j) s, LATERAL fq_n.flatten(x => s.j)");
        assertAnswer(
            "[1]|1|null|[0]|0|1|[1]",
            "SELECT * FROM (SELECT PARSE_JSON('[1]') AS j) s, LATERAL fq_n.flatten(INPUT => s.j) f");
    }
}
