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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code SELECT … INTO} carries WHERE, GROUP BY, HAVING and QUALIFY without a FROM, exactly as a
 * plain FROM-less SELECT does. The clauses used to be reachable only behind a FROM, so ordinary block
 * SQL — {@code SELECT 1 INTO :x WHERE 1 = 1} — was a syntax error.
 *
 * <p>A variable may stand in the INTO clause only once, and that is refused while the BLOCK compiles:
 * an unreachable branch is refused too, and the refusal carries no statement-error wrapper.
 */
public class SelectIntoWithoutFromTest extends BaseDatabaseTest {

    /** A block around {@code body}, run through EXECUTE IMMEDIATE, answering its RETURN. */
    private String inBlock(final String body) {
        final ResultSet rs = engine.executeQuery(
            "EXECUTE IMMEDIATE $$DECLARE x INT; BEGIN " + body + " RETURN x; END$$");
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** The message of the refusal a statement raises. */
    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                final ResultSet rs = engine.executeQuery(sql);
                while (rs.next()) {
                    continue;
                }
            }
        }).getMessage().replace("\n", " ");
    }

    /** A WHERE with no FROM runs, and decides whether the target is assigned at all. */
    @Test
    public void aWhereStandsWithoutAFrom() {
        assertEquals("1", inBlock("SELECT 1 INTO :x WHERE 1 = 1;"));
        assertEquals("null", inBlock("SELECT 1 INTO :x WHERE 1 = 0;"),
            "no row, so the variable keeps its unassigned NULL");
        assertEquals("1", inBlock("SELECT 1 INTO :x FROM (SELECT 1) WHERE 1 = 1;"),
            "with a FROM it always worked");
    }

    /** GROUP BY, HAVING and QUALIFY stand without one too. */
    @Test
    public void theGroupingClausesStandWithoutAFrom() {
        assertEquals("1", inBlock("SELECT 1 INTO :x GROUP BY 1;"));
        assertEquals("1", inBlock("SELECT MAX(1) INTO :x HAVING MAX(1) = 1;"));
        assertEquals("1", inBlock("SELECT 1 INTO :x QUALIFY ROW_NUMBER() OVER (ORDER BY 1) = 1;"));
    }

    /** ORDER BY and LIMIT still follow them, alone or after a WHERE. */
    @Test
    public void theTailClausesStillFollow() {
        assertEquals("1", inBlock("SELECT 1 INTO :x ORDER BY 1;"));
        assertEquals("1", inBlock("SELECT 1 INTO :x LIMIT 1;"));
        assertEquals("1", inBlock("SELECT 1 INTO :x WHERE 1 = 1 ORDER BY 1;"));
    }

    /** One variable may stand in the INTO clause only once. */
    @Test
    public void aRepeatedTargetIsRefused() {
        assertTrue(refusal("EXECUTE IMMEDIATE $$DECLARE x INT; BEGIN "
            + "SELECT 1, 2 INTO :x, :x WHERE 1 = 1; RETURN x; END$$")
            .endsWith("SQL compilation error: error line 1 at position 21  "
                + "Repeated variable with name 'X' inside the INTO clause."),
            "no statement-error wrapper: the block did not get as far as running it");
    }

    /** It is refused while the block COMPILES, so a branch this run never takes is refused too. */
    @Test
    public void aRepeatedTargetIsRefusedOnAnUnreachableBranch() {
        assertTrue(refusal("EXECUTE IMMEDIATE $$DECLARE x INT; BEGIN "
            + "IF (FALSE) THEN SELECT 1, 2 INTO :x, :x; END IF; RETURN x; END$$")
            .endsWith("SQL compilation error: error line 1 at position 37  "
                + "Repeated variable with name 'X' inside the INTO clause."));
    }

    /** A name the block never declared is judged BEFORE the repeated target. */
    @Test
    public void anUndeclaredNameIsJudgedFirst() {
        assertTrue(refusal("EXECUTE IMMEDIATE $$DECLARE x INT; BEGIN "
            + "LET y INT := :nosuch; SELECT 1, 2 INTO :x, :x; RETURN x; END$$")
            .contains("invalid identifier 'nosuch'"));
    }

    /**
     * A bind in the statement itself is judged while the block compiles too, before the repeated target and before
     * any table is looked up: the statement compiles without its INTO clause, so the bind is named as an identifier
     * where it stands once the clause is cut out.
     */
    @Test
    public void anUndeclaredBindInTheStatementIsJudgedFirst() {
        assertTrue(refusal("EXECUTE IMMEDIATE $$DECLARE x INT; BEGIN "
            + "SELECT 1, 2 INTO :x, :x WHERE :nosuch = 1; RETURN x; END$$")
            .endsWith("SQL compilation error: error line 1 at position 39 invalid identifier 'NOSUCH'"));
        assertTrue(refusal("EXECUTE IMMEDIATE $$DECLARE x INT; BEGIN "
            + "SELECT 1 INTO :x FROM no_such_table WHERE :nosuch = 1; RETURN x; END$$")
            .endsWith("SQL compilation error: error line 1 at position 55 invalid identifier 'NOSUCH'"));
        // The position ends its line, and the sentence opens the next.
        assertTrue(assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                engine.executeQuery("EXECUTE IMMEDIATE $$DECLARE x INT; BEGIN "
                    + "SELECT 1, 2 INTO :x, :x; RETURN x; END$$");
            }
        }).getMessage().endsWith("error line 1 at position 21\n Repeated variable with name 'X' inside the INTO clause."));
        // A missing table is no compile-time fault: the repeated target still speaks first.
        assertTrue(refusal("EXECUTE IMMEDIATE $$DECLARE x INT; BEGIN "
            + "SELECT 1, 2 INTO :x, :x FROM no_such_table; RETURN x; END$$")
            .endsWith("SQL compilation error: error line 1 at position 21  "
                + "Repeated variable with name 'X' inside the INTO clause."));
    }

    /** Outside a block the INTO clause is refused for being there at all, WHERE or no WHERE. */
    @Test
    public void theTopLevelRefusalIsUnchanged() {
        assertEquals("SQL compilation error: error line 1 at position 0 "
            + "INTO clause is not allowed in this context",
            refusal("SELECT 1 INTO t, u WHERE 1 = 1"));
    }

    /** The plain FROM-less SELECT these follow is unchanged. */
    @Test
    public void thePlainFromlessSelectIsUnchanged() {
        assertEquals("1", firstCell("SELECT 1 WHERE 1 = 1"));
        assertEquals("1", firstCell("SELECT 1 GROUP BY 1"));
        assertEquals("1", firstCell("SELECT MAX(1) HAVING MAX(1) = 1"));
        assertEquals("1", firstCell("SELECT 1 QUALIFY ROW_NUMBER() OVER (ORDER BY 1) = 1"));
    }

    /** The first column of the first row, as text. */
    private String firstCell(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }
}
