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
 * TAG_REFERENCES and SYSTEM$GET_TAG name a function or a procedure by its signature - add(NUMBER, NUMBER) -
 * under any qualification, quoting, spacing and spelling of its types, a type written with a width naming
 * only a parameter declared with it. A bare name names no routine, not even one without arguments, and
 * neither does a signature written with argument names; what is not there is refused by the text as written.
 * OBJECT_NAME spells the routine with its arguments and what it returns, a tag of the schema reaches it
 * INHERITED, and each overload carries its own tags. SERVICE and IMAGE REPOSITORY are domains, refused by
 * the full path when nothing has the name, and SYSTEM$GET_TAG takes a domain's words with an underscore
 * between them. Every answer is live-verified.
 */
public class RoutineTagReferencesTest extends MeasuredCellsTestSupport {

    @Test
    public void aRoutineIsNamedByItsSignature() {
        engine.execute(
            "CREATE OR REPLACE TAG tg1");
        engine.execute(
            "CREATE OR REPLACE FUNCTION r3_add(a NUMBER, b NUMBER) RETURNS NUMBER AS 'a + b'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE r3_p(a NUMBER) RETURNS NUMBER LANGUAGE SQL AS 'BEGIN RETURN 1; END;'");
        engine.execute(
            "ALTER FUNCTION r3_add(NUMBER, NUMBER) SET TAG tg1 = 'v1'");
        engine.execute(
            "ALTER PROCEDURE r3_p(NUMBER) SET TAG tg1 = 'v2'");
        engine.execute(
            "CREATE OR REPLACE TABLE tt (a INT)");
        engine.execute(
            "ALTER TABLE tt SET TAG tg1 = 'v3'");
        try {
            assertAnswer(
                "TG1|v1|R3_ADD(A NUMBER, B NUMBER):NUMBER(38,0)|FUNCTION|FUNCTION",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add(NUMBER, NUMBER)', 'FUNCTION'))");
            assertAnswer(
                "TG1|v1|R3_ADD(A NUMBER, B NUMBER):NUMBER(38,0)|FUNCTION|FUNCTION",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_db.test_schema.r3_add(NUMBER, NUMBER)', "
                    + "'FUNCTION'))");
            assertAnswer(
                "TG1|v1|R3_ADD(A NUMBER, B NUMBER):NUMBER(38,0)|FUNCTION|FUNCTION",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('\"TEST_DB\".\"TEST_SCHEMA\".\"R3_ADD\"(NUMBER, "
                    + "NUMBER)', 'FUNCTION'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'r3_add' does not exist or not authorized.",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add', 'FUNCTION'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'r3_add()' does not exist or not authorized.",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add()', 'FUNCTION'))");
            assertAnswer(
                "TG1|v1|R3_ADD(A NUMBER, B NUMBER):NUMBER(38,0)|FUNCTION|FUNCTION",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add(NUMBER,NUMBER)', 'FUNCTION'))");
            assertAnswer(
                "TG1|v1|R3_ADD(A NUMBER, B NUMBER):NUMBER(38,0)|FUNCTION|FUNCTION",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add(number, number)', 'FUNCTION'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'r3_add(VARCHAR)' does not exist or not authorized.",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add(VARCHAR)', 'FUNCTION'))");
            assertAnswer(
                "TG1|v1|R3_ADD(A NUMBER, B NUMBER):NUMBER(38,0)|FUNCTION|FUNCTION",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add(INT, INT)', 'FUNCTION'))");
            assertAnswer(
                "TG1|v1|R3_ADD(A NUMBER, B NUMBER):NUMBER(38,0)|FUNCTION|FUNCTION",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add(NUMBER(38,0), NUMBER(38,0))', 'FUNCTION'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'nosuch_fn(NUMBER)' does not exist or not authorized.",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_fn(NUMBER)', 'FUNCTION'))");
            assertAnswer(
                "TG1|v2|R3_P(A NUMBER):NUMBER(38,0)|PROCEDURE|PROCEDURE",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_p(NUMBER)', 'PROCEDURE'))");
            assertAnswer(
                "TG1|v2|R3_P(A NUMBER):NUMBER(38,0)|PROCEDURE|PROCEDURE",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_db.test_schema.r3_p(NUMBER)', 'PROCEDURE'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Procedure 'r3_p' does not exist or not authorized.",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_p', 'PROCEDURE'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Procedure 'r3_p()' does not exist or not authorized.",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_p()', 'PROCEDURE'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'r3_p(NUMBER)' does not exist or not authorized.",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_p(NUMBER)', 'FUNCTION'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Procedure 'r3_add(NUMBER, NUMBER)' does not exist or not "
                    + "authorized.",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add(NUMBER, NUMBER)', 'PROCEDURE'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Procedure 'nosuch_p(NUMBER)' does not exist or not authorized.",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_p(NUMBER)', 'PROCEDURE'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Service 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on SERVICE "
                    + "TEST_DB.TEST_SCHEMA.NOSUCH.",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch', 'SERVICE'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Image repository 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or "
                    + "not authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                    + "TEST_DB.TEST_SCHEMA.NOSUCH.",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch', 'IMAGE REPOSITORY'))");
            assertAnswer(
                "REFUSED Unknown domain: NOSUCHDOMAIN.",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch', 'NOSUCHDOMAIN'))");
            assertAnswer(
                "TG1|v1|R3_ADD(A NUMBER, B NUMBER):NUMBER(38,0)|FUNCTION|FUNCTION",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add(NUMBER, NUMBER)', 'function'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Table '\"R3_ADD(NUMBER, NUMBER)\"' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "\"R3_ADD(NUMBER, NUMBER)\".",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add(NUMBER, NUMBER)', 'TABLE'))");
            assertAnswer(
                "TG1|v3|TT|TABLE|TABLE",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('tt', 'TABLE'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'tt' does not exist or not authorized.",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('tt', 'FUNCTION'))");
            assertAnswer(
                "TG1|v3|TT|TABLE|TABLE",
                "SELECT tag_name, tag_value, object_name, domain, level FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_db.test_schema.tt', 'TABLE'))");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add(NUMBER, NUMBER)', "
                    + "'FUNCTION'))");
            assertAnswer(
                "1",
                "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES(' r3_add ( NUMBER , NUMBER ) ', "
                    + "'FUNCTION'))");
            assertShape(
                "TAG_DATABASE:VARCHAR(134217728), TAG_SCHEMA:VARCHAR(134217728), TAG_NAME:VARCHAR(134217728), "
                    + "TAG_VALUE:VARCHAR(134217728), LEVEL:VARCHAR(134217728), OBJECT_DATABASE:VARCHAR(134217728), "
                    + "OBJECT_SCHEMA:VARCHAR(134217728), OBJECT_NAME:VARCHAR(134217728), DOMAIN:VARCHAR(134217728), "
                    + "COLUMN_NAME:VARCHAR(134217728), APPLY_METHOD:VARCHAR(134217728)",
                "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add(NUMBER, NUMBER)', 'FUNCTION'))");
            assertAnswer(
                "TEST_DB|TEST_SCHEMA|TG1|v1|FUNCTION|TEST_DB|TEST_SCHEMA|R3_ADD(A NUMBER, B NUMBER):NUMBER(38,"
                    + "0)|FUNCTION|null",
                "SELECT tag_database, tag_schema, tag_name, tag_value, level, object_database, object_schema, "
                    + "object_name, domain, column_name FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add(NUMBER, "
                    + "NUMBER)', 'FUNCTION'))");
            assertAnswer(
                "v1",
                "SELECT SYSTEM$GET_TAG('tg1', 'r3_add(NUMBER, NUMBER)', 'FUNCTION')");
            assertAnswer(
                "v2",
                "SELECT SYSTEM$GET_TAG('tg1', 'r3_p(NUMBER)', 'PROCEDURE')");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'r3_add' does not exist or not authorized.",
                "SELECT SYSTEM$GET_TAG('tg1', 'r3_add', 'FUNCTION')");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void overloadsReturnsAndContainerDomains() {
        engine.execute(
            "CREATE OR REPLACE TAG tg1");
        engine.execute(
            "CREATE OR REPLACE TAG tg2");
        engine.execute(
            "CREATE OR REPLACE FUNCTION r3_add(a NUMBER, b NUMBER) RETURNS NUMBER AS 'a + b'");
        engine.execute(
            "ALTER FUNCTION r3_add(NUMBER, NUMBER) SET TAG tg1 = 'v1'");
        engine.execute(
            "CREATE OR REPLACE FUNCTION r3_ov(a NUMBER) RETURNS NUMBER AS 'a'");
        engine.execute(
            "CREATE OR REPLACE FUNCTION r3_ov(a VARCHAR) RETURNS VARCHAR AS 'a'");
        engine.execute(
            "ALTER FUNCTION r3_ov(NUMBER) SET TAG tg1 = 'n'");
        engine.execute(
            "ALTER FUNCTION r3_ov(VARCHAR) SET TAG tg1 = 'v'");
        engine.execute(
            "CREATE OR REPLACE FUNCTION r3_dec(a NUMBER(10,2), b VARCHAR(5), c FLOAT, d TIMESTAMP_NTZ, e ARRAY, f "
                + "BOOLEAN, g DATE, h VARIANT) RETURNS VARCHAR(7) AS '''x'''");
        engine.execute(
            "ALTER FUNCTION r3_dec(NUMBER, VARCHAR, FLOAT, TIMESTAMP_NTZ, ARRAY, BOOLEAN, DATE, VARIANT) SET TAG "
                + "tg1 = 'd'");
        engine.execute(
            "CREATE OR REPLACE FUNCTION \"r3 low\"(a INT) RETURNS INT AS 'a'");
        engine.execute(
            "ALTER FUNCTION \"r3 low\"(INT) SET TAG tg1 = 'q'");
        engine.execute(
            "CREATE OR REPLACE FUNCTION r3_tab(a INT) RETURNS TABLE (x INT, y VARCHAR) AS 'SELECT a, ''z'''");
        engine.execute(
            "ALTER FUNCTION r3_tab(INT) SET TAG tg1 = 't'");
        engine.execute(
            "CREATE OR REPLACE FUNCTION r3_none() RETURNS INT AS '1'");
        engine.execute(
            "ALTER FUNCTION r3_none() SET TAG tg1 = 'z'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE r3_pd(a NUMBER, b VARCHAR DEFAULT 'x') RETURNS VARCHAR LANGUAGE SQL AS "
                + "'BEGIN RETURN b; END;'");
        engine.execute(
            "ALTER PROCEDURE r3_pd(NUMBER, VARCHAR) SET TAG tg1 = 'pd'");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE r3_pt() RETURNS TABLE (x INT) LANGUAGE SQL AS 'DECLARE r RESULTSET "
                + "DEFAULT (SELECT 1 AS x); BEGIN RETURN TABLE(r); END;'");
        engine.execute(
            "ALTER PROCEDURE r3_pt() SET TAG tg1 = 'pt'");
        engine.execute(
            "ALTER SCHEMA test_db.test_schema SET TAG tg2 = 's'");
        try {
            assertAnswer(
                "TG1|n|FUNCTION|R3_OV(A NUMBER):NUMBER(38,0)|FUNCTION|MANUAL ;; TG2|s|SCHEMA|R3_OV(A "
                    + "NUMBER):NUMBER(38,0)|FUNCTION|INHERITED",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_ov(NUMBER)', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "TG1|v|FUNCTION|R3_OV(A VARCHAR):VARCHAR|FUNCTION|MANUAL ;; TG2|s|SCHEMA|R3_OV(A "
                    + "VARCHAR):VARCHAR|FUNCTION|INHERITED",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_ov(VARCHAR)', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'r3_ov(FLOAT)' does not exist or not authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_ov(FLOAT)', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'r3_ov' does not exist or not authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_ov', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "TG1|d|FUNCTION|R3_DEC(A NUMBER, B VARCHAR, C FLOAT, D TIMESTAMP_NTZ, E ARRAY, F BOOLEAN, G DATE, "
                    + "H VARIANT):VARCHAR(7)|FUNCTION|MANUAL ;; TG2|s|SCHEMA|R3_DEC(A NUMBER, B VARCHAR, C FLOAT, D "
                    + "TIMESTAMP_NTZ, E ARRAY, F BOOLEAN, G DATE, H VARIANT):VARCHAR(7)|FUNCTION|INHERITED",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_dec(NUMBER, VARCHAR, FLOAT, TIMESTAMP_NTZ, ARRAY, "
                    + "BOOLEAN, DATE, VARIANT)', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "TG1|d|FUNCTION|R3_DEC(A NUMBER, B VARCHAR, C FLOAT, D TIMESTAMP_NTZ, E ARRAY, F BOOLEAN, G DATE, "
                    + "H VARIANT):VARCHAR(7)|FUNCTION|MANUAL ;; TG2|s|SCHEMA|R3_DEC(A NUMBER, B VARCHAR, C FLOAT, D "
                    + "TIMESTAMP_NTZ, E ARRAY, F BOOLEAN, G DATE, H VARIANT):VARCHAR(7)|FUNCTION|INHERITED",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_dec(NUMBER(10,2), VARCHAR(5), FLOAT, "
                    + "TIMESTAMP_NTZ, ARRAY, BOOLEAN, DATE, VARIANT)', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'r3_dec(NUMBER(11,2), VARCHAR(6), FLOAT, TIMESTAMP_NTZ,"
                    + " ARRAY, BOOLEAN, DATE, VARIANT)' does not exist or not authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_dec(NUMBER(11,2), VARCHAR(6), FLOAT, "
                    + "TIMESTAMP_NTZ, ARRAY, BOOLEAN, DATE, VARIANT)', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "TG1|d|FUNCTION|R3_DEC(A NUMBER, B VARCHAR, C FLOAT, D TIMESTAMP_NTZ, E ARRAY, F BOOLEAN, G DATE, "
                    + "H VARIANT):VARCHAR(7)|FUNCTION|MANUAL ;; TG2|s|SCHEMA|R3_DEC(A NUMBER, B VARCHAR, C FLOAT, D "
                    + "TIMESTAMP_NTZ, E ARRAY, F BOOLEAN, G DATE, H VARIANT):VARCHAR(7)|FUNCTION|INHERITED",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_dec(INT, STRING, DOUBLE, TIMESTAMP, ARRAY, "
                    + "BOOLEAN, DATE, VARIANT)', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'r3_dec(NUMBER, VARCHAR, FLOAT, TIMESTAMP_LTZ, ARRAY, "
                    + "BOOLEAN, DATE, VARIANT)' does not exist or not authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_dec(NUMBER, VARCHAR, FLOAT, TIMESTAMP_LTZ, ARRAY, "
                    + "BOOLEAN, DATE, VARIANT)', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'r3_add(a NUMBER, b NUMBER)' does not exist or not "
                    + "authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add(a NUMBER, b NUMBER)', 'FUNCTION')) ORDER BY "
                    + "tag_name");
            assertAnswer(
                "TG1|q|FUNCTION|r3 low(A NUMBER):NUMBER(38,0)|FUNCTION|MANUAL ;; TG2|s|SCHEMA|r3 low(A "
                    + "NUMBER):NUMBER(38,0)|FUNCTION|INHERITED",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('\"r3 low\"(INT)', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "TG1|q|FUNCTION|r3 low(A NUMBER):NUMBER(38,0)|FUNCTION|MANUAL ;; TG2|s|SCHEMA|r3 low(A "
                    + "NUMBER):NUMBER(38,0)|FUNCTION|INHERITED",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('\"r3 low\"(NUMBER)', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'r3 low(INT)' does not exist or not authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3 low(INT)', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "TG1|v1|FUNCTION|R3_ADD(A NUMBER, B NUMBER):NUMBER(38,0)|FUNCTION|MANUAL ;; TG2|s|SCHEMA|R3_ADD(A "
                    + "NUMBER, B NUMBER):NUMBER(38,0)|FUNCTION|INHERITED",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('\"R3_ADD\"(NUMBER, NUMBER)', 'FUNCTION')) ORDER BY "
                    + "tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Function '\"r3_add\"(NUMBER, NUMBER)' does not exist or not "
                    + "authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('\"r3_add\"(NUMBER, NUMBER)', 'FUNCTION')) ORDER BY "
                    + "tag_name");
            assertAnswer(
                "TG1|t|FUNCTION|R3_TAB(A NUMBER):TABLE: (X NUMBER, Y VARCHAR)|FUNCTION|MANUAL ;; "
                    + "TG2|s|SCHEMA|R3_TAB(A NUMBER):TABLE: (X NUMBER, Y VARCHAR)|FUNCTION|INHERITED",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_tab(INT)', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "TG1|t|FUNCTION|R3_TAB(A NUMBER):TABLE: (X NUMBER, Y VARCHAR)|FUNCTION|MANUAL ;; "
                    + "TG2|s|SCHEMA|R3_TAB(A NUMBER):TABLE: (X NUMBER, Y VARCHAR)|FUNCTION|INHERITED",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_tab(NUMBER)', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "TG1|z|FUNCTION|R3_NONE():NUMBER(38,0)|FUNCTION|MANUAL ;; TG2|s|SCHEMA|R3_NONE():NUMBER(38,"
                    + "0)|FUNCTION|INHERITED",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_none()', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'r3_none' does not exist or not authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_none', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "TG1|z|FUNCTION|R3_NONE():NUMBER(38,0)|FUNCTION|MANUAL ;; TG2|s|SCHEMA|R3_NONE():NUMBER(38,"
                    + "0)|FUNCTION|INHERITED",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_none( )', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "TG1|pd|PROCEDURE|R3_PD(A NUMBER, B VARCHAR):VARCHAR|PROCEDURE|MANUAL ;; TG2|s|SCHEMA|R3_PD(A "
                    + "NUMBER, B VARCHAR):VARCHAR|PROCEDURE|INHERITED",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_pd(NUMBER, VARCHAR)', 'PROCEDURE')) ORDER BY "
                    + "tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Procedure 'r3_pd(NUMBER)' does not exist or not authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_pd(NUMBER)', 'PROCEDURE')) ORDER BY tag_name");
            assertAnswer(
                "TG1|pt|PROCEDURE|R3_PT():TABLE: (X NUMBER)|PROCEDURE|MANUAL ;; TG2|s|SCHEMA|R3_PT():TABLE: (X "
                    + "NUMBER)|PROCEDURE|INHERITED",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_pt()', 'PROCEDURE')) ORDER BY tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Procedure 'r3_pt' does not exist or not authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_pt', 'PROCEDURE')) ORDER BY tag_name");
            assertAnswer(
                "TG1|v1|FUNCTION|R3_ADD(A NUMBER, B NUMBER):NUMBER(38,0)|FUNCTION|MANUAL ;; TG2|s|SCHEMA|R3_ADD(A "
                    + "NUMBER, B NUMBER):NUMBER(38,0)|FUNCTION|INHERITED",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_schema.r3_add(NUMBER, NUMBER)', 'FUNCTION')) "
                    + "ORDER BY tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'nosuch_schema.r3_add(NUMBER, NUMBER)' does not exist "
                    + "or not authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_schema.r3_add(NUMBER, NUMBER)', 'FUNCTION')) "
                    + "ORDER BY tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'nosuch_db.test_schema.r3_add(NUMBER, NUMBER)' does "
                    + "not exist or not authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db.test_schema.r3_add(NUMBER, NUMBER)', "
                    + "'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'a.b.c.r3_add(NUMBER, NUMBER)' does not exist or not "
                    + "authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.r3_add(NUMBER, NUMBER)', 'FUNCTION')) ORDER BY "
                    + "tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'r3_add(NUMBER, NUMBER) x' does not exist or not "
                    + "authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add(NUMBER, NUMBER) x', 'FUNCTION')) ORDER BY "
                    + "tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'r3_add(NUMBER, NUMBER))' does not exist or not "
                    + "authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add(NUMBER, NUMBER))', 'FUNCTION')) ORDER BY "
                    + "tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'r3_add(NOSUCHTYPE)' does not exist or not authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add(NOSUCHTYPE)', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'r3_add(NUMBER, NUMBER' does not exist or not "
                    + "authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add(NUMBER, NUMBER', 'FUNCTION')) ORDER BY "
                    + "tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Function '(NUMBER)' does not exist or not authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('(NUMBER)', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Function '' does not exist or not authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'UPPER(VARCHAR)' does not exist or not authorized.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('UPPER(VARCHAR)', 'FUNCTION')) ORDER BY tag_name");
            assertAnswer(
                "TG1|v1|FUNCTION|R3_ADD(A NUMBER, B NUMBER):NUMBER(38,0)|FUNCTION|MANUAL ;; TG2|s|SCHEMA|R3_ADD(A "
                    + "NUMBER, B NUMBER):NUMBER(38,0)|FUNCTION|INHERITED",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add(NUMBER, NUMBER)', 'Function')) ORDER BY "
                    + "tag_name");
            assertAnswer(
                "TG1|v1|FUNCTION|R3_ADD(A NUMBER, B NUMBER):NUMBER(38,0)|FUNCTION|MANUAL ;; TG2|s|SCHEMA|R3_ADD(A "
                    + "NUMBER, B NUMBER):NUMBER(38,0)|FUNCTION|INHERITED",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_add(NUMBER,\tNUMBER)', 'FUNCTION')) ORDER BY "
                    + "tag_name");
            assertAnswer(
                "TEST_DB|TEST_SCHEMA|R3_ADD(A NUMBER, B NUMBER):NUMBER(38,0)|null|TEST_DB|TEST_SCHEMA|TG1 ;; "
                    + "TEST_DB|TEST_SCHEMA|R3_ADD(A NUMBER, B NUMBER):NUMBER(38,0)|null|TEST_DB|TEST_SCHEMA|TG2",
                "SELECT object_database, object_schema, object_name, column_name, tag_database, tag_schema, "
                    + "tag_name FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('R3_ADD(NUMBER, NUMBER)', 'FUNCTION')) "
                    + "ORDER BY tag_name");
            assertAnswer(
                "s",
                "SELECT SYSTEM$GET_TAG('tg2', 'r3_add(NUMBER, NUMBER)', 'FUNCTION')");
            assertAnswer(
                "v",
                "SELECT SYSTEM$GET_TAG('tg1', 'r3_ov(VARCHAR)', 'function')");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'r3_add()' does not exist or not authorized.",
                "SELECT SYSTEM$GET_TAG('tg1', 'r3_add()', 'FUNCTION')");
            assertAnswer(
                "v1",
                "SELECT SYSTEM$GET_TAG('tg1', 'test_db.test_schema.r3_add(NUMBER, NUMBER)', 'FUNCTION')");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'a.b.c.r3_add(NUMBER, NUMBER)' does not exist or not "
                    + "authorized.",
                "SELECT SYSTEM$GET_TAG('tg1', 'a.b.c.r3_add(NUMBER, NUMBER)', 'FUNCTION')");
            assertAnswer(
                "pd",
                "SELECT SYSTEM$GET_TAG('tg1', 'r3_pd(NUMBER, VARCHAR)', 'PROCEDURE')");
            assertAnswer(
                "z",
                "SELECT SYSTEM$GET_TAG('tg1', 'r3_none()', 'FUNCTION')");
            assertAnswer(
                "q",
                "SELECT SYSTEM$GET_TAG('tg1', '\"r3 low\"(INT)', 'FUNCTION')");
            assertAnswer(
                "REFUSED SQL compilation error: | Tag 'NOSUCH_TAG' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TAG NOSUCH_TAG.",
                "SELECT SYSTEM$GET_TAG('nosuch_tag', 'r3_add(NUMBER, NUMBER)', 'FUNCTION')");
            assertAnswer(
                "REFUSED SQL compilation error: | Function 'nosuch_fn(NUMBER)' does not exist or not authorized.",
                "SELECT SYSTEM$GET_TAG('tg1', 'nosuch_fn(NUMBER)', 'FUNCTION')");
            assertAnswer(
                "REFUSED SQL compilation error: | Service 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on SERVICE "
                    + "TEST_DB.TEST_SCHEMA.NOSUCH.",
                "SELECT SYSTEM$GET_TAG('tg1', 'nosuch', 'SERVICE')");
            assertAnswer(
                "REFUSED SQL compilation error: | Image repository 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or "
                    + "not authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                    + "TEST_DB.TEST_SCHEMA.NOSUCH.",
                "SELECT SYSTEM$GET_TAG('tg1', 'nosuch', 'IMAGE REPOSITORY')");
            assertAnswer(
                "REFUSED SQL compilation error: | Image repository 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or "
                    + "not authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                    + "TEST_DB.TEST_SCHEMA.NOSUCH.",
                "SELECT SYSTEM$GET_TAG('tg1', 'nosuch', 'IMAGE_REPOSITORY')");
            assertAnswer(
                "REFUSED SQL compilation error: | Image repository 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or "
                    + "not authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                    + "TEST_DB.TEST_SCHEMA.NOSUCH.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch', 'IMAGE_REPOSITORY')) ORDER BY tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Service 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on SERVICE "
                    + "TEST_DB.TEST_SCHEMA.NOSUCH.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_db.test_schema.nosuch', 'SERVICE')) ORDER BY "
                    + "tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Service 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on SERVICE "
                    + "TEST_DB.TEST_SCHEMA.NOSUCH.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch', 'service')) ORDER BY tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.d', 'SERVICE')) ORDER BY tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "TEST_DB.NOSUCH_SCHEMA.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_schema.x', 'IMAGE REPOSITORY')) ORDER BY "
                    + "tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db.s.x', 'SERVICE')) ORDER BY tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Service 'TEST_DB.TEST_SCHEMA.\"low\"' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on SERVICE "
                    + "TEST_DB.TEST_SCHEMA.\"low\".",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('\"low\"', 'SERVICE')) ORDER BY tag_name");
            assertAnswer(
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed.",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('a.b.c.d', 'IMAGE REPOSITORY')) ORDER BY tag_name");
            assertAnswer(
                "Image Repository R3_REPO successfully created.",
                "CREATE OR REPLACE IMAGE REPOSITORY r3_repo");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER IMAGE REPOSITORY r3_repo SET TAG tg1 = 'ir'");
            assertAnswer(
                "TG1|ir|IMAGE_REPOSITORY|R3_REPO|IMAGE_REPOSITORY|MANUAL ;; "
                    + "TG2|s|SCHEMA|R3_REPO|IMAGE_REPOSITORY|INHERITED",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_repo', 'IMAGE REPOSITORY')) ORDER BY tag_name");
            assertAnswer(
                "TG1|ir|IMAGE_REPOSITORY|R3_REPO|IMAGE_REPOSITORY|MANUAL ;; "
                    + "TG2|s|SCHEMA|R3_REPO|IMAGE_REPOSITORY|INHERITED",
                "SELECT tag_name, tag_value, level, object_name, domain, apply_method FROM "
                    + "TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('test_db.test_schema.r3_repo', 'IMAGE REPOSITORY')) "
                    + "ORDER BY tag_name");
            assertAnswer(
                "TEST_DB|TEST_SCHEMA|R3_REPO|null|TEST_DB|TEST_SCHEMA|TG1|IMAGE_REPOSITORY|IMAGE_REPOSITORY ;; "
                    + "TEST_DB|TEST_SCHEMA|R3_REPO|null|TEST_DB|TEST_SCHEMA|TG2|SCHEMA|IMAGE_REPOSITORY",
                "SELECT object_database, object_schema, object_name, column_name, tag_database, tag_schema, "
                    + "tag_name, level, domain FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('r3_repo', 'IMAGE "
                    + "REPOSITORY')) ORDER BY tag_name");
            assertAnswer(
                "ir",
                "SELECT SYSTEM$GET_TAG('tg1', 'r3_repo', 'IMAGE REPOSITORY')");
            assertAnswer(
                "s",
                "SELECT SYSTEM$GET_TAG('tg2', 'r3_repo', 'IMAGE REPOSITORY')");
            assertAnswer(
                "R3_REPO successfully dropped.",
                "DROP IMAGE REPOSITORY IF EXISTS r3_repo");
            assertAnswer(
                "Statement executed successfully.",
                "ALTER SCHEMA test_db.test_schema UNSET TAG tg2");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }

    @Test
    public void domainWordsAndMissingContainers() {
        engine.execute(
            "CREATE OR REPLACE TAG tg1");
        try {
            assertAnswer(
                "REFUSED SQL compilation error: | Database role 'TEST_DB.NOSUCH' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on ROLE "
                    + "TEST_DB.NOSUCH.",
                "SELECT SYSTEM$GET_TAG('tg1', 'nosuch', 'DATABASE_ROLE')");
            assertAnswer(
                "REFUSED SQL compilation error: | Network policy 'NOSUCH' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have at least one privilege granted on NETWORK POLICY NOSUCH.",
                "SELECT SYSTEM$GET_TAG('tg1', 'nosuch', 'NETWORK_POLICY')");
            assertAnswer(
                "REFUSED SQL compilation error: | Compute pool 'NOSUCH' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have at least one privilege granted on COMPUTE POOL NOSUCH.",
                "SELECT SYSTEM$GET_TAG('tg1', 'nosuch', 'COMPUTE_POOL')");
            assertAnswer(
                "REFUSED SQL compilation error: | Password policy 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or "
                    + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                    + "TEST_DB.TEST_SCHEMA.NOSUCH.",
                "SELECT SYSTEM$GET_TAG('tg1', 'nosuch', 'PASSWORD_POLICY')");
            assertAnswer(
                "REFUSED SQL compilation error: | Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or "
                    + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.NOSUCH.",
                "SELECT SYSTEM$GET_TAG('tg1', 'nosuch', 'DYNAMIC_TABLE')");
            assertAnswer(
                "REFUSED SQL compilation error: | Image repository 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or "
                    + "not authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                    + "TEST_DB.TEST_SCHEMA.NOSUCH.",
                "SELECT SYSTEM$GET_TAG('tg1', 'nosuch', 'image repository')");
            assertAnswer(
                "REFUSED SQL compilation error: | Image repository 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or "
                    + "not authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                    + "TEST_DB.TEST_SCHEMA.NOSUCH.",
                "SELECT SYSTEM$GET_TAG('tg1', 'nosuch', 'Image_Repository')");
            assertAnswer(
                "REFUSED Unknown domain: SERVICE .",
                "SELECT SYSTEM$GET_TAG('tg1', 'nosuch', 'SERVICE ')");
            assertAnswer(
                "REFUSED SQL compilation error: | Database role 'TEST_DB.NOSUCH' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have at least one privilege granted on ROLE "
                    + "TEST_DB.NOSUCH.",
                "SELECT SYSTEM$GET_TAG('tg1', 'nosuch', 'DATABASE ROLE')");
            assertAnswer(
                "REFUSED SQL compilation error: | Network policy 'NOSUCH' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have at least one privilege granted on NETWORK POLICY NOSUCH.",
                "SELECT SYSTEM$GET_TAG('tg1', 'nosuch', 'NETWORK POLICY')");
            assertAnswer(
                "REFUSED SQL compilation error: | Compute pool 'NOSUCH' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have at least one privilege granted on COMPUTE POOL NOSUCH.",
                "SELECT SYSTEM$GET_TAG('tg1', 'nosuch', 'COMPUTE POOL')");
            assertAnswer(
                "REFUSED SQL compilation error: | Password policy 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or "
                    + "not authorized. Your primary role <ROLE> must have at least one privilege granted on POLICY "
                    + "TEST_DB.TEST_SCHEMA.NOSUCH.",
                "SELECT SYSTEM$GET_TAG('tg1', 'nosuch', 'PASSWORD POLICY')");
            assertAnswer(
                "REFUSED SQL compilation error: | Dynamic table 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or "
                    + "not authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                    + "TEST_DB.TEST_SCHEMA.NOSUCH.",
                "SELECT SYSTEM$GET_TAG('tg1', 'nosuch', 'DYNAMIC TABLE')");
            assertAnswer(
                "REFUSED Unknown domain: IMAGE  REPOSITORY.",
                "SELECT tag_name FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch', 'IMAGE  REPOSITORY'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Image repository 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or "
                    + "not authorized. Your primary role <ROLE> must have at least one privilege granted on STAGE "
                    + "TEST_DB.TEST_SCHEMA.NOSUCH.",
                "SELECT tag_name FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch', 'image_repository'))");
            assertAnswer(
                "REFUSED Unknown domain: SERVICE .",
                "SELECT tag_name FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch', 'SERVICE '))");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
                "SELECT tag_name FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db.s.x', 'STAGE'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
                "SELECT tag_name FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db.s.x', 'STREAM'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
                "SELECT tag_name FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db.s.x', 'PIPE'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
                "SELECT tag_name FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db.s.x', 'TASK'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
                "SELECT tag_name FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db.s.x', 'ALERT'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
                "SELECT tag_name FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db.s.x', 'IMAGE "
                    + "REPOSITORY'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
                "SELECT tag_name FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db.s.x', 'TABLE'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
                "SELECT tag_name FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db.s.x', 'PASSWORD POLICY'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
                "SELECT tag_name FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db.s.x', 'DYNAMIC TABLE'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
                "SELECT tag_name FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db.s.x', 'TAG'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
                "SELECT tag_name FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db.s.x.y', 'COLUMN'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
                "SELECT tag_name FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('nosuch_db.s', 'SCHEMA'))");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
                "SELECT SYSTEM$GET_TAG('tg1', 'nosuch_db.s.x', 'SERVICE')");
            assertAnswer(
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                    + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB.",
                "SELECT SYSTEM$GET_TAG('tg1', 'nosuch_db.s.x', 'STAGE')");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "TEST_DB.NOSUCH_SCHEMA.",
                "SELECT SYSTEM$GET_TAG('tg1', 'test_db.nosuch_schema.x', 'SERVICE')");
            assertAnswer(
                "REFUSED SQL compilation error: | Schema 'TEST_DB.NOSUCH_SCHEMA' does not exist or not "
                    + "authorized. Your primary role <ROLE> must have USAGE or any other privilege granted on SCHEMA "
                    + "TEST_DB.NOSUCH_SCHEMA.",
                "SELECT SYSTEM$GET_TAG('tg1', 'test_db.nosuch_schema.x', 'STAGE')");
        } finally {
            engine.execute(
                "USE DATABASE test_db");
            engine.execute(
                "USE SCHEMA test_schema");
        }
    }
}
