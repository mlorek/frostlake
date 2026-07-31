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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * EXECUTE IMMEDIATE '<sql with ? placeholders>' USING (v1, v2, ...) — Snowflake-style positional bind
 * variables. USING is Snowflake SCRIPTING syntax: it is legal only inside a BEGIN…END block and a
 * session-level EXECUTE IMMEDIATE rejects it, so the covered paths are procedural with a literal source
 * and with a :variable source, plus quote-escaping of a string bind. EXECUTE IMMEDIATE is a statement,
 * not an expression: embedding it in a RETURN expression is a syntax error.
 */
public class ExecuteImmediateUsingTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void usingIsRejectedInATopLevelStatement() {
        // Live-verified: USING is Snowflake Scripting syntax — at session level EXECUTE IMMEDIATE takes
        // no USING clause and fails with "Unsupported statement type 'EXECUTE'".
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("EXECUTE IMMEDIATE 'INSERT INTO t VALUES (?, ?)' USING (1, 'Alice')");
            }
        });
        assertEquals(0, engine.executeQuery("SELECT name FROM t").getRowCount());
    }

    /**
     * The two live wordings of a session-level USING rejection, measured one statement per cell:
     * an argument that is not a bare name is a SYNTAX error at that argument's own position, while
     * a list of names parses and then fails "Unsupported statement type 'EXECUTE'.".
     */
    @Test
    public void topLevelUsingWordingsMatchLive() {
        final RuntimeException literalArg = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("EXECUTE IMMEDIATE 'SELECT ?' USING (1)");
            }
        });
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 36 unexpected '1'.",
            literalArg.getMessage());

        final RuntimeException nameArg = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("EXECUTE IMMEDIATE 'SELECT ?' USING (myv)");
            }
        });
        assertEquals("SQL compilation error:\nUnsupported statement type 'EXECUTE'.",
            nameArg.getMessage());
    }

    @Test
    public void usingBindsVariablesInProcedureStatement() {
        engine.execute("CREATE TABLE u (id INTEGER, name VARCHAR)");
        engine.executeQuery(
            "DECLARE i INTEGER DEFAULT 7; nm STRING DEFAULT 'Bob'; "
            + "BEGIN "
            + "  EXECUTE IMMEDIATE 'INSERT INTO u VALUES (?, ?)' USING (i, nm); "
            + "  RETURN 'ok'; "
            + "END");
        final ResultSet rs = engine.executeQuery("SELECT name FROM u WHERE id = 7");
        assertEquals("Bob", rs.getRows().get(0).getValue(0).toString());
    }

    @Test
    public void usingBindsWithVariableSqlSourceInBlock() {
        engine.execute("CREATE TABLE p (id INTEGER, price INTEGER)");
        engine.execute("INSERT INTO p VALUES (1, 50), (2, 150), (3, 250)");
        engine.execute("CREATE TABLE p_out (cnt INTEGER)");
        // The dynamic SQL text arrives via a :variable and the bind value via USING.
        engine.executeQuery(
            "DECLARE minp INTEGER DEFAULT 100; "
            + "        q STRING DEFAULT 'INSERT INTO p_out SELECT COUNT(*) FROM p WHERE price > ?'; "
            + "BEGIN "
            + "  EXECUTE IMMEDIATE :q USING (minp); "
            + "  RETURN 'ok'; "
            + "END");
        final ResultSet rs = engine.executeQuery("SELECT cnt FROM p_out");
        assertEquals("2", rs.getRows().get(0).getValue(0).toString());   // prices 150, 250
    }

    @Test
    public void executeImmediateInsideReturnExpressionIsRejected() {
        engine.execute("CREATE TABLE r (id INTEGER)");
        // EXECUTE IMMEDIATE is a statement, not an expression: RETURN (EXECUTE IMMEDIATE ...) is a
        // syntax error.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "DECLARE q STRING DEFAULT 'SELECT COUNT(*) FROM r'; "
                    + "BEGIN "
                    + "  RETURN (EXECUTE IMMEDIATE :q); "
                    + "END");
            }
        });
    }

    @Test
    public void usingEscapesStringBind() {
        engine.execute("CREATE TABLE q (name VARCHAR)");
        // The bind value contains a single quote — must be escaped, not break the inner INSERT. USING
        // lives inside a block, so the binding runs there.
        engine.executeQuery("""
            DECLARE nm VARCHAR DEFAULT 'O''Brien';
            BEGIN
              EXECUTE IMMEDIATE 'INSERT INTO q VALUES (?)' USING (nm);
              RETURN 'ok';
            END""");
        final ResultSet rs = engine.executeQuery("SELECT name FROM q");
        assertEquals("O'Brien", rs.getRows().get(0).getValue(0).toString());
    }
}
