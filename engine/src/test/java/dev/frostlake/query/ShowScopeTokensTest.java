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
 * The tokens around a SHOW listing's scope. FLOAT4 and FLOAT8 are names - a column's, an alias's, a table's -
 * and keywords where a scope's word or a class's name stands; a stage reference is one token, the at sign
 * with what is written after it; a dot after a name at the statement's end is read on, so the end is what is
 * refused; a WITH that opens no privilege list is read on and the word after it refused, and straight after
 * IN the WITH itself, then the word. Every answer is live-verified.
 */
public class ShowScopeTokensTest extends MeasuredCellsTestSupport {

    @Test
    public void scopeWordsAndWhatFollowsThem() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'IDENTIFIER'.",
                "SHOW TABLES IN USER IDENTIFIER('y')");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'SELECT'.",
                "SHOW TABLES IN DATASET SELECT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SHOW'.",
                "SHOW TABLES IN MATERIALIZED SHOW TABLES");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'BEGIN'.",
                "SHOW TABLES IN USER BEGIN");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'LET'.",
                "SHOW TABLES IN DATASET LET");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'.",
                "SHOW TABLES IN DATASET x := 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'.",
                "SHOW TABLES IN DATASET 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ''x''.",
                "SHOW TABLES IN DATASET 'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ','.",
                "SHOW TABLES IN DATASET ,");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '<EOF>'.",
                "SHOW TABLES IN DATASET .");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'.",
                "SHOW TABLES IN DATASET WITH x AS (SELECT 1) SELECT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'CALL'.",
                "SHOW TABLES IN DATASET CALL p()");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'IDENTIFIER'.",
                "SHOW TABLES IDENTIFIER('y')");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'identifier'.",
                "SHOW TABLES IN DATASET identifier('y')");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'IDENTIFIER'.",
                "SHOW FUNCTIONS IN MATERIALIZED IDENTIFIER('y')");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '$x'.",
                "SHOW TABLES IN DATASET $x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ':'.",
                "SHOW TABLES IN DATASET :x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '?'.",
                "SHOW TABLES IN DATASET ?");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '@x'.",
                "SHOW TABLES IN DATASET @x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'WITH'. | syntax "
                    + "error line 1 at position 23 unexpected 'x'.",
                "SHOW FUNCTIONS IN WITH x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '<EOF>'.",
                "SHOW TABLES IN USER .");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '@x'.",
                "SHOW TABLES IN USER @x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '<EOF>'.",
                "SHOW TABLES IN nosuch .");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '@x'.",
                "SHOW TABLES IN nosuch @x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'.",
                "SHOW TABLES IN DATASET x.");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '@x'.",
                "SHOW TABLES IN DATASET @x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '@~'.",
                "SHOW TABLES IN DATASET @~");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '<EOF>'.",
                "SHOW TABLES IN DATASET WITH");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'.",
                "SHOW TABLES IN DATASET WITH x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
                "SHOW TABLES IN USER WITH x AS (SELECT 1) SELECT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'WITH'. | syntax "
                    + "error line 1 at position 23 unexpected 'x'.",
                "SHOW FUNCTIONS IN WITH x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'WITH'. | syntax "
                    + "error line 1 at position 22 unexpected '<EOF>'.",
                "SHOW FUNCTIONS IN WITH");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'WITH'. | syntax "
                    + "error line 1 at position 20 unexpected 'x'.",
                "SHOW TABLES IN WITH x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('.",
                "SHOW FUNCTIONS IN nosuch x (y)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'.",
                "SHOW FUNCTIONS IN FLOAT x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
                "SHOW FUNCTIONS IN DOUBLE x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'.",
                "SHOW FUNCTIONS IN REAL x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'.",
                "SHOW FUNCTIONS IN INT x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'.",
                "SHOW FUNCTIONS IN INTEGER x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
                "SHOW FUNCTIONS IN BIGINT x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
                "SHOW FUNCTIONS IN NUMBER x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'.",
                "SHOW FUNCTIONS IN DECIMAL x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'.",
                "SHOW FUNCTIONS IN NUMERIC x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'.",
                "SHOW FUNCTIONS IN VARCHAR x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
                "SHOW FUNCTIONS IN STRING x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'.",
                "SHOW FUNCTIONS IN TEXT x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'.",
                "SHOW FUNCTIONS IN CHAR x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'.",
                "SHOW FUNCTIONS IN BOOLEAN x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'.",
                "SHOW FUNCTIONS IN DATE x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'.",
                "SHOW FUNCTIONS IN TIME x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'.",
                "SHOW FUNCTIONS IN TIMESTAMP x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'.",
                "SHOW FUNCTIONS IN TIMESTAMP_NTZ x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'.",
                "SHOW FUNCTIONS IN TIMESTAMP_LTZ x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'.",
                "SHOW FUNCTIONS IN TIMESTAMP_TZ x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
                "SHOW FUNCTIONS IN BINARY x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'.",
                "SHOW FUNCTIONS IN VARIANT x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'.",
                "SHOW FUNCTIONS IN ARRAY x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
                "SHOW FUNCTIONS IN OBJECT x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'.",
                "SHOW FUNCTIONS IN GEOGRAPHY x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
                "SHOW FUNCTIONS IN VECTOR x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
                "SHOW FUNCTIONS IN FLOAT4 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
                "SHOW FUNCTIONS IN FLOAT8 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'.",
                "SHOW FUNCTIONS IN INT4 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'.",
                "SHOW FUNCTIONS IN INT8 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'.",
                "SHOW FUNCTIONS IN BYTEINT x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'.",
                "SHOW FUNCTIONS IN TINYINT x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'.",
                "SHOW FUNCTIONS IN SMALLINT x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'.",
                "SHOW FUNCTIONS IN DATETIME x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'.",
                "SHOW FUNCTIONS IN VARBINARY x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'.",
                "SHOW FUNCTIONS IN NVARCHAR x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'.",
                "SHOW FUNCTIONS IN NCHAR x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
                "SHOW FUNCTIONS IN FLOAT4 x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
                "SHOW FUNCTIONS IN FLOAT8 x");
            assertAnswer(
                "REFUSED SQL compilation error: Object type or Class 'INT4' does not exist or not authorized.",
                "SHOW FUNCTIONS IN INT4 x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
                "SHOW FUNCTIONS IN DOUBLE x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
                "SHOW FUNCTIONS IN NUMBER x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'.",
                "SHOW FUNCTIONS IN VARCHAR x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'.",
                "SHOW TABLES IN FLOAT4 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'.",
                "SHOW TABLES IN FLOAT8 x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'.",
                "SHOW TABLES IN DOUBLE x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'.",
                "SHOW TABLES IN NUMBER x y");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void parenthesesDotsStagesAndWith() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            assertShape(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '('.",
                "SHOW TABLES IN ACCOUNT (x)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '('.",
                "SHOW TABLES IN nosuch x (y)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('.",
                "SHOW FUNCTIONS IN nosuch x (y)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ')'.",
                "SHOW TABLES IN nosuch )");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN DATASET . x");
            assertAnswer(
                "REFUSED syntax error line 1 at position 15 unexpected 'DATASET . x'.",
                "SHOW TABLES IN DATASET . x y");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN nosuch . x");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN DATASET .x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
                "SHOW TABLES IN DATASET ..");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'.",
                "SHOW TABLES IN DATASET . LIMIT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '<EOF>'.",
                "SHOW FUNCTIONS IN DATASET .");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN USER . x");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN nosuch . x . y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
                "SHOW TABLES IN nosuch x .");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '.'.",
                "SHOW TABLES IN .");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '.'.",
                "SHOW TABLES IN . x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '@x/y'.",
                "SHOW TABLES IN DATASET @x/y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '@x.y'.",
                "SHOW TABLES IN DATASET @x.y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '@%t'.",
                "SHOW TABLES IN DATASET @%t");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '@x'.",
                "SHOW TABLES IN @x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '@x'.",
                "SHOW TABLES @x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '@y'.",
                "SHOW FUNCTIONS IN nosuch x @y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '@'.",
                "SHOW TABLES IN DATASET @");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '@\"x'.",
                "SHOW TABLES IN DATASET @\"x y\"");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '@x'.",
                "SHOW TABLES IN DATASET @x 'a'");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '@x'. | syntax "
                    + "error line 1 at position 29 unexpected '@x'.",
                "BEGIN SHOW TABLES IN DATASET @x; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'.",
                "SHOW TABLES IN DATASET WITH x y");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '<EOF>'.",
                "SHOW TABLES IN DATASET WITH PRIVILEGES");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'.",
                "SHOW TABLES IN DATASET WITH x AS");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'.",
                "SHOW TABLES IN nosuch WITH x AS (SELECT 1) SELECT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'WITH'. | syntax "
                    + "error line 1 at position 19 unexpected '<EOF>'.",
                "SHOW TABLES IN WITH");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'WITH'. | syntax "
                    + "error line 1 at position 20 unexpected 'x'.",
                "SHOW TABLES IN WITH x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'WITH'.",
                "SHOW TABLES IN WITH PRIVILEGES USAGE");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'WITH'. | syntax "
                    + "error line 1 at position 23 unexpected 'x'.",
                "SHOW FUNCTIONS IN WITH x AS (SELECT 1) SELECT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'.",
                "SHOW TABLES WITH x AS (SELECT 1) SELECT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'.",
                "SHOW TABLES IN DATASET WITH x AS (SELECT 1)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'.",
                "SHOW TABLES IN DATASET WITH x (SELECT 1)");
            assertAnswer(
                "REFUSED Unsupported feature 'SHOW TABLES ... WITH PRIVILEGES <>'.",
                "SHOW TABLES IN DATASET WITH PRIVILEGES USAGE");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'.",
                "SHOW TABLES IN nosuch WITH x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'.",
                "SHOW TABLES IN nosuch x WITH y");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN FLOAT4");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.FLOAT4' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA TEST_DB.FLOAT4.",
                "SHOW FUNCTIONS IN FLOAT4");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN FLOAT8");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN FLOAT4.x");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN FLOAT4 LIMIT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'FLOAT4'.",
                "SHOW FLOAT4");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'FLOAT4'. | syntax "
                    + "error line 1 at position 12 unexpected 'x'.",
                "DROP FLOAT4 x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'. | syntax "
                    + "error line 1 at position 31 unexpected 'END'.",
                "BEGIN SHOW TABLES IN FLOAT4 x; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN x.FLOAT4");
            assertAnswer(
                "1",
                "SELECT 1 AS float4");
            assertAnswer(
                "1",
                "SELECT float4 FROM (SELECT 1 AS float4)");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN SCHEMA FLOAT4");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SHOW TABLES IN DATABASE FLOAT4");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'.",
                "SHOW SCHEMAS IN FLOAT4 x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'.",
                "SHOW VIEWS IN FLOAT8 x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'.",
                "SHOW TABLES IN FLOAT4 x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'FLOAT4'.",
                "SHOW TABLES LIKE FLOAT4");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void namesAndTokensElsewhere() {
        engine.execute(
            "CREATE OR REPLACE TABLE t1 (id INT PRIMARY KEY)");
        try {
            assertAnswer(
                "Table FLOAT8 successfully created.",
                "CREATE OR REPLACE TABLE float8 (float4 INT)");
            assertAnswer(
                "",
                "SELECT float4 FROM float8");
            assertAnswer(
                "FLOAT8 successfully dropped.",
                "DROP TABLE float8");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'FLOAT'.",
                "SHOW FLOAT");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'DOUBLE'.",
                "SHOW DOUBLE");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'FLOAT'. | syntax "
                    + "error line 1 at position 11 unexpected 'x'.",
                "DROP FLOAT x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'.",
                "SHOW TABLES IN FLOAT x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'.",
                "SHOW TABLES IN DOUBLE x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'FLOAT8'.",
                "SHOW FLOAT8");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'FLOAT8'. | syntax "
                    + "error line 1 at position 12 unexpected 'x'.",
                "DROP FLOAT8 x");
            assertAnswer(
                "1",
                "SELECT 1 AS float8");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '@x'.",
                "SELECT 1 FROM t1 @x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '@x'.",
                "SELECT @x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '@x'.",
                "DROP TABLE @x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '@x'.",
                "DESCRIBE TABLE @x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '@x'.",
                "USE SCHEMA @x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '@x/y'.",
                "SELECT 1 @x/y z");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'.",
                "SHOW SCHEMAS WITH x AS (SELECT 1) SELECT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'.",
                "SHOW GRANTS WITH x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'.",
                "SHOW PARAMETERS WITH x AS (SELECT 1) SELECT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'.",
                "SHOW VARIABLES WITH x AS (SELECT 1) SELECT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'x'.",
                "SHOW TABLES STARTS WITH 'a' WITH x AS (SELECT 1) SELECT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
                "SHOW TABLES LIMIT 1 WITH x AS (SELECT 1) SELECT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'.",
                "SHOW SNOWFLAKE.ML.FORECAST WITH x AS (SELECT 1) SELECT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'x'.",
                "SHOW DATASETS WITH x AS (SELECT 1) SELECT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '<EOF>'.",
                "SHOW TABLES IN SCHEMA nosuch .");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '<EOF>'.",
                "SHOW TABLES IN DATABASE nosuch .");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '<EOF>'.",
                "SHOW TABLES IN a.b .");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
                "SHOW TABLES IN nosuch . .");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '<EOF>'.",
                "SHOW SCHEMAS IN nosuch .");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'.",
                "DESCRIBE TABLE t1 .");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '<EOF>'.",
                "SHOW TABLES IN ACCOUNT .");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<EOF>'.",
                "SHOW FUNCTIONS IN nosuch .");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'.",
                "SHOW TABLES IN nosuch . LIMIT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ';'.",
                "SHOW TABLES IN nosuch .;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'WITH'. | syntax "
                    + "error line 1 at position 20 unexpected 'x'.",
                "SHOW TABLES IN WITH x; SELECT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '@x'.",
                "SHOW TABLES IN DATASET @x LIMIT 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '@x'.",
                "SHOW TABLES LIKE @x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '@x'.",
                "SHOW TABLES STARTS WITH @x");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '@x'.",
                "SHOW TABLES LIMIT @x");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
