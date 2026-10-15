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
 * A word after a CREATE that ends in no query, in a block: it is a property's name there as it is in the
 * statement alone, so the token after it is refused where its '=' is due — the next word, a number, the
 * statement's ';' (live-verified).
 */
public class BlockCreatePropertyWordLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void aWordAfterTheDefinition() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'z'.",
            "BEGIN CREATE TABLE t9 (x INT) y z; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ';'.",
            "BEGIN CREATE TABLE t9 (x INT) y; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ';'.",
            "BEGIN CREATE TABLE t9 (x INT) y; RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'z'.",
            "BEGIN CREATE TABLE t9 (x INT) y z; RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'z'. | syntax error "
                + "line 1 at position 38 unexpected 'END'.",
            "BEGIN CREATE VIEW v9 AS SELECT 1 y z; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'z'.",
            "BEGIN CREATE SEQUENCE s9 y z; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ';'.",
            "BEGIN CREATE SEQUENCE s9 y; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'z'.",
            "BEGIN ALTER TABLE t SET y z; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ';'.",
            "BEGIN ALTER TABLE t SET y; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ';'.",
            "BEGIN CREATE TABLE t9 (x INT) COMMENT; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'.",
            "BEGIN CREATE TABLE t9 (x INT) y 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'z'.",
            "BEGIN IF (TRUE) THEN CREATE TABLE t9 (x INT) y z; END IF; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'y'. | syntax error "
                + "line 1 at position 22 unexpected 'z'.",
            "BEGIN DROP TABLE t9 y z; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ';'.",
            "BEGIN CREATE TABLE t9 (x INT) y =; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ','. | syntax error "
                + "line 1 at position 34 unexpected ';'.",
            "BEGIN CREATE TABLE t9 (x INT) y, z; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ';'. | syntax error "
                + "line 1 at position 38 unexpected 'y'.",
            "CREATE TABLE t9 (x INT) y; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'z'. | syntax error "
                + "line 1 at position 40 unexpected 'y'.",
            "CREATE TABLE t9 (x INT) y z; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'z'.",
            "CREATE SEQUENCE s9 y z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '<EOF>'.",
            "CREATE SEQUENCE s9 y");
    }
}
