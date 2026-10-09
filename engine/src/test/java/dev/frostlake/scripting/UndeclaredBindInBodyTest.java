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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A {@code :name} no variable declares is the block's compilation error wherever the statement that reads it
 * stands: in the block itself, in an IF or a loop body that never runs, in a handler, and as the argument of an
 * IDENTIFIER() reference. It is an invalid identifier at the colon, placed in the block, a SELECT … INTO's place
 * counted without its INTO clause.
 */
public class UndeclaredBindInBodyTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO FZ VALUES (1, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE T (a INT, b INT)");
    }

    private String refusalOf(final String block) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("EXECUTE IMMEDIATE $$ " + block + " $$");
            }
        }, block).getMessage();
    }

    private static String undeclared(final int position) {
        return "SQL compilation error: error line 1 at position " + position + "\ninvalid identifier 'NOSUCH'";
    }

    @Test
    public void theBlocksOwnStatementsAreJudged() {
        assertEquals(undeclared(42), refusalOf("BEGIN SELECT COUNT(*) FROM FZ WHERE id = :nosuch; END;"));
        assertEquals(undeclared(57),
            refusalOf("DECLARE c INT; BEGIN SELECT COUNT(*) FROM FZ WHERE id = :nosuch; RETURN c; END;"));
        assertEquals(undeclared(57),
            refusalOf("DECLARE c INT; BEGIN SELECT COUNT(*) INTO :c FROM FZ WHERE id = :nosuch; RETURN c; END;"));
        assertEquals(undeclared(74), refusalOf("DECLARE c INT; BEGIN SELECT COUNT(*) INTO :c FROM FZ WHERE id = 1"
            + " AND IDENTIFIER(:nosuch) = 1; RETURN c; END;"));
        assertEquals(undeclared(36), refusalOf("BEGIN UPDATE T SET a = 1 WHERE b = :nosuch; END;"));
    }

    @Test
    public void aBodyIsJudgedWhetherOrNotItRuns() {
        assertEquals(undeclared(57),
            refusalOf("BEGIN IF (TRUE) THEN SELECT COUNT(*) FROM FZ WHERE id = :nosuch; END IF; END;"));
        assertEquals(undeclared(72), refusalOf("DECLARE c INT; BEGIN IF (TRUE) THEN SELECT COUNT(*) INTO :c FROM FZ"
            + " WHERE id = :nosuch; END IF; RETURN c; END;"));
        assertEquals(undeclared(40), refusalOf("BEGIN IF (FALSE) THEN UPDATE T SET a = :nosuch; END IF; END;"));
        assertEquals(undeclared(31), refusalOf("BEGIN WHILE (FALSE) DO SELECT :nosuch; END WHILE; END;"));
        assertEquals(undeclared(50), refusalOf("BEGIN SELECT 1; EXCEPTION WHEN OTHER THEN SELECT :nosuch; END;"));
    }

    @Test
    public void anIdentifierReferencesBindIsOneToo() {
        assertEquals(undeclared(54),
            refusalOf("BEGIN IF (TRUE) THEN SELECT COUNT(*) FROM IDENTIFIER(:nosuch); END IF; END;"));
        assertEquals(undeclared(69), refusalOf("DECLARE c INT; BEGIN IF (TRUE) THEN SELECT COUNT(*) INTO :c FROM"
            + " IDENTIFIER(:nosuch); END IF; RETURN c; END;"));
        assertEquals(undeclared(73), refusalOf("DECLARE c INT; BEGIN FOR i IN 1 TO 1 DO SELECT COUNT(*) INTO :c FROM"
            + " IDENTIFIER(:nosuch); END FOR; RETURN c; END;"));
    }

    @Test
    public void aDeclaredNameBindsInABody() {
        assertEquals("1", String.valueOf(engine.executeQuery("EXECUTE IMMEDIATE $$ DECLARE c INT DEFAULT 7;"
            + " tn VARCHAR DEFAULT 'FZ'; BEGIN IF (TRUE) THEN SELECT COUNT(*) INTO :c FROM IDENTIFIER(:tn)"
            + " WHERE id = :c; END IF; RETURN c; END; $$").getRows().get(0).getValue(0)));
    }
}
