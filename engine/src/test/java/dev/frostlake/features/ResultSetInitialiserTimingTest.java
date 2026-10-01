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
 */package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A RESULTSET's initialiser runs where the variable is declared or assigned, not where it is first read. Its
 * rows are the ones the query answered then; a query that fails fails the declaration even when nothing
 * reads the variable, as a STATEMENT_ERROR at the query (at the opening parenthesis for an assignment); and
 * a DECLARE section's failure is not caught by the block's own handler. Every cell is live-verified.
 */
public class ResultSetInitialiserTimingTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTable() {
        engine.execute("CREATE TABLE t (x NUMBER)");
        engine.execute("INSERT INTO t VALUES (1), (2)");
    }

    private static String block(final String body) {
        return "EXECUTE IMMEDIATE $$" + body + "$$";
    }

    private void assertRefused(final String body, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(block(body));
            }
        }, body);
        assertTrue(String.valueOf(refused.getMessage()).contains(message),
            body + " should be refused with [" + message + "] but read: " + refused.getMessage());
    }

    /** Every row's first cell, joined by a bar. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder answer = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (answer.length() > 0) {
                answer.append(" | ");
            }
            answer.append(row.getValue(0));
        }
        return answer.toString();
    }

    @Test
    public void aFailingInitialiserFailsTheDeclarationAtItsQuery() {
        assertRefused("BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT ?'); RETURN TABLE(r); END;",
            "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : SQL compilation error: error line 1 at position 7\nBind variable ? not set.");
        assertRefused("BEGIN LET r RESULTSET := (SELECT 1/0); RETURN 5; END;",
            "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : Division by zero");
        assertRefused("BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT 1/0'); RETURN 5; END;",
            "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : Division by zero");
        assertRefused("BEGIN LET r RESULTSET := (SELECT missing FROM t); RETURN 5; END;",
            "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : SQL compilation error: error line 1 at position 7\ninvalid identifier 'MISSING'");
        assertRefused("DECLARE r RESULTSET DEFAULT (SELECT 1/0); BEGIN RETURN 5; END;",
            "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 29 : Division by zero");
        assertRefused("DECLARE r RESULTSET; BEGIN r := (SELECT 1/0); RETURN 5; END;",
            "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 32 : Division by zero");
        assertRefused("DECLARE rs RESULTSET DEFAULT (SELECT * FROM t_later); BEGIN CREATE TABLE t_later (y NUMBER); RETURN TABLE(rs); END;",
            "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 30 : SQL compilation error:\nObject 'T_LATER' does not exist or not authorized.");
        assertRefused("BEGIN\n  LET rs RESULTSET := (\n    SELECT 1/0);\n  RETURN 5;\nEND;",
            "Uncaught exception of type 'STATEMENT_ERROR' on line 3 at position 4 : Division by zero");
        assertRefused("DECLARE rs RESULTSET; BEGIN rs := (\n SELECT 1/0); RETURN 5; END;",
            "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 34 : Division by zero");
    }

    @Test
    public void aDeclareSectionFailureEscapesTheBlocksHandler() {
        assertRefused("DECLARE rs RESULTSET DEFAULT (SELECT 1/0); BEGIN RETURN 5; EXCEPTION WHEN OTHER THEN RETURN SQLERRM; END;",
            "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 30 : Division by zero");
        assertEquals("Division by zero", answer(block(
            "BEGIN LET rs RESULTSET := (SELECT 1/0); RETURN 5; EXCEPTION WHEN OTHER THEN RETURN SQLERRM; END;")));
    }

    @Test
    public void theRowsAreTheOnesTheQueryAnsweredThen() {
        engine.executeQuery(block("BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE 'INSERT INTO t VALUES (7)'); RETURN 5; END;"));
        assertEquals("3", answer("SELECT COUNT(*) FROM t"));
        assertEquals("1 | 2 | 7", answer(block(
            "BEGIN LET r RESULTSET := (SELECT x FROM t ORDER BY x); INSERT INTO t VALUES (8); RETURN TABLE(r); END;")));
        assertEquals("4", answer(block(
            "DECLARE rs RESULTSET; BEGIN rs := (SELECT COUNT(*) FROM t); INSERT INTO t VALUES (9); RETURN TABLE(rs); END;")));
        assertEquals("1", answer(block(
            "DECLARE rs RESULTSET DEFAULT (SELECT x FROM t ORDER BY x); c CURSOR FOR rs; v NUMBER; "
                + "BEGIN INSERT INTO t VALUES (-1); OPEN c; FETCH c INTO v; RETURN v; END;")));
    }
}
