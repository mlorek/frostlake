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
package dev.frostlake.procedural;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * What a CALL of a SQL procedure answers, beside the handler languages: one row in a column named after the
 * procedure and typed by RETURNS, a NULL included, and what RESULT_SCAN reads of it. A NULL returned for a
 * VARIANT is a SQL NULL and {@code PARSE_JSON('null')} a JSON null; a text answers at the width nothing bounds
 * whatever width RETURNS declares, and one the declared width cannot hold is refused in the body. Every answer
 * is live-verified.
 */
public class SqlBodyResultTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void theAnswerIsTypedByReturns() {
        assertAnswer(
            "Function S00 successfully created.",
            "CREATE OR REPLACE PROCEDURE s00() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 1.5; END; $$");
        assertAnswer(
            "1.5",
            "CALL s00()");
        shape(
            "CALL s00()");
        assertAnswer(
            "FLOAT[DOUBLE] / 1.5",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function S01 successfully created.",
            "CREATE OR REPLACE PROCEDURE s01() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN NULL; END; $$");
        assertAnswer(
            "null",
            "CALL s01()");
        assertShape(
            "S01:VARCHAR(134217728)",
            "CALL s01()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function S02 successfully created.",
            "CREATE OR REPLACE PROCEDURE s02() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ BEGIN RETURN 1.38875; END; "
                + "$$");
        assertAnswer(
            "1.39",
            "CALL s02()");
        assertShape(
            "S02:NUMBER(10,2)",
            "CALL s02()");
        assertAnswer(
            "NUMBER(10,2)[SB8] / 1.39",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function S03 successfully created.",
            "CREATE OR REPLACE PROCEDURE s03() RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN "
                + "PARSE_JSON('{\"a\":1}'); END; $$");
        assertAnswer(
            "{\"a\":1}",
            "CALL s03()");
        assertShape(
            "S03:VARIANT",
            "CALL s03()");
        assertAnswer(
            "VARIANT[LOB] / {\"a\":1}",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function S04 successfully created.",
            "CREATE OR REPLACE PROCEDURE s04() RETURNS BOOLEAN LANGUAGE SQL AS $$ BEGIN RETURN NULL; END; $$");
        assertAnswer(
            "null",
            "CALL s04()");
        assertShape(
            "S04:BOOLEAN",
            "CALL s04()");
        assertAnswer(
            "BOOLEAN[SB1] / null",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function S05 successfully created.",
            "CREATE OR REPLACE PROCEDURE s05() RETURNS DATE LANGUAGE SQL AS $$ BEGIN RETURN '2024-01-02'; END; $$");
        assertAnswer(
            "2024-01-02",
            "CALL s05()");
        assertShape(
            "S05:DATE",
            "CALL s05()");
        assertAnswer(
            "DATE[SB4] / 2024-01-02",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }

    @Test
    public void nullsAndDeclaredWidths() {
        assertAnswer(
            "Function NB successfully created.",
            "CREATE OR REPLACE PROCEDURE nb() RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN NULL; END; $$");
        assertAnswer(
            "null",
            "CALL nb()");
        assertAnswer(
            "sql-null / no-type",
            "SELECT IFF($1 IS NULL, 'sql-null', 'value') || ' / ' || NVL(TYPEOF($1), 'no-type') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function NC successfully created.",
            "CREATE OR REPLACE PROCEDURE nc() RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN PARSE_JSON('null'); "
                + "END; $$");
        assertAnswer(
            "null",
            "CALL nc()");
        assertAnswer(
            "value / NULL_VALUE",
            "SELECT IFF($1 IS NULL, 'sql-null', 'value') || ' / ' || NVL(TYPEOF($1), 'no-type') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function ND successfully created.",
            "CREATE OR REPLACE PROCEDURE nd() RETURNS ARRAY LANGUAGE SQL AS $$ BEGIN RETURN NULL; END; $$");
        assertAnswer(
            "null",
            "CALL nd()");
        assertAnswer(
            "sql-null / no-type",
            "SELECT IFF($1 IS NULL, 'sql-null', 'value') || ' / ' || NVL(TYPEOF($1), 'no-type') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function W0 successfully created.",
            "CREATE OR REPLACE PROCEDURE w0() RETURNS VARCHAR(2) LANGUAGE SQL AS $$ BEGIN RETURN 'ab'; END; $$");
        assertAnswer(
            "ab",
            "CALL w0()");
        assertShape(
            "W0:VARCHAR(134217728)",
            "CALL w0()");
        assertAnswer(
            "VARCHAR(134217728)[LOB] / ab",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function W1 successfully created.",
            "CREATE OR REPLACE PROCEDURE w1() RETURNS VARCHAR(2) LANGUAGE SQL AS $$ BEGIN RETURN 'abc'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : String 'abc' is too "
                + "long and would be truncated",
            "CALL w1()");
        assertShape(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : String 'abc' is too "
                + "long and would be truncated",
            "CALL w1()");
        assertShapedAnswer(
            "REFUSED Query <QID> has no result because it failed",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function W6 successfully created.",
            "CREATE OR REPLACE PROCEDURE w6() RETURNS NUMBER(3,0) LANGUAGE SQL AS $$ BEGIN RETURN 7; END; $$");
        assertAnswer(
            "7",
            "CALL w6()");
        assertShape(
            "W6:NUMBER(3,0)",
            "CALL w6()");
        assertAnswer(
            "NUMBER(3,0)[SB2] / 7",
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function W9 successfully created.",
            "CREATE OR REPLACE PROCEDURE w9() RETURNS BINARY LANGUAGE SQL AS $$ BEGIN RETURN TO_BINARY('ABCD'); "
                + "END; $$");
        assertAnswer(
            "ABCD",
            "CALL w9()");
        assertShape(
            "W9:BINARY",
            "CALL w9()");
        answer(
            "SELECT SYSTEM$TYPEOF($1) || ' / ' || NVL($1::VARCHAR, 'null') FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }
}
