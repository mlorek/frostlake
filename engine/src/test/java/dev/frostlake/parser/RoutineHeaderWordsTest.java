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
 * A word out of place between a routine's parameters and its body. Where the clauses stop being ones a CREATE
 * FUNCTION or PROCEDURE takes, what follows is read as properties, a name, an {@code =} and a value: a name with
 * no {@code =} has the next token named, which is taken for the value when it could be one. So a second
 * {@code COPY GRANTS} is its {@code GRANTS}, and a RETURNS after it is a property's name - {@code COPY GRANTS COPY
 * GRANTS RETURNS NUMBER AS 'x'} is {@code 'GRANTS'} and {@code 'NUMBER'}. A token that could be no value is named
 * and the reading resumes at the next word; the body ends it; after a fault a word that opens a statement is
 * named where a name would stand, and nothing after it is read. Every answer is live-verified.
 */
public class RoutineHeaderWordsTest extends MeasuredCellsTestSupport {

    @Test
    public void aSecondCopyGrants() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 75 unexpected 'NUMBER'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS RETURNS NUMBER AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 75 unexpected 'GRANTS'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS RETURNS NUMBER COPY GRANTS AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 63 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 70 unexpected 'COPY'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) RETURNS NUMBER COPY GRANTS COPY GRANTS AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 63 unexpected 'GRANTS'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) RETURNS NUMBER COPY GRANTS AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 69 unexpected 'NUMBER'. | syntax error line 1 at position 85 unexpected "
                + "'SQL'.",
            "CREATE OR REPLACE PROCEDURE nf_cgp() COPY GRANTS COPY GRANTS RETURNS NUMBER LANGUAGE SQL AS 'BEGIN "
                + "RETURN 1; END'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'RETURNS'. | syntax "
                + "error line 1 at position 75 unexpected 'AS'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY RETURNS NUMBER AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'COPY'. | syntax "
                + "error line 1 at position 60 unexpected 'RETURNS'. | syntax error line 1 at position 75 unexpected "
                + "'AS'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY COPY GRANTS RETURNS NUMBER AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'RETURNS'. | syntax "
                + "error line 1 at position 77 unexpected 'AS'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS GRANTS RETURNS NUMBER AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 67 unexpected 'COPY'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS COPY GRANTS RETURNS NUMBER AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected 'RETURNS'. | syntax "
                + "error line 1 at position 63 unexpected 'AS'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY RETURNS NUMBER AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'GRANTS'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 2 at position 5 unexpected 'GRANTS'. | syntax "
                + "error line 2 at position 20 unexpected 'NUMBER'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS\nCOPY GRANTS RETURNS NUMBER AS 'x'");
        assertAnswer(
            "Table NF_CGT successfully created.",
            "CREATE OR REPLACE TABLE nf_cgt (a INT) COPY GRANTS COPY GRANTS");
        assertAnswer(
            "View NF_CGV successfully created.",
            "CREATE OR REPLACE VIEW nf_cgv COPY GRANTS COPY GRANTS AS SELECT 1 a");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 82 unexpected 'NUMBER'.",
            "CREATE OR REPLACE SECURE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS RETURNS NUMBER AS 'x'");
    }

    @Test
    public void wordsThatAreNoClause() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'BAR'. | syntax error "
                + "line 1 at position 71 unexpected 'NUMBER'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS FOO BAR RETURNS NUMBER AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'RETURNS'. | syntax "
                + "error line 1 at position 62 unexpected 'AS'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) FOO RETURNS NUMBER AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected ''a''. | syntax error "
                + "line 1 at position 59 unexpected 'NUMBER'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) FOO 'a' RETURNS NUMBER AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected 'NUMBER'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) FOO = 1 RETURNS NUMBER AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 67 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 67 unexpected 'INSERT'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS INSERT RETURNS NUMBER AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 75 unexpected 'NUMBER'. | syntax error line 1 at position 91 unexpected "
                + "'SQL'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS RETURNS NUMBER LANGUAGE SQL AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 75 unexpected 'NUMBER'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS RETURNS NUMBER COMMENT = 'c' AS "
                + "'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 75 unexpected 'TABLE'. | syntax error line 1 at position 84 unexpected "
                + "'INT'. | syntax error line 1 at position 87 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS RETURNS TABLE (a INT) AS 'SELECT "
                + "1'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 75 unexpected 'NUMBER'. | syntax error line 1 at position 82 unexpected "
                + "'NOT'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS RETURNS NUMBER NOT NULL AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 75 unexpected 'NUMBER'. | syntax error line 1 at position 81 unexpected "
                + "'('.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS RETURNS NUMBER(10,2) AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 63 unexpected 'GRANTS'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) RETURNS NUMBER COPY GRANTS");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 63 unexpected 'AS'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) RETURNS NUMBER COPY AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'AS'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) RETURNS NUMBER FOO AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'BAR'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) RETURNS NUMBER FOO BAR AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 80 unexpected 'SQL'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) RETURNS NUMBER LANGUAGE SQL LANGUAGE SQL AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'COPY'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) RETURNS NUMBER AS 'x' COPY GRANTS");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'RETURNS'. | syntax "
                + "error line 1 at position 69 unexpected 'LANGUAGE'. | syntax error line 1 at position 82 "
                + "unexpected 'AS'.",
            "CREATE OR REPLACE PROCEDURE nf_cgp() COPY GRANTS COPY RETURNS NUMBER LANGUAGE SQL AS 'BEGIN RETURN "
                + "1; END'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 73 unexpected 'SQL'.",
            "CREATE OR REPLACE PROCEDURE nf_cgp() RETURNS NUMBER COPY GRANTS LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 70 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 77 unexpected 'COPY'.",
            "CREATE OR REPLACE PROCEDURE nf_cgp() RETURNS NUMBER LANGUAGE SQL COPY GRANTS COPY GRANTS AS 'BEGIN "
                + "RETURN 1; END'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'GRANTS'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS; SELECT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 67 unexpected 'COPY'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS COPY RETURNS NUMBER AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 75 unexpected 'COPY'. | syntax error line 1 at position 87 unexpected "
                + "'AS'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS RETURNS COPY NUMBER AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected '1'. | syntax error "
                + "line 1 at position 70 unexpected 'NUMBER'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY 1 RETURNS NUMBER AS 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 75 unexpected 'NUMBER'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS RETURNS NUMBER");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 74 unexpected '<EOF>'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS RETURNS");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 75 unexpected 'NUMBER'. | syntax error line 1 at position 84 unexpected "
                + "'<EOF>'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS RETURNS NUMBER AS");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 75 unexpected 'NUMBER'. | syntax error line 1 at position 89 unexpected "
                + "''y''.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS RETURNS NUMBER AS 'x' 'y'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 75 unexpected 'NUMBER'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS RETURNS NUMBER AS $$x$$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'GRANTS'. | syntax "
                + "error line 1 at position 75 unexpected 'NUMBER'. | syntax error line 1 at position 89 unexpected "
                + "'ON'. | syntax error line 1 at position 103 unexpected 'AS'.",
            "CREATE OR REPLACE FUNCTION nf_cg(x NUMBER) COPY GRANTS COPY GRANTS RETURNS NUMBER CALLED ON NULL "
                + "INPUT AS 'x'");
    }
}
