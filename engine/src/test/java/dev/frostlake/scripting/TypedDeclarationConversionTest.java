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
 * A value given to a variable of a declared type converts by the plain cast, so a pair the cast matrix refuses is
 * refused in the cast's own words when the declaration or assignment is reached: an EXPRESSION_ERROR at the value,
 * after the statements ahead of it ran. Every cell is live-verified.
 */
public class TypedDeclarationConversionTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    private String answer(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        return String.valueOf(row.getValue(0));
    }

    private static String block(final String body) {
        return "EXECUTE IMMEDIATE $$ " + body + " $$";
    }

    private static String refused(final int at, final String echo, final String target, final String function) {
        return "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position " + at + " : SQL compilation error:\n"
            + "invalid type [CAST(" + echo + " AS " + target + ")] for parameter '" + function + "'";
    }

    @Test
    public void aPairTheCastRefusesIsRefusedAtTheValue() {
        final String[][] cells = {
            {"DECLARE x DATE DEFAULT TRUE; BEGIN RETURN x; END;", "24", "TRUE", "DATE", "TO_DATE"},
            {"BEGIN LET x DATE := TRUE; RETURN x; END;", "21", "TRUE", "DATE", "TO_DATE"},
            {"BEGIN LET x TIMESTAMP := TRUE; RETURN x; END;", "26", "TRUE", "TIMESTAMP_NTZ(9)", "TO_TIMESTAMP_NTZ"},
            {"BEGIN LET x TIMESTAMP := 1 = 1; RETURN x; END;", "26", "1 = 1", "TIMESTAMP_NTZ(9)", "TO_TIMESTAMP_NTZ"},
            {"BEGIN LET x TIMESTAMP := TRUE AND FALSE; RETURN x; END;", "26", "TRUE AND FALSE", "TIMESTAMP_NTZ(9)",
                "TO_TIMESTAMP_NTZ"},
            {"DECLARE x TIMESTAMP; BEGIN x := TRUE; RETURN x; END;", "33", "TRUE", "TIMESTAMP_NTZ(9)", "TO_TIMESTAMP_NTZ"},
            {"BEGIN LET x BINARY := TRUE; RETURN x; END;", "23", "TRUE", "BINARY(67108864)", "TO_BINARY"},
            {"DECLARE x FLOAT DEFAULT TRUE; BEGIN RETURN x; END;", "25", "TRUE", "FLOAT", "TO_DOUBLE"},
            {"BEGIN LET x TIME := TRUE; RETURN x; END;", "21", "TRUE", "TIME(9)", "TO_TIME"},
            {"BEGIN LET x DATE := 5; RETURN x; END;", "21", "5", "DATE", "TO_DATE"},
            {"BEGIN LET x BOOLEAN := TO_DATE('2024-01-01'); RETURN x; END;", "24", "TO_DATE('2024-01-01')", "BOOLEAN",
                "TO_BOOLEAN"},
            {"BEGIN LET b := TRUE; LET x DATE := b; RETURN x; END;", "36", "TO_BOOLEAN('true')", "DATE", "TO_DATE"},
            {"BEGIN LET b := TRUE; LET x DATE := :b; RETURN x; END;", "36", "TO_BOOLEAN('true')", "DATE", "TO_DATE"},
            {"BEGIN LET d := TO_DATE('2024-01-01'); d := TRUE; RETURN d; END;", "44", "TRUE", "DATE", "TO_DATE"},
            {"BEGIN LET x DATE := IFF(TRUE, TRUE, FALSE); RETURN x; END;", "21",
                "IFF(BOOLEAN_TO_ROWINDEX(TRUE), TRUE, FALSE)", "DATE", "TO_DATE"},
        };
        for (final String[] cell : cells) {
            assertEquals(refused(Integer.parseInt(cell[1]), cell[2], cell[3], cell[4]), refusal(block(cell[0])), cell[0]);
        }
    }

    @Test
    public void theStatementsAheadOfItRan() {
        assertEquals(refused(63, "TRUE", "DATE", "TO_DATE"),
            refusal(block("BEGIN CREATE OR REPLACE TABLE tdc_mark (x INT); LET y DATE := TRUE; RETURN 1; END;")));
        assertEquals(1, engine.executeQuery("SHOW TABLES LIKE 'TDC_MARK'").getRows().size());
    }

    @Test
    public void anExpressionErrorHandlerCatchesIt() {
        assertEquals("1007 22023", answer(block("BEGIN LET x DATE := TRUE; RETURN x; EXCEPTION WHEN EXPRESSION_ERROR THEN"
            + " RETURN SQLCODE || ' ' || SQLSTATE; END;")));
    }

    @Test
    public void whatTheCastTakesConverts() {
        assertEquals("1970-01-01 00:00:05", answer(block("BEGIN LET x TIMESTAMP := 5; RETURN TO_VARCHAR(x); END;"))
            .replace(".000", ""));
        assertEquals("1", answer(block("BEGIN LET x NUMBER := TRUE; RETURN x; END;")));
        assertEquals("null", String.valueOf(engine.executeQuery(block("BEGIN LET x DATE := NULL; RETURN x; END;"))
            .getRows().get(0).getValue(0)));
        assertEquals("1", answer(block("BEGIN IF (FALSE) THEN LET x DATE := TRUE; END IF; RETURN 1; END;")));
    }
}
