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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A table, a view, a materialized view, a dynamic table and a stream share one name space in a schema: a
 * create over a name another of them holds is refused naming the holder's kind, {@code Object 'KT' already
 * exists as TABLE}, and neither OR REPLACE nor IF NOT EXISTS gets past it. A TEMPORARY table is the
 * exception — it may take a view's, a materialized view's or a dynamic table's name, though not a stream's,
 * and a TEMPORARY view may take a table's — and it then shadows the object it names until it is dropped.
 * A sequence keeps its own name space. Every cell is live-verified.
 */
public class BlockScopeTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    @Test
    public void everyBranchAndLoopBodyIsItsOwnScope() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P484_DB");
            assertEquals("2",
                rows("EXECUTE IMMEDIATE $$ BEGIN LET a := 1; IF (TRUE) THEN a := 2; END IF; RETURN a; END $$"));
            assertEquals("1",
                rows("EXECUTE IMMEDIATE $$ BEGIN LET a := 1; IF (TRUE) THEN LET a := 2; END IF; RETURN a; END $$"));
            assertEquals("1",
                rows("EXECUTE IMMEDIATE $$ BEGIN LET a := 1; BEGIN LET a := 2; END; RETURN a; END $$"));
            assertEquals("0",
                rows("EXECUTE IMMEDIATE $$ DECLARE i INT DEFAULT 0; BEGIN FOR i IN 1 TO 3 DO NULL; END FOR; RETURN i; END $$"));
            assertEquals("0",
                rows("EXECUTE IMMEDIATE $$ BEGIN LET z := 0; FOR i IN 1 TO 3 DO LET z := z + 1; END FOR; RETURN z; END $$"));
            assertEquals("3",
                rows("EXECUTE IMMEDIATE $$ BEGIN LET z := 0; FOR i IN 1 TO 3 DO z := z + 1; END FOR; RETURN z; END $$"));
            assertEquals("1",
                rows("EXECUTE IMMEDIATE $$ BEGIN LET a := 1; IF (FALSE) THEN LET a := 2; ELSE LET a := 3; END IF; RETURN a; END $$"));
            assertEquals("1",
                rows("EXECUTE IMMEDIATE $$ BEGIN LET a := 1; CASE WHEN TRUE THEN LET a := 2; END; RETURN a; END $$"));
            assertEquals("1",
                rows("EXECUTE IMMEDIATE $$ BEGIN LET a := 1; BEGIN LET b := 2; EXCEPTION WHEN OTHER THEN LET a := 9; END; RETURN a; END $$"));
            assertRefused("EXECUTE IMMEDIATE $$ BEGIN LET a := 1; IF (TRUE) THEN LET b := 2; END IF; RETURN b; END $$",
                "SQL compilation error: error line 1 at position 61\ninvalid identifier 'B'");
            assertEquals("1",
                rows("EXECUTE IMMEDIATE $$ BEGIN LET a := 1; IF (TRUE) THEN LET rs RESULTSET := (SELECT 1); END IF; RETURN a; END $$"));
            assertEquals("1",
                rows("EXECUTE IMMEDIATE $$ BEGIN LET a := 1; FOR i IN 1 TO 2 DO LET a := a + 10; END FOR; RETURN a; END $$"));
            assertEquals("1",
                rows("EXECUTE IMMEDIATE $$ BEGIN LET a := 1; IF (TRUE) THEN LET a := 2; a := 3; END IF; RETURN a; END $$"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P484_DB");
        }
    }
}
