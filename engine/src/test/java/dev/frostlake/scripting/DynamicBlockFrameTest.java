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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An EXECUTE IMMEDIATE's dynamic text is its OWN execution frame. A block inside that text is not
 * nested in whatever block asked for it, so its RETURN ends that block and becomes the EXECUTE
 * IMMEDIATE's value, leaving the caller running — and a RESULTSET filled from one holds the block's
 * {@code anonymous block} answer rather than a status sentence.
 *
 * <pre>
 *   LET r RESULTSET := (EXECUTE IMMEDIATE $$ BEGIN RETURN 7; END; $$)   one row, 7
 *   the same with no RETURN                                             one row, NULL
 * </pre>
 *
 * <p>Frostlake used to answer {@code Statement executed successfully.} for both, because the caller's
 * own block made the dynamic one look nested: the inner RETURN was left in the return state and ended
 * the CALLER instead, and the no-RETURN row was suppressed outright.
 */
public class DynamicBlockFrameTest extends BaseDatabaseTest {

    /** The first column's name, its type and the rows, as {@code name:TYPE [values]}. */
    private String shape(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder(rs.getColumns().get(0).getName());
        out.append(':').append(rs.getColumns().get(0).getDataType() == null ? "null"
            : rs.getColumns().get(0).getDataType().getName()).append(" [");
        boolean first = true;
        while (rs.next()) {
            if (!first) {
                out.append(" | ");
            }
            first = false;
            out.append(String.valueOf(rs.getValue(0)));
        }
        return out.append(']').toString();
    }

    /** A RESULTSET filled from a block holds the value that block returned. */
    @Test
    public void aResultSetFromABlockHoldsTheBlocksValue() {
        assertEquals("anonymous block:NUMBER [7]", shape(
            "BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE $$ BEGIN RETURN 7; END; $$);"
            + " RETURN TABLE(r); END;"));
        assertEquals("anonymous block:VARCHAR [ab]", shape(
            "BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE $$ BEGIN RETURN 'ab'; END; $$);"
            + " RETURN TABLE(r); END;"));
    }

    /** A block that ran to its end without a RETURN still answers its one NULL row. */
    @Test
    public void aBlockWithoutAReturnStillAnswersItsRow() {
        assertEquals("anonymous block:VARCHAR [null]", shape(
            "BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE $$ BEGIN LET x := 1; END; $$);"
            + " RETURN TABLE(r); END;"));
    }

    /** The DECLARE-then-assign spelling answers the same, as it always did. */
    @Test
    public void theDeclareThenAssignSpellingAgrees() {
        assertEquals("anonymous block:NUMBER [7]", shape(
            "DECLARE r RESULTSET; BEGIN r := (EXECUTE IMMEDIATE $$ BEGIN RETURN 7; END; $$);"
            + " RETURN TABLE(r); END;"));
    }

    /**
     * The inner RETURN does not end the CALLER: the statements after it still run, and the caller's own
     * RETURN is what the script answers. The row count proves the RESULTSET really holds one row.
     */
    @Test
    public void theInnerReturnDoesNotEndTheCaller() {
        assertEquals("anonymous block:VARCHAR [caller still running]", shape(
            "BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE $$ BEGIN RETURN 7; END; $$);"
            + " RETURN 'caller still running'; END;"));
        assertEquals("anonymous block:NUMBER [1]", shape(
            "BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE $$ BEGIN RETURN 7; END; $$);"
            + " LET c INT := 0; FOR rec IN r DO c := c + 1; END FOR; RETURN c; END;"));
        assertEquals("anonymous block:NUMBER [7]", shape(
            "BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE $$ BEGIN RETURN 7; END; $$);"
            + " LET after INT := 5; RETURN TABLE(r); END;"));
    }

    /** A query in the dynamic text is untouched — the frame only decides what a BLOCK answers. */
    @Test
    public void aQuerySourceIsUnchanged() {
        assertEquals("N:NUMBER [5]", shape(
            "BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE $$ SELECT 5 AS N $$); RETURN TABLE(r); END;"));
        assertEquals("M:NUMBER [9]", shape(
            "BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE $$ SELECT 9 AS M $$); RETURN TABLE(r); END;"));
        assertEquals("K:NUMBER [3]", shape(
            "BEGIN LET r RESULTSET := (SELECT 3 AS K); RETURN TABLE(r); END;"));
    }

    /** And a block run directly, or as the whole statement, answers exactly as it did before. */
    @Test
    public void aBlockRunDirectlyIsUnchanged() {
        assertEquals("anonymous block:VARCHAR [null]", shape("BEGIN LET x := 1; END;"));
        assertEquals("anonymous block:NUMBER [7]", shape("BEGIN RETURN 7; END;"));
        assertEquals("anonymous block:NUMBER [7]",
            shape("EXECUTE IMMEDIATE $$ BEGIN RETURN 7; END; $$"));
        assertEquals("anonymous block:VARCHAR [null]",
            shape("EXECUTE IMMEDIATE $$ BEGIN LET x := 1; END; $$"));
    }
}
