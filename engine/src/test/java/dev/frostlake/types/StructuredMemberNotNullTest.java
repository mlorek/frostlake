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

package dev.frostlake.types;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The spellings of a structured routine signature and of the members a shape declares (live-verified). An ARRAY's
 * element and a MAP's value may be declared NOT NULL: {@code ARRAY(INT NOT NULL)} is a type of its own to
 * SYSTEM$TYPEOF and to a routine's written signature, though a call binds it whatever the mark; a cast refuses a NULL
 * element ({@code NULL result in a non-nullable column}); a table refuses the type ({@code DDL operation failed
 * because it would result in a non-nullable structured type field 'A.element' contained within an array or map});
 * and ARRAY_COMPACT and OBJECT_KEYS answer an ARRAY whose element is never NULL. A VARCHAR written with no width
 * inside a routine's or a policy's shape reads bare in DESCRIBE and GET_DDL and at 134217728 in INFORMATION_SCHEMA,
 * and a driver reads a MAP result column as an OBJECT.
 */
public class StructuredMemberNotNullTest extends MeasuredCellsTestSupport {

    @Test
    public void anArrayElementAndAMapValueTakeNotNull() {
        engine.execute("CREATE OR REPLACE FUNCTION g5(x INT) RETURNS OBJECT(a INT, b VARCHAR) AS $$ {'a': x, 'b': "
            + "'q'}::OBJECT(a INT, b VARCHAR) $$");
        engine.execute("CREATE OR REPLACE FUNCTION g6(x INT) RETURNS ARRAY(INT) AS $$ [x]::ARRAY(INT) $$");
        engine.execute("CREATE OR REPLACE FUNCTION g7(x INT) RETURNS MAP(VARCHAR, NUMBER) AS $$ {'k': "
            + "x}::MAP(VARCHAR, NUMBER) $$");
        engine.execute("CREATE OR REPLACE FUNCTION f5(x OBJECT(a ARRAY(INT), b OBJECT(c VARCHAR))) RETURNS VARCHAR "
            + "AS $$ 'f5' $$");
        engine.execute("CREATE OR REPLACE FUNCTION f7(x OBJECT(a INT NOT NULL, b VARCHAR)) RETURNS VARCHAR AS $$ "
            + "'f7' $$");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION f8(x ARRAY(INT NOT NULL)) RETURNS VARCHAR AS $$ 'f8' $$",
                "Function F8 successfully created."},
            {"DESCRIBE FUNCTION f8(ARRAY(INT NOT NULL))",
                "signature|(X ARRAY(NUMBER NOT NULL)) ;; returns|VARCHAR ;; language|SQL ;; body| 'f8' "},
            {"DESCRIBE FUNCTION f8(ARRAY(INT))",
                "REFUSED SQL compilation error: | Function 'TEST_DB.TEST_SCHEMA.F8' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FUNCTION "
                + "TEST_DB.TEST_SCHEMA.F8."},
            {"SELECT f8([1]::ARRAY(INT NOT NULL))", "f8"},
            {"SELECT f8([1]::ARRAY(INT))", "f8"},
            {"SELECT SYSTEM$TYPEOF([1]::ARRAY(INT NOT NULL))", "ARRAY(NUMBER(38,0) NOT NULL)[LOB]"},
            {"SELECT [1, NULL]::ARRAY(INT NOT NULL)", "REFUSED NULL result in a non-nullable column"},
            {"SELECT SYSTEM$TYPEOF(ARRAY_COMPACT([1,NULL]::ARRAY(INT)))", "ARRAY(NUMBER(38,0) NOT NULL)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(OBJECT_KEYS({'a':1,'b':'x'}::OBJECT(a INT, b VARCHAR)))",
                "ARRAY(VARCHAR NOT NULL)[LOB]"},
            {"DESCRIBE FUNCTION g5(INT)",
                "signature|(X NUMBER) ;; returns|OBJECT(a NUMBER(38,0), b VARCHAR) ;; language|SQL ;; body| {'a': "
                + "x, 'b': 'q'}::OBJECT(a INT, b VARCHAR) "},
            {"DESCRIBE FUNCTION g6(INT)",
                "signature|(X NUMBER) ;; returns|ARRAY(NUMBER(38,0)) ;; language|SQL ;; body| [x]::ARRAY(INT) "},
            {"DESCRIBE FUNCTION g7(INT)",
                "signature|(X NUMBER) ;; returns|MAP(VARCHAR, NUMBER(38,0)) ;; language|SQL ;; body| {'k': "
                + "x}::MAP(VARCHAR, NUMBER) "},
            {"SELECT function_name, argument_signature, data_type FROM information_schema.functions WHERE "
                + "function_schema = 'TEST_SCHEMA' AND function_name IN ('G5', 'G6', 'G7', 'F5', 'F7', 'F8') ORDER "
                + "BY 1",
                "F5|(X OBJECT(a ARRAY(NUMBER), b OBJECT(c VARCHAR)))|VARCHAR(134217728) ;; F7|(X OBJECT(a NUMBER "
                + "NOT NULL, b VARCHAR))|VARCHAR(134217728) ;; F8|(X ARRAY(NUMBER NOT NULL))|VARCHAR(134217728) ;; "
                + "G5|(X NUMBER)|OBJECT(a NUMBER(38,0), b VARCHAR(134217728)) ;; G6|(X NUMBER)|ARRAY(NUMBER(38,0)) "
                + ";; G7|(X NUMBER)|MAP(VARCHAR(134217728), NUMBER(38,0))"},
            {"SELECT GET_DDL('FUNCTION', 'f5(OBJECT(a ARRAY(INT), b OBJECT(c VARCHAR)))')",
                "CREATE OR REPLACE FUNCTION \"F5\"(\"X\" OBJECT(a ARRAY(NUMBER(38,0)), b OBJECT(c VARCHAR))) | "
                + "RETURNS VARCHAR | LANGUAGE SQL | AS ' ''f5'' ';"},
            {"SELECT GET_DDL('FUNCTION', 'f7(OBJECT(a INT NOT NULL, b VARCHAR))')",
                "CREATE OR REPLACE FUNCTION \"F7\"(\"X\" OBJECT(a NUMBER(38,0) NOT NULL, b VARCHAR)) | RETURNS "
                + "VARCHAR | LANGUAGE SQL | AS ' ''f7'' ';"},
            {"SELECT GET_DDL('FUNCTION', 'g7(INT)')",
                "CREATE OR REPLACE FUNCTION \"G7\"(\"X\" NUMBER(38,0)) | RETURNS MAP(VARCHAR, NUMBER(38,0)) | "
                + "LANGUAGE SQL | AS ' {''k'': x}::MAP(VARCHAR, NUMBER) ';"},
        });
        assertShape("A:OBJECT, B:ARRAY, C:OBJECT", "SELECT g5(1) a, g6(1) b, g7(1) c");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE ta (a ARRAY(INT NOT NULL), o OBJECT(c VARCHAR), m MAP(VARCHAR, VARCHAR))",
                "REFUSED SQL compilation error: DDL operation failed because it would result in a non-nullable "
                + "structured type field 'A.element' contained within an array or map"},
            {"DESCRIBE TABLE ta",
                "REFUSED SQL compilation error: | Table 'TA' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE TA."},
            {"INSERT INTO ta SELECT [1, NULL]::ARRAY(INT NOT NULL), NULL, NULL",
                "REFUSED SQL compilation error: | Table 'TA' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE TA."},
            {"INSERT INTO ta SELECT [1, 2], {'c': 'x'}, {'k': 'v'}",
                "REFUSED SQL compilation error: | Table 'TA' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE TA."},
            {"SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(o), SYSTEM$TYPEOF(m) FROM ta",
                "REFUSED SQL compilation error: | Object 'TA' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE TA."},
            {"SELECT GET_DDL('TABLE', 'ta')",
                "REFUSED SQL compilation error: | Table 'TA' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE TA."},
            {"CREATE OR REPLACE MASKING POLICY mp_o AS (val OBJECT(a NUMBER, b VARCHAR)) RETURNS OBJECT(a "
                + "NUMBER, b VARCHAR) -> val",
                "Masking policy MP_O successfully created."},
            {"CREATE OR REPLACE MASKING POLICY mp_m AS (val MAP(VARCHAR, NUMBER)) RETURNS MAP(VARCHAR, NUMBER) -> val",
                "Masking policy MP_M successfully created."},
            {"DESCRIBE MASKING POLICY mp_o",
                "MP_O|(VAL OBJECT(a NUMBER, b VARCHAR))|OBJECT(a NUMBER(38,0), b VARCHAR)|val"},
            {"DESCRIBE MASKING POLICY mp_m", "MP_M|(VAL MAP(VARCHAR, NUMBER))|MAP(VARCHAR, NUMBER(38,0))|val"},
            {"SELECT GET_DDL('POLICY', 'mp_o')",
                "create or replace masking policy MP_O as (VAL OBJECT(a NUMBER(38,0), b VARCHAR))  | returns "
                + "OBJECT(a NUMBER(38,0), b VARCHAR) -> | val | ;"},
            {"CREATE OR REPLACE FUNCTION f9(x ARRAY(VARCHAR NOT NULL)) RETURNS ARRAY(NUMBER NOT NULL) AS $$ "
                + "[1]::ARRAY(NUMBER NOT NULL) $$",
                "Function F9 successfully created."},
            {"DESCRIBE FUNCTION f9(ARRAY(VARCHAR NOT NULL))",
                "signature|(X ARRAY(VARCHAR NOT NULL)) ;; returns|ARRAY(NUMBER(38,0) NOT NULL) ;; language|SQL ;; "
                + "body| [1]::ARRAY(NUMBER NOT NULL) "},
            {"SELECT GET_DDL('FUNCTION', 'f9(ARRAY(VARCHAR NOT NULL))')",
                "CREATE OR REPLACE FUNCTION \"F9\"(\"X\" ARRAY(VARCHAR NOT NULL)) | RETURNS ARRAY(NUMBER(38,0) NOT "
                + "NULL) | LANGUAGE SQL | AS ' [1]::ARRAY(NUMBER NOT NULL) ';"},
            {"CREATE OR REPLACE FUNCTION f10(x MAP(VARCHAR, NUMBER NOT NULL)) RETURNS VARCHAR AS $$ 'f10' $$",
                "Function F10 successfully created."},
            {"SELECT NULL::OBJECT(a ARRAY(INT NOT NULL))", "null"},
            {"SELECT SYSTEM$TYPEOF(NULL::OBJECT(a ARRAY(INT NOT NULL)))",
                "OBJECT(a ARRAY(NUMBER(38,0) NOT NULL))[LOB]"},
        });
    }
}
