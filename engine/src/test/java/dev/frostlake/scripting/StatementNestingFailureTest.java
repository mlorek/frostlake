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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.executor.StatementNesting;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A text that runs itself without end fails as a statement does: with an ordinary failure a handler catches, a row
 * in the history, and a session that goes on - where it used to end in a {@code StackOverflowError} past all
 * three. Such a text finds a bound only by never ending, so the class runs on the embedded engine alone and is
 * never sent to an account. A call into a cycle of SQL functions that no CREATE refused is asked here too: the
 * account abandons it as an internal failure, and this engine's own answer is the cycle's sentence.
 */
public class StatementNestingFailureTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
        engine.execute("SET q = 'EXECUTE IMMEDIATE $q'");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void aTextThatRunsItselfFailsAsAStatementDoes() {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("EXECUTE IMMEDIATE $q");
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(StatementNesting.REFUSAL), refused.getMessage());
        assertEquals("still usable", String.valueOf(
            engine.executeQuery("SELECT 'still usable'").getRows().get(0).getValue(0)));
    }

    @Test
    public void aHandlerCatchesItAndWhatWasWrittenBeforeStays() {
        engine.execute("CREATE TABLE seen (n NUMBER)");
        final ResultSet caught = engine.executeQuery("""
            EXECUTE IMMEDIATE $$
            BEGIN
                INSERT INTO seen VALUES (1);
                EXECUTE IMMEDIATE $q;
                RETURN 'not reached';
            EXCEPTION
                WHEN STATEMENT_ERROR THEN
                    RETURN 'caught: ' || SQLERRM;
            END;
            $$""");
        final String answered = String.valueOf(caught.getRows().get(0).getValue(0));
        assertTrue(answered.startsWith("caught: ") && answered.contains("the statement nests deeper"), answered);
        assertEquals("1",
            String.valueOf(engine.executeQuery("SELECT COUNT(*) FROM seen").getRows().get(0).getValue(0)));
    }

    @Test
    public void aFunctionCycleNoCreateRefusedIsRefusedWhenItIsCalled() {
        engine.execute("CREATE FUNCTION r1(n NUMBER) RETURNS NUMBER AS 'n'");
        engine.execute("CREATE FUNCTION r2(n NUMBER) RETURNS NUMBER AS 'r1(n)'");
        engine.execute("DROP FUNCTION r1(NUMBER)");
        // The rename makes r1 call itself, and is accepted, as on an account.
        engine.execute("ALTER FUNCTION r2(NUMBER) RENAME TO r1");
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT r1(1)");
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains("Detected a cycle in SQL UDF: R1"),
            refused.getMessage());
        assertEquals("still usable", String.valueOf(
            engine.executeQuery("SELECT 'still usable'").getRows().get(0).getValue(0)));
    }

    @Test
    public void theHistoryHoldsTheFailedStatement() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("EXECUTE IMMEDIATE $q /* runaway */");
            }
        });
        final ResultSet history = engine.executeQuery("SELECT execution_status FROM "
            + "TABLE(information_schema.query_history()) WHERE query_text = 'EXECUTE IMMEDIATE $q /* runaway */'");
        assertEquals(1, history.getRows().size());
        assertEquals("FAILED_WITH_ERROR", String.valueOf(history.getRows().get(0).getValue(0)));
    }
}
