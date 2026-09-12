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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An untyped declaration takes its type from its initialiser while the block COMPILES, and a scalar
 * subquery that READS A RELATION gives it none — whatever the subquery selects, and whether or not its
 * columns resolve. The block is refused at the declaration, before any statement of it runs. A FROM-less
 * subquery types the variable normally, and so does a declaration that states its own type.
 * Live-verified.
 */
public class UntypedLetSubqueryTest extends BaseDatabaseTest {

    /** Runs an anonymous block and returns its RETURN value as text. */
    private String block(final String body) {
        final ResultSet rs = engine.executeQuery("EXECUTE IMMEDIATE $$ " + body + " $$");
        assertEquals(1, rs.getRowCount(), body);
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString();
    }

    /** Asserts a block is refused with a message carrying {@code fragment}. */
    private void assertRefused(final String body, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("EXECUTE IMMEDIATE $$ " + body + " $$");
            }
        }, body);
        assertTrue(refused.getMessage() != null && refused.getMessage().contains(fragment),
            body + " should be refused with \"" + fragment + "\" but read: " + refused.getMessage());
    }

    private static final String NO_TYPE = "variable 'A' cannot have its type inferred from initializer";

    /** A subquery that reads a relation types nothing, however well it resolves. */
    @Test
    public void aSubqueryOverARelationTypesNothing() {
        engine.execute("CREATE OR REPLACE TABLE let_source (n NUMBER, s VARCHAR)");
        engine.execute("INSERT INTO let_source VALUES (1, 'x')");
        assertRefused("BEGIN LET a := (SELECT n FROM let_source); RETURN a; END;", NO_TYPE);
        assertRefused("BEGIN LET a := (SELECT 1 FROM let_source); RETURN a; END;", NO_TYPE);
        assertRefused("BEGIN LET a := (SELECT MAX(n) FROM let_source); RETURN a; END;", NO_TYPE);
        assertRefused("BEGIN LET a := (SELECT n FROM let_source WHERE 1 = 0); RETURN a; END;", NO_TYPE);
        assertRefused("BEGIN LET a := (SELECT b FROM (SELECT 1 AS b)); RETURN a; END;", NO_TYPE);
    }

    /** Nor does one whose columns resolve to nothing — the same sentence, not the column's. */
    @Test
    public void anUnresolvableSubqueryGivesTheSameRefusal() {
        assertRefused("BEGIN LET a := (SELECT missing FROM (SELECT 1 AS b)); RETURN 5; END;", NO_TYPE);
        assertRefused("BEGIN LET a := (SELECT x FROM nosuchtable); RETURN 5; END;", NO_TYPE);
    }

    /** The refusal lands while the block compiles: nothing before the declaration runs. */
    @Test
    public void nothingBeforeTheDeclarationRuns() {
        engine.execute("CREATE OR REPLACE TABLE let_mark (n NUMBER)");
        assertRefused("BEGIN INSERT INTO let_mark VALUES (1);"
            + " LET a := (SELECT missing FROM (SELECT 1 AS b)); RETURN 5; END;", NO_TYPE);
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM let_mark");
        assertEquals("0", rs.getRows().get(0).getValue(0).toString());
    }

    /** A FROM-less subquery types the variable, and so does a declaration that states its own type. */
    @Test
    public void aFromLessSubqueryAndATypedDeclarationAreTaken() {
        engine.execute("CREATE OR REPLACE TABLE let_typed (n NUMBER)");
        engine.execute("INSERT INTO let_typed VALUES (1)");
        assertEquals("1", block("BEGIN LET a := (SELECT 1); RETURN a; END;"));
        assertEquals("2", block("BEGIN LET a := (SELECT 1 + 1); RETURN a; END;"));
        assertEquals("1", block("BEGIN LET a NUMBER := (SELECT n FROM let_typed); RETURN a; END;"));
        assertEquals("1", block("DECLARE a NUMBER; BEGIN a := (SELECT n FROM let_typed); RETURN a; END;"));
    }

    /** A bare unknown name is untypable in the same words — the rule this one joins. */
    @Test
    public void aBareUnknownNameIsUntypableToo() {
        assertRefused("BEGIN LET a := missing; RETURN 5; END;", NO_TYPE);
    }
}
