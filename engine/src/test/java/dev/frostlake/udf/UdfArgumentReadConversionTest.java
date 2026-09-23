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

package dev.frostlake.udf;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A SQL UDF argument converts to its parameter's declared type where an expression or query body READS the
 * parameter: a body that ignores it, or reads it only in a branch that is not taken, answers without converting,
 * while one that reads it is refused in the conversion's own words. A scripting-block body converts every argument
 * when it starts. Every cell is live-verified.
 */
public class UdfArgumentReadConversionTest extends BaseDatabaseTest {

    /** The first row's first cell, or the refusal on one line. */
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

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void anArgumentConvertsWhereTheBodyReadsIt() {
        engine.execute("CREATE FUNCTION pvb(p BOOLEAN) RETURNS VARCHAR AS '''B'''");
        engine.execute("CREATE FUNCTION pxn(p NUMBER) RETURNS VARCHAR AS '''N'''");
        engine.execute("CREATE FUNCTION f_boolean(p BOOLEAN) RETURNS BOOLEAN AS 'p'");
        engine.execute("CREATE FUNCTION pr2(p NUMBER) RETURNS NUMBER AS 'p + p'");
        engine.execute("CREATE FUNCTION pbr(p NUMBER) RETURNS VARCHAR AS 'IFF(FALSE, p::VARCHAR, ''X'')'");
        engine.execute("CREATE FUNCTION pfn(p NUMBER) RETURNS NUMBER AS 'ABS(p)'");
        engine.execute("CREATE FUNCTION pcs(p NUMBER) RETURNS VARCHAR AS 'CASE WHEN TRUE THEN ''C'' ELSE p::VARCHAR END'");
        engine.execute("CREATE FUNCTION pco(p NUMBER) RETURNS VARCHAR AS 'COALESCE(''O'', p::VARCHAR)'");
        engine.execute("CREATE FUNCTION pis(p NUMBER) RETURNS BOOLEAN AS 'p IS NULL'");
        engine.execute("CREATE FUNCTION pdt(p DATE) RETURNS VARCHAR AS '''D'''");
        engine.execute("CREATE FUNCTION pnn(p NUMBER(3,0)) RETURNS VARCHAR AS '''R'''");
        engine.execute("CREATE FUNCTION pvc(p VARCHAR(2)) RETURNS VARCHAR AS '''S'''");
        final String notANumber = "Numeric value 's' is not recognized";
        assertCells(new String[][] {
            {"SELECT pvb(PARSE_JSON('1'))", "B"},
            {"SELECT pxn('s')", "N"},
            {"SELECT pxn(PARSE_JSON('\"s\"'))", "N"},
            {"SELECT f_boolean(PARSE_JSON('1'))", "Failed to cast variant value 1 to BOOLEAN"},
            {"SELECT pr2('s')", notANumber},
            {"SELECT pbr('s')", "X"},
            {"SELECT pfn('s')", notANumber},
            {"SELECT pcs('s')", "C"},
            {"SELECT pco('s')", "O"},
            {"SELECT pis('s')", notANumber},
            {"SELECT pdt('notadate')", "D"},
            {"SELECT pnn(12345)", "R"},
            {"SELECT pvc('abcdef')", "S"},
        });
    }

    @Test
    public void aQueryBodyConvertsWhereItReadsAndABlockWhenItStarts() {
        engine.execute("CREATE FUNCTION pq(p NUMBER) RETURNS VARCHAR AS 'SELECT ''Q'''");
        engine.execute("CREATE FUNCTION pq2(p NUMBER) RETURNS NUMBER AS 'SELECT p'");
        engine.execute("CREATE FUNCTION pbk(p NUMBER) RETURNS VARCHAR AS $$ BEGIN RETURN 'K'; END $$");
        engine.execute("CREATE FUNCTION pbk2(p NUMBER) RETURNS NUMBER AS $$ BEGIN RETURN p; END $$");
        final String notANumber = "Numeric value 's' is not recognized";
        assertCells(new String[][] {
            {"SELECT pq('s')", "Q"},
            {"SELECT pq2('s')", notANumber},
            {"SELECT pbk('s')", notANumber},
            {"SELECT pbk2('s')", notANumber},
        });
    }
}
