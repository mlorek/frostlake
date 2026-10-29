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
 * Syntax lines (live-verified): an alert whose condition is a DROP refuses the DROP and the token after the brackets
 * its statement reads past, as a TRUNCATE's does, and a block's CREATE whose words run on is given up at its semicolon
 * with the block's later statements read on as its own, their faults reported after the CREATE's.
 */
public class AlertDropAndBlockReadOnLinesTest extends MeasuredCellsTestSupport {

    /** An alert's EXISTS over a DROP, a TRUNCATE, a DELETE and a CREATE. */
    @Test
    public void alertConditionStatements() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE ALERT al812 SCHEDULE = '60 MINUTE' IF (EXISTS (DROP TABLE zz (a))) THEN SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'DROP'. | syntax error "
                + "line 1 at position 83 unexpected ')'."},
            {"CREATE OR REPLACE ALERT al812 SCHEDULE = '60 MINUTE' IF (EXISTS (DROP TABLE zz)) THEN SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'DROP'."},
            {"CREATE OR REPLACE ALERT al812 SCHEDULE = '60 MINUTE' IF (EXISTS (DROP TABLE zz (a) b)) THEN SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'DROP'. | syntax error "
                + "line 1 at position 83 unexpected 'b'."},
            {"CREATE OR REPLACE ALERT al812 SCHEDULE = '60 MINUTE' IF (EXISTS (DROP VIEW zz (a))) THEN SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'DROP'. | syntax error "
                + "line 1 at position 82 unexpected ')'."},
            {"CREATE OR REPLACE ALERT al812 SCHEDULE = '60 MINUTE' IF (EXISTS (TRUNCATE TABLE zz (a))) THEN SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'TRUNCATE'. | syntax "
                + "error line 1 at position 87 unexpected ')'."},
            {"CREATE OR REPLACE ALERT al812 SCHEDULE = '60 MINUTE' IF (EXISTS (DROP TABLE zz (a, b))) THEN SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'DROP'. | syntax error "
                + "line 1 at position 86 unexpected ')'."},
            {"CREATE OR REPLACE ALERT al812 SCHEDULE = '60 MINUTE' IF (EXISTS (DELETE FROM zz (a))) THEN SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'DELETE'. | syntax "
                + "error line 1 at position 84 unexpected ')'."},
            {"CREATE OR REPLACE ALERT al812 SCHEDULE = '60 MINUTE' IF (EXISTS (DROP TABLE zz a)) THEN SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'DROP'."},
            {"CREATE OR REPLACE ALERT al812 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1 FROM zz (a))) THEN SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 82 unexpected '('."},
            {"CREATE OR REPLACE ALERT al812 SCHEDULE = '60 MINUTE' IF (EXISTS (CREATE TABLE zz (a))) THEN SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 65 unexpected 'CREATE'. | syntax "
                + "error line 1 at position 85 unexpected ')'."},
        });
    }

    /** A block's CREATE with words run in, and the statements after it. */
    @Test
    public void blockCreateReadsOn() {
        assertCells(new String[][] {
            {"BEGIN CREATE TABLE t9 (x INT) y z; SELECT 1 x y; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'z'. | syntax error "
                + "line 1 at position 46 unexpected 'y'. | syntax error line 1 at position 49 unexpected 'END'."},
            {"BEGIN CREATE TABLE t9 (x INT) y z w; SELECT 1 x y; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'z'. | syntax error "
                + "line 1 at position 35 unexpected ';'. | syntax error line 1 at position 48 unexpected 'y'. | syntax "
                + "error line 1 at position 51 unexpected 'END'."},
            {"BEGIN CREATE TABLE t9 (x INT) y z; SELECT 1 x y z; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'z'. | syntax error "
                + "line 1 at position 46 unexpected 'y'. | syntax error line 1 at position 48 unexpected 'z'."},
            {"BEGIN CREATE TABLE t9 (x INT) y; SELECT 1 x y; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ';'. | syntax error "
                + "line 1 at position 44 unexpected 'y'. | syntax error line 1 at position 47 unexpected 'END'."},
            {"BEGIN CREATE TABLE t9 (x INT) y z; SELECT 1; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'z'."},
            {"BEGIN CREATE TABLE t9 (x INT) y z; SELECT 1 x; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'z'."},
            {"BEGIN CREATE TABLE t9 (x INT) y z; SELECT 1 x y; SELECT 2; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'z'. | syntax error "
                + "line 1 at position 46 unexpected 'y'. | syntax error line 1 at position 59 unexpected 'END'."},
            {"BEGIN CREATE TABLE t9 (x INT) y z; SELECT 1 x y; SELECT 2 x y; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'z'. | syntax error "
                + "line 1 at position 46 unexpected 'y'. | syntax error line 1 at position 60 unexpected 'y'."},
            {"BEGIN CREATE TABLE t9 (x INT) y z; INSERT INTO t9 VALUES (1) y; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'z'. | syntax error "
                + "line 1 at position 61 unexpected 'y'. | syntax error line 1 at position 64 unexpected 'END'."},
            {"BEGIN SELECT 1 x y z; SELECT 1 x y; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'y'. | syntax error "
                + "line 1 at position 19 unexpected 'z'."},
            {"BEGIN CREATE VIEW v9 AS SELECT 1 x y z; SELECT 1 x y; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'y'. | syntax error "
                + "line 1 at position 37 unexpected 'z'."},
            {"BEGIN CREATE TABLE t9 (x INT) y z; LET q := 1 x y; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'z'. | syntax error "
                + "line 1 at position 46 unexpected 'x'. | syntax error line 1 at position 48 unexpected 'y'."},
        });
    }
}
