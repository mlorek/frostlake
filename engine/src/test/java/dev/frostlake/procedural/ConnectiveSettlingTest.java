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

import dev.frostlake.BaseJdbcTest;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AND and OR are settled by whichever side decides them: an AND with a FALSE side is FALSE and an OR with a TRUE
 * side is TRUE even when the other side faults, on either side, in a query and in a block's RETURN, LET, IF and
 * WHILE alike. Only when neither side decides does the fault raise, and a block names it at the start of the
 * whole expression, not at the operand that raised it. Every cell is live-verified.
 */
public class ConnectiveSettlingTest extends BaseJdbcTest {

    /** The first cell as lower-cased text, "no row", or "! " and the refusal. */
    private String answer(final String sql) {
        try (ResultSet rs = statement.executeQuery(sql)) {
            if (!rs.next()) {
                return "no row";
            }
            if (rs.getMetaData().getColumnType(1) == Types.BOOLEAN) {
                final boolean value = rs.getBoolean(1);
                return rs.wasNull() ? "null" : String.valueOf(value);
            }
            final String value = rs.getString(1);
            return value == null ? "null" : value.toLowerCase();
        } catch (final SQLException refused) {
            return "! " + refused.getMessage();
        }
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            final String answer = answer(cell[0]);
            if (cell[1].startsWith("! ")) {
                assertTrue(answer.startsWith("! ") && answer.contains(cell[1].substring(2)),
                    cell[0] + " should be refused with [" + cell[1] + "] but read: " + answer);
            } else {
                assertEquals(cell[1], answer, cell[0]);
            }
        }
    }

    @Test
    public void aQuerySettlesTheConnectiveFromEitherSide() {
        assertCells(new String[][] {
            {"SELECT TRUE OR 'a'", "true"},
            {"SELECT 1 OR 'a'", "true"},
            {"SELECT 'a' OR TRUE", "true"},
            {"SELECT 'a' OR 1", "true"},
            {"SELECT 'a' OR 'a'", "! Boolean value 'a' is not recognized"},
            {"SELECT 'a' AND 'a'", "! Boolean value 'a' is not recognized"},
            {"SELECT 1 AS a WHERE a = 'x' AND 1 = 2", "no row"},
            {"SELECT 1 AS a WHERE a = 'x'", "! Numeric value 'x' is not recognized"},
            {"SELECT TRUE OR 1/0 = 1", "true"},
            {"SELECT 1/0 = 1 OR TRUE", "true"},
            {"SELECT FALSE AND 1/0 = 1", "false"},
            {"SELECT FALSE AND 'abc'", "false"},
            {"SELECT 'abc' AND FALSE", "false"},
            {"SELECT NULL OR 'a'", "! Boolean value 'a' is not recognized"},
            {"SELECT TRUE OR NULL", "true"},
            {"SELECT 'a' OR NULL", "! Boolean value 'a' is not recognized"},
            {"SELECT NULL AND 'a'", "! Boolean value 'a' is not recognized"},
            {"SELECT FALSE AND NULL", "false"},
        });
    }

    @Test
    public void aBlockSettlesTheConnectiveFromEitherSide() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN 'abc' AND FALSE; END; $$", "false"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN 1/0 = 1 AND FALSE; END; $$", "false"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN TRUE OR 'abc'; END; $$", "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 0; RETURN x = 0 OR 1/x = 1; END; $$", "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 0; LET y := x = 0 OR 1/x = 1; RETURN y; END; $$", "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN IF (TRUE OR 1/0 = 1) THEN RETURN 1; END IF; RETURN 2; END; $$", "1"},
            {"EXECUTE IMMEDIATE $$ BEGIN WHILE (FALSE AND 1/0 = 1) DO RETURN 1; END WHILE; RETURN 2; END; $$", "2"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT TRUE OR 1/0 = 1); END; $$", "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN NULL OR 1/0 = 1; END; $$", "! Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Division by zero"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN 'abc' OR TRUE; END; $$", "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN FALSE AND 'abc'; END; $$", "false"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 0; IF (1/x = 1 OR x = 0) THEN RETURN 1; END IF; RETURN 2; END; $$", "1"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN NOT ('abc' AND FALSE); END; $$", "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN 'abc' OR 'abc'; END; $$", "! Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Boolean value 'abc' is not recognized"},
        });
    }

    @Test
    public void aBlockNamesAFaultAtTheStartOfTheWholeExpression() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN 1 + 1/0; END; $$", "! Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Division by zero"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN NULL AND 1/0 = 1; END; $$", "! Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Division by zero"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN 1/0 = 1 OR NULL; END; $$", "! Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Division by zero"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 0; RETURN x = 1 OR 1/x = 1; END; $$", "! Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : Division by zero"},
            {"EXECUTE IMMEDIATE $$ BEGIN IF (NULL OR 1/0 = 1) THEN RETURN 1; END IF; RETURN 2; END; $$", "! Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 11 : Division by zero"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN 'x' || (1/0); END; $$", "! Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Division by zero"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN ABS(1/0); END; $$", "! Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Division by zero"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN NOT (1/0 = 1); END; $$", "! Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Division by zero"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN (NULL OR 1/0 = 1) AND TRUE; END; $$", "! Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Division by zero"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET y BOOLEAN := NULL OR 1/0 = 1; RETURN y; END; $$", "! Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : Division by zero"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN 'abc' AND NULL; END; $$", "! Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Boolean value 'abc' is not recognized"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN 1 + 1 + 1/0; END; $$", "! Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Division by zero"},
            {"EXECUTE IMMEDIATE $$ BEGIN WHILE (NULL AND 1/0 = 1) DO RETURN 1; END WHILE; RETURN 2; END; $$", "! Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Division by zero"},
        });
    }
}
