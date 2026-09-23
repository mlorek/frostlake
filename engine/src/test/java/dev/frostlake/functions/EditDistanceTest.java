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

package dev.frostlake.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * EDITDISTANCE with and without its maximum-distance argument (live-verified). The maximum caps the distance,
 * is rounded half away from zero, answers 0 when negative and NULL when NULL, reads a text as a number, and
 * must fit a 32-bit integer; Frostlake refused the third argument as too many.
 */
public class EditDistanceTest extends BaseDatabaseTest {

    /** The one row's cells, as text. */
    private List<String> row(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> cells = new ArrayList<>();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            cells.add(String.valueOf(rs.getRows().get(0).getValue(i)));
        }
        return cells;
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void theMaximumCapsTheDistance() {
        assertEquals(List.of("1", "2", "null", "0", "1", "0"),
            row("""
                SELECT EDITDISTANCE('abc', 'abd', 1), EDITDISTANCE('abcdef', 'xyz', 2), EDITDISTANCE('abc', 'abd', NULL),
                    EDITDISTANCE('abc', 'abd', -1), EDITDISTANCE('abc', 'abd', 5), EDITDISTANCE('abc', 'abd', 0)"""));
        assertEquals(List.of("2", "3", "1", "2", "1", "2", "0"),
            row("""
                SELECT EDITDISTANCE('abcdef', 'xyz', 1.5), EDITDISTANCE('abcdef', 'xyz', 2.5),
                    EDITDISTANCE('abcdef', 'xyz', 1.4), EDITDISTANCE('abcdef', 'xyz', '2'), EDITDISTANCE('abcdef', 'xyz', 1e0),
                    EDITDISTANCE('', 'xyz', 2), EDITDISTANCE('', '', 0)"""));
        assertEquals(List.of("null", "null", "NUMBER(9,0)[SB4]", "NUMBER(9,0)[SB4]"),
            row("""
                SELECT EDITDISTANCE('abc', NULL, 1), EDITDISTANCE(NULL, 'x', 1), SYSTEM$TYPEOF(EDITDISTANCE('abc', 'abd', 1)),
                    SYSTEM$TYPEOF(EDITDISTANCE('abc', 'abd'))"""));
        engine.execute("CREATE TABLE e (a VARCHAR, b VARCHAR, m NUMBER(5,1), f FLOAT)");
        engine.execute("INSERT INTO e VALUES ('abcdef', 'xyz', 2.5, 1.9)");
        assertEquals(List.of("3", "2"), row("SELECT EDITDISTANCE(a, b, m), EDITDISTANCE(a, b, f) FROM e"));
    }

    @Test
    public void aMaximumOutsideTheNumbersIsRefused() {
        assertEquals("Numeric value '2147483648' is out of range",
            refusal("SELECT EDITDISTANCE('abcdef', 'xyz', 2147483648)"));
        assertEquals("Numeric value 'x' is not recognized", refusal("SELECT EDITDISTANCE('abcdef', 'xyz', 'x')"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'EDITDISTANCE': (VARCHAR(6), VARCHAR(3), BOOLEAN)",
            refusal("SELECT EDITDISTANCE('abcdef', 'xyz', TRUE)"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'EDITDISTANCE': (BOOLEAN, VARCHAR(1), NUMBER(1,0))",
            refusal("SELECT EDITDISTANCE(1 = 1, 'x', 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "too many arguments for function [EDITDISTANCE('a', 'b', 1, 2)] expected 3, got 4",
            refusal("SELECT EDITDISTANCE('a', 'b', 1, 2)"));
    }
}
