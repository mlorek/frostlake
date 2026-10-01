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

/**
 * A variable declared in an IF or CASE branch, a loop body, a nested block or an exception handler hides an outer
 * variable of the same name only until that body ends: the outer variable's type governs again after it, both
 * while the block compiles (an untyped declaration's inferred type) and when an expression is judged or a value
 * converted as the block runs. Every cell is live-verified.
 */
public class BlockVariableScopeTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    private String answer(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        return String.valueOf(row.getValue(0));
    }

    private static String block(final String body) {
        return "EXECUTE IMMEDIATE $$" + body + "$$";
    }

    @Test
    public void anInnerDeclarationEndsWithItsBody() {
        final String[][] cells = {
            {"BEGIN LET x := 1; BEGIN LET x := TRUE; END; LET y := x + 1; RETURN y; END;", "2"},
            {"BEGIN LET x := 1; IF (FALSE) THEN LET x := TRUE; END IF; LET y := x + 1; RETURN y; END;", "2"},
            {"BEGIN LET x := 1; IF (TRUE) THEN LET x := TRUE; END IF; RETURN x + 1; END;", "2"},
            {"BEGIN LET x := 1; IF (TRUE) THEN LET x DATE := '2024-01-01'; END IF; x := 5; RETURN x; END;", "5"},
            {"BEGIN LET x := 1; FOR i IN 1 TO 1 DO LET x := TRUE; END FOR; LET y := x + 1; RETURN y; END;", "2"},
            {"BEGIN LET x := 1; BEGIN LET x := TRUE; END; RETURN x + 1; END;", "2"},
            {"BEGIN LET x := 1; IF (TRUE) THEN LET x := TRUE; END IF; BEGIN LET y := x + 1; RETURN y; END; END;", "2"},
            {"BEGIN IF (FALSE) THEN LET m := 'a'; RETURN m || 'b'; ELSE LET m := 5; LET q := m + 1; RETURN q; END IF;"
                + " END;", "6"},
            {"BEGIN LET x := 1; LET n := 0; WHILE (n < 2) DO LET x := TRUE; n := n + 1; END WHILE; RETURN x + n; END;",
                "3"},
            {"BEGIN LET x := 1; CASE WHEN TRUE THEN LET x := 'a'; END CASE; RETURN x + 1; END;", "2"},
            {"BEGIN LET x := 1; BEGIN LET z := 1/0; EXCEPTION WHEN OTHER THEN LET x := TRUE; END; RETURN x + 1; END;",
                "2"},
            {"DECLARE x NUMBER DEFAULT 1; BEGIN BEGIN LET x VARCHAR := 'a'; END; x := '5'; RETURN x; END;", "5"},
            {"BEGIN LET x := 1; IF (TRUE) THEN LET x := 'abc'; END IF; RETURN x; END;", "1"},
            {"BEGIN LET x := 1; IF (TRUE) THEN x := 2; END IF; RETURN x; END;", "2"},
            {"BEGIN LET i := 'a'; FOR i IN 1 TO 2 DO LET z := i + 1; END FOR; LET y := i || 'b'; RETURN y; END;", "ab"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(block(cell[0])), cell[0]);
        }
        assertEquals("FALSE", answer(block(
            "BEGIN LET i := TRUE; FOR i IN 1 TO 2 DO LET z := 1; END FOR; LET y := NOT i; RETURN y; END;")).toUpperCase());
    }

    @Test
    public void anInnerDeclarationGovernsInsideItsBody() {
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 46 : SQL compilation error: error"
                + " line 1 at position 13\nInvalid argument types for function '+': (BOOLEAN, NUMBER(1,0))",
            refusal(block("BEGIN LET x := 1; BEGIN LET x := TRUE; RETURN x + 1; END; END;")));
    }

    @Test
    public void theOuterTypeIsInferredAfterTheBody() {
        assertEquals("SQL compilation error: error line 1 at position 62\n variable 'W' cannot have its type inferred"
                + " from initializer",
            refusal(block("BEGIN LET v VARIANT := 1; IF (FALSE) THEN LET v := 5; END IF; LET w := v; RETURN 1; END;")));
    }

    @Test
    public void aShadowedParameterGovernsAfterTheBranch() {
        engine.execute("CREATE OR REPLACE PROCEDURE bvs_p(d DATE) RETURNS VARCHAR LANGUAGE SQL AS"
            + " $$BEGIN IF (TRUE) THEN LET d := 5; END IF; RETURN d + 1; END;$$");
        assertEquals("2024-01-02", answer("CALL bvs_p('2024-01-01')"));
    }
}
