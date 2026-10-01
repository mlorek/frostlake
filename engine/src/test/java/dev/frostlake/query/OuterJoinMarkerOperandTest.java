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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Oracle {@code (+)} outer-join marker reads after any operand, and after anything but a column it is
 * refused while the query compiles: {@code a = 1 (+)} is {@code Invalid argument for (+): 1.}, a NULL
 * likewise, over an empty table too. A SQL UDF body refuses it as a compilation of the body, and a body
 * ending in the marker and a line comment is refused as any body ending inside a line comment is. A marked
 * column joining two tables is the outer join it always was. Every cell is live-verified.
 */
public class OuterJoinMarkerOperandTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("CREATE OR REPLACE TABLE t2 (b INT)");
    }

    /** The first row's first cell, the statement's status, "no row", or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    public void aMarkerAfterANonColumnIsAnInvalidArgument() {
        final String[][] cells = {
            {"SELECT 1 FROM t WHERE a = 1 (+)", "SQL compilation error:|Invalid argument for (+): 1."},
            {"SELECT 1 FROM t WHERE a = 1 (+) -- c", "SQL compilation error:|Invalid argument for (+): 1."},
            {"SELECT 1 FROM t WHERE a = NULL (+)", "SQL compilation error:|Invalid argument for (+): NULL."},
            {"SELECT 1 FROM t, t2 WHERE a = b (+) AND a = 1 (+)", "SQL compilation error:|Invalid argument for (+): 1."},
            {"SELECT 1 FROM t, t2 WHERE a = b (+)", "no row"},
            {"SELECT 1 FROM t, t2 WHERE t.a = t2.b (+) -- c", "no row"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void aFunctionBodyWithAMisplacedMarkerIsNotCreated() {
        final String[][] cells = {
            {"CREATE OR REPLACE FUNCTION f1() RETURNS INT AS 'SELECT 1 FROM t WHERE a = 1 (+) -- c'",
                "Compilation of SQL UDF failed: SQL compilation error:|syntax error line 1 at position 1 unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION f2() RETURNS INT AS 'SELECT 1 FROM t WHERE a = 1 (+)'",
                "Compilation of SQL UDF failed: SQL compilation error:|Invalid argument for (+): 1."},
            {"CREATE OR REPLACE FUNCTION f8() RETURNS INT AS 'SELECT 1 FROM t WHERE a = 1 (+) '",
                "Compilation of SQL UDF failed: SQL compilation error:|Invalid argument for (+): 1."},
            {"CREATE OR REPLACE FUNCTION f7() RETURNS INT AS 'SELECT 1 FROM t WHERE a = 1 -- c'",
                "Compilation of SQL UDF failed: SQL compilation error:|syntax error line 1 at position 1 unexpected 'SELECT'."},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
        engine.execute("CREATE OR REPLACE FUNCTION f4() RETURNS INT AS 'SELECT 1 FROM t, t2 WHERE a = b (+)'");
        assertEquals("null", answer("SELECT f4()"));
    }
}
