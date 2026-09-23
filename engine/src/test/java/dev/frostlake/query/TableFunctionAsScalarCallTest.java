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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A table function written as a scalar call — a built-in such as GENERATOR, FLATTEN or SPLIT_TO_TABLE, with
 * positional or named arguments, or a user-defined table function — names no function at all: it is refused as
 * {@code Unknown function <NAME>.} while the statement compiles, whether or not a row reaches it, joins the other
 * unknown names in one sentence in written order, and ranks where every unknown function name ranks (live-verified).
 */
public class TableFunctionAsScalarCallTest extends BaseDatabaseTest {

    private static final String UNKNOWN = "SQL compilation error:\nUnknown function ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE rt (n INT, f FLOAT, g VARCHAR(5))");
        engine.execute("INSERT INTO rt VALUES (1, 1.5, 'a'), (2, 2.5, 'b')");
        engine.execute("CREATE TABLE em (n INT)");
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
    public void aTableFunctionCalledAsAScalarIsAnUnknownFunction() {
        assertEquals(UNKNOWN + "GENERATOR.", refusal("SELECT GENERATOR(ROWCOUNT => 1) FROM rt"));
        assertEquals(UNKNOWN + "GENERATOR.", refusal("SELECT GENERATOR(1) FROM rt"));
        assertEquals(UNKNOWN + "GENERATOR.", refusal("SELECT GENERATOR(ROWCOUNT => 1)"));
        assertEquals(UNKNOWN + "FLATTEN.", refusal("SELECT FLATTEN(INPUT => ARRAY_CONSTRUCT(1)) FROM rt"));
        assertEquals(UNKNOWN + "FLATTEN.", refusal("SELECT FLATTEN(ARRAY_CONSTRUCT(1)) FROM rt"));
        assertEquals(UNKNOWN + "SPLIT_TO_TABLE.", refusal("SELECT SPLIT_TO_TABLE('a,b', ',') FROM rt"));
        assertEquals(UNKNOWN + "RESULT_SCAN.", refusal("SELECT RESULT_SCAN(LAST_QUERY_ID())"));
        // Wherever it is written, nested or not.
        assertEquals(UNKNOWN + "GENERATOR.", refusal("SELECT 1 FROM rt WHERE GENERATOR(ROWCOUNT => 1)"));
        assertEquals(UNKNOWN + "GENERATOR.", refusal("SELECT 1 FROM rt ORDER BY GENERATOR(ROWCOUNT => 1)"));
        assertEquals(UNKNOWN + "GENERATOR.", refusal("SELECT n FROM rt GROUP BY n HAVING GENERATOR(ROWCOUNT => 1)"));
        assertEquals(UNKNOWN + "GENERATOR.", refusal("SELECT 1 FROM rt a JOIN rt b ON GENERATOR(ROWCOUNT => 1)"));
        assertEquals(UNKNOWN + "GENERATOR.", refusal("SELECT ABS(GENERATOR(ROWCOUNT => 1)) FROM rt"));
        assertEquals(UNKNOWN + "GENERATOR.", refusal("SELECT (SELECT GENERATOR(ROWCOUNT => 1)) FROM rt"));
    }

    @Test
    public void theRefusalNeedsNoRow() {
        assertEquals(UNKNOWN + "GENERATOR.", refusal("SELECT GENERATOR(ROWCOUNT => 1) FROM em"));
    }

    @Test
    public void aUserDefinedTableFunctionIsNoScalar() {
        engine.execute("CREATE OR REPLACE FUNCTION tf() RETURNS TABLE (x INT) AS 'SELECT 1'");
        assertEquals(UNKNOWN + "TF.", refusal("SELECT tf() FROM rt"));
        assertEquals(UNKNOWN + "TF.", refusal("SELECT tf() FROM rt QUALIFY 1 = 1"));
        assertEquals("1", String.valueOf(engine.executeQuery("SELECT x FROM TABLE(tf())").getRows().get(0).getValue(0)));
    }

    @Test
    public void itRanksWithTheOtherUnknownNames() {
        // Ahead of a QUALIFY without a window, an arity fault and an aggregate in a WHERE.
        assertEquals(UNKNOWN + "GENERATOR.", refusal("SELECT GENERATOR(ROWCOUNT => 1) FROM rt QUALIFY 1 = 1"));
        assertEquals(UNKNOWN + "GENERATOR.", refusal("SELECT GENERATOR(ROWCOUNT => 1) QUALIFY 1 = 1"));
        assertEquals(UNKNOWN + "FLATTEN.", refusal("SELECT FLATTEN(INPUT => ARRAY_CONSTRUCT(1)) FROM rt QUALIFY 1 = 1"));
        assertEquals(UNKNOWN + "GENERATOR.", refusal("SELECT GENERATOR(ROWCOUNT => 1), ABS(1, 2) FROM rt"));
        assertEquals(UNKNOWN + "GENERATOR.", refusal("SELECT ABS(1, 2), GENERATOR(ROWCOUNT => 1) FROM rt"));
        assertEquals(UNKNOWN + "GENERATOR.", refusal("SELECT GENERATOR(ROWCOUNT => 1) FROM rt WHERE SUM(n) > 1"));
        // One sentence names every unknown function, in written order.
        assertEquals("SQL compilation error:\nUnknown functions GENERATOR, NOSUCHFN.",
            refusal("SELECT GENERATOR(ROWCOUNT => 1), NOSUCHFN(1) FROM rt"));
        assertEquals("SQL compilation error:\nUnknown functions NOSUCHFN, GENERATOR.",
            refusal("SELECT NOSUCHFN(1), GENERATOR(ROWCOUNT => 1) FROM rt"));
        // Behind a name nothing resolves and an ordinal past the list.
        assertEquals("SQL compilation error: error line 1 at position 33\ninvalid identifier 'NOSUCH'",
            refusal("SELECT GENERATOR(ROWCOUNT => 1), nosuch FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 29\ninvalid identifier 'NOSUCH'",
            refusal("SELECT GENERATOR(ROWCOUNT => nosuch) FROM rt"));
        assertEquals("SQL compilation error:\n[9] is not a valid order by expression",
            refusal("SELECT GENERATOR(ROWCOUNT => 1) FROM rt ORDER BY 9"));
    }
}
